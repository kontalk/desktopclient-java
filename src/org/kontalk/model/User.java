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
import org.kontalk.system.Database;

/**
 * A contact in the Kontalk/XMPP-Jabber network.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class User extends Observable {
    private final static Logger LOGGER = Logger.getLogger(User.class.getName());

    /**
     * Online status of one user.
     * Not saved to database.
     */
    public static enum Online {UNKNOWN, YES, NO};

    public final static String TABLE = "user";
    public final static String CREATE_TABLE = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "jid TEXT NOT NULL UNIQUE, " +
            "name TEXT, " +
            "status TEXT, " +
            "last_seen INTEGER, " +
            // boolean, send messages encrypted?
            "encrypted INTEGER NOT NULL, " +
            "public_key TEXT UNIQUE, " +
            "key_fingerprint TEXT UNIQUE" +
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
    //private ItemType mType;

    // used for incoming messages of unknown user
    User(String jid) {
        this(jid, "");
    }

    // used for creating new users (eg from roster)
    User(String jid, String name) {
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
            LOGGER.log(Level.WARNING, "could not insert user");
    }

    // used for loading users from database
    User(int id,
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

    public void setJID(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (jid.equals(mJID))
            return;

        mJID = jid;
        this.save();
        this.changed();
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
        // TODO thread view not updated
        this.changed();
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
        this.changed();

        if (status != null && !status.isEmpty()) {
            mStatus = status;
        }
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
            LOGGER.info("overwriting public key of user: "+this);

        mKey = Base64.getEncoder().encodeToString(rawKey);
        mFingerprint = fingerprint;
        this.save();
        this.changed();
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public void setBlocked(boolean blocked) {
        mBlocked = blocked;
    }

    public void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put("jid", mJID);
        set.put("name", mName);
        set.put("status", mStatus);
        set.put("last_seen", mLastSeen);
        set.put("encrypted", mEncrypted);
        set.put("public_key", Database.setString(mKey));
        set.put("key_fingerprint", Database.setString(mFingerprint));
        db.execUpdate(TABLE, set, mID);
    }

    private void changed() {
        this.setChanged();
        this.notifyObservers();
    }

    @Override
    public String toString() {
        return "U:id="+mID+",jid="+mJID+",name="+mName+",fp="+mFingerprint;
    }
}
