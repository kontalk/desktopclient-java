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

import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.kontalk.misc.JID;
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
class PresenceListener implements StanzaListener {
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
    public void processStanza(Stanza packet) {
        if (MUCUser.from(packet) != null) {
            // handled by MUC manager
            LOGGER.config("ignoring MUC presence, from: "+packet.getFrom());
            return;
        }

        LOGGER.config("packet: "+packet);

        Presence presence = (Presence) packet;

        JID jid = JID.fromSmack(presence.getFrom());

        ExtensionElement publicKeyExt = presence.getExtension(
                PublicKeyPresence.ELEMENT_NAME,
                PublicKeyPresence.NAMESPACE);
        PublicKeyPresence pubKey = publicKeyExt instanceof PublicKeyPresence ?
                (PublicKeyPresence) publicKeyExt :
                null;

        switch(presence.getType()) {
            case error:
                StanzaError error = presence.getError();
                if (error == null) {
                    LOGGER.warning("error presence does not contain error");
                    return;
                }
                mHandler.onPresenceError(jid, error.getType(),
                        error.getCondition());
                return;
            // NOTE: only handled here if Roster.SubscriptionMode is set to 'manual'
            case subscribe:
                byte[] key = pubKey != null ? pubKey.getKey() : null;
                if (key == null)
                    key = new byte[0];
                mHandler.onSubscriptionRequest(jid, key);
                return;
            case unsubscribe:
                // nothing to do(?)
                LOGGER.info(("ignoring unsubscribe, JID: "+jid));
                return;
        }

        // NOTE: a delay extension is sometimes included, don't know why;
        // ignoring mode, always null anyway

        // NOTE: using only the "best" presence to ignore unimportant updates
        // from multiple clients
        Presence bestPresence = mRoster.getPresence(jid.toBareSmack());

        mHandler.onPresenceUpdate(jid,
                bestPresence.getType(),
                Optional.ofNullable(bestPresence.getStatus()));

        if (pubKey != null) {
            String fp = StringUtils.defaultString(pubKey.getFingerprint()).toLowerCase();
            if (fp.isEmpty()) {
                LOGGER.warning("no fingerprint in public key presence extension");
            } else {
                mHandler.onFingerprintPresence(jid, fp);
            }
        }

        ExtensionElement signatureExt = bestPresence.getExtension(
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
