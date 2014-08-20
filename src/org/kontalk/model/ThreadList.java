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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Database;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class ThreadList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(ThreadList.class.getName());

    private static ThreadList INSTANCE = null;

    private final HashMap<Integer, KonThread> mMap = new HashMap();

    private ThreadList() {
    }

    public void load() {
        assert mMap.isEmpty();

        Database db = Database.getInstance();
        ResultSet receiverRS = db.execSelectAll(KonThread.TABLE_RECEIVER);
        ResultSet threadRS = db.execSelectAll(KonThread.TABLE);
        HashMap<Integer, Set<User>> threadUserMapping = new HashMap();
        UserList userList = UserList.getInstance();
        try {
            // first, find user for threads
            // TODO: rewrite
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
                if (userSet == null) {
                    LOGGER.warning("no users found for thread");
                    userSet = new HashSet();
                }
                String subject = threadRS.getString("subject");
                boolean read = threadRS.getBoolean("read");
                mMap.put(id, new KonThread(id, xmppThreadID, userSet, subject, read));
            }
            threadRS.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load threads from db", ex);
        }
        this.setChanged();
        this.notifyObservers();
    }

    public Collection<KonThread> getThreads() {
        return mMap.values();
    }

    public void save() {
        for (KonThread thread: mMap.values()) {
            thread.save();
        }
    }

    public KonThread getThreadByUser(User user) {
        for (KonThread thread : mMap.values()) {
            Set<User> threadUser = thread.getUser();
            if (threadUser.size() == 1 && threadUser.contains(user))
                return thread;
        }
        Set<User> userSet = new HashSet();
        userSet.add(user);
        return this.createNewThread(userSet);
    }

    public KonThread createNewThread(Set<User> user) {
        KonThread newThread = new KonThread(user);
        mMap.put(newThread.getID(), newThread);
        this.setChanged();
        this.notifyObservers();
        return newThread;
    }

    public KonThread getThreadByID(int id) {
        KonThread thread = mMap.get(id);
        if (thread == null)
            LOGGER.warning("can't find thread with id: "+id);
        return thread;
    }

    public KonThread getThreadByXMPPID(String xmppThreadID) {
        if (xmppThreadID == null) {
            return null;
        }
        for (KonThread thread : mMap.values()) {
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
