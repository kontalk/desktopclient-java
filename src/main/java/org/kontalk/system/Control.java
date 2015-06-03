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
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.client.Client;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Account;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageList;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Application control logic.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class Control extends Observable {
    private final static Logger LOGGER = Logger.getLogger(Control.class.getName());

    private final static String LEGACY_CUT_FROM_ID = " (NO COMMENT)";

    /** The current application state. */
    public enum Status {
        DISCONNECTING,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SHUTTING_DOWN,
        FAILED,
        ERROR
    }

    private final Client mClient;

    private Status mCurrentStatus = Status.DISCONNECTED;

    public Control() {
        mClient = new Client(this);
    }

    public void launch() {
        new Thread(mClient).start();

        // use password option to determine if account was imported
        Config config = Config.getInstance();
        boolean connect = config.getBoolean(Config.MAIN_CONNECT_STARTUP);
        if (config.getString(Config.ACC_PASS).isEmpty()) {
            this.setChanged();
            this.notifyObservers(new ViewEvent.MissingAccount(connect));
            return;
        }

        if (connect)
            this.connect();
    }

    /* commands from view */

    public void shutDown() {
        LOGGER.info("Shutting down...");
        mCurrentStatus = Status.SHUTTING_DOWN;
        this.setChanged();
        this.notifyObservers(new ViewEvent.StatusChanged());
        UserList.getInstance().save();
        ThreadList.getInstance().save();
        mClient.disconnect();
        try {
            Database.getInstance().close();
        } catch (RuntimeException ex) {
            // ignore
        }
        Config.getInstance().saveToFile();

        Kontalk.exit();
    }

    public void connect() {
        PersonalKey key;
        try {
            key = Account.getInstance().getPersonalKey();
        } catch (KonException ex) {
            // something wrong with the account, tell view
            this.handleException(ex);
            return;
        }
        mClient.connect(key);
    }

    public void disconnect() {
        mCurrentStatus = Status.DISCONNECTING;
        this.setChanged();
        this.notifyObservers(new ViewEvent.StatusChanged());
        mClient.disconnect();
    }

    public void sendText(KonThread thread, String text) {
        // TODO no group chat support yet
        Set<User> user = thread.getUser();
        for (User oneUser: user) {
            OutMessage newMessage = newOutMessage(
                    thread,
                    oneUser,
                    text,
                    oneUser.getEncrypted());
            mClient.sendMessage(newMessage);
        }
    }

    public void sendUserBlocking(User user, boolean blocking) {
        mClient.sendBlockingCommand(user.getJID(), blocking);
    }

    public Status getCurrentStatus() {
        return mCurrentStatus;
    }

    public KonThread createNewThread(Set<User> user) {
        return ThreadList.getInstance().createNew(user);
    }

    public void createNewUser(String jid, String name, boolean encrypted) {
        Optional<User> optNewUser = UserList.getInstance().add(jid, name);
        if (!optNewUser.isPresent()) {
            LOGGER.warning("can't create new user");
            return;
        }
        optNewUser.get().setEncrypted(encrypted);
    }

    public void sendKeyRequest(User user) {
        mClient.sendPublicKeyRequest(user.getJID());
    }

    public void handleOwnChatStateEvent(KonThread thread, ChatState state) {
        if (state == thread.getMyChatState())
            // ignore state weare already in
            return;

        // currently send states in XEP-0085: active, inactive, composing
        thread.setMyChatState(state);

        Set<User> user = thread.getUser();
        if (user.size() > 1)
            // don't send for groups
            return;

        for (User oneUser : user)
            if (!oneUser.isMe())
                mClient.sendChatState(oneUser.getJID(), thread.getXMPPID(), state);
    }

    /* events from network client */

    public void setStatus(Status status) {
        mCurrentStatus = status;
        this.setChanged();
        this.notifyObservers(new ViewEvent.StatusChanged());

        if (status == Status.CONNECTED) {
            // send all pending messages
            for (OutMessage m : MessageList.getInstance().getPending()) {
                mClient.sendMessage(m);
            }
            // send public key requests for Kontalk users with missing key
            for (User user : UserList.getInstance().getAll()) {
                // TODO only for domains that are part of the Kontalk network
                if (user.getFingerprint().isEmpty()) {
                    LOGGER.info("public key missing for user, requesting it...");
                    this.sendKeyRequest(user);
                }
            }

        }
    }

    public void handleException(KonException ex) {
        this.setChanged();
        this.notifyObservers(new ViewEvent.Exception(ex));
    }

    public void handleSecurityErrors(KonMessage message) {
        EnumSet<Coder.Error> errors = message.getCoderStatus().getErrors();
        if (errors.contains(Coder.Error.KEY_UNAVAILABLE) ||
                errors.contains(Coder.Error.INVALID_SIGNATURE) ||
                errors.contains(Coder.Error.INVALID_SENDER)) {
            // maybe there is something wrong with the senders key
            this.sendKeyRequest(message.getUser());
        }
        this.setChanged();
        this.notifyObservers(new ViewEvent.SecurityError(message));
    }

    /**
     * All-in-one method for a new outgoing message (except sending): Create,
     * save and process the message.
     * @return the new created message
     */
    public OutMessage newOutMessage(KonThread thread, User user, String text, boolean encrypted) {
        MessageContent content = new MessageContent(text);
        OutMessage.Builder builder = new OutMessage.Builder(thread, user, encrypted);
        builder.content(content);
        OutMessage newMessage = builder.build();
        thread.addMessage(newMessage);
        boolean added = MessageList.getInstance().add(newMessage);
        if (!added) {
            LOGGER.warning("could not add outgoing message to message list");
        }
        return newMessage;
    }

    /**
     * All-in-one method for a new incoming message (except handling server
     * receipts): Create, save and process the message.
     * @return true on success or message is a duplicate, false on unexpected failure
     */
    public boolean newInMessage(String from,
            String xmppID,
            String xmppThreadID,
            Optional<Date> serverDate,
            MessageContent content) {
        String jid = XmppStringUtils.parseBareJid(from);
        Optional<User> optUser = this.getOrAddUser(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("can't get user for message");
            return false;
        }
        User user = optUser.get();
        KonThread thread = getThread(xmppThreadID, user);
        if (xmppID.isEmpty()) {
            xmppID = "_kon_" + StringUtils.randomString(8);
        }
        InMessage.Builder builder = new InMessage.Builder(thread, user);
        builder.jid(from);
        builder.xmppID(xmppID);
        builder.serverDate(serverDate);
        builder.content(content);
        InMessage newMessage = builder.build();
        boolean added = MessageList.getInstance().add(newMessage);
        if (!added) {
            LOGGER.info("message already in message list, dropping this one");
            return true;
        }
        newMessage.save();

        this.decryptAndDownload(newMessage);

        thread.addMessage(newMessage);
        return newMessage.getID() >= -1;
    }

    /**
     * Decrypt an incoming message and download attachment if present.
     */
    public void decryptAndDownload(InMessage message) {
        Coder.processInMessage(message);
        if (!message.getCoderStatus().getErrors().isEmpty()) {
            this.handleSecurityErrors(message);
        }

        if (message.getContent().getAttachment().isPresent()) {
            Downloader.getInstance().queueDownload(message);
        }
    }

    /**
     * Set the receipt status of a message.
     * @param xmppID XMPP ID of message
     * @param status new receipt status of message
     */
    public void setMessageStatus(String xmppID, KonMessage.Status status) {
        Optional<OutMessage> optMessage = MessageList.getInstance().getLast(xmppID);
        if (!optMessage.isPresent())
            return;
        OutMessage m = optMessage.get();

        if (m.getReceiptStatus() == KonMessage.Status.RECEIVED)
            // probably by another client
            return;

        m.setStatus(status);
    }

    public void setMessageError(String xmppID, Condition condition, String errorText) {
        Optional<OutMessage> optMessage = MessageList.getInstance().getLast(xmppID);
        if (!optMessage.isPresent()) {
            LOGGER.warning("can't find message for error");
            return ;
        }
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

    public void addUserFromRoster(String jid, String rosterName) {
            if (UserList.getInstance().contains(jid))
                return;

            LOGGER.info("adding user from roster, jid: "+jid);

            String name = rosterName == null ? "" : rosterName;
            if (name.equals(XmppStringUtils.parseLocalpart(jid)) &&
                    name.length() == 40) {
                // this must be the hash string, don't use it as name
                name = "";
            }

            this.addUser(jid, name);
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
        UserList.getInstance().changed();
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

    // only for new incoming messages
    private Optional<User> getOrAddUser(String jid) {
        UserList userList = UserList.getInstance();

        Optional<User> optUser;
        if (userList.contains(jid)) {
            optUser = userList.get(jid);
        } else {
            optUser = this.addUser(jid, "");
            if (optUser.isPresent())
                mClient.addToRoster(optUser.get());
        }
        return optUser;
    }

    private Optional<User> addUser(String jid, String name) {
        UserList userList = UserList.getInstance();
        Optional<User> optNewUser = userList.add(jid, name);
        if (!optNewUser.isPresent()) {
            LOGGER.warning("can't add user");
            return optNewUser;
        }

        // send request for public key
        this.sendKeyRequest(optNewUser.get());

        return optNewUser;
    }

    private static KonThread getThread(String xmppThreadID, User user) {
        ThreadList threadList = ThreadList.getInstance();
        Optional<KonThread> optThread = threadList.get(xmppThreadID);
        return optThread.orElse(threadList.get(user));
    }
}
