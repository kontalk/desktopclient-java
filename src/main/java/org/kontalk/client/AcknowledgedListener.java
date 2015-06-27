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
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.kontalk.model.KonMessage.Status;
import org.kontalk.system.Control;

/**
 * Listener for acknowledged packets (Stream Management, XEP-0198).
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class AcknowledgedListener implements StanzaListener {
    private final static Logger LOGGER = Logger.getLogger(AcknowledgedListener.class.getName());

    private final Control mControl;

    public AcknowledgedListener(Control control) {
        mControl = control;
    }

    @Override
    public void processPacket(Stanza p) {
        // note: the packet is not the acknowledgement itself but the packet that
        // is acknowledged
        if (!(p instanceof Message)) {
            // we are only interested in acks for messages
            return;
        }
        Message m = (Message) p;

        LOGGER.info("got acknowledgement for message: "+m.toXML());

        if (DeliveryReceipt.from(m) != null) {
            // this is an ack for a 'received' message send by
            // KonMessageListener (XEP-0184), nothing must be done
            return;
        }

        if (m.getBody() == null && m.getExtensions().size() == 1 &&
                m.getExtension(ChatStateExtension.NAMESPACE) != null) {
            // this is an ack for a chat state notification (XEP-0085), ignore
            return;
        }

        String xmppID = p.getStanzaId();
        if (xmppID == null || xmppID.isEmpty()) {
            LOGGER.warning("acknowledged message has invalid XMPP ID: "+xmppID);
            return;
        }
        mControl.setMessageStatus(xmppID, Status.SENT);
    }

}
