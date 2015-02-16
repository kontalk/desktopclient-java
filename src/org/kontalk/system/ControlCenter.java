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

import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.client.Client;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Account;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Application control logic.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class ControlCenter extends Observable {
    private final static Logger LOGGER = Logger.getLogger(ControlCenter.class.getName());

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

    public ControlCenter() {
        mClient = new Client(this);
    }

    public void launch() {
        new Thread(mClient).start();

        // use password option to determine if account was imported
        KonConf config = KonConf.getInstance();
        if (config.getString(KonConf.ACC_PASS).isEmpty()) {
            this.setChanged();
            this.notifyObservers(new ViewEvent.MissingAccount());
            return;
        }

        if (config.getBoolean(KonConf.MAIN_CONNECT_STARTUP))
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
        if (Database.getInstance() != null)
            Database.getInstance().close();
        KonConf.getInstance().saveToFile();

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
            OutMessage newMessage = MessageCenter.getInstance().newOutMessage(
                    thread,
                    oneUser,
                    text,
                    oneUser.getEncrypted());
            mClient.sendMessage(newMessage);
        }
    }

    public void setUserBlocking(User user, boolean blocking) {
        mClient.sendBlockingCommand(user.getJID(), blocking);
    }

    public Status getCurrentStatus() {
        return mCurrentStatus;
    }

    /* events from network client */

    public void setStatus(Status status) {
        mCurrentStatus = status;
        this.setChanged();
        this.notifyObservers(new ViewEvent.StatusChanged());

        if (status == Status.CONNECTED) {
            // send all pending messages
            for (OutMessage m : MessageList.getInstance().getPendingMessages()) {
                mClient.sendMessage(m);
            }
            // send public key requests for Kontalk users with missing key
            for (User user : UserList.getInstance().getAll()) {
                // TODO only for domains that are part of the Kontalk network
                if (user.getFingerprint().isEmpty()) {
                    LOGGER.info("public key missing for user, requesting it...");
                    mClient.sendPublicKeyRequest(user.getJID());
                }
            }

        }
    }

    public void handleException(KonException ex) {
        this.setChanged();
        this.notifyObservers(new ViewEvent.Exception(ex));
    }

    public void handleSecurityErrors(KonMessage message) {
        this.setChanged();
        this.notifyObservers(new ViewEvent.SecurityError(message));
    }

    public void addUser(String jid, String rosterName) {
            UserList userList = UserList.getInstance();
            if (userList.contains(jid))
                return;

            LOGGER.info("adding user from roster, jid: "+jid);

            String name = rosterName == null ? "" : rosterName;
            if (name.equals(XmppStringUtils.parseLocalpart(jid)) &&
                    name.length() == 40) {
                // this must be the hash string, don't use it as name
                name = "";
            }
            Optional<User> optNewUser = userList.add(jid, name);
            if (!optNewUser.isPresent()) {
                LOGGER.warning("can't add user");
                return;
            }
            // send request for public key
            mClient.sendPublicKeyRequest(optNewUser.get().getJID());
    }

    public void setPresence(String jid, Presence.Type type, String status) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(presence) can't find user with jid: "+jid);
            return;
        }
        optUser.get().setPresence(type, status);
    }

    public void setPGPKey(String jid, byte[] rawKey) {
        Optional<User> optUser = UserList.getInstance().get(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("(PGPKey) can't find user with jid: "+jid);
            return;
        }
        optUser.get().setKey(rawKey);
    }
}
