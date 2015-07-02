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
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * The global list of all threads.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ThreadList extends Observable {
    private final static Logger LOGGER = Logger.getLogger(ThreadList.class.getName());

    private final static ThreadList INSTANCE = new ThreadList();

    private final HashMap<Integer, KonThread> mMap = new HashMap<>();

    private ThreadList() {
    }

    public void load() {
        assert mMap.isEmpty();

        HashMap<Integer, Set<User>> threadUserMapping = new HashMap<>();
        UserList userList = UserList.getInstance();
        Database db = Database.getInstance();
        try (ResultSet receiverRS = db.execSelectAll(KonThread.TABLE_RECEIVER);
                ResultSet threadRS = db.execSelectAll(KonThread.TABLE)) {
            // first, find user for threads
            // TODO: rewrite
            while (receiverRS.next()) {
                Integer threadID = receiverRS.getInt("thread_id");
                Integer userID = receiverRS.getInt("user_id");
                Optional<User> optUser = userList.get(userID);
                if (!optUser.isPresent()) {
                    LOGGER.warning("can't find user");
                    continue;
                }
                User user = optUser.get();
                if (threadUserMapping.containsKey(threadID)) {
                    threadUserMapping.get(threadID).add(user);
                } else {
                    Set<User> userSet = new HashSet<>();
                    userSet.add(user);
                    threadUserMapping.put(threadID, userSet);
                }
            }
            // now, create threads
            while (threadRS.next()) {
                int id = threadRS.getInt("_id");
                String xmppThreadID = Database.getString(threadRS, "xmpp_id");
                Set<User> userSet = threadUserMapping.get(id);
                if (userSet == null) {
                    LOGGER.warning("no users found for thread");
                    userSet = new HashSet<>();
                }
                String subject = Database.getString(threadRS, KonThread.COL_SUBJ);
                boolean read = threadRS.getBoolean(KonThread.COL_READ);
                String jsonViewSettings = Database.getString(threadRS, KonThread.COL_VIEW_SET);
                synchronized (this) {
                    mMap.put(id, new KonThread(id, xmppThreadID, userSet, subject, read, jsonViewSettings));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load threads from db", ex);
        }
        this.changed(null);
    }

    public synchronized SortedSet<KonThread> getAll() {
        return new TreeSet<>(mMap.values());
    }

    public synchronized void save() {
        for (KonThread thread: mMap.values()) {
            thread.save();
        }
    }

    public KonThread get(User user) {
        synchronized (this) {
            for (KonThread thread : mMap.values()) {
                Set<User> threadUser = thread.getUser();
                if (threadUser.size() == 1 && threadUser.contains(user))
                    return thread;
            }
        }
        Set<User> userSet = new HashSet<>();
        userSet.add(user);
        return this.createNew(userSet);
    }

    public KonThread createNew(Set<User> user) {
        KonThread newThread = new KonThread(user);
        synchronized (this) {
            mMap.put(newThread.getID(), newThread);
        }
        this.changed(newThread);
        return newThread;
    }

    public synchronized Optional<KonThread> get(int id) {
        KonThread thread = mMap.get(id);
        if (thread == null)
            LOGGER.warning("can't find thread with id: "+id);
        return Optional.ofNullable(thread);
    }

    public synchronized Optional<KonThread> get(String xmppThreadID) {
        if (xmppThreadID == null || xmppThreadID.isEmpty()) {
            return Optional.empty();
        }
        for (KonThread thread : mMap.values()) {
            if (xmppThreadID.equals(thread.getXMPPID().orElse(null)))
                return Optional.of(thread);
        }
        return Optional.empty();
    }

    public boolean contains(int id) {
        return mMap.containsKey(id);
    }

    public synchronized void delete(int id) {
        KonThread thread = mMap.remove(id);
        if (thread == null) {
            LOGGER.warning("can't delete thread, not found. id: "+id);
            return;
        }
        thread.delete();
        this.changed(thread);
    }

    private synchronized void changed(KonThread thread) {
        this.setChanged();
        this.notifyObservers(thread);
    }

    public static ThreadList getInstance() {
        return INSTANCE;
    }
}
