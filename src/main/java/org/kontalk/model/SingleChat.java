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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class SingleChat extends Chat {
    private static final Logger LOGGER = Logger.getLogger(SingleChat.class.getName());

    private final Contact mContact;
    private final String mXMPPID;
    private final KonChatState mChatState;

    SingleChat(Contact contact, String xmppID) {
        super(contact, xmppID, "");

        mContact = contact;
        contact.addObserver(this);
        // note: Kontalk Android client is ignoring the chat id
        mXMPPID = xmppID;

        mChatState = new KonChatState(contact);
    }

    // used when loading from database
    SingleChat(int id,
            Contact contact,
            String xmppID,
            boolean read,
            String jsonViewSettings
            ) {
        super(id, read, jsonViewSettings);

        mContact = contact;
        contact.addObserver(this);
        mXMPPID = xmppID;

        mChatState = new KonChatState(contact);
    }

    public Contact getContact() {
        return mContact;
    }

    @Override
    public Set<Contact> getAllContacts() {
        Set<Contact> contacts = new HashSet<>();
        contacts.add(mContact);

        return contacts;
    }

    @Override
    public Contact[] getValidContacts() {
        if (mContact.isDeleted() || mContact.isBlocked() && !mContact.isMe())
            return new Contact[0];

        return new Contact[]{mContact};
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
        return mContact.getEncrypted();
    }

    @Override
    public boolean canSendEncrypted() {
        return !mContact.isDeleted() && !mContact.isBlocked() && mContact.hasKey();
    }

    @Override
    public boolean isValid() {
        return !mContact.isDeleted() && !mContact.isBlocked();
    }

    @Override
    public boolean isAdministratable() {
        return false;
    }

    @Override
    public void setChatState(Contact contact, ChatState chatState) {
        if (contact != mContact) {
            LOGGER.warning("wrong contact!?");
            return;
        }
        mChatState.setState(chatState);
        this.changed(mChatState);
    }

    @Override
    void save() {
        super.save(new Contact[]{mContact}, "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof SingleChat)) return false;

        SingleChat oChat = (SingleChat) o;
        return mContact.equals(oChat.mContact) && mXMPPID.equals(oChat.mXMPPID);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.mContact);
        hash = 41 * hash + Objects.hashCode(this.mXMPPID);
        return hash;
    }

    @Override
    public String toString() {
        return "SC:id="+mID+",xmppid="+mXMPPID;
    }
}
