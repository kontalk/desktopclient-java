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

import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.RosterHandler;

/**
 * Listen for presence packets.
 *
 * The presence stanza also may include a public key fingerprint
 * extension (custom Kontalk extension, based on XEP-0189) and/or a signature
 * extension for signing the status element (XEP-0027).
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class PresenceListener implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(PresenceListener.class.getName());

    private final Roster mRoster;
    private final RosterHandler mHandler;

    public PresenceListener(Roster roster, RosterHandler handler) {
        mRoster = roster;
        mHandler = handler;

        ProviderManager.addExtensionProvider(
                PublicKeyPresence.ELEMENT_NAME,
                PublicKeyPresence.NAMESPACE,
                new PublicKeyPresence.Provider());

        ProviderManager.addExtensionProvider(
                PresenceSignature.ELEMENT_NAME,
                PresenceSignature.NAMESPACE,
                new PresenceSignature.Provider());
    }

    @Override
    public void processPacket(Stanza packet) {
        LOGGER.config("packet: "+packet);

        Presence presence = (Presence) packet;

        if (presence.getType() == Presence.Type.error) {
            XMPPError error = presence.getError();
            if (error == null) {
                LOGGER.warning("error presence does not contain error");
                return;
            }
            mHandler.onPresenceError(presence.getFrom(), error.getType(), error.getCondition());
            return;
        }

        String jid = XmppStringUtils.parseBareJid(presence.getFrom());
        Presence bestPresence = mRoster.getPresence(jid);

        // NOTE: a delay extension is sometimes included, don't know why
        // ignoring mode, always null anyway
        mHandler.onPresenceUpdate(bestPresence.getFrom(),
                bestPresence.getType(),
                bestPresence.getStatus());

        ExtensionElement publicKeyExt = presence.getExtension(
                PublicKeyPresence.ELEMENT_NAME,
                PublicKeyPresence.NAMESPACE);
        if (publicKeyExt instanceof PublicKeyPresence) {
            PublicKeyPresence pubKey = (PublicKeyPresence) publicKeyExt;
            String fingerprint = StringUtils.defaultString(pubKey.getFingerprint());
            if (!fingerprint.isEmpty()) {
                mHandler.onFingerprintPresence(jid, fingerprint);
            } else {
                LOGGER.warning("no fingerprint in public key presence extension");
            }
        }

        ExtensionElement signatureExt = presence.getExtension(
                PresenceSignature.ELEMENT_NAME,
                PresenceSignature.NAMESPACE);
        if (signatureExt instanceof PresenceSignature) {
            PresenceSignature signing = (PresenceSignature) signatureExt;
            String signature = StringUtils.defaultString(signing.getSignature());
            if (!signature.isEmpty()) {
                mHandler.onSignaturePresence(jid, signature);
            } else {
                LOGGER.warning("no signature in signed presence extension");
            }
        }
    }
}
