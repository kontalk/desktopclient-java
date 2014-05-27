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
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserList extends ChangeSubject {
    private final static Logger LOGGER = Logger.getLogger(UserList.class.getName());

    private static UserList INSTANCE = null;

    private final HashMap<String, User> mMap = new HashMap();

    private UserList() {
    }

    public void load() {
        Database db = Database.getInstance();
        ResultSet resultSet = db.execSelectAll(User.TABLE);
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                String jid = resultSet.getString("jid");
                String name = resultSet.getString("name");
                String status = resultSet.getString("status");
                long l = resultSet.getLong("last_seen");
                Date lastSeen = l == 0 ? null : new Date(l);
                boolean encr = resultSet.getBoolean("encrypted");
                String key = resultSet.getString("public_key");
                String fp = resultSet.getString("key_fingerprint");
                mMap.put(jid, new User(id, jid, name, status, lastSeen, encr, key, fp));
            }
        resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load users from db", ex);
        }
        this.changed();
    }

    public Collection<User> getUser() {
            return mMap.values();
    }

    public void addUser(String jid, String name) {
        jid = StringUtils.parseBareAddress(jid);
        if (mMap.containsKey(jid))
            return;
        User newUser = new User(jid, name);
        mMap.put(jid, newUser);
        this.changed();
        this.save();
    }

    public void save() {
        for (User user: mMap.values()) {
            user.save();
        }
    }

    public User getUserByID(int id) {
        for (User user: mMap.values()) {
            if (user.getID() == id)
                return user;
        }
        LOGGER.warning("can't find user with ID: "+id);
        return null;
    }

    /**
     * Get the user for a JID.
     * If no user can be found with the JID a new one is created.
     * @param jid
     * @return
     */
    public User getUserByJID(String jid) {
        jid = StringUtils.parseBareAddress(jid);
        if (mMap.containsKey(jid))
            return mMap.get(jid);
        User newUser = new User(jid);
        mMap.put(jid, newUser);
        return newUser;
    }

    public void setPresence(String jid, Presence.Type type, String status) {
        jid = StringUtils.parseBareAddress(jid);
        if (!mMap.containsKey(jid)) {
            LOGGER.warning("can't find user with jid: "+jid);
            return;
        }
        User user = mMap.get(jid);
        user.setPresence(type, status);
    }

    public void setPGPKey(String jid, byte[] rawKey) {
        jid = StringUtils.parseBareAddress(jid);
        if (!mMap.containsKey(jid)) {
            LOGGER.warning("can't find user with jid: "+jid);
            return;
        }
        mMap.get(jid).setKey(rawKey);
    }

    public static UserList getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new UserList();
        }
        return INSTANCE;
    }

}
