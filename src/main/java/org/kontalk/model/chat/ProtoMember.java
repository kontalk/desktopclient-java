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

import java.util.Objects;
import org.kontalk.model.Contact;

/**
 * A contact to be inserted into/removed from a (group) chat.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class ProtoMember {

    /**
     * Long-live authorization model of member in group.
     * Called 'Affiliation' in MUC
     * Do not modify, only add! Ordinal used in database
     */
    public enum Role {DEFAULT, OWNER, ADMIN}

    final Contact mContact;
    final Role mRole;

    public ProtoMember(Contact contact){
        this(contact, Role.DEFAULT);
    }

    public ProtoMember(Contact contact, Role role) {
        mContact = contact;
        mRole = role;
    }

    public Contact getContact() {
        return mContact;
    }

    public Role getRole() {
        return mRole;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof ProtoMember))
            return false;

        return mContact.equals(((ProtoMember) o).mContact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mContact);
    }

    @Override
    public String toString() {
        return "PMem:cont={"+mContact+"},role="+mRole;
    }
}
