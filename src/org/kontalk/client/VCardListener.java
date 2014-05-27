/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.client;

import java.util.logging.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;
import org.kontalk.model.UserList;

/**
 *  Listener for vCard4 iq stanzas.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class VCardListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(VCardListener.class.getName());

    VCardListener() {
        ProviderManager pm = ProviderManager.getInstance();
        pm.addIQProvider(VCard4.ELEMENT_NAME, VCard4.NAMESPACE, new VCard4.Provider());
    }

    @Override
    public void processPacket(Packet packet) {
        VCard4 p = (VCard4) packet;
        LOGGER.info("got vcard: "+p.toXML());

        byte[] publicKey = p.getPGPKey();

        // vcard coming from sync
        if (p.getType() == IQ.Type.SET) {
            LOGGER.warning("ignoring vcard with type 'set'");
            return;
        }

        if (p.getType() == IQ.Type.RESULT) {
            if (publicKey == null) {
                LOGGER.warning("got vcard without pgp key included");
                return;
            }
            UserList.getInstance().setPGPKey(p.getFrom(), publicKey);
        }
    }
}
