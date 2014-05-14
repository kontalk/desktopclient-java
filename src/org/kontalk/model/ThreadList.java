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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Database;
import org.kontalk.MyKontalk;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ThreadList extends HashMap<Integer, KontalkThread> {
    private final static Logger LOGGER = Logger.getLogger(ThreadList.class.getName());
    
    private static ThreadList INSTANCE = null;
    
    private ThreadList() {
    }
    
    public void load() {
        Database db = Database.getInstance();
        ResultSet receiverRS = db.execSelectAll(KontalkThread.TABLE_RECEIVER);
        ResultSet threadRS = db.execSelectAll(KontalkThread.TABLE);
        HashMap<Integer, Set<User>> threadUserMapping = new HashMap();
        UserList userList = UserList.getInstance();
        try {
            // first, find user for threads
            while (receiverRS.next()) {
                Integer threadID = receiverRS.getInt("thread_id");
                Integer userID = receiverRS.getInt("user_id");
                User user = userList.getUserByID(userID);
                if (threadUserMapping.containsKey(threadID)) {
                    threadUserMapping.get(threadID).add(user);
                } else {
                    Set<User> userSet = new HashSet();
                    userSet.add(user);
                    threadUserMapping.put(threadID, userSet);
                }
            }
            receiverRS.close();
            // now, create threads
            while (threadRS.next()) {
                int id = threadRS.getInt("_id");
                String xmppThreadID = threadRS.getString("xmpp_id");
                Set<User> userSet = threadUserMapping.get(id);
                String subject = threadRS.getString("subject");
                this.put(id, new KontalkThread(id, xmppThreadID, userSet, subject));
            }
            threadRS.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load threads from db", ex);
        }
        MyKontalk.getInstance().threadListChanged();
    }
    
    public void save() {
        for (KontalkThread thread: this.values()) {
            thread.save();
        }
    }
    
    public KontalkThread getThreadByUser(User user) {
        for (KontalkThread thread : this.values()) {
            Set<User> threadUser = thread.getUser();
            if (threadUser.size() == 1 && threadUser.contains(user))
                return thread;
        }
        KontalkThread newThread = new KontalkThread(user);
        this.put(newThread.getID(), newThread);
        MyKontalk.getInstance().threadListChanged();
        return newThread;
    }
    
    public KontalkThread getThreadByID(int id) {
        KontalkThread thread = this.get(id);
        if (thread == null)
            LOGGER.warning("can't find thread with id: "+id);
        return thread;
    }
    
    public KontalkThread getThreadByXMPPID(String xmppThreadID) {
        if (xmppThreadID == null) {
            return null;
        }
        for (KontalkThread thread : this.values()) {
            if (thread.getXMPPID().equals(xmppThreadID))
                return thread;
        }
        return null;
    }
    
    public static ThreadList getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ThreadList();
        }
        return INSTANCE;
    }
}
