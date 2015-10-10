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
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * Global list of all contacts.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ContactList extends Observable {

    private static final Logger LOGGER = Logger.getLogger(ContactList.class.getName());

    private static final ContactList INSTANCE = new ContactList();

    /** JID to contact. */
    private final HashMap<JID, Contact> mJIDMap = new HashMap<>();

    /** Database ID to contact. */
    private final HashMap<Integer, Contact> mIDMap = new HashMap<>();

    private ContactList() {}

    public void load() {
        Database db = Database.getInstance();
        try (ResultSet resultSet = db.execSelectAll(Contact.TABLE)) {
            while (resultSet.next()) {
                Contact contact = Contact.load(resultSet);
                JID jid = contact.getJID();

                synchronized (this) {
                    if (mJIDMap.containsKey(jid)) {
                        LOGGER.warning("contacts with equal JIDs: " + jid);
                        continue;
                    }
                    mJIDMap.put(jid, contact);
                    mIDMap.put(contact.getID(), contact);
                }
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
        if (!this.isValid(jid))
            return Optional.empty();

        Contact newContact = new Contact(jid, name);
        if (newContact.getID() < 1)
            return Optional.empty();

        synchronized (this) {
            mJIDMap.put(newContact.getJID(), newContact);
            mIDMap.put(newContact.getID(), newContact);
        }

        this.changed(newContact);
        return Optional.of(newContact);
    }

    synchronized Optional<Contact> get(int id) {
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
    public synchronized Optional<Contact> get(JID jid) {
        return Optional.ofNullable(mJIDMap.get(jid));
    }

    /**
     * Return all but deleted contacts.
     */
    public synchronized SortedSet<Contact> getAll() {
        SortedSet<Contact> contacts = new TreeSet<>();
        for (Contact u : mJIDMap.values())
            if (!u.isDeleted())
                contacts.add(u);

        return contacts;
    }

    public void remove(Contact contact) {
        synchronized (this) {
            boolean removed = mJIDMap.remove(contact.getJID(), contact);
            if (!removed) {
                LOGGER.warning("can't find contact to remove: "+contact);
            }
            mIDMap.remove(contact.getID());
        }
        this.changed(contact);
    }

    /**
     * Return whether a contact with a specified JID exists.
     */
    public synchronized boolean contains(JID jid) {
        return mJIDMap.containsKey(jid);
    }

    public boolean changeJID(Contact contact, JID jid) {
        if (!this.isValid(jid))
            return false;

        synchronized (this) {
            mJIDMap.put(jid, contact);
            mJIDMap.remove(contact.getJID());
        }
        contact.setJID(jid);

        this.changed(contact);
        return true;
    }

    private synchronized boolean isValid(JID jid) {
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

    // TODO test without synchronized
    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    public static ContactList getInstance() {
        return INSTANCE;
    }
}
