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
import java.util.Date;
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Database;

/**
 * The global list of all contacts known.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class UserList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(UserList.class.getName());

    private final static UserList INSTANCE = new UserList();

    /** JID to user map. */
    private final HashMap<String, User> mMap = new HashMap<>();

    private UserList() {
    }

    public void load() {
        Database db = Database.getInstance();
        try (ResultSet resultSet = db.execSelectAll(User.TABLE)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                String jid = resultSet.getString(User.COL_JID);
                String name = resultSet.getString(User.COL_NAME);
                String status = resultSet.getString(User.COL_STAT);
                long l = resultSet.getLong(User.COL_LAST_SEEN);
                Optional<Date> lastSeen = l == 0 ?
                        Optional.<Date>empty() :
                        Optional.<Date>of(new Date(l));
                boolean encr = resultSet.getBoolean(User.COL_ENCR);
                String key = Database.getString(resultSet, User.COL_PUB_KEY);
                String fp = Database.getString(resultSet, User.COL_KEY_FP);
                synchronized (this) {
                    mMap.put(jid, new User(id, jid, name, status, lastSeen, encr, key, fp));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load users from db", ex);
        }
        this.changed();
    }

    public synchronized SortedSet<User> getAll() {
        return new TreeSet<>(mMap.values());
    }

    /**
     * Add a new user to the list.
     * @param jid JID of new user
     * @param name nickname of new user, use an empty string if not known
     * @return the newly created user, if one was created
     */
    public synchronized Optional<User> add(String jid, String name) {
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

    public synchronized void save() {
        for (User user: mMap.values()) {
            user.save();
        }
    }

    synchronized Optional<User> get(int id) {
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
    public synchronized Optional<User> get(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return Optional.ofNullable(mMap.get(jid));
    }

    /**
     * Return whether a user with a specified JID exists.
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public synchronized boolean contains(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return mMap.containsKey(jid);
    }

    public synchronized void changed() {
        this.setChanged();
        this.notifyObservers();
    }

    public static UserList getInstance() {
        return INSTANCE;
    }
}
