/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.client;

import java.util.logging.Logger;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.kontalk.misc.JID;
import org.kontalk.system.Control;

/**
 * Listener for vCard4 iq stanzas, deprecated!
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class VCardListener implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(VCardListener.class.getName());

    private final Control mControl;

    static {
        ProviderManager.addIQProvider(
                VCard4.ELEMENT_NAME,
                VCard4.NAMESPACE,
                new VCard4.Provider());
    }

    VCardListener(Control control) {
        mControl = control;
    }

    @Override
    public void processStanza(Stanza packet) {
        VCard4 p = (VCard4) packet;
        LOGGER.info("vcard: "+p);

        byte[] publicKey = p.getPGPKey();

        // vcard coming from sync
        if (p.getType() == IQ.Type.set) {
            LOGGER.warning("ignoring vcard with type 'set'");
            return;
        }

        if (p.getType() == IQ.Type.result) {
            if (publicKey == null) {
                LOGGER.warning("got vcard without pgp key included");
                return;
            }
            mControl.onPGPKey(JID.fromSmack(p.getFrom()), publicKey);
        }
    }
}
