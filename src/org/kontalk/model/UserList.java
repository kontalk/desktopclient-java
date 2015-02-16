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
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class UserList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(UserList.class.getName());

    private final static UserList INSTANCE = new UserList();

    private final HashMap<String, User> mMap = new HashMap<>();

    private UserList() {
    }

    public void load() {
        Database db = Database.getInstance();
        ResultSet resultSet;
        try {
            resultSet = db.execSelectAll(User.TABLE);
        } catch (SQLException ex) {
            Logger.getLogger(UserList.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        try {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                String jid = resultSet.getString("jid");
                String name = resultSet.getString("name");
                String status = resultSet.getString("status");
                long l = resultSet.getLong("last_seen");
                Optional<Date> lastSeen = l == 0 ?
                        Optional.<Date>empty() :
                        Optional.<Date>of(new Date(l));
                boolean encr = resultSet.getBoolean("encrypted");
                String key = Database.getString(resultSet, "public_key");
                String fp = Database.getString(resultSet, "key_fingerprint");
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

    /**
     * Add a new user to the list.
     * @param jid JID of new user
     * @param name nickname of new user, use an empty string if not known
     * @return the newly created user, if one was created
     */
    public Optional<User> addUser(String jid, String name) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (mMap.containsKey(jid)) {
            LOGGER.warning("user already exists, jid: "+jid);
            return Optional.empty();
        }
        User newUser = new User(jid, name);
        mMap.put(jid, newUser);
        this.save();
        this.changed();
        return Optional.of(newUser);
    }

    public void save() {
        for (User user: mMap.values()) {
            user.save();
        }
    }

    public Optional<User> getUserByID(int id) {
        // TODO performance
        for (User user: mMap.values()) {
            if (user.getID() == id)
                return Optional.of(user);
        }
        LOGGER.warning("can't find user with ID: "+id);
        return Optional.empty();
    }

    /**
     * Get the user for a JID (if the JID is in the list).
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public Optional<User> getUserByJID(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return Optional.ofNullable(mMap.get(jid));
    }

    /**
     * Return whether a user with a specified JID exists.
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public boolean containsUserWithJID(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return mMap.containsKey(jid);
    }

    public void setPGPKey(String jid, byte[] rawKey) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (!mMap.containsKey(jid)) {
            LOGGER.warning("can't find user with jid: "+jid);
            return;
        }
        mMap.get(jid).setKey(rawKey);
    }

    public synchronized void changed() {
        this.setChanged();
        this.notifyObservers();
    }

    public static UserList getInstance() {
        return INSTANCE;
    }

}
