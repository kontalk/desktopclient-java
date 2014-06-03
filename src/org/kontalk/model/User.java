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

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Base64;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.Database;
import org.kontalk.crypto.PGP;
import org.kontalk.util.MessageUtils;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class User {
    private final static Logger LOGGER = Logger.getLogger(User.class.getName());

    public static enum Available {UNKNOWN, YES, NO};

    public final static String TABLE = "user";
    public final static String CREATE_TABLE = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "jid TEXT NOT NULL UNIQUE, " +
            "name TEXT, " +
            "status TEXT, " +
            "last_seen INTEGER, " +
            // send messages encrypted?
            "encrypted INTEGER NOT NULL, " +
            "public_key TEXT UNIQUE, " +
            "key_fingerprint TEXT UNIQUE" +
            ")";

    private final int mID;
    private String mJID;
    private String mName;
    private String mStatus = null;
    private Date mLastSeen = null;
    private Available mAvailable = Available.UNKNOWN;
    private boolean mEncrypted = true;
    private String mKey = null;
    private String mFingerprint = null;
    //private ItemType mType;

    /**
     * Used for incoming messages of unknown user.
     */
    User(String jid) {
        this(jid, null);
    }

    /**
     * Used for creating new users (eg from roster).
     */
    User(String jid, String name) {
        mJID = StringUtils.parseBareAddress(jid);
        mName = name;

        Database db = Database.getInstance();
        List<Object> values = new LinkedList();
        values.add(mJID);
        values.add(mName);
        values.add(mStatus);
        values.add(mLastSeen);
        values.add(mEncrypted);
        values.add(mKey);
        values.add(mFingerprint);
        mID = db.execInsert(TABLE, values);
        if (mID < 1)
            LOGGER.log(Level.WARNING, "couldn't insert user");
    }

    /**
     * Used for loading users from database
     */
    User(int id, String jid, String name, String status, Date lastSeen,
            boolean encrypted, String publicKey, String fingerprint) {
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
        mJID = StringUtils.parseBareAddress(jid);
        this.save();
    }

    public int getID() {
        return mID;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
        this.save();
        UserList.getInstance().changed();
    }

    public String getStatus() {
        return mStatus;
    }

    public Date getLastSeen() {
        return mLastSeen;
    }

    public boolean getEncrypted() {
        return mEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        mEncrypted = encrypted;
    }

    public Available getAvailable() {
        return this.mAvailable;
    }

    void setPresence(Presence.Type type, String status) {
        if (type == Presence.Type.available) {
            mAvailable = Available.YES;
            mLastSeen = new Date();
        }
        if (type == Presence.Type.unavailable) {
            mAvailable = Available.NO;
        }
        if (status != null) {
            mStatus = status;
        }
        UserList.getInstance().changed();
    }


    public byte[] getKey() {
        return Base64.decode(mKey);
    }

    public boolean hasKey() {
        return mKey != null;
    }

    public String getFingerprint() {
        return mFingerprint;
    }

    void setKey(byte[] rawKey) {
        PGPPublicKey key;
        try {
            key = PGP.getMasterKey(rawKey);
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't parse public key", ex);
            return;
        }

        // if not set use id in key for username
        String id = PGP.getUserId(key, null);
        if (id != null && id.contains(" (NO COMMENT) ")){
            String userName = id.substring(0, id.indexOf(" (NO COMMENT) "));
            if (!userName.isEmpty() && mName == null)
                mName = userName;
        }

        if (mKey != null)
            LOGGER.info("overwriting public key, user id: "+mID);

        mKey = Base64.toBase64String(rawKey);
        mFingerprint = MessageUtils.bytesToHex(key.getFingerprint());
        this.save();
        UserList.getInstance().changed();
    }

    public void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap();
        set.put("jid", mJID);
        set.put("name", mName);
        set.put("status", mStatus);
        set.put("last_seen", mLastSeen);
        set.put("encrypted", mEncrypted);
        set.put("public_key", mKey);
        set.put("key_fingerprint", mFingerprint);
        db.execUpdate(TABLE, set, mID);
    }

}
