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

package org.kontalk.system;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.kontalk.client.Client;
import org.kontalk.client.HKPClient;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.misc.JID;

/**
 * Process incoming roster and presence changes.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class RosterHandler {
    private static final Logger LOGGER = Logger.getLogger(RosterHandler.class.getName());

    private final Control mControl;
    private final Client mClient;

    private static final List<String> KEY_SERVERS = Arrays.asList(
            "pgp.mit.edu"
            // TODO: add CA for this
            //"pool.sks-keyservers.net"
            );

    public enum Error {
        SERVER_NOT_FOUND
    }

    RosterHandler(Control control, Client client) {
        mControl = control;
        mClient = client;
    }

    public void onEntryAdded(JID jid,
            String name,
            RosterPacket.ItemType type,
            RosterPacket.ItemStatus itemStatus) {
        if (ContactList.getInstance().contains(jid)) {
            this.onEntryUpdate(jid, name, type, itemStatus);
            return;
        }

        LOGGER.info("adding contact from roster, jid: "+jid);

        if (name.equals(jid.local()) && jid.isHash()) {
            // this must be the hash string, don't use it as name
            name = "";
        }

        Optional<Contact> optNewContact = mControl.createContact(jid, name);
        if (!optNewContact.isPresent())
            return;

        Contact.Subscription status = rosterToModelSubscription(itemStatus, type);
        optNewContact.get().setSubScriptionStatus(status);

        if (status == Contact.Subscription.UNSUBSCRIBED)
            mControl.sendPresenceSubscription(jid, Client.PresenceCommand.REQUEST);
    }

    public void onEntryDeleted(JID jid) {
        // note: also called on rename
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.info("can't find contact with jid: "+jid);
            return;
        }

        mControl.getViewControl().changed(new ViewEvent.ContactDeleted(optContact.get()));
    }

    public void onEntryUpdate(JID jid,
            String name,
            RosterPacket.ItemType type,
            RosterPacket.ItemStatus itemStatus) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with jid: "+jid);
            return;
        }
        Contact contact = optContact.get();
        // subcription may have changed
        contact.setSubScriptionStatus(rosterToModelSubscription(itemStatus, type));

        // name may have changed
        if (contact.getName().isEmpty() && !name.equals(jid.local()))
            contact.setName(name);
    }

    public void onSubscriptionRequest(JID jid, byte[] rawKey) {
        Optional<Contact> optContact = mControl.getOrCreate(jid, "");
        if (!optContact.isPresent())
            return;
        Contact contact = optContact.get();

        if (Config.getInstance().getBoolean(Config.NET_AUTO_SUBSCRIPTION)) {
            mControl.sendPresenceSubscription(jid, Client.PresenceCommand.GRANT);
        } else {
            // ask user
            mControl.getViewControl().changed(new ViewEvent.SubscriptionRequest(contact));
        }

        if (rawKey.length > 0)
            mControl.handlePGPKey(contact, rawKey);
    }

    public void onPresenceUpdate(JID jid, Presence.Type type, String status) {
        if (this.isMe(jid) && !ContactList.getInstance().contains(jid))
            // don't wanna see myself
            return;

        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with jid: "+jid);
            return;
        }
        optContact.get().setOnline(type, status);
    }

    public void onFingerprintPresence(JID jid, String fingerprint) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            if (!this.isMe(jid))
                LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }

        Contact contact = optContact.get();
        if (!fingerprint.isEmpty() &&
                !fingerprint.equalsIgnoreCase(contact.getFingerprint())) {
            LOGGER.info("detected public key change, requesting new key...");
            mControl.maySendKeyRequest(contact);
        }
    }

    // TODO key IDs can be forged, searching by it is defective by design
    public void onSignaturePresence(JID jid, String signature) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            if (!this.isMe(jid))
                LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }
        Contact contact = optContact.get();

        long keyID = PGPUtils.parseKeyIDFromSignature(signature);
        if (keyID == 0)
            return;

        if (contact.hasKey()) {
            Optional<PGPUtils.PGPCoderKey> optKey = Coder.contactkey(contact);
            if (optKey.isPresent() && optKey.get().signKey.getKeyID() == keyID)
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
        if (foundKey.isEmpty())
            return;

        Optional<PGPUtils.PGPCoderKey> optKey = PGPUtils.readPublicKey(foundKey);
        if (!optKey.isPresent())
            return;

        PGPUtils.PGPCoderKey key = optKey.get();

        if (key.signKey.getKeyID() != keyID) {
            LOGGER.warning("key ID is not what we were searching for");
            return;
        }

        mControl.getViewControl().changed(new ViewEvent.NewKey(optContact.get(), key));
    }

    public void onPresenceError(JID jid, XMPPError.Type type, XMPPError.Condition condition) {
        if (type != XMPPError.Type.CANCEL)
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

        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            if (!this.isMe(jid))
                LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }
        Contact contact = optContact.get();

        if (contact.getOnline() == Contact.Online.ERROR)
            // we already know this
            return;

        contact.setOnlineError();

        mControl.getViewControl().changed(new ViewEvent.PresenceError(contact, error));
    }

    /* private */

    private boolean isMe(JID jid) {
        Optional<JID> optMyJID = mClient.getOwnJID();
        return optMyJID.isPresent() ? optMyJID.get().equals(jid) : false;
    }

    private static Contact.Subscription rosterToModelSubscription(
            RosterPacket.ItemStatus status, RosterPacket.ItemType type) {
        if (type == RosterPacket.ItemType.both ||
                type == RosterPacket.ItemType.to ||
                type == RosterPacket.ItemType.remove)
            return Contact.Subscription.SUBSCRIBED;

        if (status == RosterPacket.ItemStatus.SUBSCRIPTION_PENDING)
            return Contact.Subscription.PENDING;

        return Contact.Subscription.UNSUBSCRIBED;
    }
}
