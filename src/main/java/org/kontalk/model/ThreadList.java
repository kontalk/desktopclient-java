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
import java.util.Observer;
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
public final class ThreadList extends Observable implements Observer {
    private static final Logger LOGGER = Logger.getLogger(ThreadList.class.getName());

    private static final ThreadList INSTANCE = new ThreadList();

    private final HashMap<Integer, KonThread> mMap = new HashMap<>();

    private boolean mUnread = false;

    private ThreadList() {
    }

    public void load() {
        assert mMap.isEmpty();

        HashMap<Integer, Set<Contact>> threadContactMapping = new HashMap<>();
        ContactList contactList = ContactList.getInstance();
        Database db = Database.getInstance();
        try (ResultSet receiverRS = db.execSelectAll(KonThread.TABLE_RECEIVER);
                ResultSet threadRS = db.execSelectAll(KonThread.TABLE)) {
            // first, find contact for threads
            // TODO: rewrite
            while (receiverRS.next()) {
                Integer threadID = receiverRS.getInt("thread_id");
                Integer contactID = receiverRS.getInt("user_id");
                Optional<Contact> optContact = contactList.get(contactID);
                if (!optContact.isPresent()) {
                    LOGGER.warning("can't find contact");
                    continue;
                }
                Contact contact = optContact.get();
                if (threadContactMapping.containsKey(threadID)) {
                    threadContactMapping.get(threadID).add(contact);
                } else {
                    Set<Contact> contactSet = new HashSet<>();
                    contactSet.add(contact);
                    threadContactMapping.put(threadID, contactSet);
                }
            }
            // now, create threads
            while (threadRS.next()) {
                int id = threadRS.getInt("_id");
                String xmppThreadID = Database.getString(threadRS, "xmpp_id");
                Set<Contact> contactSet = threadContactMapping.get(id);
                if (contactSet == null) {
                    LOGGER.warning("no contacts found for thread");
                    contactSet = new HashSet<>();
                }
                String subject = Database.getString(threadRS,
                        KonThread.COL_SUBJ);
                boolean read = threadRS.getBoolean(KonThread.COL_READ);
                String jsonViewSettings = Database.getString(threadRS,
                        KonThread.COL_VIEW_SET);

                this.put(new KonThread(id, xmppThreadID, contactSet, subject, read,
                        jsonViewSettings));
                if (!read)
                    mUnread = true;
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

    /**
     * Get a thread with only the contact as additional member.
     * Creates a new thread if necessary.
     */
    public KonThread get(Contact contact) {
        KonThread thread = this.getOrNull(contact);
        if (thread != null)
            return thread;

        Set<Contact> contactSet = new HashSet<>();
        contactSet.add(contact);
        return this.createNew(contactSet);
    }

    public KonThread createNew(Set<Contact> contact) {
        KonThread newThread = new KonThread(contact);
        this.put(newThread);
        this.changed(newThread);
        return newThread;
    }

    private void put(KonThread thread) {
        synchronized (this) {
            mMap.put(thread.getID(), thread);
        }
        thread.addObserver(this);
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

    public boolean contains(Contact contact) {
        return this.getOrNull(contact) != null;
    }

    public synchronized void delete(int id) {
        KonThread thread = mMap.remove(id);
        if (thread == null) {
            LOGGER.warning("can't delete thread, not found. id: "+id);
            return;
        }
        thread.delete();
        thread.deleteObservers();
        this.changed(thread);
    }

    /**
     * Return if any thread is unread.
     */
    public boolean isUnread() {
        return mUnread;
    }

    private synchronized KonThread getOrNull(Contact contact) {
        for (KonThread thread : mMap.values()) {
            Set<Contact> threadContact = thread.getContacts();
            if (threadContact.size() == 1 && threadContact.contains(contact))
                return thread;
        }
        return null;
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    public static ThreadList getInstance() {
        return INSTANCE;
    }

    @Override
    public void update(Observable o, Object arg) {
        // only observing threads 'read' status
        if (!(arg instanceof Boolean))
            return;

        boolean unread = !((boolean) arg);
        if (mUnread == unread)
            return;

        if (unread) {
            mUnread = true;
            this.changed(mUnread);
            return;
        }

        synchronized (this) {
            for (KonThread thread : mMap.values()) {
                if (!thread.isRead()) {
                    return;
                }
            }
        }
        mUnread = false;
        this.changed(mUnread);
    }
}
