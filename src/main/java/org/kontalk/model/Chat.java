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

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.system.Database;

/**
 * A model for a conversation thread consisting of an ordered list of messages.
 * Changes of contacts in this chat are forwarded.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Chat extends Observable implements Comparable<Chat>, Observer {
    private static final Logger LOGGER = Logger.getLogger(Chat.class.getName());

    public static final String TABLE = "threads";
    public static final String COL_SUBJ = "subject";
    public static final String COL_READ = "read";
    public static final String COL_VIEW_SET = "view_settings";
    public static final String CREATE_TABLE = "( " +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            // optional XMPP chat ID
            "xmpp_id TEXT UNIQUE, " +
            COL_SUBJ+" TEXT, " +
            // boolean, contains unread messages?
            COL_READ+" INTEGER NOT NULL, " +
            // view settings in JSON format
            COL_VIEW_SET+" TEXT NOT NULL" +
            ")";

    // many to many relationship requires additional table for receiver
    public static final String TABLE_RECEIVER = "receiver";
    public static final String COL_REC_CHAT_ID = "thread_id";
    public static final String COL_REC_CONTACT_ID = "user_id";
    public static final String CREATE_TABLE_RECEIVER = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_REC_CHAT_ID+" INTEGER NOT NULL, " +
            COL_REC_CONTACT_ID+" INTEGER NOT NULL, " +
            "UNIQUE ("+COL_REC_CHAT_ID+", "+COL_REC_CONTACT_ID+"), " +
            "FOREIGN KEY ("+COL_REC_CHAT_ID+") REFERENCES "+TABLE+" (_id), " +
            "FOREIGN KEY ("+COL_REC_CONTACT_ID+") REFERENCES "+Contact.TABLE+" (_id) " +
            ")";

    private final int mID;
    private final String mXMPPID;
    private final ChatMessages mMessages;
    private final HashMap<Contact, KonChatState> mContactMap = new HashMap<>();

    private String mSubject;
    private boolean mRead;
    private ViewSettings mViewSettings;

    // used when creating a new chat
    Chat(Set<Contact> contacts) {
        assert contacts != null;
        // Kontalk Android client is ignoring the chat id, don't set it for now
        //mXMPPID = StringUtils.randomString(8);
        mXMPPID = "";
        this.setContactMap(contacts);
        if (contacts.size() > 1){
            mSubject = "New group chat";
        } else {
            mSubject = "";
        }
        mRead = true;
        mViewSettings = new ViewSettings();
        mMessages = new ChatMessages(this);

        Database db = Database.getInstance();
        List<Object> values = new LinkedList<>();
        values.add(Database.setString(mXMPPID));
        values.add(Database.setString(mSubject));
        values.add(mRead);
        values.add(mViewSettings.toJSONString());
        mID = db.execInsert(TABLE, values);
        if (mID < 1) {
            LOGGER.warning("couldn't insert chat");
            return;
        }

        for (Contact contact : contacts)
            this.insertReceiver(contact);
    }

    // used when loading from database
    Chat(int id,
            String xmppID,
            Set<Contact> contacts,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        assert contacts != null;
        mID = id;
        mXMPPID = xmppID;
        this.setContactMap(contacts);
        mSubject = subject;
        mRead = read;
        mViewSettings = new ViewSettings(this, jsonViewSettings);
        mMessages = new ChatMessages(this);
    }

    public ChatMessages getMessages() {
        return mMessages;
    }

    public int getID() {
        return mID;
    }

    public String getXMPPID() {
        return mXMPPID;
    }

    public Set<Contact> getContacts() {
        return mContactMap.keySet();
    }

    /**
     * Get contact if there is only one.
     */
    public Optional<Contact> getSingleContact() {
        return mContactMap.keySet().size() == 1 ?
                Optional.of(mContactMap.keySet().iterator().next()) :
                Optional.<Contact>empty();
    }

    public void setContacts(Set<Contact> contacts) {
        if (contacts.equals(mContactMap.keySet()))
            return;

        this.setContactMap(contacts);
        this.changed(contacts);
    }

    /**
     * Get the contact defined subject of this chat (empty string if not set).
     */
    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        if (subject.equals(mSubject))
            return;

        mSubject = subject;
        this.save();
        this.changed(subject);
    }

    public boolean isRead() {
        return mRead;
    }

    public void setRead() {
        if (mRead)
            return;

        mRead = true;
        this.changed(mRead);
    }

    public ViewSettings getViewSettings() {
        return mViewSettings;
    }

    public void setViewSettings(ViewSettings settings) {
        if (settings.equals(mViewSettings))
            return;

        mViewSettings = settings;
        this.save();
        this.changed(mViewSettings);
    }

    public boolean addMessage(KonMessage message) {
        boolean added = mMessages.add(message);
        if (added) {
            if (message.getDir() == KonMessage.Direction.IN) {
                mRead = false;
                this.changed(mRead);
            }
            this.changed(message);
        }
        return added;
    }

    public void setChatState(Contact contact, ChatState chatState) {
        KonChatState state = mContactMap.get(contact);
        if (state == null) {
            LOGGER.warning("can't find contact in contact map!?");
            return;
        }
        state.setState(chatState);
        this.changed(state);
    }

    void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put(COL_SUBJ, Database.setString(mSubject));
        set.put(COL_READ, mRead);
        set.put(COL_VIEW_SET, mViewSettings.toJSONString());

        db.execUpdate(TABLE, set, mID);

        // get receiver for this chat
        Map<Integer, Integer> dbReceiver = loadReceiver(mID);

        // add missing contact
        for (Contact contact : mContactMap.keySet()) {
            if (!dbReceiver.keySet().contains(contact.getID())) {
                this.insertReceiver(contact);
            }
            dbReceiver.remove(contact.getID());
        }

        // whats left is too much and can be removed
        for (int id : dbReceiver.values()) {
            db.execDelete(TABLE_RECEIVER, id);
        }
    }

    void delete() {
        Database db = Database.getInstance();
        // delete messages
        db.execDeleteWhereInsecure(KonMessage.TABLE,
                KonMessage.COL_CHAT_ID + " == " + mID);

        // delete receiver
        Map<Integer, Integer> dbReceiver = loadReceiver(mID);
        for (int id : dbReceiver.values()) {
            boolean deleted = db.execDelete(TABLE_RECEIVER, id);
            if (!deleted) return;
        }

        // delete chat itself
        db.execDelete(TABLE, mID);
    }

    private void insertReceiver(Contact contact) {
        Database db = Database.getInstance();
        List<Object> recValues = new LinkedList<>();
        recValues.add(mID);
        recValues.add(contact.getID());
        int id = db.execInsert(TABLE_RECEIVER, recValues);
        if (id < 1) {
            LOGGER.warning("couldn't insert receiver");
        }
    }

    private void setContactMap(Set<Contact> contacts){
        // TODO only apply differences to preserve chat states
        for (Contact contact: mContactMap.keySet())
            contact.deleteObserver(this);

        mContactMap.clear();
        for (Contact contact : contacts) {
            contact.addObserver(this);
            mContactMap.put(contact, new KonChatState(contact));
        }
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public String toString() {
        return "T:id="+mID+",xmppid="+mXMPPID+",subject="+mSubject;
    }

    @Override
    public void update(Observable o, Object arg) {
        this.changed(o);
    }

    @Override
    public int compareTo(Chat o) {
        return Integer.compare(this.mID, o.mID);
    }

    static Map<Integer, Integer> loadReceiver(int chatID) {
        Database db = Database.getInstance();
        String where = COL_REC_CHAT_ID + " == " + chatID;
        Map<Integer, Integer> dbReceiver = new HashMap<>();
        ResultSet resultSet;
        try {
            resultSet = db.execSelectWhereInsecure(TABLE_RECEIVER, where);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't get receiver from db", ex);
            return dbReceiver;
        }
        try {
            while (resultSet.next()) {
                dbReceiver.put(resultSet.getInt(COL_REC_CONTACT_ID),
                        resultSet.getInt("_id"));
            }
            resultSet.close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't get receiver", ex);
        }
        return dbReceiver;
    }

    public class KonChatState {
        private final Contact mContact;
        private ChatState mState = ChatState.gone;
        // note: the Android client does not set active states when only viewing
        // the chat (not necessary according to XEP-0085), this makes the
        // extra date field a bit useless
        // TODO save last active date to DB
        private Optional<Date> mLastActive = Optional.empty();

        private KonChatState(Contact contact) {
            mContact = contact;
        }

        public Contact getContact() {
            return mContact;
        }

        public ChatState getState() {
            return mState;
        }

        private void setState(ChatState state) {
            mState = state;
            if (mState == ChatState.active || mState == ChatState.composing)
                mLastActive = Optional.of(new Date());
        }
    }

    public static class ViewSettings {
        private static final String JSON_BG_COLOR = "bg_color";
        private static final String JSON_IMAGE_PATH = "img";

        // background color, if set
        private final Optional<Color> mOptColor;
        // custom image, if set
        private final String mImagePath;

        private ViewSettings(Chat t, String json) {
            Object obj = JSONValue.parse(json);
            Optional<Color> optColor;
            String imagePath;
            try {
                Map<?, ?> map = (Map) obj;
                optColor = map.containsKey(JSON_BG_COLOR) ?
                    Optional.of(new Color(((Long) map.get(JSON_BG_COLOR)).intValue())) :
                    Optional.<Color>empty();
                imagePath = map.containsKey(JSON_IMAGE_PATH) ?
                    (String) map.get(JSON_IMAGE_PATH) :
                    "";
            } catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON view settings", ex);
                optColor = Optional.empty();
                imagePath = "";
            }
            mOptColor = optColor;
            mImagePath = imagePath;
        }

        public ViewSettings() {
            mOptColor = Optional.empty();
            mImagePath = "";
        }

        public ViewSettings(Color color) {
            mOptColor = Optional.of(color);
            mImagePath = "";
        }

        public ViewSettings(String imagePath) {
            mOptColor = Optional.empty();
            mImagePath = imagePath;
        }

        public Optional<Color> getBGColor() {
            return mOptColor;
        }

        public String getImagePath() {
            return mImagePath;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        String toJSONString() {
            JSONObject json = new JSONObject();
            if (mOptColor.isPresent())
                json.put(JSON_BG_COLOR, mOptColor.get().getRGB());
            if (!mImagePath.isEmpty())
                json.put(JSON_IMAGE_PATH, mImagePath);
            return json.toJSONString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;

            if (!(obj instanceof ViewSettings)) return false;

            ViewSettings o = (ViewSettings) obj;
            return mOptColor.equals(o.mOptColor) &&
                    mImagePath.equals(o.mImagePath);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.mOptColor);
            hash = 37 * hash + Objects.hashCode(this.mImagePath);
            return hash;
        }
    }
}
