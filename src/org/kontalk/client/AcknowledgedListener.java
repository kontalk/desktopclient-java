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
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.kontalk.system.MessageCenter;
import org.kontalk.model.KonMessage.Status;

/**
 * Listener for acknowledged packets (Stream Management, XEP-0198).
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class AcknowledgedListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(AcknowledgedListener.class.getName());

    @Override
    public void processPacket(Packet p) {
        // note: the packet is not the acknowledgement itself but the packet that
        // is acknowledged
        if (!(p instanceof Message)) {
            // we are only interested in acks for messages
            return;
        }

        LOGGER.info("got acknowledgement for message: "+p.toXML());

        if (DeliveryReceipt.from(p) != null) {
            // this is an ack for a 'received' message send by
            // KonMessageListener (XEP-0184), nothing must be done
            return;
        }

        String xmppID = p.getPacketID();
        if (xmppID == null || xmppID.isEmpty()) {
            LOGGER.warning("acknowledged message has invalid XMPP ID: "+xmppID);
            return;
        }
        MessageCenter.getInstance().setMessageStatus(xmppID, Status.SENT);
    }

}
