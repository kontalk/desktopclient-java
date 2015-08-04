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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * The global list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatList extends Observable implements Observer {
    private static final Logger LOGGER = Logger.getLogger(ChatList.class.getName());

    private static final ChatList INSTANCE = new ChatList();

    private final HashMap<Integer, Chat> mMap = new HashMap<>();

    private boolean mUnread = false;

    private ChatList() {
    }

    public void load() {
        assert mMap.isEmpty();

        ContactList contactList = ContactList.getInstance();
        Database db = Database.getInstance();
        try (ResultSet chatRS = db.execSelectAll(Chat.TABLE)) {
            // now, create chats
            while (chatRS.next()) {
                int id = chatRS.getInt("_id");
                String xmppThreadID = Database.getString(chatRS, "xmpp_id");
                // get contacts for chats
                Map<Integer, Integer> dbReceiver = Chat.loadReceiver(id);
                Set<Contact> contacts = new HashSet<>();
                for (int conID: dbReceiver.keySet()) {
                    Optional<Contact> optCon = contactList.get(conID);
                    if (optCon.isPresent())
                        contacts.add(optCon.get());
                    else
                        LOGGER.warning("can't find contact");
                }
                String subject = Database.getString(chatRS,
                        Chat.COL_SUBJ);
                boolean read = chatRS.getBoolean(Chat.COL_READ);
                String jsonViewSettings = Database.getString(chatRS,
                        Chat.COL_VIEW_SET);

                this.put(new Chat(id, xmppThreadID, contacts, subject, read,
                        jsonViewSettings));
                if (!read)
                    mUnread = true;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load chats from db", ex);
        }
        this.changed(null);
    }

    public synchronized SortedSet<Chat> getAll() {
        return new TreeSet<>(mMap.values());
    }

    public synchronized void save() {
        for (Chat chat: mMap.values()) {
            chat.save();
        }
    }

    /**
     * Get a chat with only the contact as additional member.
     * Creates a new chat if necessary.
     */
    public Chat get(Contact contact) {
        Chat chat = this.getOrNull(contact);
        if (chat != null)
            return chat;

        Set<Contact> contactSet = new HashSet<>();
        contactSet.add(contact);
        return this.createNew(contactSet);
    }

    public Chat createNew(Set<Contact> contact) {
        Chat newChat = new Chat(contact);
        this.put(newChat);
        this.changed(newChat);
        return newChat;
    }

    private void put(Chat chat) {
        synchronized (this) {
            mMap.put(chat.getID(), chat);
        }
        chat.addObserver(this);
    }

    public synchronized Optional<Chat> get(int id) {
        Chat chat = mMap.get(id);
        if (chat == null)
            LOGGER.warning("can't find chat with id: "+id);
        return Optional.ofNullable(chat);
    }

    public synchronized Optional<Chat> get(String xmppThreadID) {
        if (xmppThreadID == null || xmppThreadID.isEmpty()) {
            return Optional.empty();
        }
        for (Chat chat : mMap.values()) {
            if (xmppThreadID.equals(chat.getXMPPID().orElse(null)))
                return Optional.of(chat);
        }
        return Optional.empty();
    }

    public boolean contains(int id) {
        return mMap.containsKey(id);
    }

    public boolean contains(Contact contact) {
        return this.getOrNull(contact) != null;
    }

    public synchronized void delete(int id) {
        Chat chat = mMap.remove(id);
        if (chat == null) {
            LOGGER.warning("can't delete chat, not found. id: "+id);
            return;
        }
        chat.delete();
        chat.deleteObservers();
        this.changed(chat);
    }

    /**
     * Return if any chat is unread.
     */
    public boolean isUnread() {
        return mUnread;
    }

    private synchronized Chat getOrNull(Contact contact) {
        for (Chat chat : mMap.values()) {
            Set<Contact> chatContact = chat.getContacts();
            if (chatContact.size() == 1 && chatContact.contains(contact))
                return chat;
        }
        return null;
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    public static ChatList getInstance() {
        return INSTANCE;
    }

    @Override
    public void update(Observable o, Object arg) {
        // only observing chats 'read' status
        if (!(arg instanceof Boolean))
            return;

        boolean unread = !((boolean) arg);
        if (mUnread == unread)
            return;

        if (unread) {
            mUnread = true;
            this.changed(mUnread);
            return;
        }

        synchronized (this) {
            for (Chat chat : mMap.values()) {
                if (!chat.isRead()) {
                    return;
                }
            }
        }
        mUnread = false;
        this.changed(mUnread);
    }
}
