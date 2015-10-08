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
import org.apache.commons.lang.StringUtils;

import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.packet.Presence;
import org.kontalk.misc.JID;
import org.kontalk.system.RosterHandler;

/**
 * Listener for events in the roster (a server-side contact list in XMPP).
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class KonRosterListener implements RosterListener {
    private static final Logger LOGGER = Logger.getLogger(KonRosterListener.class.getName());

    private final Roster mRoster;
    private final RosterHandler mHandler;

    KonRosterListener(Roster roster, RosterHandler handler) {
        mRoster = roster;
        mHandler = handler;
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

            LOGGER.config("entry: "+entry.toString());
            mHandler.onEntryAdded(JID.bare(entry.getUser()),
                    StringUtils.defaultString(entry.getName()),
                    entry.getType(),
                    entry.getStatus());
        }
    }

    @Override
    public void entriesUpdated(Collection<String> addresses) {
        // note: we don't know what exactly changed here
        for (String jid: addresses) {
            RosterEntry entry = mRoster.getEntry(jid);
            if (entry == null) {
                LOGGER.warning("jid not in roster: "+jid);
                return;
            }

            LOGGER.info("entry: "+entry.toString());
            mHandler.onEntryUpdate(JID.bare(entry.getUser()),
                    StringUtils.defaultString(entry.getName()),
                    entry.getType(),
                    entry.getStatus());
        }
    }

    @Override
    public void entriesDeleted(Collection<String> addresses) {
        for (String jid: addresses) {
            LOGGER.info("address: "+jid);
            mHandler.onEntryDeleted(JID.bare(jid));
        }
    }

    @Override
    public void presenceChanged(Presence presence) {
        // handled by PresenceListener
    }
}
