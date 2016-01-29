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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class SingleChat extends Chat {
    private static final Logger LOGGER = Logger.getLogger(SingleChat.class.getName());

    private final Member mMember;
    private final String mXMPPID;

    SingleChat(Contact contact, String xmppID) {
        super(contact, xmppID, "");

        mMember = new Member(contact);
        contact.addObserver(this);
        // note: Kontalk Android client is ignoring the chat id
        mXMPPID = xmppID;
    }

    // used when loading from database
    SingleChat(int id,
            Contact contact,
            String xmppID,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mMember = new Member(contact);
        contact.addObserver(this);
        mXMPPID = xmppID;
    }

    public Contact getContact() {
        return mMember.contact;
    }

    @Override
    public List<Contact> getAllContacts() {
        return Arrays.asList(mMember.contact);
    }

    @Override
    public Contact[] getValidContacts() {
        Contact c = mMember.contact;
        if (c.isDeleted() || c.isBlocked() && !c.isMe())
            return new Contact[0];

        return new Contact[]{c};
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
        return mMember.contact.getEncrypted();
    }

    @Override
    public boolean canSendEncrypted() {
        Contact c = mMember.contact;
        return !c.isDeleted() && !c.isBlocked() && c.hasKey();
    }

    @Override
    public boolean isValid() {
        return !mMember.contact.isDeleted() && !mMember.contact.isBlocked();
    }

    @Override
    public boolean isAdministratable() {
        return false;
    }

    @Override
    public void setChatState(Contact contact, ChatState chatState) {
        if (!contact.equals(mMember.contact)) {
            LOGGER.warning("wrong contact!?");
            return;
        }
        mMember.setState(chatState);
        this.changed(mMember.getState());
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
        return mMember.equals(oChat.mMember) && mXMPPID.equals(oChat.mXMPPID);
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
        return "SC:id="+mID+",xmppid="+mXMPPID;
    }
}
