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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.system.Database;

/**
 * A model for a conversation thread consisting of an ordered list of messages.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class KonThread extends Observable {
    private final static Logger LOGGER = Logger.getLogger(KonThread.class.getName());

    public static final String TABLE = "threads";
    public static final String CREATE_TABLE = "( " +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "xmpp_id TEXT UNIQUE, " +
            "subject TEXT, " +
            // boolean, contains unread messages?
            "read INTEGER NOT NULL" +
            ")";

    // many to many relationship requires additional table for receiver
    public static final String TABLE_RECEIVER = "receiver";
    public static final String CREATE_TABLE_RECEIVER = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "UNIQUE (thread_id, user_id), " +
            "FOREIGN KEY (thread_id) REFERENCES "+TABLE+" (_id), " +
            "FOREIGN KEY (user_id) REFERENCES "+User.TABLE+" (_id) " +
            ")";

    // remember that KonMessage's natural ordering is not consistent with equals
    private final SortedSet<KonMessage> mSet =
            Collections.synchronizedSortedSet(new TreeSet<KonMessage>());

    private final int mID;
    private final String mXMPPID;
    private HashMap<User, KonChatState> mUserMap;
    private String mSubject;
    private boolean mRead;
    private boolean mDeleted = false;

    // used when creating a new thread
    KonThread(Set<User> user) {
        assert user != null;
        // Kontalk Android client is ignoring the thread id, don't set it for now
        //mXMPPID = StringUtils.randomString(8);
        mXMPPID = "";
        this.setUserMap(user);
        mSubject = "";
        mRead = true;

        Database db = Database.getInstance();
        List<Object> values = new LinkedList<>();
        values.add(Database.setString(mXMPPID));
        values.add(Database.setString(mSubject));
        values.add(mRead);
        mID = db.execInsert(TABLE, values);
        if (mID < 1) {
            LOGGER.warning("couldn't insert thread");
            return;
        }

        for (User oneUser : user)
            this.insertReceiver(oneUser);
    }

    // used when loading from database
    KonThread(int id, String xmppID, Set<User> user, String subject, boolean read) {
        assert user != null;
        mID = id;
        mXMPPID = xmppID;
        this.setUserMap(user);
        mSubject = subject;
        mRead = read;
    }

    public SortedSet<KonMessage> getMessages() {
        return mSet;
    }

    public int getID() {
        return mID;
    }

    public Optional<String> getXMPPID() {
        return mXMPPID.isEmpty() ? Optional.<String>empty() : Optional.of(mXMPPID);
    }

    public Set<User> getUser() {
        return mUserMap.keySet();
    }

    public void setUser(Set<User> user) {
        if (user.equals(mUserMap.keySet()))
            return;

        this.setUserMap(user);
        this.changed();
    }

    /**
     * Get the user defined subject of this thread (empty string if not set).
     */
    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        if (subject.equals(mSubject))
            return;

        mSubject = subject;
        this.save();
        this.changed();
    }

    public boolean isRead() {
        return mRead;
    }

    public void setRead() {
        if (mRead)
            return;

        mRead = true;
        this.changed();
    }

    public boolean isDeleted() {
        return mDeleted;
    }

    public void addMessage(KonMessage message) {
        boolean added = this.add(message);
        if (added) {
            if (message.getDir() == KonMessage.Direction.IN)
                mRead = false;
            this.changed();
        }
    }

    public void setChatState(User user, ChatState chatState) {
        KonChatState state = mUserMap.get(user);
        if (state == null) {
            LOGGER.warning("can't find user in user map!?");
            return;
        }
        state.setState(chatState);
        // TODO notify
    }

    /**
     * Add message to thread without notifying other components.
     */
    boolean add(KonMessage message) {
        // see KonMessage.equals()
        if (mSet.contains(message)) {
            LOGGER.warning("message already in thread, ID: " + message.getID());
            return false;
        }
        boolean added = mSet.add(message);
        return added;
    }

    void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put("subject", Database.setString(mSubject));
        set.put("read", mRead);
        db.execUpdate(TABLE, set, mID);

        // get receiver for this thread
        Map<Integer, Integer> dbReceiver = this.loadReceiver();

        // add missing user
        for (User user : mUserMap.keySet()) {
            if (!dbReceiver.keySet().contains(user.getID())) {
                this.insertReceiver(user);
            }
            dbReceiver.remove(user.getID());
        }

        // whats left is too much and can be removed
        for (int id : dbReceiver.values()) {
            db.execDelete(TABLE_RECEIVER, id);
        }
    }

    void delete() {
        // delete messages
        for (KonMessage message : mSet) {
            boolean deleted = message.delete();
            if (!deleted) return;
        }

        Database db = Database.getInstance();
        // delete receiver
        Map<Integer, Integer> dbReceiver = this.loadReceiver();
        for (int id : dbReceiver.values()) {
            boolean deleted = db.execDelete(TABLE_RECEIVER, id);
            if (!deleted) return;
        }

        // delete thread itself
        db.execDelete(TABLE, mID);
        mDeleted = true;
        this.changed();
    }

    private Map<Integer, Integer> loadReceiver() {
        Database db = Database.getInstance();
        String where = "thread_id == " + mID;
        Map<Integer, Integer> dbReceiver = new HashMap<>();
        ResultSet resultSet;
        try {
            resultSet = db.execSelectWhereInsecure(TABLE_RECEIVER, where);
        } catch (SQLException ex) {
            LOGGER.warning("can't get receiver from db");
            return dbReceiver;
        }
        try {
            while (resultSet.next()) {
                dbReceiver.put(resultSet.getInt("user_id"), resultSet.getInt("_id"));
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't get receiver", ex);
        }
        return dbReceiver;
    }

    private void insertReceiver(User user) {
        Database db = Database.getInstance();
        List<Object> recValues = new LinkedList<>();
        recValues.add(mID);
        recValues.add(user.getID());
        int id = db.execInsert(TABLE_RECEIVER, recValues);
        if (id < 1) {
            LOGGER.warning("couldn't insert receiver");
        }
    }

    private void setUserMap(Set<User> user){
        // TODO only apply differences to preserve chat states
        mUserMap = new HashMap<>();
        for (User oneUser : user)
            mUserMap.put(oneUser, new KonChatState());
    }

    private synchronized void changed() {
        this.setChanged();
        this.notifyObservers();
    }

    private class KonChatState {
        private ChatState mState = ChatState.gone;
        // note: the Android client does not set active states when only viewing
        // the thread (not necessary according to XEP-0085), this makes the
        // extra date field a bit useless
        // TODO save last active date to DB
        private Optional<Date> mLastActive = Optional.empty();

        private void setState(ChatState state) {
            mState = state;
            if (mState == ChatState.active || mState == ChatState.composing)
                mLastActive = Optional.of(new Date());
        }
    }
}
