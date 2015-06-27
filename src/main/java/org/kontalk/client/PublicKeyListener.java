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
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.kontalk.system.Control;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class PublicKeyListener implements StanzaListener {
    private final static Logger LOGGER = Logger.getLogger(PublicKeyListener.class.getName());

    private final Control mControl;

    public PublicKeyListener(Control control) {
        mControl = control;

        ProviderManager.addIQProvider(PublicKeyPublish.ELEMENT_NAME,
                PublicKeyPublish.NAMESPACE,
                new PublicKeyPublish.Provider());
    }

    @Override
    public void processPacket(Stanza packet) {
        LOGGER.info("got public key: "+packet.toXML());

        PublicKeyPublish publicKeyPacket = (PublicKeyPublish) packet;

        if (publicKeyPacket.getType() == IQ.Type.set) {
            LOGGER.warning("ignoring public key packet with type 'set'");
            return;
        }

        if (publicKeyPacket.getType() == IQ.Type.result) {
            byte[] keyData = publicKeyPacket.getPublicKey();
            if (keyData == null) {
                LOGGER.warning("got public key packet without public key");
                return;
            }
            mControl.setPGPKey(publicKeyPacket.getFrom(), keyData);
        }
    }

}
