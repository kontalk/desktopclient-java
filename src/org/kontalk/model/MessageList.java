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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.Database;
import org.kontalk.Kontalk;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class MessageList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static MessageList INSTANCE;

    private final HashMap<Integer, KonMessage> mMap = new HashMap();

    private MessageList() {
    }

    public void load() {
        Database db = Database.getInstance();
        ResultSet resultSet = db.execSelectAll(KonMessage.TABLE);
        KonMessage.Direction[] dirValues = KonMessage.Direction.values();
        KonMessage.Status[] statusValues = KonMessage.Status.values();
        Coder.Encryption[] encryptionValues = Coder.Encryption.values();
        Coder.Signing[] signingValues = Coder.Signing.values();
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                int threadID = resultSet.getInt("thread_id");
                KonThread thread = ThreadList.getInstance().getThreadByID(threadID);
                int dirIndex = resultSet.getInt("direction");
                KonMessage.Direction dir = dirValues[dirIndex];
                int userID = resultSet.getInt("user_id");
                User user = UserList.getInstance().getUserByID(userID);
                String jid = resultSet.getString("jid");
                String xmppID = resultSet.getString("xmpp_id");
                Date date = new Date(resultSet.getLong("date"));
                int statusIndex = resultSet.getInt("receipt_status");
                KonMessage.Status status = statusValues[statusIndex];
                String receiptID = resultSet.getString("receipt_id");
                String jsonContent = resultSet.getString("content");
                MessageContent content = MessageContent.fromJSONString(jsonContent);
                int encryptionIndex = resultSet.getInt("encryption_status");
                Coder.Encryption encryption = encryptionValues[encryptionIndex];
                int signingIndex = resultSet.getInt("signing_status");
                Coder.Signing signing = signingValues[signingIndex];
                int errorFlags = resultSet.getInt("coder_errors");
                EnumSet<Coder.Error> coderErrors = Database.intToEnumSet(Coder.Error.class, errorFlags);

                KonMessage.Builder builder = new KonMessage.Builder(id, thread, dir, user);
                builder.jid(jid);
                builder.xmppID(xmppID);
                builder.date(date);
                builder.receiptStatus(status);
                builder.receiptID(receiptID == null ? "" : receiptID);
                builder.content(content);
                builder.encryption(encryption);
                builder.signing(signing);
                builder.coderErrors(coderErrors);

                KonMessage newMessage = builder.build();

                thread.add(newMessage);
                mMap.put(id, newMessage);
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    public OutMessage addTo(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        // TODO more possible content
        MessageContent content = new MessageContent(text);
        OutMessage newMessage = new OutMessage(thread, user, content, encrypted);
        thread.addMessage(newMessage);
        mMap.put(newMessage.getID(), newMessage);
        return newMessage;
    }

    public void addFrom(String from,
            String xmppID,
            String xmppThreadID,
            Date date,
            String receiptID,
            MessageContent content) {
        String jid = StringUtils.parseBareAddress(from);
        UserList userList = UserList.getInstance();
        User user = userList.containsUserWithJID(jid) ?
                userList.getUserByJID(jid) :
                userList.addUser(jid, null);
        ThreadList threadList = ThreadList.getInstance();
        KonThread thread = threadList.getThreadByXMPPID(xmppThreadID);
        if (thread == null)
            thread = threadList.getThreadByUser(user);

        InMessage newMessage = new InMessage(thread,
                user,
                from,
                xmppID,
                date,
                receiptID,
                content);

        // decrypt and verify message
        Coder.processInMessage(newMessage);

        if (!newMessage.getSecurityErrors().isEmpty()) {
            Kontalk.getInstance().handleSecurityErrors(newMessage);
        }

        thread.addMessage(newMessage);
        mMap.put(newMessage.getID(), newMessage);

        this.setChanged();
        this.notifyObservers(newMessage);
    }

    public Collection<KonMessage> getMessages() {
        return mMap.values();
    }

    public void updateMsgBySentReceipt(String xmppID, String receiptID) {
        // TODO performance
        KonMessage message = null;
        for (KonMessage m : mMap.values()) {
            if (m.getXMPPID() != null && m.getXMPPID().equals(xmppID))
                message = m;
        }
        if (message == null) {
            LOGGER.warning("can't find message with XMPP id: " + xmppID);
            return;
        }
        if (!(message instanceof OutMessage)) {
            LOGGER.warning("message is not an outgoing message: " + message.getID());
            return;
        }
        ((OutMessage)message).updateBySentReceipt(receiptID);
    }

    public void updateMsgByReceivedReceipt(String receiptID) {
        // TODO performance
        KonMessage message = null;
        for (KonMessage m : mMap.values()) {
            if (m.getReceiptID() != null && m.getReceiptID().equals(receiptID))
                message = m;
        }
        if (message == null) {
            LOGGER.warning("can't find message with receipt id: " + receiptID);
            return;
        }
        if (!(message instanceof OutMessage)) {
            LOGGER.warning("message is not an outgoing message");
            return;
        }
        ((OutMessage)message).updateByReceivedReceipt();
    }

    public static MessageList getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MessageList();
        }
        return INSTANCE;
    }

}
