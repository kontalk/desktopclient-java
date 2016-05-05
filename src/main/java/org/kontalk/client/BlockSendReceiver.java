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

import java.util.logging.Logger;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.kontalk.misc.JID;
import org.kontalk.system.Control;

/**
 * Send blocking command and listen to response.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class BlockSendReceiver implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(BlockSendReceiver.class.getName());

    private final Control mControl;
    private final KonConnection mConn;
    private final boolean mBlocking;
    private final JID mJID;

    BlockSendReceiver(Control control,
            KonConnection conn,
            boolean blocking,
            JID jid){
        mControl = control;
        mConn = conn;
        mBlocking = blocking;
        mJID = jid;
    }

    void sendAndListen() {
        LOGGER.info("jid: "+mJID+" blocking="+mBlocking);

        String command = mBlocking ? BlockingCommand.BLOCK : BlockingCommand.UNBLOCK;
        BlockingCommand blockingCommand = new BlockingCommand(command, mJID.string());

        mConn.sendWithCallback(blockingCommand, this);
    }

    @Override
    public void processPacket(Stanza packet)
            throws SmackException.NotConnectedException {
        LOGGER.info("response: "+packet);

        if (!(packet instanceof IQ)) {
            LOGGER.warning("response not an IQ packet");
            return;
        }
        IQ p = (IQ) packet;

        if (p.getType() != IQ.Type.result) {
            LOGGER.warning("ignoring response with IQ type: "+p.getType());
            return;
        }

        mControl.onContactBlocked(mJID, mBlocking);
    }
};
