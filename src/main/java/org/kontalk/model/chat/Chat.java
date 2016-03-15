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

package org.kontalk.model.chat;

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.ObjectUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.model.Contact;
import org.kontalk.model.message.KonMessage;
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

    protected final int mID;
    private final ChatMessages mMessages;

    private boolean mRead;
    private boolean mDeleted = false;

    private ViewSettings mViewSettings;

    protected Chat(List<Member> members, String xmppID, String subject, GroupMetaData gData) {
        mMessages = new ChatMessages();
        mRead = true;
        mViewSettings = new ViewSettings();

        // insert
        List<Object> values = Arrays.asList(
                Database.setString(xmppID),
                Database.setString(subject),
                mRead,
                mViewSettings.toJSONString(),
                Database.setString(gData == null ? "" : gData.toJSON()));
        mID = Database.getInstance().execInsert(TABLE, values);
        if (mID < 1) {
            LOGGER.warning("couldn't insert chat");
            return;
        }

        members.stream().forEach(member -> member.insert(mID));
    }

    // used when loading from database
    protected Chat(int id, boolean read, String jsonViewSettings) {
        mID = id;
        mMessages = new ChatMessages();
        mRead = read;
        mViewSettings = new ViewSettings(this, jsonViewSettings);
    }

    void loadMessages(Map<Integer, Contact> contactMap) {
        mMessages.load(this, contactMap);
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

    public abstract List<Member> getAllMembers();

    /** Get all contacts (including deleted, blocked and user contact). */
    public abstract List<Contact> getAllContacts();

    /** Get valid receiver contacts (without deleted and blocked). */
    public abstract List<Contact> getValidContacts();

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
        List<Member> oldMembers = new ArrayList<>(this.getAllMembers());

        // save new members
        members.stream()
                .filter(m -> !oldMembers.contains(m))
                .forEach(m -> m.insert(mID));

        oldMembers.removeAll(members);
        // whats left is too much and can be deleted
        oldMembers.stream().forEach(m -> m.delete());
    }

    void delete() {
        Database db = Database.getInstance();

        // messages
        boolean succ = this.getMessages().getAll().stream().allMatch(m -> m.delete());
        if (!succ)
            return;

        // members
        succ = this.getAllMembers().stream().allMatch(m -> m.delete());
        if (!succ)
            return;

        // chat itself
        db.execDelete(TABLE, mID);

        // all done, commmit deletions
        succ = db.commit();
        if (!succ)
            return;

        mDeleted = true;
    }

    public boolean isDeleted()  {
        return mDeleted;
    }

    protected void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public void update(Observable o, Object arg) {
        this.changed(o);
    }

    static Chat loadOrNull(ResultSet rs, Map<Integer, Contact> contactMap)
            throws SQLException {
        int id = rs.getInt("_id");

        String jsonGD = Database.getString(rs, Chat.COL_GD);
        GroupMetaData gData = jsonGD.isEmpty() ?
                null :
                GroupMetaData.fromJSONOrNull(jsonGD);

        String xmppID = Database.getString(rs, Chat.COL_XMPPID);

        // get members of chat
        List<Member> members = Member.load(id, contactMap);

        String subject = Database.getString(rs, Chat.COL_SUBJ);

        boolean read = rs.getBoolean(Chat.COL_READ);

        String jsonViewSettings = Database.getString(rs,
                Chat.COL_VIEW_SET);

        Chat chat;
        if (gData != null) {
            chat = GroupChat.create(id, members, gData, subject, read, jsonViewSettings);
        } else {
            if (members.size() != 1) {
                LOGGER.warning("not one contact for single chat, id="+id);
                return null;
            }
            chat = new SingleChat(id, members.get(0), xmppID, read, jsonViewSettings);
        }

        chat.loadMessages(contactMap);
        return chat;
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
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof ViewSettings))
                return false;

            ViewSettings ovs = (ViewSettings) o;

            return ObjectUtils.equals(mColor, ovs.mColor) &&
                    mImagePath.equals(ovs.mImagePath);
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
