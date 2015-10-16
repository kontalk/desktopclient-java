/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.system;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.Kontalk;
import org.kontalk.client.Client;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.crypto.PGPUtils.PGPCoderKey;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.Chat;
import org.kontalk.model.Chat.GID;
import org.kontalk.model.MessageContent;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.misc.JID;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.model.MessageContent.GroupCommand.OP;
import org.kontalk.model.ProtoMessage;
import org.kontalk.util.ClientUtils.MessageIDs;
import org.kontalk.util.XMPPUtils;

/**
 * Application control logic.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Control {
    private static final Logger LOGGER = Logger.getLogger(Control.class.getName());

    /** The current application state. */
    public enum Status {
        DISCONNECTING,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SHUTTING_DOWN,
        /** Connection attempt failed. */
        FAILED,
        /** Connection was lost due to error. */
        ERROR
    }

    private final Client mClient;
    private final ChatStateManager mChatStateManager;
    private final AttachmentManager mAttachmentManager;
    private final RosterHandler mRosterHandler;

    private final ViewControl mViewControl;

    private Status mCurrentStatus = Status.DISCONNECTED;

    private Control(Path appDir) {
        mViewControl = new ViewControl();

        mClient = new Client(this);
        mChatStateManager = new ChatStateManager(mClient);
        mAttachmentManager = AttachmentManager.create(appDir, this);
        mRosterHandler = new RosterHandler(this, mClient);
    }

    public RosterHandler getRosterHandler() {
        return mRosterHandler;
    }

    ViewControl getViewControl() {
        return mViewControl;
    }

    /* events from network client */

    public void setStatus(Status status) {
        mCurrentStatus = status;
        mViewControl.changed(new ViewEvent.StatusChanged());

        if (status == Status.CONNECTED) {
            // send all pending messages
            for (Chat chat: ChatList.getInstance().getAll())
                for (OutMessage m : chat.getMessages().getPending())
                    this.sendMessage(m);

            // send public key requests for Kontalk contacts with missing key
            for (Contact contact : ContactList.getInstance().getAll())
                if (contact.getFingerprint().isEmpty())
                    this.maySendKeyRequest(contact);
        } else if (status == Status.DISCONNECTED || status == Status.FAILED) {
            for (Contact contact : ContactList.getInstance().getAll())
                contact.setOffline();
        }
    }

    public void handleException(KonException ex) {
        mViewControl.changed(new ViewEvent.Exception(ex));
    }

    // TODO unused
    public void handleEncryptionErrors(KonMessage message, Contact contact) {
        EnumSet<Coder.Error> errors = message.getCoderStatus().getErrors();
        if (errors.contains(Coder.Error.KEY_UNAVAILABLE) ||
                errors.contains(Coder.Error.INVALID_SIGNATURE) ||
                errors.contains(Coder.Error.INVALID_SENDER)) {
            // maybe there is something wrong with the senders key
            this.maySendKeyRequest(contact);
        }
        this.handleSecurityErrors(message);
    }

    public void handleSecurityErrors(KonMessage message) {
        mViewControl.changed(new ViewEvent.SecurityError(message));
    }

    /**
     * All-in-one method for a new incoming message (except handling server
     * receipts): Create, save and process the message.
     * @return true on success or message is a duplicate, false on unexpected failure
     */
    public boolean newInMessage(MessageIDs ids,
            Optional<Date> serverDate,
            MessageContent content) {
        LOGGER.info("new incoming message, "+ids);

        ContactList contactList = ContactList.getInstance();
        Optional<Contact> optContact = contactList.contains(ids.jid) ?
                contactList.get(ids.jid) :
                this.createContact(ids.jid, "");
        if (!optContact.isPresent()) {
            LOGGER.warning("can't get contact for message");
            return false;
        }
        Contact contact = optContact.get();
        Chat chat = ChatList.getInstance().getOrCreate(contact, ids.xmppThreadID);
        InMessage.Builder builder = new InMessage.Builder(chat, contact, ids.jid);
        builder.xmppID(ids.xmppID);
        if (serverDate.isPresent())
            builder.serverDate(serverDate.get());
        builder.content(content);
        InMessage newMessage = builder.build();

        // TODO always false
        if (chat.getMessages().getAll().contains(newMessage)) {
            LOGGER.info("message already in chat, dropping this one");
            return true;
        }

        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.warning("can't add message to chat");
            return false;
        }

        newMessage.save();

        this.decryptAndDownload(newMessage);

        mViewControl.changed(new ViewEvent.NewMessage(newMessage));

        return newMessage.getID() >= -1;
    }

    public void setSent(MessageIDs ids) {
        Optional<OutMessage> optMessage = findMessage(ids);
        if (!optMessage.isPresent())
            return;

        optMessage.get().setStatus(KonMessage.Status.SENT);
    }

    public void setReceived(MessageIDs ids) {
        Optional<OutMessage> optMessage = findMessage(ids);
        if (!optMessage.isPresent())
            return;

        optMessage.get().setReceived(ids.jid);
    }

    public void setMessageError(MessageIDs ids, Condition condition, String errorText) {
        Optional<OutMessage> optMessage = findMessage(ids);
        if (!optMessage.isPresent())
            return ;
        optMessage.get().setServerError(condition.toString(), errorText);
    }

    /**
     * Inform model (and view) about a received chat state notification.
     */
    public void processChatState(JID jid,
            String xmppThreadID,
            Optional<Date> serverDate,
            ChatState chatState) {
        if (serverDate.isPresent()) {
            long diff = new Date().getTime() - serverDate.get().getTime();
            if (diff > TimeUnit.SECONDS.toMillis(10)) {
                // too old
                return;
            }
        }
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }
        Contact contact = optContact.get();
        Optional<Chat> optChat = ChatList.getInstance().get(contact, xmppThreadID);
        if (!optChat.isPresent())
            return;

        optChat.get().setChatState(contact, chatState);
    }

    public void handlePGPKey(JID jid, byte[] rawKey) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with jid: "+jid);
            return;
        }
        Contact contact = optContact.get();

        Optional<PGPCoderKey> optKey = PGPUtils.readPublicKey(rawKey);
        if (!optKey.isPresent()) {
            LOGGER.warning("invalid public PGP key, contact: "+contact);
            return;
        }
        PGPCoderKey key = optKey.get();

        if (!key.userID.contains("<"+contact.getJID().string()+">")) {
            LOGGER.warning("UID does not contain contact JID");
            return;
        }

        if (key.fingerprint.equals(contact.getFingerprint()))
            // same key
            return;

        if (contact.hasKey())
            // ask before overwriting
            mViewControl.changed(new ViewEvent.NewKey(contact, key));
        else
            this.setKey(contact, key);
    }

    public void setKey(Contact contact, PGPCoderKey key) {
        contact.setKey(key.rawKey, key.fingerprint);

        // enable encryption without asking
        contact.setEncrypted(true);

        // if not set, use uid in key for contact name
        if (contact.getName().isEmpty() && key.userID != null) {
            LOGGER.info("full UID in key: '" + key.userID + "'");
            String contactName = PGPUtils.parseUID(key.userID)[0];
            if (!contactName.isEmpty())
                contact.setName(contactName);
        }
    }

    public void setBlockedContacts(JID[] jids) {
        for (JID jid : jids) {
            if (jid.isFull()) {
                LOGGER.info("ignoring blocking of JID with resource");
                return;
            }
            this.setContactBlocking(jid, true);
        }
    }

    public void setContactBlocking(JID jid, boolean blocking) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.info("ignoring blocking of JID not in contact list");
            return;
        }
        Contact contact = optContact.get();

        LOGGER.info("set contact blocking: "+contact+" "+blocking);
        contact.setBlocked(blocking);
    }

    /* package */

    boolean sendMessage(OutMessage message) {
        if (message.getContent().getAttachment().isPresent() &&
                !message.getContent().getAttachment().get().hasURL()) {
            // continue later...
            mAttachmentManager.queueUpload(message);
            return false;
        }

        boolean sent = mClient.sendMessage(message,
                Config.getInstance().getBoolean(Config.NET_SEND_CHAT_STATE));
        mChatStateManager.handleOwnChatStateEvent(message.getChat(), ChatState.active);
        return sent;
    }

    void maySendKeyRequest(Contact contact) {
        if (!contact.isKontalkUser())
            return;

        if (contact.getSubScription() == Contact.Subscription.UNSUBSCRIBED ||
                contact.getSubScription() == Contact.Subscription.PENDING) {
            LOGGER.info("no presence subscription, not sending key request, contact: "+contact);
            return;
        }
        mClient.sendPublicKeyRequest(contact.getJID());
    }

    Optional<Contact> createContact(JID jid, String name) {
        return this.createContact(jid, name, XMPPUtils.isKontalkJID(jid));
    }

    Optional<Contact> createContact(JID jid, String name, boolean encrypted) {
        if (!mClient.isConnected()) {
            // workaround: create only if contact can be added to roster
            return Optional.empty();
        }

        if (name.isEmpty() && !jid.isHash()){
            name = jid.local();
        }

        Optional<Contact> optNewContact = ContactList.getInstance().create(jid, name);
        if (!optNewContact.isPresent()) {
            LOGGER.warning("can't create new contact");
            // TODO tell view
            return Optional.empty();
        }
        Contact newContact = optNewContact.get();

        newContact.setEncrypted(encrypted);

        this.addToRoster(newContact);

        this.maySendKeyRequest(newContact);

        return Optional.of(newContact);
    }

    /* private */

    /**
     * Decrypt an incoming message and download attachment if present.
     */
    private void decryptAndDownload(InMessage message) {
        boolean decrypted = Coder.decryptMessage(message);

        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.handleSecurityErrors(message);
        }

        Optional<GroupCommand> optCom = message.getContent().getGroupCommand();
        if (decrypted && optCom.isPresent()) {
            this.processGroupCommand(optCom.get(), message.getChat(), message.getContact());
        }

        if (message.getContent().getPreview().isPresent()) {
            mAttachmentManager.savePreview(message);
        }

        if (message.getContent().getAttachment().isPresent()) {
            this.download(message);
        }
    }

    private void download(InMessage message){
        mAttachmentManager.queueDownload(message);
    }

    private void addToRoster(Contact contact) {
        if (contact.isMe())
            return;

        String contactName = contact.getName();
        String rosterName =
                Config.getInstance().getBoolean(Config.NET_SEND_ROSTER_NAME) &&
                !contactName.isEmpty() ?
                contactName :
                contact.getJID().local();
        boolean succ = mClient.addToRoster(contact.getJID(), rosterName);
        if (!succ)
            LOGGER.warning("can't add contact to roster: "+contact);
    }

    private void removeFromRoster(JID jid) {
        boolean succ = mClient.removeFromRoster(jid);
        if (!succ) {
            LOGGER.warning("could not remove contact from roster");
        }
    }

    // warning: call this only once for each group command!
    private void processGroupCommand(GroupCommand command, Chat chat, Contact sender) {
        // note: chat was selected/created by GID so we can be sure message and
        // chat GIDs match
        Optional<GID> optGID = chat.getGID();
        if (!optGID.isPresent()) {
            LOGGER.warning("no GID for chat!?");
            return;
        }
        GID gid = optGID.get();

        OP op = command.getOperation();

        // validation check
        if (op != OP.LEAVE) {
            // sender must be owner
            if (!gid.ownerJID.equals(sender.getJID())) {
                LOGGER.warning("sender not owner");
                return;
            }
        }

        if (op == OP.CREATE || op == OP.SET) {
            // add contacts if necessary
            // TODO design problem here: we need at least the public keys, but user
            // might dont wanna have group members in contact list
            ContactList contactList = ContactList.getInstance();
            for (JID jid : command.getAdded()) {
                if (!contactList.contains(jid)) {
                    boolean succ = this.createContact(jid, "").isPresent();
                    if (!succ)
                        LOGGER.warning("can't create contact, JID: "+jid);
                }
            }
        }

        chat.applyGroupCommand(command, sender);
    }

    /* static */

    public static ViewControl create(Path appDir) {
        return new Control(appDir).mViewControl;
    }

    private static Optional<OutMessage> findMessage(MessageIDs ids) {
        ChatList cl = ChatList.getInstance();

        // get chat by jid -> thread ID -> message id
        Optional<Contact> optContact = ContactList.getInstance().get(ids.jid);
        if (optContact.isPresent()) {
            Optional<Chat> optChat = cl.get(optContact.get(), ids.xmppThreadID);
            if (optChat.isPresent()) {
                Optional<OutMessage> optM = optChat.get().getMessages().getLast(ids.xmppID);
                if (optM.isPresent())
                    return optM;
            }
        }

        // fallback: search everywhere
        LOGGER.info("fallback search, IDs: "+ids);
        for (Chat chat: cl.getAll()) {
            Optional<OutMessage> optM = chat.getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        LOGGER.warning("can't find message by IDs: "+ids);
        return Optional.empty();
    }

    /* commands from view */

    public class ViewControl extends Observable {

        public void launch() {
            new Thread(mClient).start();

            boolean connect = Config.getInstance().getBoolean(Config.MAIN_CONNECT_STARTUP);
            if (!AccountLoader.getInstance().accountIsPresent()) {
                this.changed(new ViewEvent.MissingAccount(connect));
                return;
            }

            if (connect)
                this.connect();
        }

        public void shutDown() {
            this.disconnect();
            LOGGER.info("Shutting down...");
            mCurrentStatus = Status.SHUTTING_DOWN;
            this.changed(new ViewEvent.StatusChanged());
            try {
                Database.getInstance().close();
            } catch (RuntimeException ex) {
                // ignore
            }
            Config.getInstance().saveToFile();

            Kontalk.exit();
        }

        public void connect() {
            this.connect(new char[0]);
        }

        public void connect(char[] password) {
            PersonalKey key = this.keyOrNull(password);
            if (key == null)
                return;

            mClient.connect(key);
        }

        public void disconnect() {
            mChatStateManager.imGone();
            mCurrentStatus = Status.DISCONNECTING;
            this.changed(new ViewEvent.StatusChanged());
            mClient.disconnect();
        }

        public Status getCurrentStatus() {
            return mCurrentStatus;
        }

        public void sendStatusText() {
            mClient.sendInitialPresence();
        }

        public Path getFilePath(Attachment attachment) {
            return mAttachmentManager.filePath(attachment);
        }

        public Optional<Path> getImagePath(KonMessage message) {
            return mAttachmentManager.imagePreviewPath(message);
        }

        /* contact */

        public Optional<Contact> createContact(JID jid, String name, boolean encrypted) {
            return Control.this.createContact(jid, name, encrypted);
        }

        public void deleteContact(Contact contact) {
            ContactList.getInstance().remove(contact);

            Control.this.removeFromRoster(contact.getJID());

            contact.setDeleted();
        }

        public void sendContactBlocking(Contact contact, boolean blocking) {
            mClient.sendBlockingCommand(contact.getJID(), blocking);
        }

        public void changeJID(Contact contact, JID newJID) {
            JID oldJID = contact.getJID();

            if (oldJID.equals(newJID))
                return;

            boolean succ = ContactList.getInstance().changeJID(contact, newJID);
            if (!succ)
                return;

            Control.this.removeFromRoster(oldJID);
            Control.this.addToRoster(contact);
        }

        public void changeName(Contact contact, String name) {
            if (Config.getInstance().getBoolean(Config.NET_SEND_ROSTER_NAME))
                // TODO care about success?
                mClient.updateRosterEntry(contact.getJID(), name);

            contact.setName(name);
        }

        public void requestKey(Contact contact) {
            Control.this.maySendKeyRequest(contact);
        }

        public void acceptKey(Contact contact, PGPCoderKey key) {
            Control.this.setKey(contact, key);
        }

        public void declineKey(Contact contact) {
            this.sendContactBlocking(contact, true);
        }

        /* chats */

        public Chat getOrCreateSingleChat(Contact contact) {
            return ChatList.getInstance().getOrCreate(contact);
        }

        public void createGroupChat(Contact[] contacts, String subject) {
            JID jid = JID.bare(Config.getInstance().getString(Config.ACC_JID));
            if (!jid.isValid()) {
                LOGGER.warning("can't create group, invalid JID");
                return;
            }
            Chat chat = ChatList.getInstance().createNew(contacts,
                    new GID(jid,
                            org.jivesoftware.smack.util.StringUtils.randomString(8)),
                    subject);

            // send create group command
            List<JID> jids = new ArrayList<>(contacts.length);
            for (Contact c: contacts)
                jids.add(c.getJID());
            this.createAndSendMessage(chat,
                    new MessageContent(
                            GroupCommand.create(jids.toArray(new JID[0]),subject)
                    )
            );
        }

        public void deleteChat(Chat chat) {
            if (chat.isGroupChat() && !chat.getContacts().isEmpty()) {
                //note: group chats are not 'deleted', were just leaving them
                boolean sent = this.createAndSendMessage(chat,
                        new MessageContent(GroupCommand.leave()));
                if (!sent)
                    // TODO tell view
                    return;
            }

            ChatList.getInstance().delete(chat.getID());
        }

        public void handleOwnChatStateEvent(Chat chat, ChatState state) {
            mChatStateManager.handleOwnChatStateEvent(chat, state);
        }

        /* messages */

        public void decryptAgain(InMessage message) {
            Control.this.decryptAndDownload(message);
        }

        public void downloadAgain(InMessage message) {
            Control.this.download(message);
        }

        public void sendText(Chat chat, String text) {
            this.sendTextMessage(chat, text, Paths.get(""));
        }

        public void sendAttachment(Chat chat, Path file){
            this.sendTextMessage(chat, "", file);
        }

        /* private */

        private void sendTextMessage(Chat chat, String text, Path file) {
            Attachment attachment = null;
            if (!file.toString().isEmpty()) {
                attachment = AttachmentManager.attachmentOrNull(file);
                if (attachment == null)
                    return;
            }
            MessageContent content =
                    attachment == null ? new MessageContent(text) :
                    new MessageContent(text, attachment);

            this.createAndSendMessage(chat, content);
        }

        private PersonalKey keyOrNull(char[] password) {
            AccountLoader account = AccountLoader.getInstance();
            Optional<PersonalKey> optKey = account.getPersonalKey();
            if (optKey.isPresent())
                return optKey.get();

            if (password.length == 0) {
                if (account.isPasswordProtected()) {
                    this.changed(new ViewEvent.PasswordSet());
                    return null;
                }

                password = Config.getInstance().getString(Config.ACC_PASS).toCharArray();
            }

            try {
                return account.load(password);
            } catch (KonException ex) {
                // something wrong with the account, tell view
                Control.this.handleException(ex);
                return null;
            }
        }

        /**
         * All-in-one method for a new outgoing message: Create,
         * save, process and send message.
         */
        private boolean createAndSendMessage(Chat chat, MessageContent content) {

            LOGGER.config("chat: "+chat+" content: "+content);

            Set<Contact> contacts = chat.getContacts();
            if (contacts.isEmpty()) {
                LOGGER.warning("can't send message, no (valid) contact(s)");
                return false;
            }

            OutMessage.Builder builder = new OutMessage.Builder(chat,
                    contacts.toArray(new Contact[0]),
                    chat.sendEncrypted());
            builder.content(content);
            OutMessage newMessage = builder.build();
            if (newMessage.getContent().getAttachment().isPresent())
                mAttachmentManager.createImagePreview(newMessage);
            boolean added = chat.addMessage(newMessage);
            if (!added) {
                LOGGER.warning("could not add outgoing message to chat");
            }

            return Control.this.sendMessage(newMessage);
        }

        void changed(ViewEvent event) {
            this.setChanged();
            this.notifyObservers(event);
        }
    }
}
