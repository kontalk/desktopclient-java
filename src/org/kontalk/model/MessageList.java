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
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MessageList {
    private final static Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static MessageList INSTANCE;

    private final HashMap<Integer, KontalkMessage> mMap = new HashMap();

    private MessageList() {
    }

    public void load() {
        Database db = Database.getInstance();
        ResultSet resultSet = db.execSelectAll(KontalkMessage.TABLE);
        KontalkMessage.Direction[] dirValues = KontalkMessage.Direction.values();
        KontalkMessage.Status[] statusValues = KontalkMessage.Status.values();
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                int threadID = resultSet.getInt("thread_id");
                KontalkThread thread = ThreadList.getInstance().getThreadByID(threadID);
                int dirIndex = resultSet.getInt("direction");
                KontalkMessage.Direction dir = dirValues[dirIndex];
                int userID = resultSet.getInt("user_id");
                User user = UserList.getInstance().getUserByID(userID);
                String jid = resultSet.getString("jid");
                String xmppID = resultSet.getString("xmpp_id");
                Date date = new Date(resultSet.getLong("date"));
                boolean read = resultSet.getBoolean("read");
                int statusIndex = resultSet.getInt("status");
                KontalkMessage.Status status = statusValues[statusIndex];
                String receiptID = resultSet.getString("receipt_id");
                String text = resultSet.getString("content");
                boolean encrypted = resultSet.getBoolean("encrypted");
                KontalkMessage newMessage = new KontalkMessage(id,
                        thread,
                        dir,
                        user,
                        jid,
                        xmppID,
                        date,
                        read,
                        status,
                        receiptID,
                        text,
                        encrypted);
                thread.add(newMessage);
                mMap.put(id, newMessage);
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    public String addTo(KontalkThread thread,
            User user,
            String text,
            boolean encrypted) {
        KontalkMessage newMessage = new KontalkMessage(thread, user, text, encrypted);
        thread.add(newMessage);
        mMap.put(newMessage.getID(), newMessage);
        return newMessage.getXMPPID();
    }

    public void addFrom(String from,
            String xmppID,
            String xmppThreadID,
            Date date,
            String receiptID,
            String text,
            boolean encrypted) {
        User user = UserList.getInstance().getUserByJID(from);
        ThreadList threadList = ThreadList.getInstance();
        KontalkThread thread = threadList.getThreadByXMPPID(xmppThreadID);
        if (thread == null)
            thread = threadList.getThreadByUser(user);

        KontalkMessage newMessage = new KontalkMessage(thread,
                user,
                from,
                xmppID,
                date,
                receiptID,
                text,
                encrypted);
        thread.add(newMessage);
        mMap.put(newMessage.getID(), newMessage);
    }

    public void updateMsgBySentReceipt(String xmppID, String receiptID) {
        // TODO performance
        KontalkMessage message = null;
        for (KontalkMessage m : mMap.values()) {
            if (m.getXMPPID() != null && m.getXMPPID().equals(xmppID))
                message = m;
        }
        if (message == null) {
            LOGGER.warning("can't find message with XMPP id: " + xmppID);
            return;
        }
        message.updateBySentReceipt(receiptID);
    }

    public void updateMsgByReceivedReceipt(String receiptID) {
        // TODO performance
        KontalkMessage message = null;
        for (KontalkMessage m : mMap.values()) {
            if (m.getReceiptID() != null && m.getReceiptID().equals(receiptID))
                message = m;
        }
        if (message == null) {
            LOGGER.warning("can't find message with receipt id: " + receiptID);
            return;
        }
        message.updateByReceivedReceipt();
    }

    public static MessageList getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MessageList();
        }
        return INSTANCE;
    }

}
