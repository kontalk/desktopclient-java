/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.client.Client;
import org.kontalk.client.FeatureDiscovery;
import org.kontalk.client.KonMessageSender;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.crypto.PGPUtils.PGPCoderKey;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.JID;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Account;
import org.kontalk.model.Avatar;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat;
import org.kontalk.model.chat.GroupMetaData;
import org.kontalk.model.chat.Member;
import org.kontalk.model.chat.ProtoMember;
import org.kontalk.model.chat.SingleChat;
import org.kontalk.model.message.InMessage;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.MessageContent.Attachment;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.MessageContent.OutAttachment;
import org.kontalk.model.message.OutMessage;
import org.kontalk.model.message.ProtoMessage;
import org.kontalk.persistence.Config;
import org.kontalk.persistence.Database;
import org.kontalk.util.ClientUtils.MessageIDs;
import org.kontalk.util.MessageUtils.SendTask;
import org.kontalk.util.MessageUtils.SendTask.Encryption;
import org.kontalk.util.XMPPUtils;
import org.kontalk.view.View;

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

    /** Interval between retry connection attempts after failure. */
    private static final int RETRY_TIMER_INTERVAL = 20; // seconds

    private final ViewControl mViewControl;

    private final Database mDB;
    private final Client mClient;
    private final Model mModel;
    private final ChatStateManager mChatStateManager;
    private final AttachmentManager mAttachmentManager;
    private final RosterHandler mRosterHandler;
    private final AvatarHandler mAvatarHandler;
    private final GroupControl mGroupControl;

    private boolean mShuttingDown = false;
    private Timer mRetryTimer = null;

    public Control(Path appDir) throws KonException {
        mViewControl = new ViewControl();

        Config.initialize(appDir);

        try {
            mDB = new Database(appDir);
        } catch (KonException ex) {
            LOGGER.log(Level.SEVERE, "can't initialize database", ex);
            throw ex;
        }

        mModel = Model.setup(mDB, appDir);

        mClient = Client.create(this, appDir);
        mChatStateManager = new ChatStateManager(mClient);
        mAttachmentManager = AttachmentManager.create(this, mClient, appDir);
        mRosterHandler = new RosterHandler(this, mClient, mModel);
        mAvatarHandler = new AvatarHandler(mClient, mModel);
        mGroupControl = new GroupControl(this, mModel);
    }

    public void launch(boolean ui) {

        mModel.load();

        if (ui) {
            View view = View.create(mViewControl, mModel).orElse(null);
            if (view == null) {
                this.shutDown(true);
                return; // never reached
            }
            view.init();
        }

        boolean connect = Config.getInstance().getBoolean(Config.MAIN_CONNECT_STARTUP);
        if (!mModel.account().isPresent()) {
            LOGGER.info("no account found, asking for import...");
            mViewControl.changed(new ViewEvent.MissingAccount(connect));
            return;
        }

        if (connect)
            mViewControl.connect();
    }

    public void shutDown(boolean exit) {
        if (mShuttingDown)
            // we were already here
            return;

        mShuttingDown = true;

        LOGGER.info("Shutting down...");
        mViewControl.disconnect();

        mViewControl.changed(new ViewEvent.StatusChange(Status.SHUTTING_DOWN,
                EnumSet.noneOf(FeatureDiscovery.Feature.class)));

        mModel.onShutDown();
        try {
            mDB.close();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "can't close database", ex);
        }

        Config.getInstance().saveToFile();

        if (exit) {
            LOGGER.info("exit");
            System.exit(0);
        }
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

    public void onStatusChange(Status status, EnumSet<FeatureDiscovery.Feature> features) {
        mViewControl.changed(new ViewEvent.StatusChange(status, features));

        Config config = Config.getInstance();
        if (status == Status.CONNECTED) {
            String[] strings = config.getStringArray(Config.NET_STATUS_LIST);
            mClient.sendUserPresence(strings.length > 0 ? strings[0] : "");
            // send all pending messages
            for (Chat chat: mModel.chats())
                chat.getMessages().getPending().forEach(this::sendMessage);

            // send public key requests for Kontalk contacts with missing key
            for (Contact contact : mModel.contacts().getAll(false, false))
                this.maySendKeyRequest(contact);

            // TODO check current user avatar on server and upload if necessary

        } else if (status == Status.DISCONNECTED || status == Status.FAILED) {
            for (Contact contact : mModel.contacts().getAll(false, false))
                contact.setOnlineStatus(Contact.Online.UNKNOWN);
        }

        if ((status == Status.FAILED || status == Status.ERROR)
                    && config.getBoolean(Config.NET_RETRY_CONNECT)) {
            mRetryTimer = new Timer("Retry Timer", true);
            TimerTask task = new TimerTask() {
                private int mCountDown = RETRY_TIMER_INTERVAL;

                @Override
                public void run() {
                    if (mCountDown > 0) {
                        mViewControl.changed(new ViewEvent.RetryTimerMessage(mCountDown--));
                    } else {
                        mViewControl.connect();
                    }
                }
            };
            mRetryTimer.schedule(task, 0, 1000);
        }
    }

    public void onAuthenticated(JID jid) {
        mModel.setUserJID(jid);
    }

    public void onException(KonException ex) {
        mViewControl.changed(new ViewEvent.Exception(ex));
    }

    // TODO unused
    public void onEncryptionErrors(KonMessage message, Contact contact) {
        EnumSet<Coder.Error> errors = message.getCoderStatus().getErrors();
        if (errors.contains(Coder.Error.KEY_UNAVAILABLE) ||
                errors.contains(Coder.Error.INVALID_SIGNATURE) ||
                errors.contains(Coder.Error.INVALID_SENDER)) {
            // maybe there is something wrong with the senders key
            this.sendKeyRequest(contact);
        }
        this.onSecurityErrors(message);
    }

    private void onSecurityErrors(KonMessage message) {
        mViewControl.changed(new ViewEvent.SecurityError(message));
    }

    /**
     * All-in-one method for a new incoming message (except handling server
     * receipts): Create, save and process the message.
     */
    public void onNewInMessage(MessageIDs ids,
            Optional<Date> serverDate,
            MessageContent content) {
        LOGGER.info("new incoming message, "+ids);

        Contact sender = this.getOrCreateContact(ids.jid).orElse(null);
        if (sender == null) {
            LOGGER.warning("can't get contact for message");
            return;
        }

        // decrypt message now to get possible group data
        ProtoMessage protoMessage = new ProtoMessage(sender, content);
        if (protoMessage.isEncrypted()) {
            this.myKey().ifPresent(mk -> Coder.decryptMessage(mk, protoMessage));
        }

        // NOTE: decryption must be successful to select group chat
        GroupMetaData groupData = content.getGroupData().orElse(null);
        Chat chat = groupData != null ?
                mGroupControl.getGroupChat(groupData, sender, content.getGroupCommand()).orElse(null) :
                mModel.chats().getOrCreate(sender, ids.xmppThreadID);
        if (chat == null) {
            LOGGER.warning("no chat found, message lost: "+protoMessage);
            return;
        }

        InMessage newMessage = mModel.createInMessage(
                protoMessage, chat, ids, serverDate).orElse(null);
        if (newMessage == null)
            return;

        GroupCommand com = newMessage.getContent().getGroupCommand().orElse(null);
        if (com != null) {
            if (chat instanceof GroupChat) {
                mGroupControl.getInstanceFor((GroupChat) chat)
                        .onInMessage(com, sender);
            } else {
                LOGGER.warning("group command for non-group chat");
            }
        }

        this.processContent(newMessage);

        mViewControl.changed(new ViewEvent.NewMessage(newMessage));
    }

    public void onMessageSent(MessageIDs ids) {
        OutMessage message = this.findMessage(ids).orElse(null);
        if (message == null)
            return;

        message.setStatus(KonMessage.Status.SENT);
    }

    public void onMessageReceived(MessageIDs ids, Date receivedDate) {
        OutMessage message = this.findMessage(ids).orElse(null);
        if (message == null)
            return;

        message.setReceived(ids.jid, receivedDate);
    }

    public void onMessageError(MessageIDs ids, StanzaError.Condition condition, String errorText) {
        OutMessage message = this.findMessage(ids).orElse(null);
        if (message == null)
            return ;
        message.setServerError(condition.toString(), errorText);
    }

    /**
     * Inform model (and view) about a received chat state notification.
     */
    public void onChatStateNotification(MessageIDs ids,
            Optional<Date> serverDate,
            ChatState chatState) {
        if (serverDate.isPresent()) {
            long diff = new Date().getTime() - serverDate.get().getTime();
            if (diff > TimeUnit.SECONDS.toMillis(10)) {
                // too old
                return;
            }
        }
        Contact contact = mModel.contacts().get(ids.jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+ids.jid);
            return;
        }
        // NOTE: assume chat states are only send for single chats
        SingleChat chat = mModel.chats().get(contact, ids.xmppThreadID).orElse(null);
        if (chat == null)
            // not that important
            return;

        chat.setChatState(contact, chatState);
    }

    public void onPGPKey(JID jid, byte[] rawKey) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.warning("can't find contact with jid: "+jid);
            return;
        }

        this.onPGPKey(contact, rawKey);
    }

    void onPGPKey(Contact contact, byte[] rawKey) {
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
            setKey(contact, key);
        }
    }

    public void onBlockList(JID[] jids) {
        for (JID jid : jids) {
            if (jid.isFull()) {
                LOGGER.info("ignoring blocking of JID with resource");
                return;
            }
            this.onContactBlocked(jid, true);
        }
    }

    public void onContactBlocked(JID jid, boolean blocking) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("ignoring blocking of JID not in contact list");
            return;
        }

        LOGGER.info("set contact blocking: "+contact+" "+blocking);
        contact.setBlocked(blocking);
    }

    public void onLastActivity(JID jid, long lastSecondsAgo, String status) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        if (contact.getOnline() == Contact.Online.YES) {
            // mobile clients connect only for a short time, last seen is some minutes ago but they
            // are actually online
            return;
        }

        if (lastSecondsAgo == 0) {
            // contact is online
            contact.setOnlineStatus(Contact.Online.YES);
            return;
        }

        // 'last seen' seconds to date
        LocalDateTime ldt = LocalDateTime.now().minusSeconds(lastSecondsAgo);
        Date lastSeen = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());

        contact.setLastSeen(lastSeen, status);
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

        List<Contact> contacts = chat.getValidContacts();
        if (contacts.isEmpty()) {
            LOGGER.warning("can't send message, no (valid) contact(s)");
            return false;
        }

        OutMessage newMessage = mModel.createOutMessage(
                chat, contacts, content).orElse(null);
        if (newMessage == null)
            return false;

        if (newMessage.getContent().getOutAttachment().isPresent())
            mAttachmentManager.mayCreateImagePreview(newMessage);

        return this.sendMessage(newMessage);
    }

    boolean sendMessage(OutMessage message) {
        final MessageContent content = message.getContent();
        final OutAttachment attachment = content.getOutAttachment().orElse(null);
        if (attachment != null && !attachment.hasURL()) {
            // continue later...
            mAttachmentManager.queueUpload(message);
            return false;
        }

        final SendTask task = new SendTask(message,
                // TODO which encryption method to use?
                message.isSendEncrypted() ? Encryption.RFC3923 : Encryption.NONE,
                Config.getInstance().getBoolean(Config.NET_SEND_CHAT_STATE));

        if (task.encryption != Encryption.NONE) {
            // prepare encrypted content
            PersonalKey myKey = this.myKey().orElse(null);
            if (myKey == null)
                return false;

            String encryptedData = "";
            if (task.encryption == Encryption.XEP0373) {
                String stanza = KonMessageSender.getSignCryptElement(message);
                encryptedData = Coder.encryptString(myKey, message, stanza);
            } else if (task.encryption == Encryption.RFC3923) {
                // legacy
                Chat chat = message.getChat();
                if (content.getAttachment().isPresent() || content.getGroupCommand().isPresent()
                    || chat.isGroupChat()) {
                    String stanza = KonMessageSender.getEncryptionPayloadRFC3923(content, chat);
                    encryptedData = Coder.encryptStanzaRFC3923(myKey, message, stanza);
                } else {
                    encryptedData = Coder.encryptMessageRFC3923(myKey, message);
                }
            }

            // check also for security errors just to be sure
            if (encryptedData.isEmpty() || !message.getCoderStatus().getErrors().isEmpty()) {
                LOGGER.warning("encryption failed ("+task.encryption+")");
                message.setStatus(KonMessage.Status.ERROR);
                this.onSecurityErrors(message);
                return false;
            } else {
                LOGGER.config("encryption successful ("+task.encryption+")");
            }

            task.setEncryptedData(encryptedData);
        }

        final boolean sent = mClient.sendMessage(task);
        mChatStateManager.handleOwnChatStateEvent(message.getChat(), ChatState.active);
        return sent;
    }

    private static boolean canSendKeyRequest(Contact contact) {
        return contact.isMe() ||
                (contact.isKontalkUser() &&
                contact.getSubScription() == Contact.Subscription.SUBSCRIBED);
    }

    void maySendKeyRequest(Contact contact) {
        if (canSendKeyRequest(contact) && !contact.hasKey())
            this.sendKeyRequest(contact);
    }

    void sendKeyRequest(Contact contact) {
        if (!canSendKeyRequest(contact)) {
            LOGGER.warning("better do not, contact: "+contact);
            return;
        }

        mClient.sendPublicKeyRequest(contact.getJID());
    }

    Optional<Contact> getOrCreateContact(JID jid) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
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

    Optional<PersonalKey> myKey() {
        Optional<PersonalKey> myKey = mModel.account().getPersonalKey();
        if (!myKey.isPresent()) {
            LOGGER.log(Level.WARNING, "can't get personal key");
        }
        return myKey;
    }

    /* private */

    private Optional<Contact> createContact(JID jid, String name, boolean encrypted) {
        if (!mClient.isConnected()) {
            // workaround: create only if contact can be added to roster
            // this is a general problem with XMPPs roster: no real sync possible
            LOGGER.warning("can't create contact, not connected: "+jid);
            return Optional.empty();
        }

        if (name.isEmpty() && !jid.isHash()){
            name = jid.local();
        }

        Contact newContact = mModel.contacts().create(jid, name).orElse(null);
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
            this.myKey().ifPresent(mk -> Coder.decryptMessage(mk, message));
        }

        this.processContent(message);
    }

    private void setKey(Contact contact, PGPCoderKey key) {
        for (Contact c: mModel.contacts().getAll(true, true)) {
            if (key.fingerprint.equals(c.getFingerprint())) {
                LOGGER.warning("key already set, setting for: "+contact+" set for: "+c);
            }
        }

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

    /**
     * Download attachment for incoming message if present.
     */
    private void processContent(InMessage message) {
        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.onSecurityErrors(message);
        }

        message.getContent().getPreview()
                .ifPresent(p -> mAttachmentManager.savePreview(p, message.getID()));

        if (message.getContent().getInAttachment().isPresent()) {
            this.download(message);
        }
    }

    private void download(InMessage message){
        mAttachmentManager.queueDownload(message);
    }

    private void addToRoster(Contact contact) {
        if (contact.isMe())
            return;

        if (contact.isDeleted()) {
            LOGGER.warning("you don't want to add a deleted contact: " + contact);
            return;
        }

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

    private Optional<OutMessage> findMessage(MessageIDs ids) {
        // get chat by jid -> thread ID -> message id
        Contact contact = mModel.contacts().get(ids.jid).orElse(null);
        if (contact != null) {
            Chat chat = mModel.chats().get(contact, ids.xmppThreadID).orElse(null);
            if (chat != null) {
                Optional<OutMessage> optM = chat.getMessages().getLast(ids.xmppID);
                if (optM.isPresent())
                    return optM;
            }
        }

        // TODO group chats

        // fallback: search in every chat
        LOGGER.info("fallback search, IDs: "+ids);
        for (Chat chat: mModel.chats()) {
            Optional<OutMessage> optM = chat.getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        LOGGER.warning("can't find message by IDs: "+ids);
        return Optional.empty();
    }

    /* commands from view */

    public class ViewControl extends Observable {

        public void shutDown() {
            Control.this.shutDown(true);
        }

        public void connect() {
            this.connect(new char[0]);
        }

        public void connect(char[] password) {
            if (mRetryTimer != null)
                mRetryTimer.cancel();

            PersonalKey key = this.keyOrNull(password);
            if (key == null)
                return;

            mClient.connect(key);
        }

        public void disconnect() {
            // this should not be necessary
            if (mRetryTimer != null)
                mRetryTimer.cancel();

            mChatStateManager.imGone();
            mClient.disconnect();
        }

        public void setStatusText(String status) {
            Config conf = Config.getInstance();
            // must be editable
            List<String> stats = new LinkedList<>(Arrays.asList(
                    conf.getStringArray(Config.NET_STATUS_LIST)));

            if (!stats.isEmpty() && stats.get(0).equals(status))
                // did not change
                return;

            stats.remove(status);
            stats.add(0, status);

            if (stats.size() > 20)
                stats = stats.subList(0, 20);

            conf.setProperty(Config.NET_STATUS_LIST, stats.toArray());
            mClient.sendUserPresence(status);
        }

        public void setAccountPassword(char[] oldPass, char[] newPass) throws KonException {
            mModel.account().setPassword(oldPass, newPass);
        }

        public Path getAttachmentDir() {
            return mAttachmentManager.getAttachmentDir();
        }

        /* contact */

        public void createContact(JID jid, String name, boolean encrypted) {
            Control.this.createContact(jid, name, encrypted);
        }

        public void deleteContact(Contact contact) {
            JID jid = contact.getJID();
            mModel.contacts().delete(contact);

            Control.this.removeFromRoster(jid);
        }

        public void sendContactBlocking(Contact contact, boolean blocking) {
            mClient.sendBlockingCommand(contact.getJID(), blocking);
        }

        public void changeJID(Contact contact, JID newJID) {
            JID oldJID = contact.getJID();

            if (oldJID.equals(newJID))
                return;

            boolean succ = mModel.contacts().changeJID(contact, newJID);
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
            Control.this.sendKeyRequest(contact);
        }

        public void acceptKey(Contact contact, PGPCoderKey key) {
            setKey(contact, key);
        }

        public void declineKey(Contact contact) {
            this.sendContactBlocking(contact, true);
            // TODO remember that a key was not accepted
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

        public void createRosterEntry(Contact contact) {
            Control.this.addToRoster(contact);
        }

        /* chats */

        public Chat getOrCreateSingleChat(Contact contact) {
            return mModel.chats().getOrCreate(contact);
        }

        public Optional<GroupChat> createGroupChat(List<Contact> contacts, String subject) {
            // user is part of the group
            List<ProtoMember> members = contacts.stream()
                    .map(ProtoMember::new)
                    .collect(Collectors.toList());
            Contact me = mModel.contacts().getMe().orElse(null);
            if (me == null) {
                LOGGER.warning("can't find myself");
                return Optional.empty();
            }
            members.add(new ProtoMember(me, Member.Role.OWNER));

            GroupChat chat = mModel.chats().createNew(members,
                    GroupControl.newKonGroupData(me.getJID()),
                    subject);

            mGroupControl.getInstanceFor(chat).onCreate();
            return Optional.of(chat);
        }

        public void deleteChat(Chat chat) {
            if (chat instanceof GroupChat) {
                boolean succ = mGroupControl.getInstanceFor((GroupChat) chat).beforeDelete();
                if (!succ)
                    return;
            }

            mModel.chats().delete(chat);
        }

        public void leaveGroupChat(GroupChat chat) {
            mGroupControl.getInstanceFor(chat).onLeave();
        }

        public void setChatSubject(GroupChat chat, String subject) {
            mGroupControl.getInstanceFor(chat).onSetSubject(subject);
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
            this.sendNewMessage(chat, text, Paths.get(""));
        }

        public void sendAttachment(Chat chat, Path file){
            this.sendNewMessage(chat, "", file);
        }

        public void sendAgain(OutMessage outMessage) {
            Control.this.sendMessage(outMessage);
        }

        /* avatar */

        public void setUserAvatar(BufferedImage image) {
            Avatar.UserAvatar newAvatar = Avatar.UserAvatar.set(image);
            byte[] avatarData = newAvatar.imageData().orElse(null);
            if (avatarData == null)
                return;

            mClient.publishAvatar(newAvatar.getID(), avatarData);
        }

        public void unsetUserAvatar(){
            if (!Avatar.UserAvatar.get().isPresent()) {
                LOGGER.warning("no user avatar set");
                return;
            }

            boolean succ = mClient.deleteAvatar();
            if (!succ)
                // TODO
                return;

            Avatar.UserAvatar.remove();
        }

        public void setCustomContactAvatar(Contact contact, BufferedImage image) {
            // overwriting file here!
            contact.setCustomAvatar(new Avatar.CustomAvatar(contact.getID(), image));
        }

        public void unsetCustomContactAvatar(Contact contact) {
            if (!contact.hasCustomAvatarSet()) {
                LOGGER.warning("no custom avatar set, "+contact);
                return;
            }

            contact.deleteCustomAvatar();
        }

        /* private */

        private void sendNewMessage(Chat chat, String text, Path file) {
            Attachment attachment = null;
            if (!file.toString().isEmpty()) {
                attachment = AttachmentManager.createAttachmentOrNull(file);
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
            Account account = mModel.account();
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
                Control.this.onException(ex);
                return null;
            }
        }

        void changed(ViewEvent event) {
            this.setChanged();
            this.notifyObservers(event);
        }

        // TODO
        public AccountImporter createAccountImporter() {
            return new AccountImporter(mModel.account());
        }
    }
}
