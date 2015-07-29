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
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
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
     * Message attributes to identify the thread for a message.
     */
    public static class MessageIDs {
        public final String jid;
        public final String xmppID;
        public final String threadID;
        //public final Optional<GroupID> groupID;

        private MessageIDs(String jid, String xmppID, String threadID) {
            this.jid = jid;
            this.xmppID = xmppID;
            this.threadID = threadID;
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
            return "IDs:jid="+jid+",xmpp="+xmppID+",thread="+threadID;
        }
    }

    private final Client mClient;
    private final ChatStateManager mChatStateManager;

    private final ViewControl mViewControl;

    private Status mCurrentStatus = Status.DISCONNECTED;

    private Control() {
        mClient = new Client(this);
        mChatStateManager = new ChatStateManager(mClient);

        mViewControl = new ViewControl();
    }

    /* events from network client */

    public void setStatus(Status status) {
        mCurrentStatus = status;
        mViewControl.changed(new ViewEvent.StatusChanged());

        if (status == Status.CONNECTED) {
            // send all pending messages
            for (KonThread thread: ThreadList.getInstance().getAll())
                for (OutMessage m : thread.getMessages().getPending()) {
                    this.sendMessage(m);
            }

            // send public key requests for Kontalk users with missing key
            for (User user : UserList.getInstance().getAll()) {
                // TODO only for domains that are part of the Kontalk network
                if (user.getFingerprint().isEmpty()) {
                    LOGGER.info("public key missing for user, requesting it...");
                    Control.this.sendKeyRequest(user);
                }
            }
        } else if (status == Status.DISCONNECTED || status == Status.FAILED) {
            for (User user : UserList.getInstance().getAll())
                user.setOffline();
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
            this.sendKeyRequest(message.getUser());
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
        UserList userList = UserList.getInstance();
        Optional<User> optUser = userList.contains(jid) ?
                userList.get(jid) :
                this.createNewUser(jid, "", true);
        if (!optUser.isPresent()) {
            LOGGER.warning("can't get user for message");
            return false;
        }
        User user = optUser.get();
        KonThread thread = getThread(ids.threadID, user);
        InMessage.Builder builder = new InMessage.Builder(thread, user);
        builder.jid(ids.jid);
        builder.xmppID(ids.xmppID);
        builder.serverDate(serverDate);
        builder.content(content);
        InMessage newMessage = builder.build();
        boolean added = thread.addMessage(newMessage);
        if (!added) {
            LOGGER.info("message already in thread, dropping this one");
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
        optMessage.get().setError(condition.toString(), errorText);
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
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(chat state) can't find user with jid: "+jid);
            return;
        }
        User user = optUser.get();
        KonThread thread = getThread(xmppThreadID, user);
        thread.setChatState(user, chatState);
    }

    public void addUserFromRoster(String jid,
        String rosterName,
        ItemType type,
        ItemStatus itemStatus) {
        if (UserList.getInstance().contains(jid)) {
            this.setSubscriptionStatus(jid, type, itemStatus);
            return;
        }

        LOGGER.info("adding user from roster, jid: "+jid);

        String name = rosterName == null ? "" : rosterName;
        if (name.equals(XmppStringUtils.parseLocalpart(jid)) &&
                XMPPUtils.isHash(jid)) {
            // this must be the hash string, don't use it as name
            name = "";
        }

        Optional<User> optNewUser = UserList.getInstance().createUser(jid, name);
        if (!optNewUser.isPresent())
            return;
        User newUser = optNewUser.get();

        User.Subscription status = rosterToModelSubscription(itemStatus, type);
        newUser.setSubScriptionStatus(status);

        if (status == User.Subscription.UNSUBSCRIBED)
            mClient.sendPresenceSubscriptionRequest(jid);

        this.sendKeyRequest(newUser);
    }

    public void setSubscriptionStatus(String jid, ItemType type, ItemStatus itemStatus) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(subscription) can't find user with jid: "+jid);
            return;
        }
        optUser.get().setSubScriptionStatus(rosterToModelSubscription(itemStatus, type));
    }

    public void setPresence(String jid, Presence.Type type, String status) {
        if (jid.equals(XmppStringUtils.parseBareJid(mClient.getOwnJID()))
                && !UserList.getInstance().contains(jid))
            // don't wanna see myself
            return;

        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(presence) can't find user with jid: "+jid);
            return;
        }
        optUser.get().setOnline(type, status);
    }

    public void checkFingerprint(String jid, String fingerprint) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(fingerprint) can't find user with jid:" + jid);
            return;
        }

        User user = optUser.get();
        if (!user.getFingerprint().equals(fingerprint)) {
            LOGGER.info("detected public key change, requesting new key...");
            this.sendKeyRequest(user);
        }
    }

    public void setPGPKey(String jid, byte[] rawKey) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(PGPKey) can't find user with jid: "+jid);
            return;
        }
        User user = optUser.get();

        Optional<PGPUtils.PGPCoderKey> optKey = PGPUtils.readPublicKey(rawKey);
        if (!optKey.isPresent()) {
            LOGGER.log(Level.WARNING, "can't get public key");
            return;
        }
        PGPUtils.PGPCoderKey key = optKey.get();
        user.setKey(rawKey, key.fingerprint);

        // if not set, use uid in key for user name
        LOGGER.info("full UID in key: '" + key.userID + "'");
        if (user.getName().isEmpty() && key.userID != null) {
            String userName = key.userID.replaceFirst(" <[a-f0-9]+@.+>$", "");
            if (userName.endsWith(LEGACY_CUT_FROM_ID))
                userName = userName.substring(0,
                        userName.length() - LEGACY_CUT_FROM_ID.length());
            LOGGER.info("user name from key: '" + userName + "'");
            if (!userName.isEmpty())
                user.setName(userName);
        }
    }

    public void setBlockedUser(List<String> jids) {
        for (String jid : jids) {
            if (XmppStringUtils.isFullJID(jid)) {
                LOGGER.info("ignoring blocking of JID with resource");
                return;
            }
            this.setUserBlocking(jid, true);
        }
    }

    public void setUserBlocking(String jid, boolean blocking) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.info("ignoring blocking of JID not in user list");
            return;
        }
        User user = optUser.get();

        LOGGER.info("set user blocking: "+user+" "+blocking);
        user.setBlocked(blocking);
    }

    /* private */

    private Optional<User> createNewUser(String jid, String name, boolean encrypted) {
        Optional<User> optNewUser = UserList.getInstance().createUser(jid, name);
        if (!optNewUser.isPresent()) {
            LOGGER.warning("can't create new user");
            // TODO tell view
            return Optional.empty();
        }
        User newUser = optNewUser.get();

        newUser.setEncrypted(encrypted);

        // TODO do this later if not connected
        mClient.addToRoster(newUser);

        return Optional.of(newUser);
    }

    private void sendMessage(OutMessage message) {
        mClient.sendMessage(message);
        mChatStateManager.handleOwnChatStateEvent(message.getThread(), ChatState.active);
    }

    private void sendKeyRequest(User user) {
        if (user.getSubScription() == User.Subscription.UNSUBSCRIBED ||
                user.getSubScription() == User.Subscription.PENDING) {
            LOGGER.info("no presence subscription, not sending key request, user: "+user);
            return;
        }
        mClient.sendPublicKeyRequest(user.getJID());
    }

    /**
     * Decrypt an incoming message and download attachment if present.
     */
    private void decryptAndDownload(InMessage message) {
        Coder.processInMessage(message);

        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.handleSecurityErrors(message);
        }

        if (message.getContent().getAttachment().isPresent()) {
            Downloader.getInstance().queueDownload(message);
        }
    }

    /* static */

    public static ViewControl create() {
        return new Control().mViewControl;
    }

    private static KonThread getThread(String xmppThreadID, User user) {
        ThreadList threadList = ThreadList.getInstance();
        Optional<KonThread> optThread = threadList.get(xmppThreadID);
        return optThread.orElse(threadList.get(user));
    }

    private static Optional<OutMessage> getMessage(MessageIDs ids) {
        // get thread by thread ID
        ThreadList tl = ThreadList.getInstance();
        Optional<KonThread> optThread = tl.get(ids.threadID);
        if (optThread.isPresent()) {
            return optThread.get().getMessages().getLast(ids.xmppID);
        }

        // get thread by thread by jid
        Optional<User> optUser = UserList.getInstance().get(ids.jid);
        if (optUser.isPresent() && tl.contains(optUser.get())) {
            Optional<OutMessage> optM = tl.get(optUser.get()).getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        // fallback: search everywhere
        for (KonThread thread: tl.getAll()) {
            Optional<OutMessage> optM = thread.getMessages().getLast(ids.xmppID);
            if (optM.isPresent())
                return optM;
        }

        LOGGER.warning("can't find message by IDs: "+ids);
        return Optional.empty();
    }

    private static User.Subscription rosterToModelSubscription(
            RosterPacket.ItemStatus status, RosterPacket.ItemType type) {
        if (type == RosterPacket.ItemType.both ||
                type == RosterPacket.ItemType.to ||
                type == RosterPacket.ItemType.remove)
            return User.Subscription.SUBSCRIBED;

        if (status == RosterPacket.ItemStatus.SUBSCRIPTION_PENDING)
            return User.Subscription.PENDING;

        return User.Subscription.UNSUBSCRIBED;
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
            UserList.getInstance().save();
            ThreadList.getInstance().save();
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

        /* user */

        public Optional<User> createUser(String jid, String name, boolean encrypted) {
            return Control.this.createNewUser(jid, name, encrypted);
        }

        public void deleteUser(User user) {
            boolean succ = mClient.removeFromRoster(user);
            if (!succ)
                // only delete if not in roster
                return;

            UserList.getInstance().remove(user);

            user.setDeleted();
        }

        public void sendUserBlocking(User user, boolean blocking) {
            mClient.sendBlockingCommand(user.getJID(), blocking);
        }

        public void changeJID(User user, String jid) {
            jid = XmppStringUtils.parseBareJid(jid);
            if (user.getJID().equals(jid))
                return;

            UserList.getInstance().changeJID(user, jid);
        }

        public void requestKey(User user) {
            Control.this.sendKeyRequest(user);
        }

        /* threads */

        public KonThread createNewThread(Set<User> user) {
            return ThreadList.getInstance().createNew(user);
        }

        public void deleteThread(KonThread thread) {
            ThreadList.getInstance().delete(thread.getID());
        }

        public void handleOwnChatStateEvent(KonThread thread, ChatState state) {
            mChatStateManager.handleOwnChatStateEvent(thread, state);
        }

        /* messages */

        public void decryptAgain(InMessage message) {
            Control.this.decryptAndDownload(message);
        }

        public void sendText(KonThread thread, String text) {
            // TODO no group chat support yet
            Set<User> user = thread.getUser();
            for (User oneUser: user) {
                if (oneUser.isDeleted())
                    continue;
                OutMessage newMessage = this.newOutMessage(
                        thread,
                        oneUser,
                        text,
                        oneUser.getEncrypted());
                Control.this.sendMessage(newMessage);
            }
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
        private OutMessage newOutMessage(KonThread thread, User user, String text, boolean encrypted) {
            MessageContent content = new MessageContent(text);
            OutMessage.Builder builder = new OutMessage.Builder(thread, user, encrypted);
            builder.content(content);
            OutMessage newMessage = builder.build();
            boolean added = thread.addMessage(newMessage);
            if (!added) {
                LOGGER.warning("could not add outgoing message to thread");
            }
            return newMessage;
        }

        private void changed(ViewEvent event) {
            this.setChanged();
            this.notifyObservers(event);
        }
    }
}
