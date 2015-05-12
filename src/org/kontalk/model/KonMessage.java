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
import java.util.Objects;
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.system.Database;
import org.kontalk.crypto.Coder;
import org.kontalk.util.EncodingUtils;

/**
 * Base class for incoming and outgoing XMMP messages.
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
     * Sending status of one message.
     * Do not modify, only add! Ordinal used in database
     */
    public static enum Status {
        /** For all incoming messages. */
        IN,
        //ACKNOWLEDGED,
        /** Outgoing message, message is about to be send. */
        PENDING,
        /** Outgoing message, message was handled by server. */
        SENT,
        /** Outgoing message, message was received by recipient. */
        RECEIVED,
        /** Outgoing message, an error occurred somewhere in the transmission. */
        ERROR
    };

    public final static String TABLE = "messages";
    public static final String COL_THREAD_ID = "thread_id";
    public static final String COL_DIR = "direction";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_JID = "jid";
    public static final String COL_XMPP_ID = "xmpp_id";
    public static final String COL_DATE = "date";
    public static final String COL_REC_STAT = "receipt_status";
    public static final String COL_CONTENT = "content";
    public static final String COL_ENCR_STAT = "encryption_status";
    public static final String COL_SIGN_STAT = "signing_status";
    public static final String COL_COD_ERR = "coder_errors";
    public static final String COL_SERV_ERR = "server_error";
    public static final String COL_SERV_DATE = "server_date";
    public final static String CREATE_TABLE = "( " +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_THREAD_ID + " INTEGER NOT NULL, " +
            // enum, in- or outgoing
            COL_DIR + " INTEGER NOT NULL, " +
            // from or to user
            COL_USER_ID + " INTEGER NOT NULL, " +
            // full jid with ressource
            COL_JID + " TEXT NOT NULL, " +
            // XMPP ID attribute; only recommended (RFC 6120), but we generate
            // a random string if not in message for model consistency
            // Note: must be unique only within a stream (RFC 6120)
            COL_XMPP_ID + " TEXT NOT NULL, " +
            // unix time, local creation timestamp
            COL_DATE + " INTEGER NOT NULL, " +
            // enum, server receipt status
            COL_REC_STAT + " INTEGER NOT NULL, " +
            // message content in JSON format
            COL_CONTENT + " TEXT NOT NULL, " +
            // enum, determines if content is encrypted
            COL_ENCR_STAT + " INTEGER NOT NULL, " +
            // enum, determines if content is verified
            // can only tell if signed after encryption attempt
            COL_SIGN_STAT + " INTEGER NOT NULL, " +
            // enum set, encryption and signing errors of content
            COL_COD_ERR + " INTEGER NOT NULL, " +
            // optional error reply in JSON format
            COL_SERV_ERR + " TEXT, " +
            // unix time, transmission/delay timestamp
            COL_SERV_DATE + " INTEGER, " +
            // if this combinations is equal we consider messages to be equal
            // (see .equals())
            "UNIQUE (direction, jid, xmpp_id, date), " +
            "FOREIGN KEY (thread_id) REFERENCES "+KonThread.TABLE+" (_id), " +
            "FOREIGN KEY (user_id) REFERENCES "+User.TABLE+" (_id) " +
            ")";

    private int mID;
    private final KonThread mThread;
    private final Direction mDir;
    private final User mUser;

    private final String mJID;
    private final String mXMPPID;

    // (local) creation time
    private final Date mDate;
    // last timestamp of server transmission packet
    // incoming: (delayed) sent; outgoing: sent/received/error
    protected Optional<Date> mServerDate;
    protected Status mReceiptStatus;
    protected final MessageContent mContent;

    protected CoderStatus mCoderStatus;

    protected ServerError mServerError;

    protected KonMessage(Builder builder) {
        mID = builder.mID;
        mThread = builder.mThread;
        mDir = builder.mDir;
        // TODO group message stuff
        mUser = builder.mUser;
        mJID = builder.mJID;
        mXMPPID = builder.mXMPPID;
        mDate = builder.mDate;
        mServerDate = builder.mServerDate;
        mReceiptStatus = builder.mReceiptStatus;
        mContent = builder.mContent;
        mCoderStatus = builder.mCoderStatus;
        mServerError = builder.mServerError;

        if (mJID == null ||
                mXMPPID == null ||
                mDate == null ||
                mServerDate == null ||
                mReceiptStatus == null ||
                mContent == null ||
                mCoderStatus == null ||
                mServerError == null)
            throw new IllegalStateException();

        if (mID < 0)
            this.save();
    }

    /**
     * Get the database ID. <br>
     * -1 : message is not saved in database <br>
     * {@literal <} -1 : unexpected error on insertion attempt
     * @return ID of message in db
     */
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

    public Optional<Date> getServerDate() {
        return mServerDate;
    }

    public Status getReceiptStatus() {
        return mReceiptStatus;
    }

    public MessageContent getContent() {
        return mContent;
    }

    public CoderStatus getCoderStatus() {
        return mCoderStatus;
    }

    public void setSecurityErrors(EnumSet<Coder.Error> errors) {
        mCoderStatus.setSecurityErrors(errors);
        this.save();
    }

    /**
     * Return if two messages are logically equal.
     * Inconsistent with "natural ordering"!
     */
    @Override
    public boolean equals(Object obj) {
        // performance optimization
        if (this == obj)
            return true;

        if (!(obj instanceof KonMessage))
            return false;

        KonMessage o = (KonMessage) obj;

        // note: use ONLY final fields
        return mDir == o.mDir &&
                mJID.equals(o.mJID) &&
                mXMPPID.equals(o.mXMPPID) &&
                mDate.equals(o.mDate);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.mDir);
        hash = 67 * hash + Objects.hashCode(this.mJID);
        hash = 67 * hash + Objects.hashCode(this.mXMPPID);
        hash = 67 * hash + Objects.hashCode(this.mDate);
        return hash;
    }

    /**
     * Java's "natural ordering", used for sorting the messages in the order
     * they were created.
     * Inconsistent with equals!
     */
    @Override
    public int compareTo(KonMessage o) {
        int idComp = Integer.compare(this.mID, o.mID);
        int dateComp = mDate.compareTo(o.getDate());
        return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
    }

    private void insert() {
        if (mID >= 0) {
            LOGGER.warning("message already in db, ID: "+mID);
            return;
        }
        Database db = Database.getInstance();

        List<Object> values = new LinkedList<>();
        values.add(mThread.getID());
        values.add(mDir);
        values.add(mUser.getID());
        values.add(mJID);
        values.add(Database.setString(mXMPPID));
        values.add(mDate);
        values.add(mReceiptStatus);
        // i simply don't like to save all possible content explicitly in the
        // database, so we use JSON here
        values.add(mContent.toJSONString());
        values.add(mCoderStatus.getEncryption());
        values.add(mCoderStatus.getSigning());
        values.add(mCoderStatus.getErrors());
        values.add(mServerError.toJSON());
        values.add(mServerDate);

        int id = db.execInsert(TABLE, values);
        if (id <= 0) {
            LOGGER.log(Level.WARNING, "db, could not insert message");
            mID = -2;
            return;
        }
        mID = id;
    }

    /**
     * Save (or insert) this message to/into the database.
     */
    public final void save() {
        if (mID < 0) {
            this.insert();
            return;
        }
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put(COL_REC_STAT, mReceiptStatus);
        set.put(COL_CONTENT, mContent.toJSONString());
        set.put(COL_ENCR_STAT, mCoderStatus.getEncryption());
        set.put(COL_SIGN_STAT, mCoderStatus.getSigning());
        set.put(COL_COD_ERR, mCoderStatus.getErrors());
        set.put(COL_SERV_DATE, mServerDate);
        db.execUpdate(TABLE, set, mID);
    }

    boolean delete() {
        Database db = Database.getInstance();
        return db.execDelete(TABLE, mID);
    }

    protected synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public String toString() {
        return "M:id="+mID+",thread="+mThread+",dir="+mDir+",mUser="+mUser
                +",jid="+mJID+",xmppid="+mXMPPID
                +",date="+mDate+",sdate="+mServerDate
                +",recstat="+mReceiptStatus+",cont="+mContent
                +",codstat="+mCoderStatus+",serverr="+mServerError;
    }

    public final static class ServerError {
        private final static String JSON_COND = "cond";
        private final static String JSON_TEXT = "text";

        public final String condition;
        public final String text;

        private ServerError() {
            this("", "");
        }

        ServerError(String condition, String text) {
            this.condition = condition;
            this.text = text;
        }

        private String toJSON() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_COND, condition);
            EncodingUtils.putJSON(json, JSON_TEXT, text);
            return json.toJSONString();
        }

        @Override
        public String toString() {
            return this.toJSON();
        }

        static ServerError fromJSON(String jsonContent) {
            Object obj = JSONValue.parse(jsonContent);
            Map<?, ?> map = (Map) obj;
            if (map == null) return new ServerError();
            String condition = EncodingUtils.getJSON(map, JSON_COND);
            String text = EncodingUtils.getJSON(map, JSON_TEXT);
            return new ServerError(condition, text);
        }
    }

    static class Builder {
        private final int mID;
        private final KonThread mThread;
        private final Direction mDir;
        private final User mUser;
        private final Date mDate;

        private ServerError mServerError = new ServerError();

        protected String mJID = null;
        protected String mXMPPID = null;

        protected Optional<Date> mServerDate = null;
        protected Status mReceiptStatus = null;
        protected MessageContent mContent = null;

        protected CoderStatus mCoderStatus = null;

        // used by subclasses and when loading from database
        Builder(int id,
                KonThread thread,
                Direction dir,
                User user,
                Date date) {
            mID = id;
            mThread = thread;
            mDir = dir;
            mUser = user;
            mDate = date;
        }

        public void jid(String jid) { mJID = jid; }
        public void xmppID(String xmppID) { mXMPPID = xmppID; }

        public void serverDate(Optional<Date> date) { mServerDate = date; }
        public void content(MessageContent content) { mContent = content; }

        void receiptStatus(Status status) { mReceiptStatus = status; }
        void coderStatus(CoderStatus coderStatus) { mCoderStatus = coderStatus; }
        void serverError(ServerError error) { mServerError = error; }

        KonMessage build() {
            if (mDir == Direction.IN)
                return new InMessage(this);
            else
                return new OutMessage(this);
        }
    }
}
