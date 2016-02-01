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

import org.kontalk.model.Account;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
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
import org.kontalk.model.MessageContent;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.misc.JID;
import org.kontalk.model.Avatar;
import org.kontalk.model.GroupChat;
import org.kontalk.model.GroupMetaData.KonGroupData;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.model.ProtoMessage;
import org.kontalk.model.SingleChat;
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

    private final ViewControl mViewControl;

    private final Client mClient;
    private final ChatStateManager mChatStateManager;
    private final AttachmentManager mAttachmentManager;
    private final RosterHandler mRosterHandler;
    private final AvatarHandler mAvatarHandler;
    private final GroupControl mGroupControl;

    private Status mCurrentStatus = Status.DISCONNECTED;

    private Control() {
        mViewControl = new ViewControl();

        mClient = new Client(this);
        mChatStateManager = new ChatStateManager(mClient);
        mAttachmentManager = AttachmentManager.create(this);
        mRosterHandler = new RosterHandler(this, mClient);
        mAvatarHandler = new AvatarHandler(mClient);
        mGroupControl = new GroupControl(this);
    }

    public RosterHandler getRosterHandler() {
        return mRosterHandler;
    }

    public AvatarHandler getAvatarHandler() {
        return mAvatarHandler;
    }

    ViewControl getViewControl() {
        return mViewControl;
    }

    /* events from network client */

    public void setStatus(Status status) {
        mCurrentStatus = status;
        mViewControl.changed(new ViewEvent.StatusChanged());

        if (status == Status.CONNECTED) {
            String[] strings = Config.getInstance().getStringArray(Config.NET_STATUS_LIST);
            mClient.sendUserPresence(strings.length > 0 ? strings[0] : "");
            // send all pending messages
            for (Chat chat: ChatList.getInstance())
                for (OutMessage m : chat.getMessages().getPending())
                    this.sendMessage(m);

            // send public key requests for Kontalk contacts with missing key
            for (Contact contact : ContactList.getInstance())
                if (contact.getFingerprint().isEmpty())
                    this.maySendKeyRequest(contact);

            // TODO check current user avatar on server and upload if necessary
        } else if (status == Status.DISCONNECTED || status == Status.FAILED) {
            for (Contact contact : ContactList.getInstance())
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

        Contact contact = this.getOrCreateContact(ids.jid).orElse(null);
        if (contact == null) {
            LOGGER.warning("can't get contact for message");
            return false;
        }

        // decrypt message now to get group id
        ProtoMessage protoMessage = new ProtoMessage(contact, content);
        if (protoMessage.isEncrypted()) {
            Coder.decryptMessage(protoMessage);
        }

        // NOTE: decryption must be successful to select group chat
        KonGroupData gData = protoMessage.getContent().getGroupData().orElse(null);

        // TODO ignore message if it contains unexpected group commands

        Chat chat = gData != null ?
                ChatList.getInstance().getOrCreate(gData, contact) :
                ChatList.getInstance().getOrCreate(contact, ids.xmppThreadID);

        InMessage newMessage = new InMessage(protoMessage, chat, ids.jid,
                ids.xmppID, serverDate);

        if (newMessage.getID() <= 0)
            return false;

        // TODO implement equals()
        if (chat.getMessages().contains(newMessage)) {
            LOGGER.info("message already in chat, dropping this one");
            return true;
        }

        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.warning("can't add message to chat");
            return false;
        }

        GroupCommand com = newMessage.getContent().getGroupCommand().orElse(null);
        if (com != null) {
            if (chat instanceof GroupChat) {
                mGroupControl.getInstanceFor((GroupChat) chat)
                        .onInMessage(com, contact);
            } else {
                LOGGER.warning("group command for non-group chat");
            }
        }

        this.processContent(newMessage);

        mViewControl.changed(new ViewEvent.NewMessage(newMessage));

        return newMessage.getID() >= -1;
    }

    public void messageSent(MessageIDs ids) {
        OutMessage message = findMessage(ids).orElse(null);
        if (message == null)
            return;

        message.setStatus(KonMessage.Status.SENT);
    }

    public void setReceived(MessageIDs ids) {
        OutMessage message = findMessage(ids).orElse(null);
        if (message == null)
            return;

        message.setReceived(ids.jid);
    }

    public void setMessageError(MessageIDs ids, Condition condition, String errorText) {
        OutMessage message = findMessage(ids).orElse(null);
        if (message == null)
            return ;
        message.setServerError(condition.toString(), errorText);
    }

    /**
     * Inform model (and view) about a received chat state notification.
     */
    public void processChatState(MessageIDs ids,
            Optional<Date> serverDate,
            ChatState chatState) {
        if (serverDate.isPresent()) {
            long diff = new Date().getTime() - serverDate.get().getTime();
            if (diff > TimeUnit.SECONDS.toMillis(10)) {
                // too old
                return;
            }
        }
        Contact contact = ContactList.getInstance().get(ids.jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+ids.jid);
            return;
        }
        // TODO chat states for group chats?
        SingleChat chat = ChatList.getInstance().get(contact, ids.xmppThreadID).orElse(null);
        if (chat == null)
            return;

        chat.setChatState(contact, chatState);
    }

    public void handlePGPKey(JID jid, byte[] rawKey) {
        Contact contact = ContactList.getInstance().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.warning("can't find contact with jid: "+jid);
            return;
        }

        this.handlePGPKey(contact, rawKey);
    }

    void handlePGPKey(Contact contact, byte[] rawKey) {
        PGPCoderKey key = PGPUtils.readPublicKey(rawKey).orElse(null);
        if (key == null) {
            LOGGER.warning("invalid public PGP key, contact: "+contact);
            return;
        }

        if (!key.userID.contains("<"+contact.getJID().string()+">")) {
            LOGGER.warning("UID does not contain contact JID");
            return;
        }

        if (key.fingerprint.equals(contact.getFingerprint()))
            // same key
            return;

        if (contact.hasKey()) {
            // ask before overwriting
            mViewControl.changed(new ViewEvent.NewKey(contact, key));
        } else {
            this.setKey(contact, key);
        }
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
        Contact contact = ContactList.getInstance().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("ignoring blocking of JID not in contact list");
            return;
        }

        LOGGER.info("set contact blocking: "+contact+" "+blocking);
        contact.setBlocked(blocking);
    }

    /* package */

    /**
      * All-in-one method for a new outgoing message: Create,
      * save, process and send message.
      */
    boolean createAndSendMessage(Chat chat, MessageContent content) {
        LOGGER.config("chat: "+chat+" content: "+content);

        if (!chat.isValid()) {
                LOGGER.warning("invalid chat");
                return false;
        }

        Contact[] contacts = chat.getValidContacts();
        if (contacts.length == 0) {
            LOGGER.warning("can't send message, no (valid) contact(s)");
            return false;
        }

        OutMessage newMessage = new OutMessage(chat, contacts, content,
                chat.isSendEncrypted());
        if (newMessage.getContent().getAttachment().isPresent())
            mAttachmentManager.createImagePreview(newMessage);
        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.warning("could not add outgoing message to chat");
        }

        return this.sendMessage(newMessage);
     }

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
        if (!contact.isKontalkUser()) {
            LOGGER.config("not sending, not a kontalk user, contact: "+contact);
            return;
        }

        if (contact.getSubScription() != Contact.Subscription.SUBSCRIBED) {
            LOGGER.config("not sending, no subscription, contact: "+contact);
            return;
        }
        mClient.sendPublicKeyRequest(contact.getJID());
    }

    Optional<Contact> getOrCreateContact(JID jid) {
        Contact contact = ContactList.getInstance().get(jid).orElse(null);
        if (contact != null)
            return Optional.of(contact);

        return this.createContact(jid, "");
    }

    Optional<Contact> createContact(JID jid, String name) {
        return this.createContact(jid, name, XMPPUtils.isKontalkJID(jid));
    }

    void sendPresenceSubscription(JID jid, Client.PresenceCommand command) {
        mClient.sendPresenceSubscription(jid, command);
    }

    /* private */

    private Optional<Contact> createContact(JID jid, String name, boolean encrypted) {
        if (!mClient.isConnected()) {
            // workaround: create only if contact can be added to roster
            return Optional.empty();
        }

        if (name.isEmpty() && !jid.isHash()){
            name = jid.local();
        }

        Contact newContact = ContactList.getInstance().create(jid, name).orElse(null);
        if (newContact == null) {
            LOGGER.warning("can't create new contact");
            // TODO tell view
            return Optional.empty();
        }

        newContact.setEncrypted(encrypted);

        this.addToRoster(newContact);

        this.maySendKeyRequest(newContact);

        return Optional.of(newContact);
    }

    private void decryptAndProcess(InMessage message) {
        if (!message.isEncrypted()) {
            LOGGER.info("message not encrypted");
        } else {
            Coder.decryptMessage(message);
        }

        this.processContent(message);
    }

    /**
     * Download attachment for incoming message if present.
     */
    private void processContent(InMessage message) {
        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.handleSecurityErrors(message);
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

    /* static */

    public static ViewControl create() {
        return new Control().mViewControl;
    }

    private static Optional<OutMessage> findMessage(MessageIDs ids) {
        ChatList cl = ChatList.getInstance();

        // get chat by jid -> thread ID -> message id
        Contact contact = ContactList.getInstance().get(ids.jid).orElse(null);
        if (contact != null) {
            Chat chat = cl.get(contact, ids.xmppThreadID).orElse(null);
            if (chat != null) {
                OutMessage m = chat.getMessages().getLast(ids.xmppID).orElse(null);
                if (m != null)
                    return Optional.of(m);
            }
        }

        // fallback: search everywhere
        LOGGER.info("fallback search, IDs: "+ids);
        for (Chat chat: cl) {
            OutMessage m = chat.getMessages().getLast(ids.xmppID).orElse(null);
            if (m != null)
                return Optional.of(m);
        }

        LOGGER.warning("can't find message by IDs: "+ids);
        return Optional.empty();
    }

    /* commands from view */

    public class ViewControl extends Observable {

        public void launch() {
            new Thread(mClient).start();

            boolean connect = Config.getInstance().getBoolean(Config.MAIN_CONNECT_STARTUP);
            if (!Account.getInstance().isPresent()) {
                LOGGER.info("no account found, asking for import...");
                this.changed(new ViewEvent.MissingAccount(connect));
                return;
            }

            if (connect)
                this.connect();
        }

        public void shutDown(boolean exit) {
            if (mCurrentStatus == Status.SHUTTING_DOWN)
                // we were already here
                return;

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
            Kontalk.removeLock();
            if (exit) {
                LOGGER.info("exit");
                System.exit(0);
            }
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

        public void setStatusText(String newStatus) {
            Config conf = Config.getInstance();
            String[] strings = conf.getStringArray(Config.NET_STATUS_LIST);
            List<String> stats = new ArrayList<>(Arrays.asList(strings));

            stats.remove(newStatus);

            stats.add(0, newStatus);

            if (stats.size() > 20)
                stats = stats.subList(0, 20);

            conf.setProperty(Config.NET_STATUS_LIST, stats.toArray());
            mClient.sendUserPresence(newStatus);
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
            JID jid = contact.getJID();
            ContactList.getInstance().delete(contact);

            Control.this.removeFromRoster(jid);
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

        public void sendSubscriptionResponse(Contact contact, boolean accept) {
            Control.this.sendPresenceSubscription(contact.getJID(),
                    accept ?
                            Client.PresenceCommand.GRANT :
                            Client.PresenceCommand.DENY);
        }

        public void sendSubscriptionRequest(Contact contact) {
            Control.this.sendPresenceSubscription(contact.getJID(),
                    Client.PresenceCommand.REQUEST);
        }

        /* chats */

        public Chat getOrCreateSingleChat(Contact contact) {
            return ChatList.getInstance().getOrCreate(contact);
        }

        public void createGroupChat(List<Contact> contacts, String subject) {
            Contact me = ContactList.getInstance().getMe().orElse(null);
            if (me == null) {
                LOGGER.warning("can't find myself");
                return;
            }

            // user should be part of the group
            List<Contact> withMe = new ArrayList<>(contacts);
            withMe.add(me);

            // TODO
            KonGroupData gData = GroupControl.newKonGroupData(me.getJID());
            //MUCData gData = GroupControl.newMUCGroupData();

            GroupChat chat = ChatList.getInstance().createNew(withMe,
                    gData,
                    subject);

            mGroupControl.getInstanceFor(chat).onCreate();
        }

        public void deleteChat(Chat chat) {
            if (chat instanceof GroupChat) {
                boolean succ = mGroupControl.getInstanceFor((GroupChat) chat).beforeDelete();
                if (!succ)
                    return;
            }

            ChatList.getInstance().delete(chat.getID());
        }

        public void setChatSubject(GroupChat chat, String subject) {
            if (!chat.isAdministratable()) {
                LOGGER.warning("not admin");
                return;
            }
            Control.this.createAndSendMessage(chat, MessageContent.groupCommand(
                    GroupCommand.set(new JID[0], new JID[0], subject)));

            chat.setSubject(subject);
        }

        public void handleOwnChatStateEvent(Chat chat, ChatState state) {
            mChatStateManager.handleOwnChatStateEvent(chat, state);
        }

        /* messages */

        public void decryptAgain(InMessage message) {
            Control.this.decryptAndProcess(message);
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

        public void setUserAvatar(BufferedImage image) {
            Avatar.UserAvatar newAvatar = Avatar.UserAvatar.setImage(image);
            byte[] avatarData = newAvatar.imageData().orElse(null);
            if (avatarData == null || newAvatar.getID().isEmpty())
                return;

            mClient.publishAvatar(newAvatar.getID(), avatarData);
        }

        public void unsetUserAvatar(){
            if (Avatar.UserAvatar.instance().getID().isEmpty())
                return;

            boolean succ = mClient.deleteAvatar();
            if (succ)
                Avatar.UserAvatar.deleteImage();
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
                    attachment == null ?
                    MessageContent.plainText(text) :
                    MessageContent.outgoing(text, attachment);

            Control.this.createAndSendMessage(chat, content);
        }

        private PersonalKey keyOrNull(char[] password) {
            Account account = Account.getInstance();
            PersonalKey key = account.getPersonalKey().orElse(null);
            if (key != null)
                return key;

            if (password.length == 0) {
                if (account.isPasswordProtected()) {
                    this.changed(new ViewEvent.PasswordSet());
                    return null;
                }

                password = account.getPassword();
            }

            try {
                return account.load(password);
            } catch (KonException ex) {
                // something wrong with the account, tell view
                Control.this.handleException(ex);
                return null;
            }
        }

        void changed(ViewEvent event) {
            this.setChanged();
            this.notifyObservers(event);
        }
    }
}
