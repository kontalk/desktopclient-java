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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.roster.packet.RosterPacket.ItemStatus;
import org.jivesoftware.smack.roster.packet.RosterPacket.ItemType;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
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
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.util.XMPPUtils;

/**
 * Application control logic.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Control {
    private static final Logger LOGGER = Logger.getLogger(Control.class.getName());

    private static final String LEGACY_CUT_FROM_ID = " (NO COMMENT)";

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

    /**
     * Message attributes to identify the chat for a message.
     */
    public static class MessageIDs {
        public final String jid;
        public final String xmppID;
        public final String xmppThreadID;
        //public final Optional<GroupID> groupID;

        private MessageIDs(String jid, String xmppID, String threadID) {
            this.jid = jid;
            this.xmppID = xmppID;
            this.xmppThreadID = threadID;
        }

        public static MessageIDs from(Message m) {
            return from(m, "");
        }

        public static MessageIDs from(Message m, String receiptID) {
            return new MessageIDs(
                    StringUtils.defaultString(m.getFrom()),
                    !receiptID.isEmpty() ? receiptID :
                            StringUtils.defaultString(m.getStanzaId()),
                    StringUtils.defaultString(m.getThread()));
        }

        @Override
        public String toString() {
            return "IDs:jid="+jid+",xmpp="+xmppID+",thread="+xmppThreadID;
        }
    }

    private final Client mClient;
    private final ChatStateManager mChatStateManager;
    private final AttachmentManager mAttachmentManager;

    private final ViewControl mViewControl;

    private Status mCurrentStatus = Status.DISCONNECTED;

    private Control() {
        mClient = new Client(this);
        mChatStateManager = new ChatStateManager(mClient);
        Path attachmentDir = Kontalk.getConfigDir().resolve("attachments");
        mAttachmentManager = AttachmentManager.create(this, attachmentDir);

        mViewControl = new ViewControl();
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
                    Control.this.sendKeyRequest(contact);
        } else if (status == Status.DISCONNECTED || status == Status.FAILED) {
            for (Contact contact : ContactList.getInstance().getAll())
                contact.setOffline();
        }
    }

    public void handleException(KonException ex) {
        mViewControl.changed(new ViewEvent.Exception(ex));
    }

    public void handleSecurityErrors(KonMessage message) {
        EnumSet<Coder.Error> errors = message.getCoderStatus().getErrors();
        if (errors.contains(Coder.Error.KEY_UNAVAILABLE) ||
                errors.contains(Coder.Error.INVALID_SIGNATURE) ||
                errors.contains(Coder.Error.INVALID_SENDER)) {
            // maybe there is something wrong with the senders key
            this.sendKeyRequest(message.getContact());
        }
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
        String jid = XmppStringUtils.parseBareJid(ids.jid);
        ContactList contactList = ContactList.getInstance();
        Optional<Contact> optContact = contactList.contains(jid) ?
                contactList.get(jid) :
                this.createNewContact(jid, "", true);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't get contact for message");
            return false;
        }
        Contact contact = optContact.get();
        Chat chat = getChat(ids.xmppThreadID, contact);
        InMessage.Builder builder = new InMessage.Builder(chat, contact);
        builder.jid(ids.jid);
        builder.xmppID(ids.xmppID);
        if (serverDate.isPresent())
            builder.serverDate(serverDate.get());
        builder.content(content);
        InMessage newMessage = builder.build();
        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.info("message already in chat, dropping this one");
            return true;
        }
        newMessage.save();

        this.decryptAndDownload(newMessage);

        mViewControl.changed(new ViewEvent.NewMessage(newMessage));

        return newMessage.getID() >= -1;
    }

    /**
     * Set the receipt status of a message.
     * @param xmppID XMPP ID of message
     * @param status new receipt status of message
     */
    public void setMessageStatus(MessageIDs ids, KonMessage.Status status) {
        Optional<OutMessage> optMessage = getMessage(ids);
        if (!optMessage.isPresent())
            return;
        OutMessage m = optMessage.get();

        if (m.getReceiptStatus() == KonMessage.Status.RECEIVED)
            // probably by another client
            return;

        m.setStatus(status);
    }

    public void setMessageError(MessageIDs ids, Condition condition, String errorText) {
        Optional<OutMessage> optMessage = getMessage(ids);
        if (!optMessage.isPresent())
            return ;
        optMessage.get().setServerError(condition.toString(), errorText);
    }

    /**
     * Inform model (and view) about a received chat state notification.
     */
    public void processChatState(String from,
            String xmppThreadID,
            Optional<Date> serverDate,
            String chatStateString) {
        if (serverDate.isPresent()) {
            long diff = new Date().getTime() - serverDate.get().getTime();
            if (diff > TimeUnit.SECONDS.toMillis(10)) {
                // too old
                return;
            }
        }
        ChatState chatState;
        try {
            chatState = ChatState.valueOf(chatStateString);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "can't parse chat state ", ex);
            return;
        }
        String jid = XmppStringUtils.parseBareJid(from);
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("(chat state) can't find contact with jid: "+jid);
            return;
        }
        Contact contact = optContact.get();
        Chat chat = getChat(xmppThreadID, contact);
        chat.setChatState(contact, chatState);
    }

    public void addContactFromRoster(String jid,
        String rosterName,
        ItemType type,
        ItemStatus itemStatus) {
        if (ContactList.getInstance().contains(jid)) {
            this.setSubscriptionStatus(jid, type, itemStatus);
            return;
        }

        LOGGER.info("adding contact from roster, jid: "+jid);

        String name = rosterName == null ? "" : rosterName;
        if (name.equals(XmppStringUtils.parseLocalpart(jid)) &&
                XMPPUtils.isHash(jid)) {
            // this must be the hash string, don't use it as name
            name = "";
        }

        Optional<Contact> optNewContact = ContactList.getInstance().createContact(jid, name);
        if (!optNewContact.isPresent())
            return;
        Contact newContact = optNewContact.get();

        Contact.Subscription status = rosterToModelSubscription(itemStatus, type);
        newContact.setSubScriptionStatus(status);

        if (status == Contact.Subscription.UNSUBSCRIBED)
            mClient.sendPresenceSubscriptionRequest(jid);

        this.sendKeyRequest(newContact);
    }

    public void setSubscriptionStatus(String jid, ItemType type, ItemStatus itemStatus) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("(subscription) can't find contact with jid: "+jid);
            return;
        }
        optContact.get().setSubScriptionStatus(rosterToModelSubscription(itemStatus, type));
    }

    public void setPresence(String jid, Presence.Type type, String status) {
        if (jid.equals(XmppStringUtils.parseBareJid(mClient.getOwnJID()))
                && !ContactList.getInstance().contains(jid))
            // don't wanna see myself
            return;

        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("(presence) can't find contact with jid: "+jid);
            return;
        }
        optContact.get().setOnline(type, status);
    }

    public void checkFingerprint(String jid, String fingerprint) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("(fingerprint) can't find contact with jid:" + jid);
            return;
        }

        Contact contact = optContact.get();
        if (!contact.getFingerprint().equals(fingerprint)) {
            LOGGER.info("detected public key change, requesting new key...");
            this.sendKeyRequest(contact);
        }
    }

    public void handlePGPKey(String jid, byte[] rawKey) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("(PGPKey) can't find contact with jid: "+jid);
            return;
        }
        Contact contact = optContact.get();

        Optional<PGPCoderKey> optKey = PGPUtils.readPublicKey(rawKey);
        if (!optKey.isPresent()) {
            LOGGER.warning("invalid public PGP key, contact: "+contact);
            return;
        }
        PGPCoderKey key = optKey.get();

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

        // if not set, use uid in key for contact name
        LOGGER.info("full UID in key: '" + key.userID + "'");
        if (contact.getName().isEmpty() && key.userID != null) {
            String contactName = key.userID.replaceFirst(" <[a-f0-9]+@.+>$", "");
            if (contactName.endsWith(LEGACY_CUT_FROM_ID))
                contactName = contactName.substring(0,
                        contactName.length() - LEGACY_CUT_FROM_ID.length());
            LOGGER.info("contact name from key: '" + contactName + "'");
            if (!contactName.isEmpty())
                contact.setName(contactName);
        }
    }

    public void setBlockedContacts(List<String> jids) {
        for (String jid : jids) {
            if (XmppStringUtils.isFullJID(jid)) {
                LOGGER.info("ignoring blocking of JID with resource");
                return;
            }
            this.setContactBlocking(jid, true);
        }
    }

    public void setContactBlocking(String jid, boolean blocking) {
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

    void sendMessage(OutMessage message) {
        if (message.getContent().getAttachment().isPresent() &&
                !message.getContent().getAttachment().get().hasURL()) {
            // continue later...
            mAttachmentManager.queueUpload(message);
            return;
        }

        mClient.sendMessage(message);
        mChatStateManager.handleOwnChatStateEvent(message.getChat(), ChatState.active);
    }

    /* private */

    private Optional<Contact> createNewContact(String jid, String name, boolean encrypted) {
        if (!mClient.isConnected()) {
            // workaround: create only if contact can be added to roster
            return Optional.empty();
        }

        Optional<Contact> optNewContact = ContactList.getInstance().createContact(jid, name);
        if (!optNewContact.isPresent()) {
            LOGGER.warning("can't create new contact");
            // TODO tell view
            return Optional.empty();
        }
        Contact newContact = optNewContact.get();

        newContact.setEncrypted(encrypted);

        boolean succ = mClient.addToRoster(newContact);
        if (!succ)
            LOGGER.warning("can't add new contact to roster: "+newContact);

        return Optional.of(newContact);
    }

    private void sendKeyRequest(Contact contact) {
        if (!XMPPUtils.isKontalkContact(contact))
            return;

        if (contact.getSubScription() == Contact.Subscription.UNSUBSCRIBED ||
                contact.getSubScription() == Contact.Subscription.PENDING) {
            LOGGER.info("no presence subscription, not sending key request, contact: "+contact);
            return;
        }
        mClient.sendPublicKeyRequest(contact.getJID());
    }

    /**
     * Decrypt an incoming message and download attachment if present.
     */
    private void decryptAndDownload(InMessage message) {
        Coder.decryptMessage(message);

        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.handleSecurityErrors(message);
        }

        if (message.getContent().getAttachment().isPresent()) {
            this.download(message);
        }
    }

    private void download(InMessage message){
        mAttachmentManager.queueDownload(message);
    }

    /* static */

    public static ViewControl create() {
        return new Control().mViewControl;
    }

    private static Chat getChat(String xmppThreadID, Contact contact) {
        ChatList chatList = ChatList.getInstance();
        Optional<Chat> optChat = chatList.get(xmppThreadID);
        return optChat.orElse(chatList.get(contact));
    }

    private static Optional<OutMessage> getMessage(MessageIDs ids) {
        // get chat by thread ID
        ChatList tl = ChatList.getInstance();
        Optional<Chat> optChat = tl.get(ids.xmppThreadID);
        if (optChat.isPresent()) {
            return optChat.get().getMessages().getLast(ids.xmppID);
        }

        // get chat by jid
        Optional<Contact> optContact = ContactList.getInstance().get(ids.jid);
        if (optContact.isPresent() && tl.contains(optContact.get())) {
            Optional<OutMessage> optM = tl.get(optContact.get()).getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        // fallback: search everywhere
        for (Chat chat: tl.getAll()) {
            Optional<OutMessage> optM = chat.getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        LOGGER.warning("can't find message by IDs: "+ids);
        return Optional.empty();
    }

    private static Contact.Subscription rosterToModelSubscription(
            RosterPacket.ItemStatus status, RosterPacket.ItemType type) {
        if (type == RosterPacket.ItemType.both ||
                type == RosterPacket.ItemType.to ||
                type == RosterPacket.ItemType.remove)
            return Contact.Subscription.SUBSCRIBED;

        if (status == RosterPacket.ItemStatus.SUBSCRIPTION_PENDING)
            return Contact.Subscription.PENDING;

        return Contact.Subscription.UNSUBSCRIBED;
    }

    /* commands from view */

    public class ViewControl extends Observable {

        public void launch() {
            new Thread(mClient).start();

            boolean connect = Config.getInstance().getBoolean(Config.MAIN_CONNECT_STARTUP);
            if (!AccountLoader.getInstance().isPresent()) {
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
            ContactList.getInstance().save();
            ChatList.getInstance().save();
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
            Optional<PersonalKey> optKey = this.loadKey(password);
            if (!optKey.isPresent())
                return;

            mClient.connect(optKey.get());
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

        public Path getAttachmentDir() {
            return mAttachmentManager.getAttachmentDir();
        }

        /* contact */

        public Optional<Contact> createContact(String jid, String name, boolean encrypted) {
            return Control.this.createNewContact(jid, name, encrypted);
        }

        public void deleteContact(Contact contact) {
            boolean succ = mClient.removeFromRoster(contact);
            if (!succ)
                // only delete if not in roster
                return;

            ContactList.getInstance().remove(contact);

            contact.setDeleted();
        }

        public void sendContactBlocking(Contact contact, boolean blocking) {
            mClient.sendBlockingCommand(contact.getJID(), blocking);
        }

        public void changeJID(Contact contact, String jid) {
            jid = XmppStringUtils.parseBareJid(jid);
            if (contact.getJID().equals(jid))
                return;

            ContactList.getInstance().changeJID(contact, jid);
        }

        public void requestKey(Contact contact) {
            Control.this.sendKeyRequest(contact);
        }

        public void acceptKey(Contact contact, PGPCoderKey key) {
            Control.this.setKey(contact, key);
        }

        public void declineKey(Contact contact) {
            this.sendContactBlocking(contact, true);
        }

        /* chats */

        public Chat createNewChat(Set<Contact> contact) {
            return ChatList.getInstance().createNew(contact);
        }

        public void deleteChat(Chat chat) {
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
            this.sendMessage(chat, text, Paths.get(""));
        }

        public void sendAttachment(Chat chat, Path file){
            this.sendMessage(chat, "", file);
        }

        private void sendMessage(Chat chat, String text, Path file) {
            // TODO no group chat support yet
            Contact contact = null;
            for (Contact c: chat.getContacts()) {
                if (!c.isDeleted()) {
                    contact = c;
                }
            }
            //Contact = chat.getContacts().stream().filter(c -> !c.isDeleted()).findFirst().orElse(null);
            if (contact == null) {
                LOGGER.warning("can't send message, no (valid) contact");
                return;
            }

            Attachment attachment = null;
            if (!file.toString().isEmpty()) {
                attachment = this.attachmentOrNull(file);
                if (attachment == null)
                    return;
            }
            MessageContent content =
                    attachment == null ? new MessageContent(text) :
                    new MessageContent(text, attachment);

            OutMessage newMessage = this.newOutMessage(chat, contact, content,
                    contact.getEncrypted());

            Control.this.sendMessage(newMessage);
        }

        /* private */

        private Optional<PersonalKey> loadKey(char[] password) {
            AccountLoader account = AccountLoader.getInstance();
            Optional<PersonalKey> optKey = account.getPersonalKey();
            if (optKey.isPresent())
                return optKey;

            if (password.length == 0) {
                if (account.isPasswordProtected()) {
                    this.changed(new ViewEvent.PasswordSet());
                    return Optional.empty();
                }

                password = Config.getInstance().getString(Config.ACC_PASS).toCharArray();
            }

            try {
                optKey = Optional.of(account.load(password));
            } catch (KonException ex) {
                // something wrong with the account, tell view
                Control.this.handleException(ex);
                return Optional.empty();
            }
            return optKey;
        }

        /**
         * All-in-one method for a new outgoing message (except sending): Create,
         * save and process the message.
         * @return the new created message
         */
        private OutMessage newOutMessage(Chat chat, Contact contact,
                MessageContent content , boolean encrypted) {
            OutMessage.Builder builder = new OutMessage.Builder(chat, contact, encrypted);
            builder.content(content);
            OutMessage newMessage = builder.build();
            boolean added = chat.addMessage(newMessage);
            if (!added) {
                LOGGER.warning("could not add outgoing message to chat");
            }
            return newMessage;
        }

        private Attachment attachmentOrNull(Path path) {
            File file = path.toFile();
            if (!file.isFile() || !file.canRead()) {
                LOGGER.warning("invalid attachment file: "+path);
                return null;
            }
            String mimeType = null;
            try {
                mimeType = Files.probeContentType(path);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't get attachment mime type", ex);
                return null;
            }
            long length = file.length();
            if (length <= 0) {
                LOGGER.warning("invalid attachment file size: "+length);
                return null;
            }
            return new Attachment(path, mimeType, length);
        }

        private void changed(ViewEvent event) {
            this.setChanged();
            this.notifyObservers(event);
        }
    }
}
