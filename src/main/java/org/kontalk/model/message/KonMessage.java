/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

package org.kontalk.model.message;

import org.kontalk.model.chat.Chat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.persistence.Database;
import org.kontalk.crypto.Coder;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.message.MessageContent.Preview;
import org.kontalk.util.EncodingUtils;

/**
 * Base class for incoming and outgoing XMMP messages.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class KonMessage extends Observable {
    private static final Logger LOGGER = Logger.getLogger(KonMessage.class.getName());

    /**
     * Sending status of one message.
     * Do not modify, only add! Ordinal used in database
     */
    public enum Status {
        /** For all incoming messages. */
        IN,
        /** Outgoing message, message is about to be send. */
        PENDING,
        /** Outgoing message, message was handled by server. */
        SENT,
        /** Outgoing message, message was received by recipient.
         * Not saved to database. Transmission used for that.
         */
        RECEIVED,
        /** Outgoing message, an error occurred somewhere in the transmission. */
        ERROR
    };

    public static final String TABLE = "messages";
    public static final String COL_CHAT_ID = "thread_id";
    //public static final String COL_DIR = "direction";
    public static final String COL_XMPP_ID = "xmpp_id";
    public static final String COL_DATE = "date";
    public static final String COL_STATUS = "status";
    public static final String COL_CONTENT = "content";
    public static final String COL_ENCR_STAT = "encryption_status";
    public static final String COL_SIGN_STAT = "signing_status";
    public static final String COL_COD_ERR = "coder_errors";
    public static final String COL_SERV_ERR = "server_error";
    public static final String COL_SERV_DATE = "server_date";
    public static final String SCHEMA = "( " +
            Database.SQL_ID +
            COL_CHAT_ID + " INTEGER NOT NULL, " +
            // XMPP ID attribute; only RECOMMENDED and must be unique only
            // within a stream (RFC 6120)
            COL_XMPP_ID + " TEXT NOT NULL, " +
            // unix time, local creation timestamp
            COL_DATE + " INTEGER NOT NULL, " +
            // enum, message sending status
            COL_STATUS + " INTEGER NOT NULL, " +
            // message content in JSON format
            COL_CONTENT + " TEXT NOT NULL, " +
            // enum, determines if content is encrypted
            COL_ENCR_STAT + " INTEGER NOT NULL, " +
            // enum, determines if content is verified
            // can only tell after encryption
            COL_SIGN_STAT + " INTEGER NOT NULL, " +
            // enum set, encryption and signing errors of content
            COL_COD_ERR + " INTEGER NOT NULL, " +
            // optional error reply in JSON format
            COL_SERV_ERR + " TEXT, " +
            // unix time, transmission/delay timestamp
            COL_SERV_DATE + " INTEGER, " +
            "FOREIGN KEY ("+COL_CHAT_ID+") REFERENCES "+Chat.TABLE+" (_id) " +
            ")";

    protected final int mID;
    private final Chat mChat;
    private final String mXMPPID;
    // (local) creation time
    private final Date mDate;
    protected final MessageContent mContent;

    // last timestamp of server transmission packet
    // incoming: (delayed) sent; outgoing: sent or error
    protected Date mServerDate;
    protected Status mStatus;
    protected CoderStatus mCoderStatus;
    protected ServerError mServerError;

    protected KonMessage(
            Chat chat,
            String xmppID,
            MessageContent content,
            Optional<Date> serverDate,
            Status status,
            CoderStatus coderStatus) {
        mChat = chat;
        mXMPPID = xmppID;
        mDate = new Date();
        mContent = content;

        mServerDate = serverDate.orElse(null);
        mStatus = status;
        mCoderStatus = coderStatus;
        mServerError = new ServerError();

        // insert
        List<Object> values = Arrays.asList(
                mChat.getID(),
                Database.setString(mXMPPID),
                mDate,
                mStatus,
        // i simply don't like to save all possible content explicitly in the
        // database, so we use JSON here
                mContent.toJSON(),
                mCoderStatus.getEncryption(),
                mCoderStatus.getSigning(),
                mCoderStatus.getErrors(),
                mServerError.toJSON(),
                mServerDate);

        mID = Model.database().execInsert(TABLE, values);
        if (mID <= 0) {
            LOGGER.log(Level.WARNING, "db, could not insert message");
        }
    }

    // used when loading from database
    protected KonMessage(Builder builder) {
        mID = builder.mID;
        mChat = builder.mChat;
        mXMPPID = builder.mXMPPID;
        mDate = builder.mDate;
        mContent = builder.mContent;

        mServerDate = builder.mServerDate;
        mStatus = builder.mStatus;
        mCoderStatus = builder.mCoderStatus;
        mServerError = builder.mServerError;
    }

    public int getID() {
        return mID;
    }

    public Chat getChat() {
        return mChat;
    }

    public boolean isInMessage() {
        return mStatus == Status.IN;
    }

    public abstract Set<Transmission> getTransmissions();

    public String getXMPPID() {
        return mXMPPID;
    }

    public Date getDate() {
        return mDate;
    }

    public Optional<Date> getServerDate() {
        return Optional.ofNullable(mServerDate);
    }

    public Status getStatus() {
        return mStatus;
    }

    public MessageContent getContent() {
        return mContent;
    }

    public void setAttachmentErrors(EnumSet<Coder.Error> errors) {
        MessageContent.Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.getCoderStatus().setSecurityErrors(errors);
        this.save();
    }

    protected MessageContent.Attachment getAttachment() {
        MessageContent.Attachment att = this.getContent().getAttachment().orElse(null);
        if (att == null) {
            LOGGER.warning("no attachment!?");
            return null;
        }
        return att;
    }

    public CoderStatus getCoderStatus() {
        return mCoderStatus;
    }

    public void setSecurityErrors(EnumSet<Coder.Error> errors) {
        mCoderStatus.setSecurityErrors(errors);
        this.save();
        this.changed(mCoderStatus);
    }

    public ServerError getServerError() {
        return mServerError;
    }

    public void setPreview(Preview preview) {
        mContent.setPreview(preview);
        this.save();
        this.changed(preview);
    }

    public boolean isEncrypted() {
        return mCoderStatus.isEncrypted();
    }

    protected void save() {
        Map<String, Object> set = new HashMap<>();
        set.put(COL_STATUS, mStatus);
        set.put(COL_CONTENT, mContent.toJSON());
        set.put(COL_ENCR_STAT, mCoderStatus.getEncryption());
        set.put(COL_SIGN_STAT, mCoderStatus.getSigning());
        set.put(COL_COD_ERR, mCoderStatus.getErrors());
        set.put(COL_SERV_ERR, Database.setString(mServerError.toJSON()));
        set.put(COL_SERV_DATE, mServerDate);
        Model.database().execUpdate(TABLE, set, mID);
    }

    public boolean delete() {
        boolean succ = this.getTransmissions().stream().allMatch(t -> t.delete());
        if (!succ)
            return false;

        if (mID < 0) {
            LOGGER.warning("not in database: "+this);
            return true;
        }
        return Model.database().execDelete(TABLE, mID);
    }

    protected void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    protected boolean abstractEquals(KonMessage oMessage) {
        return mChat.equals(oMessage.mChat)
                && !mXMPPID.isEmpty() && mXMPPID.equals(oMessage.mXMPPID);
    }

    protected int abstractHashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.mChat);
        hash = 17 * hash + Objects.hashCode(this.mXMPPID);
        return hash;
    }

    @Override
    public String toString() {
        return "M:id="+mID+",status="+mStatus+",chat="+mChat+",xmppid="+mXMPPID
                +",transmissions="+this.getTransmissions()
                +",date="+mDate+",sdate="+mServerDate
                +",cont="+mContent
                +",codstat="+mCoderStatus+",serverr="+mServerError;
    }

    public static KonMessage load(Database db, ResultSet messageRS, Chat chat,
            Map<Integer, Contact> contactMap)
            throws SQLException {
        int id = messageRS.getInt("_id");

        String xmppID = Database.getString(messageRS, KonMessage.COL_XMPP_ID);

        Date date = new Date(messageRS.getLong(KonMessage.COL_DATE));

        int statusIndex = messageRS.getInt(KonMessage.COL_STATUS);
        KonMessage.Status status = KonMessage.Status.values()[statusIndex];

        String jsonContent = messageRS.getString(KonMessage.COL_CONTENT);

        MessageContent content = MessageContent.fromJSONString(jsonContent);

        int encryptionIndex = messageRS.getInt(KonMessage.COL_ENCR_STAT);
        Coder.Encryption encryption = Coder.Encryption.values()[encryptionIndex];

        int signingIndex = messageRS.getInt(KonMessage.COL_SIGN_STAT);
        Coder.Signing signing = Coder.Signing.values()[signingIndex];

        int errorFlags = messageRS.getInt(KonMessage.COL_COD_ERR);
        EnumSet<Coder.Error> coderErrors = EncodingUtils.intToEnumSet(
                Coder.Error.class, errorFlags);

        CoderStatus coderStatus = new CoderStatus(encryption, signing, coderErrors);

        String jsonServerError = messageRS.getString(KonMessage.COL_SERV_ERR);
        KonMessage.ServerError serverError =
                KonMessage.ServerError.fromJSON(jsonServerError);

        long sDate = messageRS.getLong(KonMessage.COL_SERV_DATE);
        Date serverDate = sDate == 0 ? null : new Date(sDate);

        KonMessage.Builder builder = new KonMessage.Builder(id, chat, status, date, content);
        // TODO one SQL SELECT for each message, performance? looks ok
        builder.transmissions(Transmission.load(id, contactMap));
        builder.xmppID(xmppID);
        if (serverDate != null)
            builder.serverDate(serverDate);
        builder.coderStatus(coderStatus);
        builder.serverError(serverError);

        return builder.build();
    }

    public static final class ServerError {
        private static final String JSON_COND = "cond";
        private static final String JSON_TEXT = "text";

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
            String condition = EncodingUtils.getJSONString(map, JSON_COND);
            String text = EncodingUtils.getJSONString(map, JSON_TEXT);
            return new ServerError(condition, text);
        }
    }

    protected static class Builder {
        private final int mID;
        private final Chat mChat;
        private final Status mStatus;
        private final Date mDate;
        private final MessageContent mContent;

        protected Set<Transmission> mTransmissions = null;

        private String mXMPPID = null;
        private Date mServerDate = null;
        private CoderStatus mCoderStatus = null;
        private ServerError mServerError = null;

        private Builder(int id,
                Chat chat,
                Status status,
                Date date,
                MessageContent content) {
            mID = id;
            mChat = chat;
            mStatus = status;
            mDate = date;
            mContent = content;
        }

        private void transmissions(Set<Transmission> transmission) { mTransmissions = transmission; }

        private void xmppID(String xmppID) { mXMPPID = xmppID; }
        private void serverDate(Date date) { mServerDate = date; }
        private void coderStatus(CoderStatus coderStatus) { mCoderStatus = coderStatus; }
        private void serverError(ServerError error) { mServerError = error; }

        private KonMessage build() {
            if (mTransmissions == null ||
                    mXMPPID == null ||
                    mCoderStatus == null ||
                    mServerError == null)
                throw new IllegalStateException();

            if (mStatus == Status.IN)
                return new InMessage(this);
            else
                return new OutMessage(this);
        }
    }
}
