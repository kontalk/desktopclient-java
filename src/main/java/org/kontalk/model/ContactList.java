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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Database;
import org.kontalk.util.XMPPUtils;

/**
 * Global list of all contacts.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ContactList extends Observable {
    private static final Logger LOGGER = Logger.getLogger(ContactList.class.getName());

    private static final ContactList INSTANCE = new ContactList();

    /** JID to contact. */
    private final HashMap<String, Contact> mJIDMap = new HashMap<>();
    /** Database ID to contact. */
    private final HashMap<Integer, Contact> mIDMap = new HashMap<>();

    private ContactList() {}

    public void load() {
        Database db = Database.getInstance();
        try (ResultSet resultSet = db.execSelectAll(Contact.TABLE)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                String jid = resultSet.getString(Contact.COL_JID);
                String name = resultSet.getString(Contact.COL_NAME);
                String status = resultSet.getString(Contact.COL_STAT);
                long l = resultSet.getLong(Contact.COL_LAST_SEEN);
                Optional<Date> lastSeen = l == 0 ?
                        Optional.<Date>empty() :
                        Optional.<Date>of(new Date(l));
                boolean encr = resultSet.getBoolean(Contact.COL_ENCR);
                String key = Database.getString(resultSet, Contact.COL_PUB_KEY);
                String fp = Database.getString(resultSet, Contact.COL_KEY_FP);
                Contact newContact = new Contact(id, jid, name, status, lastSeen, encr, key, fp);
                synchronized (this) {
                    mJIDMap.put(jid, newContact);
                    mIDMap.put(id, newContact);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load contacts from db", ex);
        }
        this.changed(null);
    }

    /**
     * Return all but deleted contact.
     */
    public synchronized SortedSet<Contact> getAll() {
        SortedSet<Contact> contact = new TreeSet<>();
        for (Contact u : mJIDMap.values())
            if (!u.isDeleted())
                contact.add(u);
        return contact;
    }

    /**
     * Create and add a new contact.
     * @param jid JID of new contact
     * @param name nickname of new contact, use an empty string if not known
     * @return the newly created contact, if one was created
     */
    public synchronized Optional<Contact> createContact(String jid, String name) {
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

    public synchronized void changeJID(Contact contact, String jid) {
        if (!this.isValid(jid))
            return;

        mJIDMap.put(jid, contact);
        mJIDMap.remove(contact.getJID());
        contact.setJID(jid);

        this.changed(contact);
    }

    private boolean isValid(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (!XMPPUtils.isValid(jid)) {
            LOGGER.warning("invalid jid: "+jid);
            return false;
        }

        if (mJIDMap.containsKey(jid)) {
            LOGGER.warning("jid already exists: "+jid);
            return false;
        }

        return true;
    }

    public synchronized void save() {
        for (Contact contact: mJIDMap.values()) {
            contact.save();
        }
    }

    synchronized Optional<Contact> get(int id) {
        Optional<Contact> optContact = Optional.ofNullable(mIDMap.get(id));
        if (!optContact.isPresent())
            LOGGER.warning("can't find contact with ID: "+id);
        return optContact;
    }

    public synchronized void remove(Contact contact) {
        boolean removed = mJIDMap.remove(contact.getJID(), contact);
        if (!removed) {
            LOGGER.warning("can't find contact to remove: "+contact);
        }
        mIDMap.remove(contact.getID());
        this.changed(contact);
    }

    /**
     * Get the contact for a JID (if the JID is in the list).
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public synchronized Optional<Contact> get(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return Optional.ofNullable(mJIDMap.get(jid));
    }

    /**
     * Return whether a contact with a specified JID exists.
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public synchronized boolean contains(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return mJIDMap.containsKey(jid);
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    public static ContactList getInstance() {
        return INSTANCE;
    }
}
