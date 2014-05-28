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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Packet;
import org.kontalk.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class KonMessage extends ChangeSubject implements Comparable<KonMessage> {
    private final static Logger LOGGER = Logger.getLogger(KonMessage.class.getName());

    public static enum Direction {IN, OUT};
    public static enum Status {IN, //ACKNOWLEDGED, // for incoming
                               PENDING, SENT, RECEIVED, // for outgoing
                               ERROR};

    public final static String TABLE = "messages";
    public final static String CREATE_TABLE = "( " +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "direction INTEGER NOT NULL, " +
            // from or to user
            "user_id INTEGER NOT NULL, " +
            // full jid with ressource
            "jid TEXT NOT NULL, " +
            // optional, but required for receipts
            "xmpp_id TEXT UNIQUE, " +
            // this is the create/received timestamp
            // this will not change after insert EVER
            "date INTEGER NOT NULL, " +
            "read INTEGER NOT NULL, " +
            // server receipt status
            "status INTEGER NOT NULL, " +
            // receipt id
            "receipt_id TEXT UNIQUE, " +

            "content BLOB, " +
            "encrypted INTEGER NOT NULL, " +

            "FOREIGN KEY (thread_id) REFERENCES thread (_id), " +
            "FOREIGN KEY (user_id) REFERENCES user (_id) " +
            ")";

    private final int mID;
    private final KonThread mThread;
    private final Direction mDir;
    private final User mUser;
    private final String mJID;
    private final String mXMPPID;
    private final Date mDate;
    private boolean mRead;
    private Status mStatus;
    private String mReceiptID;
    private final String mText;
    private final boolean mEncrypted;

    /**
     * Used when sending a new message
     */
    KonMessage(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        mThread = thread;
        mDir = Direction.OUT;
        mUser = user;
        mJID = user.getJID();
        mXMPPID = Packet.nextID();
        mDate = new Date(); // "now"
        mRead = false;
        mStatus = Status.PENDING;
        mReceiptID = null;
        mText = text;
        mEncrypted = encrypted;

        mID = this.insert();
    }

    /**
     * Used when receiving a new message
     */
    KonMessage(KonThread thread,
            User user,
            String jid,
            String xmppID,
            Date date,
            String receiptID,
            String text,
            boolean encrypted) {
        mThread = thread;
        mDir = Direction.IN;
        mUser = user;
        mJID = jid;
        mXMPPID = xmppID;
        mDate = date;
        mRead = false;
        mStatus = Status.IN;
        mReceiptID = receiptID;
        mText = text;
        mEncrypted = encrypted;

        mID = this.insert();
    }

    /**
     * Used when loading from database
     */
    KonMessage(int id,
            KonThread thread,
            Direction dir,
            User user,
            String jid,
            String xmppID,
            Date date,
            boolean read,
            Status status,
            String receiptID,
            String text,
            boolean encrypted) {
        mID = id;
        mThread = thread;
        mDir = dir;
        mUser = user;
        mJID = jid;
        mXMPPID = xmppID;
        mDate = date;
        mRead = read;
        mStatus = status;
        mReceiptID = receiptID;
        mText = text;
        mEncrypted = encrypted;
    }

    public int getID() {
        return mID;
    }

    public KonThread getThread() {
        return mThread;
    }

    public Direction getDir() {
        return mDir;
    }

    public String getJID() {
        return mJID;
    }

    public String getXMPPID() {
        return mXMPPID;
    }

    public Date getDate() {
        return mDate;
    }

    public Status getStatus() {
        return mStatus;
    }

    String getReceiptID() {
        return mReceiptID;
    }

    public String getText() {
        return mText;
    }

    @Override
    public int compareTo(KonMessage o) {
        int idComp = Integer.compare(this.mID, o.mID);
        int dateComp = mDate.compareTo(o.getDate());
        return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
    }

    void updateBySentReceipt(String receiptID) {
        assert mDir == Direction.OUT;
        assert mStatus == Status.PENDING;
        assert mReceiptID == null;
        mReceiptID = receiptID;
        mStatus = Status.SENT;
        this.save();
        this.changed();
    }

    void updateByReceivedReceipt() {
        assert mDir == Direction.OUT;
        assert mStatus == Status.SENT;
        assert mReceiptID != null;
        mStatus = Status.RECEIVED;
        this.save();
        this.changed();
    }

    private int insert(){
        Database db = Database.getInstance();
        List<Object> values = new LinkedList();
        values.add(mThread.getID());
        values.add(mDir.ordinal());
        values.add(mUser.getID());
        values.add(mJID);
        values.add(mXMPPID);
        values.add(mDate);
        values.add(mRead);
        values.add(mStatus.ordinal());
        values.add(mReceiptID);
        values.add(mText);
        values.add(mEncrypted);

        int id = db.execInsert(TABLE, values);
        if (id < 1) {
            LOGGER.log(Level.WARNING, "couldn't insert message");
        }
        return id;
    }

    private void save() {
       Database db = Database.getInstance();
       Map<String, Object> set = new HashMap();
       set.put("xmpp_id", mXMPPID);
       set.put("read", mRead);
       set.put("status", mStatus.ordinal());
       set.put("receipt_id", mReceiptID);
       db.execUpdate(TABLE, set, mID);
    }
}
