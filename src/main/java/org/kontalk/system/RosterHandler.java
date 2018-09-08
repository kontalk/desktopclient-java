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

package org.kontalk.system;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.kontalk.client.Client;
import org.kontalk.client.HKPClient;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.JID;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Contact;
import org.kontalk.model.Contact.Subscription;
import org.kontalk.model.Model;
import org.kontalk.persistence.Config;
import org.kontalk.util.ClientUtils;

/**
 * Process incoming roster and presence changes.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class RosterHandler {
    private static final Logger LOGGER = Logger.getLogger(RosterHandler.class.getName());

    private final Control mControl;
    private final Client mClient;
    private final Model mModel;

    private static final List<String> KEY_SERVERS = Collections.singletonList(
            "pgp.mit.edu"
            // TODO: add CA for this
            //"pool.sks-keyservers.net"
    );

    public enum Error {
        SERVER_NOT_FOUND
    }

    RosterHandler(Control control, Client client, Model model) {
        mControl = control;
        mClient = client;
        mModel = model;
    }

    public void onLoaded(List<ClientUtils.KonRosterEntry> entries) {
        for (ClientUtils.KonRosterEntry entry: entries)
            this.onEntryAdded(entry);

        // check for deleted entries
        List<JID> rosterJIDs = entries.stream().map(e -> e.jid).collect(Collectors.toList());
        for (Contact contact : mModel.contacts().getAll(false, true))
            if (!rosterJIDs.contains(contact.getJID()))
                this.onEntryDeleted(contact.getJID());
    }

    public void onEntryAdded(ClientUtils.KonRosterEntry entry) {
        if (mModel.contacts().contains(entry.jid)) {
            this.onEntryUpdate(entry);
            return;
        }

        LOGGER.info("adding contact from roster, jid: "+entry.jid);

        String name = entry.name.equals(entry.jid.local()) && entry.jid.isHash() ?
                // this must be the hash string, don't use it as name
                "" :
                entry.name;

        Contact newContact = mControl.createContact(entry.jid, name).orElse(null);
        if (newContact == null)
            return;

        newContact.setSubscriptionStatus(entry.subscription);

        mControl.maySendKeyRequest(newContact);

        if (entry.subscription == Contact.Subscription.UNSUBSCRIBED)
            mControl.sendPresenceSubscription(entry.jid, Client.PresenceCommand.REQUEST);
    }

    public void onEntryDeleted(JID jid) {
        // NOTE: also called on rename
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        // TODO detect if contact account still exists

        mControl.getViewControl().changed(new ViewEvent.ContactDeleted(contact));
    }

    // NOTE: also called for every contact in roster on every (re-)connect
    public void onEntryUpdate(ClientUtils.KonRosterEntry entry) {
        Contact contact = mModel.contacts().get(entry.jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+entry.jid);
            return;
        }
        // subscription may have changed
        contact.setSubscriptionStatus(entry.subscription);

        // maybe subscribed now
        mControl.maySendKeyRequest(contact);

        // name may have changed
        if (contact.getName().isEmpty() && !entry.name.equals(entry.jid.local()))
            contact.setName(entry.name);

        if (contact.getSubScription() == Subscription.SUBSCRIBED &&
                (contact.getOnline() == Contact.Online.UNKNOWN ||
                        contact.getOnline() == Contact.Online.NO))
            mClient.sendLastActivityRequest(contact.getJID());
    }

    public void onSubscriptionRequest(JID jid, byte[] rawKey) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null)
            return;

        if (Config.getInstance().getBoolean(Config.NET_AUTO_SUBSCRIPTION)) {
            mControl.sendPresenceSubscription(jid, Client.PresenceCommand.GRANT);
        } else {
            // ask user
            mControl.getViewControl().changed(new ViewEvent.SubscriptionRequest(contact));
        }

        if (rawKey.length > 0)
            mControl.onPGPKey(contact, rawKey);
    }

    public void onPresenceUpdate(JID jid, Presence.Type type, Optional<String> optStatus) {
        JID myJID = mClient.getOwnJID().orElse(null);
        if (myJID != null && myJID.equals(jid))
            // don't wanna see myself
            return;

        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        if (type == Presence.Type.available) {
            contact.setOnlineStatus(Contact.Online.YES);
        } else if (type == Presence.Type.unavailable) {
            contact.setOnlineStatus(Contact.Online.NO);
        }

        if (optStatus.isPresent())
            contact.setStatusText(optStatus.get());
    }

    public void onFingerprintPresence(JID jid, String fingerprint) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        if (!fingerprint.isEmpty() &&
                !fingerprint.equalsIgnoreCase(contact.getFingerprint())) {
            LOGGER.info("detected public key change, requesting new key...");
            mControl.sendKeyRequest(contact);
        }
    }

    // TODO key IDs can be forged, searching by it is defective by design
    public void onSignaturePresence(JID jid, String signature) {
        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        long keyID = PGPUtils.parseKeyIDFromSignature(signature);
        if (keyID == 0)
            return;

        if (contact.hasKey()) {
            PGPUtils.PGPCoderKey key = Coder.contactkey(contact).orElse(null);
            if (key != null && key.signKey.getKeyID() == keyID)
                // already have this key
                return;
        }

        String id = Long.toHexString(keyID);
        HKPClient hkpClient = new HKPClient();
        String foundKey = "";
        for (String server: KEY_SERVERS) {
            foundKey = hkpClient.search(server, id);
            if (!foundKey.isEmpty())
                break;
        }
        if (foundKey.isEmpty()) {
            LOGGER.config("searched for public key (nothing found): "+jid+" keyId="+id);
            return;
        }
        LOGGER.info("key found with HKP: "+jid+" keyId="+id);

        PGPUtils.PGPCoderKey key = PGPUtils.readPublicKey(foundKey).orElse(null);
        if (key == null)
            return;

        if (key.signKey.getKeyID() != keyID) {
            LOGGER.warning("key ID is not what we were searching for");
            return;
        }

        mControl.getViewControl().changed(new ViewEvent.NewKey(contact, key));
    }

    public void onPresenceError(JID jid, StanzaError.Type type, StanzaError.Condition condition) {
        if (type != StanzaError.Type.CANCEL)
            // it can't be that bad)
            return;

        Error error = null;
        switch (condition) {
            case remote_server_not_found:
                error = Error.SERVER_NOT_FOUND;
        }
        if (error == null) {
            LOGGER.warning("unhandled error condition: "+condition);
            return;
        }

        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        if (contact.getOnline() == Contact.Online.ERROR)
            // we already know this
            return;

        contact.setOnlineStatus(Contact.Online.ERROR);

        mControl.getViewControl().changed(new ViewEvent.PresenceError(contact, error));
    }
}
