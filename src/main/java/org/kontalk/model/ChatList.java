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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.model.GroupMetaData;
import org.kontalk.system.Database;

/**
 * The global list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatList extends Observable implements Observer, Iterable<Chat> {
    private static final Logger LOGGER = Logger.getLogger(ChatList.class.getName());

    private static final ChatList INSTANCE = new ChatList();

    private final Map<Integer, Chat> mMap =
            Collections.synchronizedMap(new HashMap<Integer, Chat>());

    private boolean mUnread = false;

    private ChatList() {}

    public void load() {
        assert mMap.isEmpty();

        Database db = Database.getInstance();
        try (ResultSet chatRS = db.execSelectAll(Chat.TABLE)) {
            while (chatRS.next()) {
                Chat chat = Chat.loadOrNull(chatRS);
                if (chat == null)
                    continue;
                this.putSilent(chat);

                mUnread |= !chat.isRead();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load chats from db", ex);
        }
        this.changed(null);
    }

    public Set<Chat> getAll() {
        return new HashSet<>(mMap.values());
    }

    /** Get single chat with contact and XMPPID. */
    public Optional<SingleChat> get(Contact contact, String xmmpThreadID) {
        for (Chat chat : mMap.values()) {
            if (!(chat instanceof SingleChat))
                continue;
            SingleChat singleChat = (SingleChat) chat;

            if (singleChat.getXMPPID().equals(xmmpThreadID)
                    && singleChat.getContact().equals(contact))
                return Optional.of(singleChat);
        }
        return Optional.empty();
    }

    /** Get group chat with group ID and containing contact. */
    public Optional<GroupChat> get(GroupMetaData gData, Contact contact) {
        for (Chat chat : mMap.values()) {
            if (!(chat instanceof GroupChat))
                continue;

            GroupChat groupChat = (GroupChat) chat;
            if (groupChat.getGroupData().equals(gData) &&
                    groupChat.getAllContacts().contains(contact)) {
                return Optional.of(groupChat);
            }
        }

        return Optional.empty();
    }

    public Optional<GroupChat> get(GroupMetaData gData) {
        for (Chat chat : mMap.values()) {
            if (!(chat instanceof GroupChat))
                continue;

            GroupChat groupChat = (GroupChat) chat;
            if (groupChat.getGroupData().equals(gData))
                return Optional.of(groupChat);

        }

        return Optional.empty();
    }

    /** Find group chat by group data or create a new chat. */
    public GroupChat getOrCreate(GroupMetaData gData, Contact contact) {
        Optional<GroupChat> optChat = this.get(gData, contact);
        if (optChat.isPresent())
            return optChat.get();

        return this.createNew(new Contact[]{contact}, gData, "");
    }

    public Chat getOrCreate(Contact contact) {
        return this.getOrCreate(contact, "");
    }

    /** Find single chat for contact and XMPP ID or creates a new chat. */
    public SingleChat getOrCreate(Contact contact, String xmppThreadID) {
        Optional<SingleChat> optChat = this.get(contact, xmppThreadID);
        if (optChat.isPresent())
            return optChat.get();

        return this.createNew(contact, xmppThreadID);
    }

    private SingleChat createNew(Contact contact, String xmppThreadID) {
        SingleChat newChat = new SingleChat(contact, xmppThreadID);
        LOGGER.config("new single chat: "+newChat);
        this.putSilent(newChat);
        this.changed(newChat);
        return newChat;
    }

    public GroupChat createNew(Contact[] contacts, GroupMetaData gData, String subject) {
        GroupChat newChat = GroupChat.create(contacts, gData, subject);
        LOGGER.config("new group chat: "+newChat);
        this.putSilent(newChat);
        this.changed(newChat);
        return newChat;
    }

    private void putSilent(Chat chat) {
        if (mMap.containsValue(chat)) {
            LOGGER.warning("chat already in chat list");
            return;
        }

        mMap.put(chat.getID(), chat);
        chat.addObserver(this);
    }

    public boolean contains(int id) {
        return mMap.containsKey(id);
    }

    public boolean contains(Contact contact) {
        return this.get(contact, "").isPresent();
    }

    public boolean isEmpty() {
        return mMap.isEmpty();
    }

    public void delete(int id) {
        Chat chat = mMap.remove(id);
        if (chat == null) {
            LOGGER.warning("can't delete chat, not found. id: "+id);
            return;
        }
        chat.delete();
        chat.deleteObservers();
        this.changed(chat);
    }

    /** Return if any chat is unread. */
    public boolean isUnread() {
        return mUnread;
    }

    private void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
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

        for (Chat chat : mMap.values()) {
            if (!chat.isRead()) {
                return;
            }
        }

        mUnread = false;
        this.changed(mUnread);
    }

    @Override
    public Iterator<Chat> iterator() {
        return mMap.values().iterator();
    }

    public static ChatList getInstance() {
        return INSTANCE;
    }
}
