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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Database;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class KonMessage extends Observable implements Comparable<KonMessage> {
    private final static Logger LOGGER = Logger.getLogger(KonMessage.class.getName());

    /**
     * Direction (in-, outgoing) of one message.
     * Do not modify, only add! Ordinal used in database.
     */
    public static enum Direction {IN, OUT};

    /**
     * Receipt status of one message.
     * Do not modify, only add! Ordinal used in database
     */
    public static enum Status {IN, //ACKNOWLEDGED, // for incoming
                               PENDING, SENT, RECEIVED, // for outgoing
                               ERROR};

    public final static String TABLE = "messages";
    public final static String CREATE_TABLE = "( " +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            // enum, in- or outgoing
            "direction INTEGER NOT NULL, " +
            // from or to user
            "user_id INTEGER NOT NULL, " +
            // full jid with ressource
            "jid TEXT NOT NULL, " +
            // optional, but required for receipts
            // TODO RFC 6120 says it doesn't have to be unique
            "xmpp_id TEXT UNIQUE, " +
            // unix time, create/received timestamp
            "date INTEGER NOT NULL, " +
            // enum, server receipt status
            "receipt_status INTEGER NOT NULL, " +
            // receipt id (Kontalk XMPP extension based on XEP-0184)
            "receipt_id TEXT UNIQUE, " +
            // message content in JSON format
            "content TEXT NOT NULL, " +
            // enum, determines if content is encrypted
            "encryption_status INTEGER NOT NULL, " +
            // enum, determines if content is verified
            // can only tell if signed after encryption attempt
            "signing_status INTEGER NOT NULL, " +
            // encryption and signing errors
            "coder_errors INTEGER NOT NULL, " +

            "FOREIGN KEY (thread_id) REFERENCES "+KonThread.TABLE+" (_id), " +
            "FOREIGN KEY (user_id) REFERENCES "+User.TABLE+" (_id) " +
            ")";

    private final int mID;
    private final KonThread mThread;
    private final Direction mDir;
    private final User mUser;

    private final String mJID;
    private final String mXMPPID;

    private final Date mDate;
    protected Status mReceiptStatus;
    protected String mReceiptID;
    protected final MessageContent mContent;

    protected Coder.Encryption mEncryption;
    protected Coder.Signing mSigning;
    protected final EnumSet<Coder.Error> mCoderErrors;

    protected KonMessage(Builder builder) {
        mThread = builder.mThread;
        mDir = builder.mDir;
        // TODO group message stuff
        mUser = builder.mUser;
        mJID = builder.mJID;
        mXMPPID = builder.mXMPPID;
        mDate = builder.mDate;
        mReceiptStatus = builder.mReceiptStatus;
        mReceiptID = builder.mReceiptID;
        mContent = builder.mContent;
        mEncryption = builder.mEncryption;
        mSigning = builder.mSigning;
        mCoderErrors = builder.mCoderErrors;

        if (mJID == null ||
                mXMPPID == null ||
                mDate == null ||
                mReceiptStatus == null ||
                mReceiptID == null ||
                mContent == null ||
                mEncryption == null ||
                mSigning == null ||
                mCoderErrors == null)
            throw new IllegalStateException();

        if (builder.mID >= 0)
            mID = builder.mID;
        else
            mID = this.insert();
    }

    private int insert() {
        Database db = Database.getInstance();

        List<Object> values = new LinkedList<>();
        values.add(mThread.getID());
        values.add(mDir);
        values.add(mUser.getID());
        values.add(mJID);
        values.add(mXMPPID.isEmpty() ? null : mXMPPID);
        values.add(mDate);
        values.add(mReceiptStatus);
        values.add(mReceiptID.isEmpty() ? null : mReceiptID);
        // i simply don't like to save all possible content explicitly in the
        // database, so we use JSON here
        values.add(mContent.toJSONString());
        values.add(mEncryption);
        values.add(mSigning);
        values.add(mCoderErrors);

        // database contains request and insert as atomic action
        synchronized (KonMessage.class) {
            if (!mReceiptID.isEmpty()) {
                if (db.execCount(TABLE, "receipt_id", mReceiptID) > 0) {
                    LOGGER.info("db already contains a message with receipt ID: "+mReceiptID);
                    return -1;
                }
            }
            int id = db.execInsert(TABLE, values);
            if (id <= 0)
                LOGGER.log(Level.WARNING, "db, couldn't insert message");
            return id;
        }
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

    public User getUser() {
        return mUser;
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

    public Status getReceiptStatus() {
        return mReceiptStatus;
    }

    public String getReceiptID() {
        return mReceiptID;
    }

    public MessageContent getContent() {
        return mContent;
    }

    // TODO
    public boolean isEncrypted() {
        return mEncryption == Coder.Encryption.ENCRYPTED ||
                mEncryption == Coder.Encryption.DECRYPTED;
    }

    public Coder.Encryption getEncryption() {
        return mEncryption;
    }

    public Coder.Signing getSigning() {
        return mSigning;
    }

    public void setSigning(Coder.Signing signing) {
        if (signing == mSigning)
            return;
        if (signing == Coder.Signing.NOT)
            assert mSigning == Coder.Signing.UNKNOWN;
        if (signing == Coder.Signing.SIGNED)
            assert mSigning == Coder.Signing.UNKNOWN;
        if (signing == Coder.Signing.VERIFIED)
            assert mSigning == Coder.Signing.SIGNED;
        mSigning = signing;
    }

    public void addSecurityError(Coder.Error error) {
        mCoderErrors.add(error);
        this.save();
    }

    public EnumSet<Coder.Error> getSecurityErrors() {
        // better return a copy
        return mCoderErrors.clone();
    }

    public boolean hasSecurityError(Coder.Error error) {
        return mCoderErrors.contains(error);
    }

    public void resetSecurityErrors() {
        mCoderErrors.clear();
        this.save();
    }

    @Override
    public int compareTo(KonMessage o) {
        int idComp = Integer.compare(this.mID, o.mID);
        int dateComp = mDate.compareTo(o.getDate());
        return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
    }

    protected void save() {
       Database db = Database.getInstance();
       Map<String, Object> set = new HashMap<>();
       set.put("xmpp_id", mXMPPID.isEmpty() ? null : mXMPPID);
       set.put("receipt_status", mReceiptStatus);
       set.put("receipt_id", mReceiptID.isEmpty() ? null : mReceiptID);
       set.put("content", mContent.toJSONString());
       set.put("encryption_status", mEncryption);
       set.put("signing_status", mSigning);
       set.put("coder_errors", mCoderErrors);
       db.execUpdate(TABLE, set, mID);
    }

    boolean delete() {
        Database db = Database.getInstance();
        return db.execDelete(TABLE, mID);
    }

    protected synchronized void changed() {
        this.setChanged();
        this.notifyObservers();
    }

    static class Builder {
        private final int mID;
        private final KonThread mThread;
        private final Direction mDir;
        private final User mUser;

        protected String mJID = null;
        protected String mXMPPID = null;

        protected Date mDate = null;
        protected Status mReceiptStatus = null;
        protected String mReceiptID = null;
        protected MessageContent mContent = null;

        protected Coder.Encryption mEncryption = null;
        protected Coder.Signing mSigning = null;
        protected EnumSet<Coder.Error> mCoderErrors = null;

        /**
        * Used when loading from database
        */
        Builder(int id,
                KonThread thread,
                Direction dir,
                User user) {
            mID = id;
            mThread = thread;
            mDir = dir;
            mUser = user;
        }

        public void jid(String jid) { mJID = jid; }
        public void xmppID(String xmppID) { mXMPPID = xmppID; }

        public void date(Date date) { mDate = date; }
        public void receiptStatus(Status status) { mReceiptStatus = status; }
        public void receiptID(String id) { mReceiptID = id; }
        public void content(MessageContent content) { mContent = content; }

        public void encryption(Coder.Encryption encryption) { mEncryption = encryption; }
        public void signing(Coder.Signing signing) { mSigning = signing; }
        public void coderErrors(EnumSet<Coder.Error> coderErrors) { mCoderErrors = coderErrors; }

        KonMessage build() {
            if (mDir == Direction.IN)
                return new InMessage(this);
            else
                return new OutMessage(this);
        }

    }

}
