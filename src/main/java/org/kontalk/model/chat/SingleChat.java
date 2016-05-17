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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.model.Contact;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class SingleChat extends Chat {
    private static final Logger LOGGER = Logger.getLogger(SingleChat.class.getName());

    private final Member mMember;
    private final String mXMPPID;

    SingleChat(Member member, String xmppID) {
        super(Arrays.asList(member), xmppID, "", null);

        mMember = member;
        // NOTE: Kontalk Android client is ignoring the chat XMPP-ID
        mXMPPID = xmppID;
        mMember.getContact().addObserver(this);
    }

    // used when loading from database
    SingleChat(
            int id,
            Member member,
            String xmppID,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mMember = member;
        mXMPPID = xmppID;
        mMember.getContact().addObserver(this);
    }

    public Member getMember() {
        return mMember;
    }

    @Override
    public List<Member> getAllMembers() {
        return Arrays.asList(mMember);
    }

    @Override
    public List<Contact> getAllContacts() {
        return Arrays.asList(mMember.getContact());
    }

    @Override
    public List<Contact> getValidContacts() {
        Contact c = mMember.getContact();
        if ((c.isDeleted() || c.isBlocked()) && !c.isMe())
            return Collections.emptyList();

        return Arrays.asList(c);
    }

    @Override
    public String getXMPPID() {
        return mXMPPID;
    }

    @Override
    public String getSubject() {
        return "";
    }

    @Override
    public boolean isSendEncrypted() {
        return mMember.getContact().getEncrypted();
    }

    @Override
    public boolean canSendEncrypted() {
        Contact c = mMember.getContact();
        return !c.isDeleted() && !c.isBlocked() && c.hasKey();
    }

    @Override
    public boolean isValid() {
        Contact c = mMember.getContact();
        return !c.isDeleted() && !c.isBlocked();
    }

    @Override
    public boolean isAdministratable() {
        return false;
    }

    @Override
    public void setChatState(Contact contact, ChatState chatState) {
        if (!contact.equals(mMember.getContact())) {
            LOGGER.warning("wrong contact!?");
            return;
        }
        mMember.setState(chatState);
        this.changed(ViewChange.MEMBER_STATE);
    }

    @Override
    void save() {
        super.save(Arrays.asList(mMember), "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof SingleChat)) return false;

        SingleChat oChat = (SingleChat) o;
        return mMember.equals(oChat.mMember) &&
                mXMPPID.equals(oChat.mXMPPID);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.mMember);
        hash = 41 * hash + Objects.hashCode(this.mXMPPID);
        return hash;
    }

    @Override
    public String toString() {
        return "SC:id="+mID+",xmppid="+mXMPPID+",mem="+mMember;
    }
}
