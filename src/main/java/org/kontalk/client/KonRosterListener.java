/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jxmpp.jid.Jid;
import org.kontalk.misc.JID;
import org.kontalk.system.RosterHandler;
import org.kontalk.util.ClientUtils;

/**
 * Listener for events in the roster (a server-side contact list in XMPP).
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class KonRosterListener implements RosterLoadedListener, RosterListener {
    private static final Logger LOGGER = Logger.getLogger(KonRosterListener.class.getName());

    private final Roster mRoster;
    private final RosterHandler mHandler;
    private boolean mLoaded = false;

    KonRosterListener(Roster roster, RosterHandler handler) {
        mRoster = roster;
        mHandler = handler;
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        Set<RosterEntry> entries = roster.getEntries();
        LOGGER.info("loading "+entries.size()+" entries");

        mHandler.onLoaded(entries.stream()
                .map(KonRosterListener::clientToModel)
                .collect(Collectors.toList()));
        mLoaded = true;
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {
        LOGGER.log(Level.WARNING, "roster loading failed", exception);
    }

    /**
     * NOTE: on every (re-)connect all entries are added again (loaded),
     * one method call for all contacts.
     */
    @Override
    public void entriesAdded(Collection<Jid> addresses) {
        if (mRoster == null || !mLoaded)
            return;

        for (Jid jid: addresses) {
            RosterEntry entry = mRoster.getEntry(jid.asBareJid());
            if (entry == null) {
                LOGGER.warning("jid not in roster: "+jid);
                return;
            }

            LOGGER.config("entry: "+entry.toString());
            mHandler.onEntryAdded(clientToModel(entry));
        }
    }

    @Override
    public void entriesUpdated(Collection<Jid> addresses) {
        // note: we don't know what exactly changed here
        for (Jid jid: addresses) {
            RosterEntry entry = mRoster.getEntry(jid.asBareJid());
            if (entry == null) {
                LOGGER.warning("jid not in roster: "+jid);
                return;
            }

            LOGGER.info("entry: "+entry.toString());
            mHandler.onEntryUpdate(clientToModel(entry));
        }
    }

    @Override
    public void entriesDeleted(Collection<Jid> addresses) {
        for (Jid jid: addresses) {
            LOGGER.info("address: "+jid);

            mHandler.onEntryDeleted(JID.fromSmack(jid));
        }
    }

    @Override
    public void presenceChanged(Presence presence) {
        // handled by PresenceListener
    }

    private static ClientUtils.KonRosterEntry clientToModel(RosterEntry entry) {
        return new ClientUtils.KonRosterEntry(JID.fromSmack(entry.getJid()),
                StringUtils.defaultString(entry.getName()),
                entry.getType(),
                entry.isSubscriptionPending());
    }
}
