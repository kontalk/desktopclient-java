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

package org.kontalk.model.chat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.model.Contact;
import org.kontalk.system.Database;

/**
 * The global list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatList extends Observable implements Observer, Iterable<Chat> {
    private static final Logger LOGGER = Logger.getLogger(ChatList.class.getName());

    private final Database mDB;
    private final Set<Chat> mChats = Collections.synchronizedSet(new HashSet<Chat>());

    private boolean mUnread = false;

    public ChatList(Database db) {
        mDB = db;
    }

    public void load(Map<Integer, Contact> contactMap) {
        assert mChats.isEmpty();

        try (ResultSet chatRS = mDB.execSelectAll(Chat.TABLE)) {
            while (chatRS.next()) {
                Chat chat = Chat.load(mDB, chatRS, contactMap).orElse(null);
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
        return Collections.unmodifiableSet(mChats);
    }

    /** Get single chat with contact and XMPPID. */
    public Optional<SingleChat> get(Contact contact, String xmmpThreadID) {
        synchronized(mChats) {
            return mChats.stream()
                    .filter(chat -> chat instanceof SingleChat)
                    .map(chat -> (SingleChat) chat)
                    .filter(chat -> chat.getXMPPID().equals(xmmpThreadID)
                            && chat.getContact().equals(contact))
                    .findFirst();
        }
    }

    public Optional<GroupChat> get(GroupMetaData gData) {
        synchronized(mChats) {
            return mChats.stream()
                    .filter(chat -> chat instanceof GroupChat)
                    .map(chat -> (GroupChat) chat)
                    .filter(chat -> chat.getGroupData().equals(gData))
                    .findFirst();
        }
    }

    public SingleChat getOrCreate(Contact contact) {
        return this.getOrCreate(contact, "");
    }

    /** Find single chat for contact and XMPP ID or creates a new chat. */
    public SingleChat getOrCreate(Contact contact, String xmppThreadID) {
        SingleChat chat = this.get(contact, xmppThreadID).orElse(null);
        if (chat != null)
            return chat;

        return this.createNew(contact, xmppThreadID);
    }

    private SingleChat createNew(Contact contact, String xmppThreadID) {
        SingleChat newChat = new SingleChat(mDB, new Member(contact), xmppThreadID);
        LOGGER.config("new single chat: "+newChat);
        this.putSilent(newChat);
        this.changed(newChat);
        return newChat;
    }

    public GroupChat create(List<Member> members, GroupMetaData gData) {
        return createNew(members, gData, "");
    }

    public GroupChat createNew(List<Member> members, GroupMetaData gData, String subject) {
        GroupChat newChat = GroupChat.create(mDB, members, gData, subject);
        LOGGER.config("new group chat: "+newChat);
        this.putSilent(newChat);
        this.changed(newChat);
        return newChat;
    }

    private void putSilent(Chat chat) {
        boolean succ = mChats.add(chat);
        if (!succ) {
            LOGGER.warning("chat already in chat list: "+chat);
            return;
        }
        chat.addObserver(this);
    }

    public boolean contains(Contact contact) {
        return this.get(contact, "").isPresent();
    }

    public boolean isEmpty() {
        return mChats.isEmpty();
    }

    public void delete(Chat chat) {
        boolean succ = mChats.remove(chat);
        if (!succ) {
            LOGGER.warning("can't delete chat, not found: "+chat);
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

        synchronized(mChats) {
            if (mChats.stream().anyMatch(chat -> !chat.isRead()))
                return;
        }

        mUnread = false;
        this.changed(mUnread);
    }

    @Override
    public Iterator<Chat> iterator() {
        return mChats.iterator();
    }
}
