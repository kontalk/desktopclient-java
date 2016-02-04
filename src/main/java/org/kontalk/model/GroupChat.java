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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

    //private final HashMap<Member, KonChatState> mContactMap = new HashMap<>();
    private final HashSet<Member> mMemberSet = new HashSet<>();
    private final D mGroupData;

    private String mSubject;
    // TODO overwrite encryption=OFF field
    private boolean mForceEncryptionOff = false;

    private GroupChat(List<Member> members, D gData, String subject) {
        super(members, "", subject, gData);

        mGroupData = gData;
        mSubject = subject;

        for (Member member: members)
            this.addMemberSilent(member);
    }

    // used when loading from database
    private GroupChat(int id,
            List<Member> members,
            D gData,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mGroupData = gData;
        mSubject = subject;

        for (Member member: members)
            this.addMemberSilent(member);
    }

    @Override
    protected List<Member> getAllMembers() {
        return new ArrayList<>(mMemberSet);
    }

    /** Get all contacts (including deleted and user contact). */
    @Override
    public List<Contact> getAllContacts() {
        List<Contact> l = new ArrayList<>(mMemberSet.size());
        for (Member m : mMemberSet)
            l.add(m.getContact());

        return l;
    }

    @Override
    public Contact[] getValidContacts() {
        //chat.getContacts().stream().filter(c -> !c.isDeleted());
        Set<Contact> contacts = new HashSet<>();
        for (Member m : mMemberSet) {
            Contact c = m.getContact();
            if (!c.isDeleted() && !c.isMe()) {
                contacts.add(m.getContact());
            }
        }
        return contacts.toArray(new Contact[0]);
    }

    public void addContact(Contact contact) {
        this.addMemberSilent(new Member(contact));
        this.save();
        this.changed(contact);
    }

    public void addContacts(List<Contact> contacts) {
        boolean changed = false;
        for (Contact c: contacts) {
            Member m = new Member(c);
            if (!mMemberSet.contains(m)) {
                this.addMemberSilent(m);
                changed = true;
            } else {
                LOGGER.info("contact already in chat: "+c);
            }
        }

        if (changed) {
            System.out.println("addContacts save");
            this.save();
            this.changed(contacts);
        }
    }

    private void addMemberSilent(Member member) {
        if (mMemberSet.contains(member)) {
            LOGGER.warning("contact already in chat: "+member);
            return;
        }

        member.getContact().addObserver(this);
        mMemberSet.add(member);
    }

    private void removeContactSilent(Contact contact) {
        contact.deleteObserver(this);
        boolean succ = mMemberSet.remove(new Member(contact));
        if (!succ) {
            LOGGER.warning("contact not in chat: "+contact);
            return;
        }
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
    public void setChatState(final Contact contact, ChatState chatState) {
        Member member = mMemberSet.stream().filter(new Predicate<Member>(){
            @Override
            public boolean test(Member t) {
                return t.getContact().equals(contact);
            }
        }).findFirst().orElse(null);

        if (member == null) {
            LOGGER.warning("can't find member in member set!?");
            return;
        }

        member.setState(chatState);
        this.changed(member);
    }

    public void applyGroupCommand(MessageContent.GroupCommand command, Contact sender) {
        switch(command.getOperation()) {
            case CREATE:
                assert mMemberSet.size() == 1;
                assert mMemberSet.contains(new Member(sender));

                boolean meIn = false;
                for (JID jid: command.getAdded()) {
                    Contact contact = ContactList.getInstance().get(jid).orElse(null);
                    if (contact == null) {
                        LOGGER.warning("can't find contact, jid: "+jid);
                        continue;
                    }

                    Member member = new Member(contact);
                    if (mMemberSet.contains(member)) {
                        LOGGER.warning("member already in chat: "+member);
                        continue;
                    }
                    meIn |= contact.isMe();
                    this.addMemberSilent(member);
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
                    Contact contact = ContactList.getInstance().get(jid).orElse(null);
                    if (contact == null) {
                        LOGGER.warning("can't get added contact, jid="+jid);
                        continue;
                    }
                    this.addMemberSilent(new Member(contact));
                }
                for (JID jid : command.getRemoved()) {
                    Contact contact = ContactList.getInstance().get(jid).orElse(null);
                    if (contact == null) {
                        LOGGER.warning("can't get removed contact, jid="+jid);
                        continue;
                    }
                    this.removeContactSilent(contact);
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
        return mMemberSet.parallelStream().anyMatch(
                new Predicate<Member>() {
                    @Override
                    public boolean test(Member t) {
                        return t.getContact().isMe();
                    }
                }
        );
    }

    @Override
    void save() {
        this.save(new ArrayList<>(mMemberSet), mSubject);
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
        private KonGroupChat(List<Member> members, KonGroupData gData, String subject) {
            super(members, gData, subject);
        }

        private KonGroupChat(int id, List<Member> members, KonGroupData gData, String subject, boolean read, String jsonViewSettings) {
            super(id, members, gData, subject, read, jsonViewSettings);
        }
    }

    public static final class MUCChat extends GroupChat<GroupMetaData.MUCData> {
        private MUCChat(List<Member> members, GroupMetaData.MUCData gData, String subject) {
            super(members, gData, subject);
        }

        private MUCChat(int id, List<Member> members, GroupMetaData.MUCData gData, String subject, boolean read, String jsonViewSettings) {
            super(id, members, gData, subject, read, jsonViewSettings);
        }
    }

    static GroupChat create(int id, List<Member> members, GroupMetaData gData, String subject, boolean read, String jsonViewSettings) {
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(id, members, (KonGroupData) gData, subject, read, jsonViewSettings) :
                new MUCChat(id, members, (MUCData) gData, subject, read, jsonViewSettings);
    }

    public static GroupChat create(List<Contact> contacts, GroupMetaData gData, String subject) {
        List<Member> members = new ArrayList<>(contacts.size());
        for (Contact c : contacts) {
            members.add(new Member(c));
        }
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(members, (KonGroupData) gData, subject) :
                new MUCChat(members, (MUCData) gData, subject);
    }

}
