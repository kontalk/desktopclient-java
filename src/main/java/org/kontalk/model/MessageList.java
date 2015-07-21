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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Central list of all messages.
 *
 * TODO make this class obsolete
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class MessageList {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final MessageList INSTANCE = new MessageList();

    // the list is implemented as 'XMPP ID' to "list of messages" map, as equal
    // XMPP IDs are possible but assumed to happen rarely
    // note: map and lists are not thread-safe on modification / iteration!
    private final HashMap<String, List<KonMessage>> mMap = new HashMap<>();

    private MessageList() {}

    /**
     * Add message without notifying observers.
     */
    synchronized boolean addMessage(KonMessage m) {
        // small capacity (dunno if this even matters)
        List<KonMessage> l = mMap.getOrDefault(m.getXMPPID(), new ArrayList<KonMessage>(3));
        mMap.putIfAbsent(m.getXMPPID(), l);
        // see KonMessage.equals()
        if (l.contains(m)) {
            LOGGER.warning("message already in message list, ID: "+m.getID());
            return true;
        }
        return l.add(m);
    }

    /**
     * Add a new message to this list.
     * @return true on success, else false
     */
    public boolean add(KonMessage newMessage) {
        boolean success = this.addMessage(newMessage);
        return success;
    }

    /**
     * Get the newest (ie last received) outgoing message.
     */
    public synchronized Optional<OutMessage> getLast(String xmppID) {
        if (mMap.containsKey(xmppID)) {
            SortedSet<OutMessage> s = new TreeSet<>();
            for (KonMessage m : mMap.get(xmppID)) {
                if (m instanceof OutMessage) {
                    s.add((OutMessage) m);
                }
            }
            if (!s.isEmpty())
                return Optional.of(s.last());
        }
        LOGGER.warning("can't find any outgoing message with XMPP ID: " + xmppID);
        return Optional.empty();
    }

    public static MessageList getInstance() {
        return INSTANCE;
    }
}
