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

package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Database;
import org.kontalk.crypto.Coder;

/**
 * Central list of all messages.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class MessageList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private final static MessageList INSTANCE = new MessageList();

    private final HashMap<Integer, KonMessage> mMap = new HashMap<>();

    private MessageList() {
    }

    public void load() {
        Database db = Database.getInstance();
        ResultSet resultSet;
        try {
            resultSet = db.execSelectAll(KonMessage.TABLE);
        } catch (SQLException ex) {
            LOGGER.warning("can't get messages from db");
            return;
        }
        KonMessage.Direction[] dirValues = KonMessage.Direction.values();
        KonMessage.Status[] statusValues = KonMessage.Status.values();
        Coder.Encryption[] encryptionValues = Coder.Encryption.values();
        Coder.Signing[] signingValues = Coder.Signing.values();
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                int threadID = resultSet.getInt("thread_id");
                Optional<KonThread> optThread =
                        ThreadList.getInstance().getThreadByID(threadID);
                if (!optThread.isPresent()) {
                    LOGGER.warning("can't find thread, id:"+threadID);
                    continue;
                }
                int dirIndex = resultSet.getInt("direction");
                KonMessage.Direction dir = dirValues[dirIndex];
                int userID = resultSet.getInt("user_id");
                Optional<User> optUser =
                        UserList.getInstance().getUserByID(userID);
                if (!optUser.isPresent()) {
                    LOGGER.warning("can't find user, id:"+userID);
                    continue;
                }
                String jid = resultSet.getString("jid");
                String xmppID = Database.getString(resultSet, "xmpp_id");
                Date date = new Date(resultSet.getLong("date"));
                int statusIndex = resultSet.getInt("receipt_status");
                KonMessage.Status status = statusValues[statusIndex];
                String jsonContent = resultSet.getString("content");
                MessageContent content = MessageContent.fromJSONString(jsonContent);
                int encryptionIndex = resultSet.getInt("encryption_status");
                Coder.Encryption encryption = encryptionValues[encryptionIndex];
                int signingIndex = resultSet.getInt("signing_status");
                Coder.Signing signing = signingValues[signingIndex];
                int errorFlags = resultSet.getInt("coder_errors");
                EnumSet<Coder.Error> coderErrors = Database.intToEnumSet(Coder.Error.class, errorFlags);

                KonMessage.Builder builder = new KonMessage.Builder(id,
                        optThread.get(),
                        dir,
                        optUser.get());
                builder.jid(jid);
                builder.xmppID(xmppID);
                builder.date(date);
                builder.receiptStatus(status);
                builder.content(content);
                builder.encryption(encryption);
                builder.signing(signing);
                builder.coderErrors(coderErrors);

                KonMessage newMessage = builder.build();

                optThread.get().add(newMessage);
                mMap.put(id, newMessage);
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    public void add(KonMessage newMessage) {
        if (mMap.containsKey(newMessage.getID())) {
            LOGGER.warning("message already in message list, ID: "+newMessage.getID());
            return;
        }
        mMap.put(newMessage.getID(), newMessage);

        this.setChanged();
        this.notifyObservers(newMessage);
    }

    public Collection<KonMessage> getMessages() {
        return mMap.values();
    }

    public Optional<OutMessage> getMessageByXMPPID(String xmppID) {
        // TODO performance
        KonMessage message = null;
        for (KonMessage m : mMap.values()) {
            if (m.getXMPPID().equals(xmppID))
                message = m;
        }
        if (message == null) {
            LOGGER.warning("can't find message with XMPP ID: " + xmppID);
            return Optional.empty();
        }
        return checkOutMessage(message);
    }

    private Optional<OutMessage> checkOutMessage(KonMessage message) {
        if (!(message instanceof OutMessage)) {
            LOGGER.warning("message is not an outgoing message");
            return Optional.empty();
        }
        return Optional.of((OutMessage) message);
    }

    public static MessageList getInstance() {
        return INSTANCE;
    }
}
