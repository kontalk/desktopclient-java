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

package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kontalk.misc.JID;
import org.kontalk.persistence.Database;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.XMPPUtils;

/**
 * A contact in the Kontalk/XMPP-Jabber network.
 *
 * TODO group chats need some weaker entity here: not deletable,
 * not shown in ui contact list(?), but with public key
 *
 * idea: "deletable"/"weak" field: contact gets deleted
 * when no group chat exists anymore
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Contact extends Observable {
    private static final Logger LOGGER = Logger.getLogger(Contact.class.getName());

    /**
     * Online status of one contact.
     */
    public enum Online {UNKNOWN, YES, NO, ERROR};

    /**
     * XMPP subscription status in roster.
     */
    public enum Subscription {
        UNKNOWN, PENDING, SUBSCRIBED, UNSUBSCRIBED
    }

    public enum ViewChange {
        JID, NAME, ONLINE_STATUS, KEY, BLOCKING, SUBSCRIPTION, AVATAR, DELETED
    }

    public static final String TABLE = "user";
    public static final String COL_JID = "jid";
    public static final String COL_NAME = "name";
    public static final String COL_STAT = "status";
    public static final String COL_LAST_SEEN = "last_seen";
    public static final String COL_ENCR = "encrypted";
    public static final String COL_PUB_KEY = "public_key";
    public static final String COL_KEY_FP = "key_fingerprint";
    public static final String COL_AVATAR_ID = "avatar_id";
    public static final String SCHEMA = "(" +
            Database.SQL_ID +
            COL_JID + " TEXT NOT NULL UNIQUE, " +
            COL_NAME + " TEXT, " +
            COL_STAT + " TEXT, " +
            COL_LAST_SEEN + " INTEGER, " +
            // boolean, send messages encrypted?
            COL_ENCR + " INTEGER NOT NULL, " +
            COL_PUB_KEY + " TEXT UNIQUE, " +
            COL_KEY_FP + " TEXT UNIQUE," +
            COL_AVATAR_ID + " TEXT" +
            ")";

    private final int mID;
    private JID mJID;
    private String mName;
    private String mStatus = "";
    private Date mLastSeen = null;
    // not in database
    private Online mOnline = Online.UNKNOWN;
    private boolean mEncrypted = true;
    private String mKey = "";
    private String mFingerprint = "";
    private boolean mBlocked = false;
    // not in database
    private Subscription mSubStatus = Subscription.UNKNOWN;
    //private ItemType mType;
    private Avatar.DefaultAvatar mAvatar = null;
    private Avatar.CustomAvatar mCustomAvatar = null;

    // new contact (eg from roster)
    Contact(JID jid, String name) {
        mJID = jid;
        mName = name;

        // insert
        List<Object> values = Arrays.asList(
                mJID,
                mName,
                mStatus,
                mLastSeen,
                mEncrypted,
                null, // key
                null, // fingerprint
                null); // avatar id
        mID = Model.database().execInsert(TABLE, values);
        if (mID < 1)
            LOGGER.log(Level.WARNING, "could not insert contact");
    }

    // loading from database
    private Contact(
            int id,
            JID jid,
            String name,
            String status,
            Optional<Date> lastSeen,
            boolean encrypted,
            String publicKey,
            String fingerprint,
            String avatarID) {
        mID = id;
        mJID = jid;
        mName = name;
        mStatus = status;
        mLastSeen = lastSeen.orElse(null);
        mEncrypted = encrypted;
        mKey = publicKey;
        mFingerprint = fingerprint.toLowerCase();
        mAvatar = avatarID.isEmpty() ?
                null :
                Avatar.DefaultAvatar.load(avatarID).orElse(null);
        mCustomAvatar = Avatar.CustomAvatar.load(mID).orElse(null);
    }

    public JID getJID() {
        return mJID;
    }

    void setJID(JID jid) {
        if (jid.equals(mJID))
            return;

        if (!jid.isValid()) {
            LOGGER.warning("jid is not valid: "+jid);
            return;
        }

        mJID = jid;
        this.save();
        this.changed(ViewChange.JID);
    }

    public int getID() {
        return mID;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        if (name.equals(mName))
            return;

        mName = name;
        this.save();

        this.changed(ViewChange.NAME);
    }

    public String getStatus() {
        return mStatus;
    }

    public Optional<Date> getLastSeen() {
        return Optional.ofNullable(mLastSeen);
    }

    public void setLastSeen(Date lastSeen, String status) {
        boolean save = false;
        if (!lastSeen.equals(mLastSeen)) {
            mLastSeen = lastSeen;
            save = true;
        }
        if (!status.isEmpty() && !status.equals(mStatus)) {
            mStatus = status;
            save = true;
        }

        if (save)
            this.save();
    }

    public boolean getEncrypted() {
        return mEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        if (encrypted == mEncrypted)
            return;

        mEncrypted = encrypted;
        this.save();
    }

    public Online getOnline() {
        return this.mOnline;
    }

    public void setStatusText(String status) {
        if (mStatus.equals(status))
            return;

        mStatus = status;
        this.save();
    }

    public void setOnlineStatus(Online onlineStatus) {
        if (onlineStatus == mOnline)
            return;

        if (onlineStatus == Online.YES ||
                (onlineStatus == Online.NO && mOnline == Online.YES)) {
            mLastSeen = new Date();
            this.save();
        }

        mOnline = onlineStatus;

        this.changed(ViewChange.ONLINE_STATUS);
    }

    public byte[] getKey() {
        return EncodingUtils.base64ToBytes(mKey);
    }

    public boolean hasKey() {
        return !mKey.isEmpty();
    }

    public String getFingerprint() {
        return mFingerprint;
    }

    public void setKey(byte[] rawKey, String fingerprint) {
        if (!mKey.isEmpty())
            LOGGER.info("overwriting public key of contact: "+this);

        mKey = EncodingUtils.bytesToBase64(rawKey);
        mFingerprint = fingerprint.toLowerCase();
        this.save();
        this.changed(ViewChange.KEY);
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public void setBlocked(boolean blocked) {
        mBlocked = blocked;
        this.changed(ViewChange.BLOCKING);
    }

    public Subscription getSubScription() {
        return mSubStatus;
    }

    public void setSubscriptionStatus(Subscription status) {
        if (status == mSubStatus)
            return;

        mSubStatus = status;
        this.changed(ViewChange.SUBSCRIPTION);
    }

    public Optional<Avatar> getAvatar() {
        return Optional.ofNullable(mAvatar);
    }

    /** Get custom or downloaded avatar. */
    public Optional<Avatar> getDisplayAvatar() {
        return mCustomAvatar != null ?
                Optional.of(mCustomAvatar) :
                Optional.ofNullable(mAvatar);
    }

    public void setAvatar(Avatar.DefaultAvatar avatar) {
        // delete old
        if (mAvatar != null)
            mAvatar.delete();

        // set new
        mAvatar = avatar;
        this.save();

        if (mCustomAvatar == null)
            this.changed(ViewChange.AVATAR);
    }

    public void deleteAvatar() {
        if (mAvatar == null)
            return;

        mAvatar.delete();
        mAvatar = null;
        this.save();

        this.changed(ViewChange.AVATAR);
    }

    public void setCustomAvatar(Avatar.CustomAvatar avatar) {
        // overwrite file!
        mCustomAvatar = avatar;

        this.changed(ViewChange.AVATAR);
    }

    public boolean hasCustomAvatarSet() {
        return mCustomAvatar != null;
    }

    public void deleteCustomAvatar() {
        if (mCustomAvatar == null)
            return;

        mCustomAvatar.delete();
        mCustomAvatar = null;

        this.changed(ViewChange.AVATAR);
    }

    public boolean isMe() {
        return mJID.isValid() && mJID.equals(Model.getUserJID());
    }

    public boolean isKontalkUser(){
        return XMPPUtils.isKontalkJID(mJID);
    }

    /**
     * 'Delete' this contact: faked by resetting all values.
     */
    void setDeleted() {
        LOGGER.config("contact: "+this);
        mJID = JID.deleted(mID);
        mName = "";
        mStatus = "";
        mLastSeen = null;
        mEncrypted = false;
        mKey = "";
        mFingerprint = "";
        if (mAvatar != null)
            mAvatar.delete();
        mAvatar = null;

        this.save();
        this.changed(ViewChange.DELETED);
    }

    public boolean isDeleted() {
        return mJID.string().equals(Integer.toString(mID));
    }

    private void save() {
        Map<String, Object> set = new HashMap<>();
        set.put(COL_JID, mJID);
        set.put(COL_NAME, mName);
        set.put(COL_STAT, mStatus);
        set.put(COL_LAST_SEEN, mLastSeen);
        set.put(COL_ENCR, mEncrypted);
        set.put(COL_PUB_KEY, Database.setString(mKey));
        set.put(COL_KEY_FP, Database.setString(mFingerprint));
        set.put(COL_AVATAR_ID, Database.setString(mAvatar != null ? mAvatar.getID() : ""));
        Model.database().execUpdate(TABLE, set, mID);
    }

    private void changed(ViewChange change) {
        this.setChanged();
        this.notifyObservers(change);
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Contact))
            return false;

        return mID == ((Contact) o).mID;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + this.mID;
        return hash;
    }

    @Override
    public String toString() {
        return "C:id="+mID+",jid="+mJID+",name="+mName+",fp="+mFingerprint
                +",subsc="+mSubStatus;
    }

    static Contact load(Database db, ResultSet rs) throws SQLException {
        int id = rs.getInt("_id");
        JID jid = JID.bare(rs.getString(Contact.COL_JID));

        String name = rs.getString(Contact.COL_NAME);
        String status = rs.getString(Contact.COL_STAT);
        long l = rs.getLong(Contact.COL_LAST_SEEN);
        Date lastSeen = l == 0 ? null : new Date(l);
        boolean encr = rs.getBoolean(Contact.COL_ENCR);
        String key = Database.getString(rs, Contact.COL_PUB_KEY);
        String fp = Database.getString(rs, Contact.COL_KEY_FP);
        String avatarID = Database.getString(rs, Contact.COL_AVATAR_ID);

        return new Contact(id, jid, name, status,
                Optional.ofNullable(lastSeen), encr, key, fp, avatarID);
    }
}
