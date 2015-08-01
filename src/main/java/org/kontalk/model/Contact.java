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

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Config;
import org.kontalk.system.Database;

/**
 * A contact in the Kontalk/XMPP-Jabber network.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Contact extends Observable implements Comparable<Contact> {
    private static final Logger LOGGER = Logger.getLogger(Contact.class.getName());

    /**
     * Online status of one contact.
     * Not saved to database.
     */
    public static enum Online {UNKNOWN, YES, NO};

    /**
     * XMPP subscription status in roster.
     */
    public static enum Subscription {
        UNKNOWN, PENDING, SUBSCRIBED, UNSUBSCRIBED
    }

    public static final String TABLE = "user";
    public static final String COL_JID = "jid";
    public static final String COL_NAME = "name";
    public static final String COL_STAT = "status";
    public static final String COL_LAST_SEEN = "last_seen";
    public static final String COL_ENCR = "encrypted";
    public static final String COL_PUB_KEY = "public_key";
    public static final String COL_KEY_FP = "key_fingerprint";
    public static final String CREATE_TABLE = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_JID + " TEXT NOT NULL UNIQUE, " +
            COL_NAME + " TEXT, " +
            COL_STAT + " TEXT, " +
            COL_LAST_SEEN + " INTEGER, " +
            // boolean, send messages encrypted?
            COL_ENCR + " INTEGER NOT NULL, " +
            COL_PUB_KEY + " TEXT UNIQUE, " +
            COL_KEY_FP + " TEXT UNIQUE" +
            ")";

    private final int mID;
    private String mJID;
    private String mName;
    private String mStatus = "";
    private Optional<Date> mLastSeen = Optional.empty();
    private Online mAvailable = Online.UNKNOWN;
    private boolean mEncrypted = true;
    private String mKey = "";
    private String mFingerprint = "";
    private boolean mBlocked = false;
    private Subscription mSubStatus = Subscription.UNKNOWN;
    //private ItemType mType;

    // used for creating new contacts (eg from roster)
    Contact(String jid, String name) {
        mJID = XmppStringUtils.parseBareJid(jid);
        mName = name;

        Database db = Database.getInstance();
        List<Object> values = new LinkedList<>();
        values.add(mJID);
        values.add(mName);
        values.add(mStatus);
        values.add(mLastSeen);
        values.add(mEncrypted);
        values.add(null); // key
        values.add(null); // fingerprint
        mID = db.execInsert(TABLE, values);
        if (mID < 1)
            LOGGER.log(Level.WARNING, "could not insert contact");
    }

    // used for loading contacts from database
    Contact(int id,
            String jid,
            String name,
            String status,
            Optional<Date> lastSeen,
            boolean encrypted,
            String publicKey,
            String fingerprint) {
        mID = id;
        mJID = jid;
        mName = name;
        mStatus = status;
        mLastSeen = lastSeen;
        mEncrypted = encrypted;
        mKey = publicKey;
        mFingerprint = fingerprint;
    }

    public String getJID() {
        return mJID;
    }

    void setJID(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (jid.equals(mJID))
            return;

        mJID = jid;
        this.save();
        this.changed(mJID);
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
        // contact itself as argument for thread view items
        this.changed(this);
    }

    public String getStatus() {
        return mStatus;
    }

    public Optional<Date> getLastSeen() {
        return mLastSeen;
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
        return this.mAvailable;
    }

    public void setOnline(Presence.Type type, String status) {
        if (type == Presence.Type.available) {
            mAvailable = Online.YES;
            mLastSeen = Optional.of(new Date());
        } else if (type == Presence.Type.unavailable) {
            mAvailable = Online.NO;
        }
        this.changed(mAvailable);

        if (status != null && !status.isEmpty()) {
            mStatus = status;
        }
    }

    /**
     * Reset online status when client is disconneted.
     */
    public void setOffline() {
        mAvailable = Online.UNKNOWN;
        this.changed(mAvailable);
    }

    public byte[] getKey() {
        return Base64.getDecoder().decode(mKey);
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

        mKey = Base64.getEncoder().encodeToString(rawKey);
        mFingerprint = fingerprint;
        this.save();
        this.changed(null);
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public void setBlocked(boolean blocked) {
        mBlocked = blocked;
        this.changed(mBlocked);
    }

    public Subscription getSubScription() {
        return mSubStatus;
    }

    public void setSubScriptionStatus(Subscription status) {
        if (status == mSubStatus)
            return;

        mSubStatus = status;
        this.changed(mSubStatus);
    }

    public boolean isMe() {
        return !mJID.isEmpty() &&
                mJID.equals(Config.getInstance().getProperty(Config.ACC_JID));
    }

    /**
     * 'Delete' this contact: faked by resetting all values.
     */
    public void setDeleted() {
        mJID = Integer.toString(mID);
        mName = "";
        mStatus = "";
        mLastSeen = Optional.empty();
        mEncrypted = false;
        mKey = "";
        mFingerprint = "";

        this.save();
    }

    public boolean isDeleted() {
        return mJID.equals(Integer.toString(mID));
    }

    void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put(COL_JID, mJID);
        set.put(COL_NAME, mName);
        set.put(COL_STAT, mStatus);
        set.put(COL_LAST_SEEN, mLastSeen);
        set.put(COL_ENCR, mEncrypted);
        set.put(COL_PUB_KEY, Database.setString(mKey));
        set.put(COL_KEY_FP, Database.setString(mFingerprint));
        db.execUpdate(TABLE, set, mID);
    }

    private void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public String toString() {
        return "U:id="+mID+",jid="+mJID+",name="+mName+",fp="+mFingerprint
                +",subsc="+mSubStatus;
    }

    @Override
    public int compareTo(Contact o) {
        return Integer.compare(this.mID, o.mID);
    }
}
