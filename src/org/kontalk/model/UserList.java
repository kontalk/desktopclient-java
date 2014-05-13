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
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.Database;
import org.kontalk.MyKontalk;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserList extends HashMap<String, User>{
    private final static Logger LOGGER = Logger.getLogger(UserList.class.getName());

    private static UserList INSTANCE = null;
    
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
                this.put(jid, new User(id, jid, name));
            }
        resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load users from db", ex);
        }
        MyKontalk.getInstance().userListChanged();
    }
    
    public void addUser(String jid, String name) {
        jid = StringUtils.parseBareAddress(jid);
        if (this.containsKey(jid))
            return;
        User newUser = new User(jid, name);
        this.put(jid, newUser);
        MyKontalk.getInstance().userListChanged();
        this.save();
    }
    
    public void save() {
        for (User user: this.values()) {
            user.save();
        }
    }
    
    public User getUserByID(int id) {
        for (User user: this.values()) {
            if (user.getID() == id)
                return user;
        }
        LOGGER.warning("couldn't find user with ID: "+id);
        return null;
    }
    
    public User getUserByJID(String jid) {
        jid = StringUtils.parseBareAddress(jid);
        if (this.containsKey(jid))
            return this.get(jid);
        User newUser = new User(jid);
        this.put(jid, newUser);
        return newUser;
    }

    public static UserList getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new UserList();
        }
        return INSTANCE;
    }
    
}
