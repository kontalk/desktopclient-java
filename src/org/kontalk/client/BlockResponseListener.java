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
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.kontalk.system.ControlCenter;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class BlockResponseListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(BlockResponseListener.class.getName());

    private final ControlCenter mControl;
    private final XMPPConnection mConn;
    private final boolean mBlocking;
    private final String mJID;

    BlockResponseListener(ControlCenter control,
            XMPPConnection conn,
            boolean blocking,
            String jid){
        mControl = control;
        mConn = conn;
        mBlocking = blocking;
        mJID = jid;
    }

    @Override
    public void processPacket(Packet packet)
            throws SmackException.NotConnectedException {
        LOGGER.info("got block response: "+packet.toXML());

        mConn.removePacketListener(this);

        if (!(packet instanceof IQ)) {
            LOGGER.warning("block response not an IQ packet");
            return;
        }
        IQ p = (IQ) packet;

        if (p.getType() != IQ.Type.result) {
            LOGGER.warning("ignoring block response with IQ type: "+p.getType());
            return;
        }

        mControl.setUserBlocking(mJID, mBlocking);
    }
};
