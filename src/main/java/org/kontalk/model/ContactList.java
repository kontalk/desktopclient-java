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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * Global list of all contacts.
 *
 * Does not contain deleted user (only accessible by database ID).
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ContactList extends Observable implements Iterable<Contact> {

    private static final Logger LOGGER = Logger.getLogger(ContactList.class.getName());

    private static final ContactList INSTANCE = new ContactList();

    /** JID to contact. Without deleted contacts. */
    private final Map<JID, Contact> mJIDMap =
            Collections.synchronizedMap(new HashMap<JID, Contact>());
    /** Database ID to contact. With deleted contacts. */
    private final Map<Integer, Contact> mIDMap =
            Collections.synchronizedMap(new HashMap<Integer, Contact>());

    private ContactList() {}

    public void load() {
        Database db = Database.getInstance();
        try (ResultSet resultSet = db.execSelectAll(Contact.TABLE)) {
            while (resultSet.next()) {
                Contact contact = Contact.load(resultSet);

                JID jid = contact.getJID();
                if (mJIDMap.containsKey(jid)) {
                    LOGGER.warning("contacts with equal JIDs: " + jid);
                    continue;
                }
                if (!contact.isDeleted())
                    mJIDMap.put(jid, contact);

                mIDMap.put(contact.getID(), contact);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load contacts from db", ex);
        }
        this.changed(null);
    }

    /**
     * Create and add a new contact.
     */
    public Optional<Contact> create(JID jid, String name) {
        jid = jid.toBare();

        if (!this.isValid(jid))
            return Optional.empty();

        Contact newContact = new Contact(jid, name);
        if (newContact.getID() < 1)
            return Optional.empty();

        mJIDMap.put(newContact.getJID(), newContact);
        mIDMap.put(newContact.getID(), newContact);

        this.changed(newContact);
        return Optional.of(newContact);
    }

    Optional<Contact> get(int id) {
        Optional<Contact> optContact = Optional.ofNullable(mIDMap.get(id));
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with ID: " + id);
        }
        return optContact;
    }

    /**
     * Get the contact for a JID (if the JID is in the list).
     * Resource is removed for lookup.
     */
    public Optional<Contact> get(JID jid) {
        return Optional.ofNullable(mJIDMap.get(jid));
    }

    /**
     * Get the contact that represents the user itself. It is created and added
     * if not yet in the list.
     */
    public Optional<Contact> getMe() {
        JID myJID = JID.me();
        Optional<Contact> optContact = this.get(myJID);
        if (optContact.isPresent())
            return optContact;

        return this.create(myJID, "");
    }

    public Set<Contact> getAll() {
        return new HashSet<>(mJIDMap.values());
    }

    public void delete(Contact contact) {
        boolean removed = mJIDMap.remove(contact.getJID(), contact);
        if (!removed) {
            LOGGER.warning("can't find contact "+contact);
        }

        contact.setDeleted();

        this.changed(contact);
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

        this.changed(contact);
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

    private void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    @Override
    public Iterator<Contact> iterator() {
        return mJIDMap.values().iterator();
    }

    public static ContactList getInstance() {
        return INSTANCE;
    }
}
