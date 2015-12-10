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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PubSubManager;


/**
 *
 * Note: Smacks PEPManager is not in a usable state.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarSender {
    private static final Logger LOGGER = Logger.getLogger(AvatarSender.class.getName());

    private static final String DATA_NODE = "urn:xmpp:avatar:data";

    private final PubSubManager mPubSubManager;

    AvatarSender(PubSubManager m) {
        this.mPubSubManager = m;
    }

    // TODO beta.kontalk.net does not support this, untested
    void publish(String id, byte[] data) {

        LeafNode node;
        try {
            node = mPubSubManager.createNode(DATA_NODE);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't create node", ex);
            return;
        }

        PayloadItem<AvatarDataExtension> item = new PayloadItem<>(id,
                new AvatarDataExtension(data));
        try {
            // blocking
            node.send(item);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't send item", ex);
            return;
        }

        // publish meta data...
    }

}


