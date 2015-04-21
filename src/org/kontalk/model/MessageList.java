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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;
import org.kontalk.crypto.Coder;
import org.kontalk.util.EncodingUtils;

/**
 * Central list of all messages.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class MessageList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private final static MessageList INSTANCE = new MessageList();

    // the list is implemented as 'XMPP ID' to "list of messages" map, as equal
    // XMPP IDs are possible but assumed to happen rarely
    // note: map and lists are not thread-safe on modification / iteration!
    private final HashMap<String, List<KonMessage>> mMap = new HashMap<>();

    private MessageList() {
    }

    public void load() {
        Database db = Database.getInstance();
        KonMessage.Direction[] dirValues = KonMessage.Direction.values();
        KonMessage.Status[] statusValues = KonMessage.Status.values();
        Coder.Encryption[] encryptionValues = Coder.Encryption.values();
        Coder.Signing[] signingValues = Coder.Signing.values();
        try (ResultSet resultSet = db.execSelectAll(KonMessage.TABLE)) {
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
                        UserList.getInstance().get(userID);
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
                EnumSet<Coder.Error> coderErrors = EncodingUtils.intToEnumSet(Coder.Error.class, errorFlags);
                CoderStatus coderStatus = new CoderStatus(encryption, signing, coderErrors);
                long sDate = resultSet.getLong("server_date");
                Optional<Date> serverDate = sDate == 0 ?
                        Optional.<Date>empty() :
                        Optional.of(new Date(sDate));

                KonMessage.Builder builder = new KonMessage.Builder(id,
                        optThread.get(),
                        dir,
                        optUser.get(),
                        date);
                builder.jid(jid);
                builder.xmppID(xmppID);
                builder.serverDate(serverDate);
                builder.receiptStatus(status);
                builder.content(content);
                builder.coderStatus(coderStatus);

                KonMessage newMessage = builder.build();

                optThread.get().add(newMessage);
                this.addMessage(newMessage);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    /**
     * Add message without notifying observers.
     */
    private synchronized boolean addMessage(KonMessage m) {
        // small capacity (dunno if this even matters)
        List<KonMessage> l = mMap.getOrDefault(m.getXMPPID(), new ArrayList<KonMessage>(3));
        mMap.putIfAbsent(m.getXMPPID(), l);
        // see KonMessage.equals()
        if (l.contains(m)) {
            LOGGER.warning("message already in message list, ID: "+m.getID());
            return true;
        }
        return l.add(m);
    }

    /**
     * Add a new message to this list.
     * @return true on success, else false
     */
    public boolean add(KonMessage newMessage) {
        boolean success = this.addMessage(newMessage);
        this.setChanged();
        this.notifyObservers(newMessage);
        return success;
    }

    /**
     * Get all outgoing messages with status "PENDING".
     */
    public synchronized Collection<OutMessage> getPendingMessages() {
        // TODO performance, probably additional map needed
        Set<OutMessage> s = new HashSet<>();
        for (List<KonMessage> l : mMap.values()) {
            // TODO use lambda in near future
            for (KonMessage m : l) {
                if (m.getReceiptStatus() == KonMessage.Status.PENDING &&
                        m instanceof OutMessage) {
                    s.add((OutMessage) m);
                }
            }
        }
        return s;
    }

    /**
     * Get the newest (ie last received) outgoing message that has not the status
     * "RECEIVED".
     */
    public synchronized Optional<OutMessage> getUncompletedMessage(String xmppID) {
        if (!mMap.containsKey(xmppID)) {
            LOGGER.warning("can't find message with XMPP ID: " + xmppID);
            return Optional.empty();
        }
        SortedSet<OutMessage> s = new TreeSet<>();
        for (KonMessage m : mMap.get(xmppID)) {
            if (m instanceof OutMessage &&
                    m.getReceiptStatus() != KonMessage.Status.RECEIVED) {
                s.add((OutMessage) m);
            }
        }
        if (s.isEmpty()) {
            LOGGER.warning("can't find any not received outgoing message, XMPP ID: " + xmppID);
            return Optional.empty();
        }
        return Optional.of(s.last());
    }

    public static MessageList getInstance() {
        return INSTANCE;
    }
}
