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

import java.util.Optional;
import java.util.logging.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.ControlCenter;

/**
 * Listen for presence packets. They also may include a custom Kontalk extension
 * for the public key fingerprint of a user.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class PresenceListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(PresenceListener.class.getName());

    private final Client mClient;
    private final Roster mRoster;
    private final ControlCenter mControl;

    public PresenceListener(Client client, Roster roster, ControlCenter control) {
        mClient = client;
        mRoster = roster;
        mControl = control;

        ProviderManager.addExtensionProvider(
                PublicKeyPresence.ELEMENT_NAME,
                PublicKeyPresence.NAMESPACE,
                new PublicKeyPresence.Provider());
    }

    @Override
    public void processPacket(Packet packet) {
        LOGGER.info("got presence packet: "+packet.toXML());

        Presence presence = (Presence) packet;

        String jid = XmppStringUtils.parseBareJid(presence.getFrom());
        if (jid.equals(XmppStringUtils.parseBareJid(mClient.getOwnJID())))
            // this is a presence for/from myself, ignore it
            return;

        Presence bestPresence = mRoster.getPresence(jid);

        // NOTE: a delay extension is sometimes included, don't know why
        // ignoring mode, always null anyway
        mControl.setPresence(bestPresence.getFrom(),
                bestPresence.getType(),
                bestPresence.getStatus());

        PacketExtension publicKeyExt = presence.getExtension(
                PublicKeyPresence.ELEMENT_NAME,
                PublicKeyPresence.NAMESPACE);

        if (publicKeyExt == null || !(publicKeyExt instanceof PublicKeyPresence))
            // nothing more to do
            return;

        PublicKeyPresence publicKeyPresence = (PublicKeyPresence) publicKeyExt;
        String fingerprint = publicKeyPresence.getFingerprint();
        if (fingerprint == null || fingerprint.isEmpty()) {
            LOGGER.warning("no fingerprint in public key presence extension");
            return;
        }

        Optional<User> optUser = UserList.getInstance().getUserByJID(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("can't find user with jid:" + jid);
            return;
        }

        User user = optUser.get();
        if (!user.getFingerprint().equals(fingerprint)) {
            LOGGER.info("detected public key change, requesting new key...");
            mClient.sendPublicKeyRequest(user.getJID());
        }
    }
}
