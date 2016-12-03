/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.chat.GroupMetaData.MUCData;
import org.kontalk.persistence.Database;

/**
 * A long-term persistent chat conversation with multiple participants.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class GroupChat<D extends GroupMetaData> extends Chat {
    private static final Logger LOGGER = Logger.getLogger(GroupChat.class.getName());

    private final HashSet<Member> mMemberSet = new HashSet<>();
    private final D mGroupData;

    /** Chat subject/title set by user. Empty string only if unknown/not set. */
    private String mSubject;
    // TODO overwrite encryption=OFF field
    private boolean mForceEncryptionOff = false;

    private GroupChat(List<ProtoMember> members, D gData, String subject) {
        super("", subject, gData);

        mGroupData = gData;
        mSubject = subject;

        this.addMembersSilent(members);
    }

    // used when loading from database
    private GroupChat(
            int id,
            List<Member> members,
            D gData,
            String subject,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mGroupData = gData;
        mSubject = subject;

        members.forEach(m -> this.addMemberSilent(m));
    }

    @Override
    public List<Member> getAllMembers() {
        return new ArrayList<>(mMemberSet);
    }

    /** Get all contacts (including deleted and user contact). */
    @Override
    public List<Contact> getAllContacts() {
        return mMemberSet.stream()
                .map(m -> m.getContact())
                .collect(Collectors.toList());
    }

    @Override
    public List<Contact> getValidContacts() {
        return mMemberSet.stream()
                .map(m -> m.getContact())
                .filter(c -> (!c.isDeleted() && !c.isMe()))
                .collect(Collectors.toList());
    }

    private void addMembersSilent(List<ProtoMember> members) {
        members.stream()
        .filter(m -> {
            if (mMemberSet.contains(m)) {
                LOGGER.warning("(proto)member already in chat: " + m);
                return false;
            } else {
                return true;
            }
        })
        .map(m -> new Member(m, mID))
        .forEach(m -> this.addMemberSilent(m));
    }

    private void addMemberSilent(Member member) {
        if (mMemberSet.contains(member)) {
            LOGGER.warning("member already in chat: "+member);
            return;
        }

        member.getContact().addObserver(this);
        mMemberSet.add(member);
    }

    public D getGroupData() {
        return mGroupData;
    }

    @Override
    public String getSubject() {
        return mSubject;
    }

    @Override
    public void setChatState(final Contact contact, ChatState chatState) {
        Member member = mMemberSet.stream()
                .filter(m -> m.getContact().equals(contact))
                .findFirst().orElse(null);

        if (member == null) {
            LOGGER.warning("can't find member in member set!?");
            return;
        }

        member.setState(chatState);
        this.changed(ViewChange.MEMBER_STATE);
    }

    public void applyGroupChanges(
            List<ProtoMember> added,
            List<ProtoMember> removed,
            String subject) {
        this.addMembersSilent(added);

        Database db = Model.database();
        for (ProtoMember pm : removed) {
            Member member = mMemberSet.stream()
                    .filter(m -> pm.equals(m))
                    .findFirst().orElse(null);
            if (member == null) {
                LOGGER.warning("(proto)member not in chat: "+pm);
                continue;
            }
            member.getContact().deleteObservers();
            boolean succ = mMemberSet.remove(member);
            if (!succ) {
                LOGGER.warning("member not in chat: "+member);
            }
            member.delete(db);
        }
        if (!removed.isEmpty()) {
            db.commit();
        }

        if (!subject.isEmpty() && !subject.equals(mSubject)) {
            mSubject = subject;
            this.save();
        }

        if (!added.isEmpty() || !removed.isEmpty()) {
            this.changed(ViewChange.MEMBERS);
        }
        if (!subject.isEmpty())
            this.changed(ViewChange.SUBJECT);
    }

    @Override
    public String getXMPPID() {
        return "";
    }

    @Override
    public boolean isSendEncrypted() {
        return this.getValidContacts().stream()
                .anyMatch(c -> c.getEncrypted());
    }

    @Override
    public boolean canSendEncrypted() {
        List<Contact> contacts = this.getValidContacts();
        return !contacts.isEmpty() &&
                contacts.stream().allMatch(c -> c.hasKey());
    }

    @Override
    public boolean isValid() {
        return !this.getValidContacts().isEmpty() && this.containsMe();
    }

    @Override
    public boolean isAdministratable() {
        Member me = mMemberSet.stream()
                .filter(m -> m.getContact().isMe())
                .findFirst().orElse(null);
        if (me == null)
            return false;
        Member.Role myRole = me.getRole();
        return myRole == Member.Role.OWNER || myRole == Member.Role.ADMIN;
    }

    public boolean containsMe() {
        return mMemberSet.stream().anyMatch(m -> m.getContact().isMe());
    }

    @Override
    void save() {
        this.save(mSubject);
    }

    @Override
    public final boolean equals(Object o) {
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

    public static final class KonGroupChat extends GroupChat<KonGroupData> {
        private KonGroupChat(List<ProtoMember> members, KonGroupData gData, String subject) {
            super(members, gData, subject);
        }

        private KonGroupChat(int id, List<Member> members,
                KonGroupData gData, String subject, boolean read, String jsonViewSettings) {
            super(id, members, gData, subject, read, jsonViewSettings);
        }
    }

    public static final class MUCChat extends GroupChat<MUCData> {
        private MUCChat(List<ProtoMember> members, MUCData gData, String subject) {
            super(members, gData, subject);
        }

        private MUCChat(int id, List<Member> members, MUCData gData,
                String subject, boolean read, String jsonViewSettings) {
            super(id, members, gData, subject, read, jsonViewSettings);
        }
    }

    static GroupChat create(int id, List<Member> members,
            GroupMetaData gData, String subject, boolean read, String jsonViewSettings) {
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(id, members, (KonGroupData) gData, subject, read, jsonViewSettings) :
                new MUCChat(id, members, (MUCData) gData, subject, read, jsonViewSettings);
    }

    static GroupChat create(List<ProtoMember> members, GroupMetaData gData, String subject) {
        return (gData instanceof KonGroupData) ?
                new KonGroupChat(members, (KonGroupData) gData, subject) :
                new MUCChat(members, (MUCData) gData, subject);
    }
}
