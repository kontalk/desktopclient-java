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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.system.Database;

/**
 * A model for a conversation thread consisting of an ordered list of messages.
 *
 * Changes of contacts in this chat are forwarded.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class Chat extends Observable implements Observer {
    private static final Logger LOGGER = Logger.getLogger(Chat.class.getName());

    public static final String TABLE = "threads";
    public static final String COL_XMPPID = "xmpp_id";
    public static final String COL_GD = "gid";
    public static final String COL_SUBJ = "subject";
    public static final String COL_READ = "read";
    public static final String COL_VIEW_SET = "view_settings";
    public static final String SCHEMA = "( " +
            Database.SQL_ID +
            // optional XMPP chat ID
            COL_XMPPID+" TEXT UNIQUE, " +
            // optional subject
            COL_SUBJ+" TEXT, " +
            // boolean, contains unread messages?
            COL_READ+" INTEGER NOT NULL, " +
            // view settings in JSON format
            COL_VIEW_SET+" TEXT NOT NULL, " +
            // optional group id in JSON format
            COL_GD+" TEXT " +
            ")";

    // many to many relationship requires additional table for receiver
    public static final String RECEIVER_TABLE = "receiver";
    public static final String COL_REC_CHAT_ID = "thread_id";
    public static final String COL_REC_CONTACT_ID = "user_id";
    public static final String COL_REC_ROLE = "role";
    public static final String RECEIVER_SCHEMA = "(" +
            Database.SQL_ID +
            COL_REC_CHAT_ID+" INTEGER NOT NULL, " +
            COL_REC_CONTACT_ID+" INTEGER NOT NULL, " +
            COL_REC_ROLE+" INTEGER NOT NULL, " +
            "UNIQUE ("+COL_REC_CHAT_ID+", "+COL_REC_CONTACT_ID+"), " +
            "FOREIGN KEY ("+COL_REC_CHAT_ID+") REFERENCES "+TABLE+" (_id), " +
            "FOREIGN KEY ("+COL_REC_CONTACT_ID+") REFERENCES "+Contact.TABLE+" (_id) " +
            ")";

    /**
     * Long-live authorization model of member in group.
     * Called 'Affiliation' in MUC
     * Do not modify, only add! Ordinal used in database
     */
    public enum Role {DEFAULT, OWNER, ADMIN};

    protected final int mID;
    private final ChatMessages mMessages;

    private boolean mRead;

    private ViewSettings mViewSettings;

    protected Chat(Contact contact, String xmppID, String subject) {
        this(Arrays.asList(new Member(contact)), xmppID, subject, null);
    }

    protected Chat(List<Member> members, String xmppID, String subject, GroupMetaData gData) {
        mMessages = new ChatMessages(this, true);
        mRead = true;
        mViewSettings = new ViewSettings();

        // insert
        Database db = Database.getInstance();
        List<Object> values = new LinkedList<>();
        values.add(Database.setString(xmppID));
        values.add(Database.setString(subject));
        values.add(mRead);
        values.add(mViewSettings.toJSONString());
        values.add(Database.setString(gData == null ? "" : gData.toJSON()));
        mID = db.execInsert(TABLE, values);
        if (mID < 1) {
            LOGGER.warning("couldn't insert chat");
            return;
        }

        for (Member member : members)
            member.insert(mID);
    }

    // used when loading from database
    protected Chat(int id, boolean read, String jsonViewSettings) {
        mID = id;
        mMessages = new ChatMessages(this, false);
        mRead = read;
        mViewSettings = new ViewSettings(this, jsonViewSettings);
    }

    public ChatMessages getMessages() {
        return mMessages;
    }

    public boolean addMessage(KonMessage message) {
        assert message.getChat() == this;

        boolean added = mMessages.add(message);
        if (added) {
            if (message.isInMessage() && mRead) {
                mRead = false;
                this.save();
                this.changed(mRead);
            }
            this.changed(message);
        }
        return added;
    }

    public int getID() {
        return mID;
    }

    public boolean isRead() {
        return mRead;
    }

    public void setRead() {
        if (mRead)
            return;

        mRead = true;
        this.save();
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

    public boolean isGroupChat() {
        return (this instanceof GroupChat);
    }

    protected abstract List<Member> getAllMembers();

    /** Get all contacts (including deleted, blocked and user contact).
     * TODO remove me
     */
    public abstract List<Contact> getAllContacts();

    /** Get valid receiver contacts (without deleted and blocked). */
    public abstract Contact[] getValidContacts();

    /** XMPP thread ID (empty string if not set). */
    public abstract String getXMPPID();

    /** Subject/title (empty string if not set). */
    public abstract String getSubject();

    /**
     * Return if new outgoing messages in chat will be encrypted.
     * True if encryption is turned on for at least one valid chat contact.
     */
    public abstract boolean isSendEncrypted();

    /**
     * Return if new outgoing messages could be send encrypted.
     * True if all valid  chat contacts have a key.
     */
    public abstract boolean canSendEncrypted();

    /** Return if new valid outgoing message could be send. */
    public abstract boolean isValid();

    public abstract boolean isAdministratable();

    public abstract void setChatState(Contact contact, ChatState chatState);

    abstract void save();

    protected void save(List<Member> members, String subject) {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put(COL_SUBJ, Database.setString(subject));
        set.put(COL_READ, mRead);
        set.put(COL_VIEW_SET, mViewSettings.toJSONString());

        db.execUpdate(TABLE, set, mID);

        // get receiver for this chat
        List<Member> oldMembers = this.getAllMembers();

        // save new members
        for (Member m : members) {
            if (!oldMembers.contains(m)) {
                m.insert(mID);
            }
            oldMembers.remove(m);
        }

        // whats left is too much and can be deleted
        for (Member m : oldMembers) {
            m.delete();
        }
    }

    void delete() {
        Database db = Database.getInstance();

        String whereMessages = KonMessage.COL_CHAT_ID + " == " + mID;

        // transmissions
        boolean succ = db.execDeleteWhereInsecure(Transmission.TABLE,
                Transmission.COL_MESSAGE_ID + " IN (SELECT _id FROM " +
                        KonMessage.TABLE + " WHERE " + whereMessages + ")");
        if (!succ)
            return;

        // messages
        succ = db.execDeleteWhereInsecure(KonMessage.TABLE, whereMessages);
        if (!succ)
            return;

        // members
        boolean allDeleted = true;
        for (Member member : this.getAllMembers()) {
            allDeleted &= member.delete();
        }
        if (!allDeleted)
            return;

        // chat itself
        db.execDelete(TABLE, mID);
    }

    protected void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public void update(Observable o, Object arg) {
        this.changed(o);
    }

    static Chat loadOrNull(ResultSet rs) throws SQLException {
        int id = rs.getInt("_id");

        String jsonGD = Database.getString(rs, Chat.COL_GD);
        GroupMetaData gData = jsonGD.isEmpty() ?
                null :
                GroupMetaData.fromJSONOrNull(jsonGD);

        String xmppID = Database.getString(rs, Chat.COL_XMPPID);

        // get members for chat
        List<Member> members = Member.load(id);

        String subject = Database.getString(rs, Chat.COL_SUBJ);

        boolean read = rs.getBoolean(Chat.COL_READ);

        String jsonViewSettings = Database.getString(rs,
                Chat.COL_VIEW_SET);

        if (gData != null) {
            return GroupChat.create(id, members, gData, subject, read, jsonViewSettings);
        } else {
            if (members.size() != 1) {
                LOGGER.warning("not one contact for single chat, id="+id);
                return null;
            }
            return new SingleChat(id, members.get(0).contact, xmppID, read, jsonViewSettings);
        }
    }

    public static final class Member {
        public final Contact contact;
        public final GroupChat.Role role;

        private int id;

        private ChatState mState = ChatState.gone;
        // note: the Android client does not set active states when only viewing
        // the chat (not necessary according to XEP-0085), this makes the
        // extra date field a bit useless
        // TODO save last active date to DB
        private Date mLastActive = null;

        public Member(Contact contact){
            this(contact, Role.DEFAULT);
        }

        public Member(Contact contact, GroupChat.Role role) {
            this(0, contact, role);
        }

        private Member(int id, Contact contact, GroupChat.Role role) {
            this.id = id;
            this.contact = contact;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Member))
                return false;

            return this.contact.equals(o);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.contact);
            return hash;
        }

        @Override
        public String toString() {
            return "Mem:c={"+contact+"}r="+role;
        }

        public ChatState getState() {
            return mState;
        }

        private boolean insert(int chatID) {
            if (id > 0) {
                LOGGER.warning("already in database");
                return true;
            }

            List<Object> recValues = new LinkedList<>();
            recValues.add(chatID);
            recValues.add(contact.getID());
            recValues.add(role);
            id = Database.getInstance().execInsert(RECEIVER_TABLE, recValues);
            if (id <= 0) {
                LOGGER.warning("could not insert member");
                return false;
            }
            return true;
        }

        private void save() {
            // TODO
        }

        private boolean delete() {
            if (id <= 0) {
                LOGGER.warning("not in database");
                return true;
            }

            return Database.getInstance().execDelete(RECEIVER_TABLE, id);
        }

        protected void setState(ChatState state) {
            mState = state;
            if (mState == ChatState.active || mState == ChatState.composing)
                mLastActive = new Date();
        }

        static List<Member> load(int chatID) {
            Database db = Database.getInstance();
            String where = COL_REC_CHAT_ID + " == " + chatID;
            ResultSet resultSet;
            try {
                resultSet = db.execSelectWhereInsecure(RECEIVER_TABLE, where);
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "can't get receiver from db", ex);
                return Collections.emptyList();
            }
            List<Member> members = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    int id = resultSet.getInt("_id");
                    int contactID = resultSet.getInt(COL_REC_CONTACT_ID);
                    int r = resultSet.getInt(COL_REC_ROLE);
                    Role role = Role.values()[r];
                    Contact c = ContactList.getInstance().get(contactID).orElse(null);
                    if (c == null) {
                        LOGGER.warning("can't find contact, ID:"+contactID);
                        continue;
                    }

                    members.add(new Member(id, c, role));
                }
                resultSet.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "can't get members", ex);
            }
            return members;
        }
    }

    public static class ViewSettings {
        private static final String JSON_BG_COLOR = "bg_color";
        private static final String JSON_IMAGE_PATH = "img";

        // background color, if set
        private final Color mColor;
        // custom image, if set
        private final String mImagePath;

        private ViewSettings(Chat t, String json) {
            Object obj = JSONValue.parse(json);
            Color color;
            String imagePath;
            try {
                Map<?, ?> map = (Map) obj;
                color = map.containsKey(JSON_BG_COLOR) ?
                    new Color(((Long) map.get(JSON_BG_COLOR)).intValue()) :
                    null;
                imagePath = map.containsKey(JSON_IMAGE_PATH) ?
                    (String) map.get(JSON_IMAGE_PATH) :
                    "";
            } catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON view settings", ex);
                color = null;
                imagePath = "";
            }
            mColor = color;
            mImagePath = imagePath;
        }

        public ViewSettings() {
            mColor = null;
            mImagePath = "";
        }

        public ViewSettings(Color color) {
            mColor = null;
            mImagePath = "";
        }

        public ViewSettings(String imagePath) {
            mColor = null;
            mImagePath = imagePath;
        }

        public Optional<Color> getBGColor() {
            return Optional.ofNullable(mColor);
        }

        public String getImagePath() {
            return mImagePath;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        String toJSONString() {
            JSONObject json = new JSONObject();
            if (mColor != null)
                json.put(JSON_BG_COLOR, mColor.getRGB());
            if (!mImagePath.isEmpty())
                json.put(JSON_IMAGE_PATH, mImagePath);
            return json.toJSONString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;

            if (!(obj instanceof ViewSettings)) return false;

            ViewSettings o = (ViewSettings) obj;
            return mColor.equals(o.mColor) &&
                    mImagePath.equals(o.mImagePath);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.mColor);
            hash = 37 * hash + Objects.hashCode(this.mImagePath);
            return hash;
        }
    }
}
