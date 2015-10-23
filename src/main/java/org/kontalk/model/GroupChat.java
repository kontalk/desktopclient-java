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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.misc.JID;
import org.kontalk.util.EncodingUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class GroupChat extends Chat {
    private static final Logger LOGGER = Logger.getLogger(GroupChat.class.getName());

    private final HashMap<Contact, KonChatState> mContactMap = new HashMap<>();
    private final GID mGID;

    private String mSubject;
    // TODO overwrite encryption=OFF field
    private boolean mForceEncryptionOff = false;

    GroupChat(Contact[] contacts, GID gid, String subject) {
        super(contacts, "", subject, gid);

        for (Contact contact: contacts)
            this.addContactSilent(contact);
        // group chats also include the user itself
        Optional<Contact> optMe = ContactList.getInstance().getMe();
        if (!optMe.isPresent())
            LOGGER.warning("can't add user to group chat");
        else
            this.addContactSilent(optMe.get());

        mGID = gid;
        mSubject = subject;
    }

    // used when loading from database
    GroupChat(int id,
            Set<Contact> contacts,
            GID gid,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mGID = gid;
        mSubject = subject;

        for (Contact contact: contacts)
            this.addContactSilent(contact);
    }

    /** Get all contacts (including deleted and user contact). */
    @Override
    public Set<Contact> getAllContacts() {
        return mContactMap.keySet();
    }

    @Override
    public Contact[] getValidContacts() {
        //chat.getContacts().stream().filter(c -> !c.isDeleted());
        Set<Contact> contacts = new HashSet<>();
        for (Contact c : this.getAllContacts()) {
            if (!c.isDeleted() && !c.isMe()) {
                contacts.add(c);
            }
        }
        return contacts.toArray(new Contact[0]);
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

    public GID getGID() {
        return mGID;
    }

    @Override
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

    @Override
    public void setChatState(Contact contact, ChatState chatState) {
        KonChatState state = mContactMap.get(contact);
        if (state == null) {
            LOGGER.warning("can't find contact in contact map!?");
            return;
        }
        state.setState(chatState);
        this.changed(state);
    }

    public void applyGroupCommand(MessageContent.GroupCommand command, Contact sender) {
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

    @Override
    public String getXMPPID() {
        return "";
    }

    @Override
    public boolean isSendEncrypted() {
        boolean encrypted = false;
        for (Contact c: this.getValidContacts()) {
            encrypted |= c.getEncrypted();
        }
        return encrypted;
    }

    @Override
    public boolean canSendEncrypted() {
        Contact[] contacts = this.getValidContacts();
        boolean encrypted = contacts.length != 0;
        for (Contact c: contacts) {
            encrypted &= c.hasKey();
        }
        return encrypted;
    }

    @Override
    public boolean isValid() {
        return this.getValidContacts().length != 0 && this.containsMe();
    }

    public boolean isAdministratable() {
        return mGID.ownerJID.isMe();
    }

    private boolean containsMe() {
        return mContactMap.keySet().parallelStream().anyMatch(
                new Predicate<Contact>() {
                    @Override
                    public boolean test(Contact t) {
                        return t.isMe();
                    }
                }
        );
    }

    @Override
    void save() {
        this.save(mContactMap.keySet().toArray(new Contact[0]), mSubject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof GroupChat)) return false;

        GroupChat oChat = (GroupChat) o;
        return mGID.equals(oChat.mGID);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.mGID);
        return hash;
    }

    @Override
    public String toString() {
        return "GC:id="+mID+",gid="+mGID+",subject="+mSubject;
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
        protected String toJSON() {
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
            if (this == o) return true;

            if (!(o instanceof GID)) return false;

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
