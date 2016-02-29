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
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.message.OutMessage;
import org.kontalk.system.Database;

/**
 * All messages of a chat.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatMessages {
    private static final Logger LOGGER = Logger.getLogger(ChatMessages.class.getName());

    private final Chat mChat;
    // comparator inconsistent with .equals(); using one set for ordering...
    private final NavigableSet<KonMessage> mSortedSet =
        Collections.synchronizedNavigableSet(new TreeSet<KonMessage>(
                (KonMessage o1, KonMessage o2) -> {
                    return o1.getDate().compareTo(o2.getDate()); }
        ));
    // ... and one set for .contains()
    private final Set<KonMessage> mContainsSet =
            Collections.synchronizedSet(new HashSet<>());

    private boolean mLoaded;

    ChatMessages(Chat chat, boolean newChat) {
        mChat = chat;
        // don't load from db if chat is just created
        mLoaded = newChat;
    }

    private void ensureLoaded() {
        if (mLoaded)
            return;
        mLoaded = true;

        this.loadMessages();
    }

    private void loadMessages() {
        Database db = Database.getInstance();

        try (ResultSet messageRS = db.execSelectWhereInsecure(KonMessage.TABLE,
                KonMessage.COL_CHAT_ID + " == " + mChat.getID())) {
            while (messageRS.next()) {
                KonMessage message = KonMessage.load(messageRS, mChat);
                if (message.getTransmissions().isEmpty())
                    // ignore broken message
                    continue;
                this.addSilent(message);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    /**
     * Add message to chat without notifying other components.
     */
    boolean add(KonMessage message) {
        this.ensureLoaded();

        return this.addSilent(message);
    }

    private boolean addSilent(KonMessage message) {
        boolean added = mContainsSet.add(message);
        if (!added) {
            LOGGER.warning("message already in chat: " + message);
            return false;
        }
        mSortedSet.add(message);
        return true;
    }

    public Set<KonMessage> getAll() {
        this.ensureLoaded();

        return Collections.unmodifiableSet(mSortedSet);
    }

    /** Get all outgoing messages with status "PENDING" for this chat. */
    public SortedSet<OutMessage> getPending() {
        this.ensureLoaded();

        return mSortedSet.stream()
                .filter(m -> m.getStatus() == KonMessage.Status.PENDING
                        && m instanceof OutMessage)
                .map(m -> (OutMessage) m)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Get the newest (ie last received) outgoing message. */
    public Optional<OutMessage> getLast(String xmppID) {
        this.ensureLoaded();

        return mSortedSet.descendingSet().stream()
                .filter(m -> m.getXMPPID().equals(xmppID) && m instanceof OutMessage)
                .map(m -> (OutMessage) m).findFirst();
    }

    /** Get the last created message. */
    public Optional<KonMessage> getLast() {
        this.ensureLoaded();
        return mSortedSet.isEmpty() ?
                Optional.<KonMessage>empty() :
                Optional.of(mSortedSet.last());
    }

    public boolean contains(KonMessage message) {
        this.ensureLoaded();
        return mContainsSet.contains(message);
    }

    public int size() {
        this.ensureLoaded();
        return mSortedSet.size();
    }

    public boolean isEmpty() {
        this.ensureLoaded();
        return mSortedSet.isEmpty();
    }
}
