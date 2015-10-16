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

import org.kontalk.misc.JID;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.system.Database;
import org.kontalk.util.EncodingUtils;

/**
 * A model for a conversation thread consisting of an ordered list of messages.
 *
 * Changes of contacts in this chat are forwarded.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Chat extends Observable implements Comparable<Chat>, Observer {
    private static final Logger LOGGER = Logger.getLogger(Chat.class.getName());

    public static final String TABLE = "threads";
    public static final String COL_XMPPID = "xmpp_id";
    public static final String COL_GID = "gid";
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
            COL_GID+" TEXT " +
            ")";

    // many to many relationship requires additional table for receiver
    public static final String RECEIVER_TABLE = "receiver";
    public static final String COL_REC_CHAT_ID = "thread_id";
    public static final String COL_REC_CONTACT_ID = "user_id";
    public static final String RECEIVER_SCHEMA = "(" +
            Database.SQL_ID +
            COL_REC_CHAT_ID+" INTEGER NOT NULL, " +
            COL_REC_CONTACT_ID+" INTEGER NOT NULL, " +
            "UNIQUE ("+COL_REC_CHAT_ID+", "+COL_REC_CONTACT_ID+"), " +
            "FOREIGN KEY ("+COL_REC_CHAT_ID+") REFERENCES "+TABLE+" (_id), " +
            "FOREIGN KEY ("+COL_REC_CONTACT_ID+") REFERENCES "+Contact.TABLE+" (_id) " +
            ")";

    private final int mID;
    private final HashMap<Contact, KonChatState> mContactMap = new HashMap<>();
    private final String mXMPPID;
    private final ChatMessages mMessages;
    private final Optional<GID> mOptGID;

    private String mSubject;
    private boolean mRead;
    private ViewSettings mViewSettings;

    /** New single user chat. */
    Chat(Contact contact, String xmppID) {
        this(new Contact[]{contact}, xmppID, null, "");
    }

    /** New group chat. */
    Chat(Contact[] contacts, GID gid, String subject) {
        this(contacts, "", gid, subject);
    }

    private Chat(Contact[] contacts, String xmppID, GID gid, String subject) {
        assert contacts != null;
        assert xmppID.isEmpty() || gid == null;

        for (Contact contact: contacts)
            this.addContactSilent(contact);
        // group chats also include the user itself
        if (gid != null) {
            Optional<Contact> optMe = ContactList.getInstance().getMe();
            if (!optMe.isPresent())
                LOGGER.warning("can't add user to group chat");
            else
                this.addContactSilent(optMe.get());
        }

        // note: Kontalk Android client is ignoring the chat id
        mXMPPID = xmppID;
        mOptGID = Optional.ofNullable(gid);
        mSubject = subject;

        mRead = true;
        mViewSettings = new ViewSettings();
        mMessages = new ChatMessages(this);

        Database db = Database.getInstance();
        List<Object> values = new LinkedList<>();
        values.add(Database.setString(mXMPPID));
        values.add(Database.setString(mSubject));
        values.add(mRead);
        values.add(mViewSettings.toJSONString());
        values.add(Database.setString(mOptGID.isPresent() ?
                mOptGID.get().toJSON() :
                ""));
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
            Set<Contact> contacts,
            Optional<GID> optGID,
            String xmppID,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        assert contacts != null;
        mID = id;
        for (Contact contact: contacts)
            this.addContactSilent(contact);
        mOptGID = optGID;
        mXMPPID = xmppID;
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

    public Optional<GID> getGID() {
        return mOptGID;
    }

    public String getXMPPID() {
        return mXMPPID;
    }

    /** Get all contacts (including deleted and user contact). */
    public Set<Contact> getAllContacts() {
        return mContactMap.keySet();
    }

    /** Get contacts (without deleted and user contact). */
    public Set<Contact> getContacts() {
        //chat.getContacts().stream().filter(c -> !c.isDeleted());
        Set<Contact> contacts = new HashSet<>();
        for (Contact c : this.getAllContacts()) {
            if (!c.isDeleted() && !c.isMe()) {
                contacts.add(c);
            }
        }
        return contacts;
    }

    /**
     * Get contact if there is only one (can be deleted).
     */
    public Optional<Contact> getSingleContact() {
        return mContactMap.keySet().size() == 1 ?
                Optional.of(mContactMap.keySet().iterator().next()) :
                Optional.<Contact>empty();
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
        this.save();
        this.changed(mRead);
    }

    public boolean isGroupChat() {
        return mOptGID.isPresent();
    }

    public boolean containsMe() {
        return mContactMap.keySet().parallelStream().anyMatch(
                new Predicate<Contact>() {
                    @Override
                    public boolean test(Contact t) {
                        return t.isMe();
                    }
                }
        );
    }

    /**
     * Return if new outgoing messages in chat will be encrypted.
     * True if encryption is turned on for at least one chat contact.
     */
    public boolean sendEncrypted() {
        boolean encrypted = false;
        for (Contact c: this.getContacts()) {
            encrypted |= c.getEncrypted();
        }
        return encrypted;
    }

    /**
     * Return if new outgoing messages could be send encrypted.
     * True if all chat contacts have a key.
     */
    public boolean canSendEncrypted() {
        Set<Contact> contacts = this.getContacts();
        boolean encrypted = !contacts.isEmpty();
        for (Contact c: contacts) {
            encrypted &= c.hasKey();
        }
        return encrypted;
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
            if (message.isInMessage() && mRead) {
                mRead = false;
                this.save();
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

    public void applyGroupCommand(GroupCommand command, Contact sender) {
        switch(command.getOperation()) {
            case CREATE:
                assert mContactMap.size() == 1;
                assert mContactMap.containsKey(sender);

                boolean meIn = false;
                for (JID jid: command.getAdded()) {
                    Optional<Contact> optContact = ContactList.getInstance().get(jid);
                    if (!optContact.isPresent()) {
                        LOGGER.warning("can't find contact, jid: "+jid);
                        continue;
                    }
                    Contact contact = optContact.get();
                    if (this.getAllContacts().contains(contact)) {
                        LOGGER.warning("contact already in chat: "+contact);
                        continue;
                    }
                    meIn |= contact.isMe();
                    this.addContact(contact);
                }

                if (!meIn)
                    LOGGER.warning("user JID not included");

                mSubject = command.getSubject();
                this.save();
                this.changed(command);
                break;
            case LEAVE:
                this.removeContact(sender);
                this.save();
                this.changed(command);
                break;
            // TODO
            //case SET:
                //this.changed(command);
            //    this.save();
            //    break;
            default:
                LOGGER.warning("unhandled operation: "+command.getOperation());
        }
    }

    private void save() {
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
            db.execDelete(RECEIVER_TABLE, id);
        }
    }

    void delete() {
        Database db = Database.getInstance();

        String whereMessages = KonMessage.COL_CHAT_ID + " == " + mID;

        // transmissions
        db.execDeleteWhereInsecure(Transmission.TABLE,
                Transmission.COL_MESSAGE_ID + " IN (SELECT _id FROM " +
                        KonMessage.TABLE + " WHERE " + whereMessages + ")");

        // messages
        db.execDeleteWhereInsecure(KonMessage.TABLE, whereMessages);

        // receiver
        Map<Integer, Integer> dbReceiver = loadReceiver(mID);
        for (int id : dbReceiver.values()) {
            boolean deleted = db.execDelete(RECEIVER_TABLE, id);
            if (!deleted) return;
        }

        // chat itself
        db.execDelete(TABLE, mID);
    }

    private void insertReceiver(Contact contact) {
        Database db = Database.getInstance();
        List<Object> recValues = new LinkedList<>();
        recValues.add(mID);
        recValues.add(contact.getID());
        int id = db.execInsert(RECEIVER_TABLE, recValues);
        if (id < 1) {
            LOGGER.warning("couldn't insert receiver");
        }
    }

    private void addContact(Contact contact) {
        this.addContactSilent(contact);
        this.save();
    }

    private void addContactSilent(Contact contact) {
        if (mContactMap.containsKey(contact)) {
            LOGGER.warning("contact already in chat: "+contact);
            return;
        }

        contact.addObserver(this);
        mContactMap.put(contact, new KonChatState(contact));
    }

    private void removeContact(Contact contact) {
        if (!mContactMap.containsKey(contact)) {
            LOGGER.warning("contact not in chat: "+contact);
            return;
        }

        contact.deleteObserver(this);
        mContactMap.remove(contact);
        this.save();
        this.changed(contact);
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public String toString() {
        return "T:id="+mID+",xmppid="+mXMPPID+",subject="+mSubject+",gid="+mOptGID;
    }

    @Override
    public void update(Observable o, Object arg) {
        this.changed(o);
    }

    @Override
    public int compareTo(Chat o) {
        return Integer.compare(this.mID, o.mID);
    }

    static Chat load(ResultSet rs) throws SQLException {
        int id = rs.getInt("_id");

        String jsonGID = Database.getString(rs, Chat.COL_GID);
        Optional<GID> optGID = Optional.ofNullable(jsonGID.isEmpty() ?
                null :
                GID.fromJSONOrNull(jsonGID));

        String xmppThreadID = Database.getString(rs, Chat.COL_XMPPID);

        // get contacts for chats
        Map<Integer, Integer> dbReceiver = Chat.loadReceiver(id);
        Set<Contact> contacts = new HashSet<>();
        for (int conID: dbReceiver.keySet()) {
            Optional<Contact> optCon = ContactList.getInstance().get(conID);
            if (optCon.isPresent())
                contacts.add(optCon.get());
            else
                LOGGER.warning("can't find contact");
        }

        String subject = Database.getString(rs, Chat.COL_SUBJ);

        boolean read = rs.getBoolean(Chat.COL_READ);

        String jsonViewSettings = Database.getString(rs,
                Chat.COL_VIEW_SET);

        return new Chat(id, contacts, optGID, xmppThreadID, subject, read, jsonViewSettings);
    }

    static Map<Integer, Integer> loadReceiver(int chatID) {
        Database db = Database.getInstance();
        String where = COL_REC_CHAT_ID + " == " + chatID;
        Map<Integer, Integer> dbReceiver = new HashMap<>();
        ResultSet resultSet;
        try {
            resultSet = db.execSelectWhereInsecure(RECEIVER_TABLE, where);
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

    /** Group ID. */
    public static class GID {
        private static final String JSON_OWNER_JID = "jid";
        private static final String JSON_ID = "id";

        public final JID ownerJID;
        public final String id;

        public GID(JID ownerJID, String id) {
            this.ownerJID = ownerJID;
            this.id = id;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private String toJSON() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_OWNER_JID, ownerJID.string());
            EncodingUtils.putJSON(json, JSON_ID, id);
            return json.toJSONString();
        }

        static GID fromJSONOrNull(String json) {
            Object obj = JSONValue.parse(json);
            try {
                Map<?, ?> map = (Map) obj;
                JID jid = JID.bare((String) map.get(JSON_OWNER_JID));
                String id = (String) map.get(JSON_ID);
                return new GID(jid, id);
            }  catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON preview", ex);
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (!(o instanceof GID))
                return false;

            GID oGID = (GID) o;
            return ownerJID.equals(oGID.ownerJID) && id.equals(oGID.id);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.ownerJID);
            hash = 37 * hash + Objects.hashCode(this.id);
            return hash;
        }
    }
}
