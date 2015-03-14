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

package org.kontalk.client;

import java.util.Collection;
import java.util.logging.Logger;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.kontalk.system.ControlCenter;

/**
 * Listener for events in the roster (a server-side contact list in XMPP).
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class KonRosterListener implements RosterListener {
    private final static Logger LOGGER = Logger.getLogger(KonRosterListener.class.getName());

    private final Roster mRoster;
    private final Client mClient;
    private final ControlCenter mControl;

    KonRosterListener(Roster roster, Client client, ControlCenter control) {
        mRoster = roster;
        mClient = client;
        mControl = control;
    }

    /**
     * Note: on every (re-)connect all entries are added again
     */
    @Override
    public void entriesAdded(Collection<String> addresses) {
        if (mRoster == null)
            return;

        for (String jid: addresses) {
            RosterEntry entry = mRoster.getEntry(jid);
            if (entry == null) {
                LOGGER.warning("jid not in roster: "+jid);
                return;
            }

            LOGGER.info("roster entry: "+entry.toString());

            if (entry.getType() != RosterPacket.ItemType.to &&
                    entry.getType() != RosterPacket.ItemType.both) {
                // we are not subscribed to presence changes for this user...
                if (entry.getStatus() != RosterPacket.ItemStatus.SUBSCRIPTION_PENDING) {
                    // ... and there is no request pending. Request it
                    // TODO always?
                    mClient.sendPresenceSubscriptionRequest(jid);
                }
            }

            mControl.addUserFromRoster(entry.getUser(), entry.getName());
        }
    }

    @Override
    public void entriesUpdated(Collection<String> addresses) {
        LOGGER.info("ignoring entry update in roster");
    }

    @Override
    public void entriesDeleted(Collection<String> addresses) {
        LOGGER.info("ignoring entry deletion in roster");
    }

    /**
     * TODO obsolete?
     * Note: custom Kontalk presence packets with public key ID are handled by
     * the presence listener
     */
    @Override
    public void presenceChanged(Presence presence) {
        LOGGER.info("got presence change: "+presence.toXML());

        if (presence.getFrom() == null || mRoster == null)
            // dunno why this happens
            return;

        String jid = presence.getFrom();
        Presence bestPresence = mRoster.getPresence(jid);

        // NOTE: a delay extension is sometimes included, don't know why
        // ignoring mode, always null anyway
        mControl.setPresence(bestPresence.getFrom(),
                bestPresence.getType(),
                bestPresence.getStatus());
    }

}
