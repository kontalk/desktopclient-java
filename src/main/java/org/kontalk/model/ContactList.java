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
package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.kontalk.misc.JID;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kontalk.persistence.Database;

/**
 * Global list of all contacts.
 *
 * Does not contain deleted contacts.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ContactList extends Observable implements Iterable<Contact> {
    private static final Logger LOGGER = Logger.getLogger(ContactList.class.getName());

    private enum ViewChange { MODIFIED }

    private final Map<JID, Contact> mJIDMap =
            Collections.synchronizedMap(new HashMap<JID, Contact>());

    ContactList() {}

    Map<Integer, Contact> load() {
        assert mJIDMap.isEmpty();

        Map<Integer, Contact> contactMap = new HashMap<>();

        Database db = Model.database();
        try (ResultSet resultSet = db.execSelectAll(Contact.TABLE)) {
            while (resultSet.next()) {
                Contact contact = Contact.load(db, resultSet);

                JID jid = contact.getJID();
                if (mJIDMap.containsKey(jid)) {
                    LOGGER.warning("contacts with equal JIDs: " + jid);
                    continue;
                }
                if (!contact.isDeleted())
                    mJIDMap.put(jid, contact);

                contactMap.put(contact.getID(), contact);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load contacts from db", ex);
        }
        this.changed(null);

        return contactMap;
    }

    /** Create and add a new contact. */
    public Optional<Contact> create(JID jid, String name) {
        jid = jid.toBare();

        if (!this.isValid(jid))
            return Optional.empty();

        Contact newContact = new Contact(jid, name);
        if (newContact.getID() < 1)
            return Optional.empty();

        mJIDMap.put(newContact.getJID(), newContact);

        this.changed(ViewChange.MODIFIED);
        return Optional.of(newContact);
    }

    /**
     * Get the contact for a JID (if the JID is in the list).
     * Resource is removed for lookup.
     */
    public Optional<Contact> get(JID jid) {
        return Optional.ofNullable(mJIDMap.get(jid));
    }

    /**
     * Get the contact that represents the user itself.
     */
    public Optional<Contact> getMe() {
        JID myJID = Model.getUserJID();
        if (!myJID.isValid())
            return Optional.empty();

        return this.get(myJID);
    }

    public Set<Contact> getAll(boolean withMe, boolean blocked) {
        synchronized(mJIDMap) {
            return Collections.unmodifiableSet(
                    mJIDMap.values().stream()
                            .filter(c ->
                                    (blocked || !c.isBlocked()) &&
                                    (withMe || !c.isMe()))
                            .collect(Collectors.toSet()));
        }
    }

    public void delete(Contact contact) {
        boolean removed = mJIDMap.remove(contact.getJID(), contact);
        if (!removed) {
            LOGGER.warning("can't find contact "+contact);
        }

        contact.setDeleted();

        this.changed(ViewChange.MODIFIED);
    }

    /**
     * Return whether a contact with a specified JID exists.
     */
    public boolean contains(JID jid) {
        return mJIDMap.containsKey(jid);
    }

    public boolean changeJID(Contact contact, JID jid) {
        if (!this.isValid(jid))
            return false;

        mJIDMap.put(jid, contact);
        mJIDMap.remove(contact.getJID());

        contact.setJID(jid);

        return true;
    }

    private boolean isValid(JID jid) {
        if (!jid.isValid()) {
            LOGGER.warning("invalid jid: " + jid);
            return false;
        }

        if (mJIDMap.containsKey(jid)) {
            LOGGER.warning("jid already exists: "+jid);
            return false;
        }

        return true;
    }

    private void changed(ViewChange change) {
        this.setChanged();
        this.notifyObservers(change);
    }

    @Override
    public Iterator<Contact> iterator() {
        return mJIDMap.values().iterator();
    }
}
