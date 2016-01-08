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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.misc.JID;
import org.kontalk.model.GroupMetaData.KonGroupData;
import org.kontalk.model.GroupMetaData.MUCData;

/**
 * A long-term persistent chat conversation with multiple participants.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class GroupChat<D extends GroupMetaData> extends Chat {
    private static final Logger LOGGER = Logger.getLogger(GroupChat.class.getName());

    private final HashMap<Contact, KonChatState> mContactMap = new HashMap<>();
    private final D mGroupData;

    private String mSubject;
    // TODO overwrite encryption=OFF field
    private boolean mForceEncryptionOff = false;

    private GroupChat(Contact[] contacts, D gData, String subject) {
        super(contacts, "", subject, gData);

        mGroupData = gData;
        mSubject = subject;

        for (Contact contact: contacts)
            this.addContactSilent(contact);
    }

    // used when loading from database
    private GroupChat(int id,
            Set<Contact> contacts,
            D gData,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mGroupData = gData;
        mSubject = subject;

        for (Contact contact: contacts)
            this.addContactSilent(contact);
    }

    /** Get all contacts (including deleted and user contact). */
    @Override
    public Set<Contact> getAllContacts() {
        return new HashSet<>(mContactMap.keySet());
    }

    @Override
    public Contact[] getValidContacts() {
        //chat.getContacts().stream().filter(c -> !c.isDeleted());
        Set<Contact> contacts = new HashSet<>();
        for (Contact c : mContactMap.keySet()) {
            if (!c.isDeleted() && !c.isMe()) {
                contacts.add(c);
            }
        }
        return contacts.toArray(new Contact[0]);
    }

    public void addContact(Contact contact) {
        this.addContactSilent(contact);
        this.save();
        this.changed(contact);
    }

    public void addContacts(List<Contact> contacts) {
        boolean changed = false;
        for (Contact c: contacts)
            if (!this.getAllContacts().contains(c)) {
                this.addContactSilent(c);
                changed = true;
            }

        if (changed) {
            System.out.println("addContacts save");
            this.save();
            this.changed(contacts);
        }
    }

    private void addContactSilent(Contact contact) {
        if (mContactMap.containsKey(contact)) {
            LOGGER.warning("contact already in chat: "+contact);
            return;
        }

        contact.addObserver(this);
        mContactMap.put(contact, new KonChatState(contact));
    }

    private void removeContactSilent(Contact contact) {
        contact.deleteObserver(this);
        if (!mContactMap.containsKey(contact)) {
            LOGGER.warning("contact not in chat: "+contact);
            return;
        }

        mContactMap.remove(contact);
        this.save();
    }

    public D getGroupData() {
        return mGroupData;
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
                    if (mContactMap.keySet().contains(contact)) {
                        LOGGER.warning("contact already in chat: "+contact);
                        continue;
                    }
                    meIn |= contact.isMe();
                    this.addContactSilent(contact);
                }

                if (!meIn)
                    LOGGER.warning("user JID not included");

                mSubject = command.getSubject();
                this.save();
                this.changed(command);
                break;
            case LEAVE:
                this.removeContactSilent(sender);
                this.save();
                this.changed(command);
                break;
            case SET:
                for (JID jid : command.getAdded()) {
                    Optional<Contact> optC = ContactList.getInstance().get(jid);
                    if (optC.isPresent()) {
                        LOGGER.warning("can't get added contact, jid="+jid);
                        continue;
                    }
                    this.addContactSilent(optC.get());
                }
                for (JID jid : command.getRemoved()) {
                    Optional<Contact> optC = ContactList.getInstance().get(jid);
                    if (optC.isPresent()) {
                        LOGGER.warning("can't get removed contact, jid="+jid);
                        continue;
                    }
                    this.removeContactSilent(optC.get());
                }
                mSubject = command.getSubject();
                this.save();
                this.changed(command);
                break;
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

    @Override
    public boolean isAdministratable() {
        return mGroupData.isAdministratable();
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
        return mGroupData.equals(oChat.mGroupData);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.mGroupData);
        return hash;
    }

    @Override
    public String toString() {
        return "GC:id="+mID+",gd="+mGroupData+",subject="+mSubject;
    }

    public static final class KonGroupChat extends GroupChat<GroupMetaData.KonGroupData> {
        KonGroupChat(Contact[] contacts, KonGroupData gData, String subject) {
            super(contacts, gData, subject);
        }

        KonGroupChat(int id, Set<Contact> contacts, KonGroupData gData, String subject, boolean read, String jsonViewSettings) {
            super(id, contacts, gData, subject, read, jsonViewSettings);
        }
    }

    public static final class MUCChat extends GroupChat<GroupMetaData.MUCData> {
        private MUCChat(Contact[] contacts, GroupMetaData.MUCData gData, String subject) {
            super(contacts, gData, subject);
        }

        private MUCChat(int id, Set<Contact> contacts, GroupMetaData.MUCData gData, String subject, boolean read, String jsonViewSettings) {
            super(id, contacts, gData, subject, read, jsonViewSettings);
        }
    }

    static GroupChat create(int id, Set<Contact> contacts, GroupMetaData gData, String subject, boolean read, String jsonViewSettings) {
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(id, contacts, (KonGroupData) gData, subject, read, jsonViewSettings) :
                new MUCChat(id, contacts, (MUCData) gData, subject, read, jsonViewSettings);
    }

    public static GroupChat create(Contact[] contacts, GroupMetaData gData, String subject) {
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(contacts, (KonGroupData) gData, subject) :
                new MUCChat(contacts, (MUCData) gData, subject);
    }

}
