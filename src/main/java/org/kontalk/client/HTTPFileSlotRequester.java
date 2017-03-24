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
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.kontalk.misc.Callback;
import org.kontalk.misc.JID;
import org.kontalk.system.AttachmentManager;
import org.kontalk.util.EncodingUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class HTTPFileSlotRequester {
    private static final Logger LOGGER = Logger.getLogger(HTTPFileSlotRequester.class.getName());

    static {
        ProviderManager.addIQProvider(
                HTTPFileUpload.Slot.ELEMENT_NAME,
                HTTPFileUpload.NAMESPACE,
                new HTTPFileUpload.Slot.Provider());
    }

    private final KonConnection mConn;
    private final JID mService;

    public HTTPFileSlotRequester(KonConnection conn, JID service) {
        mConn = conn;
        mService = service;
    }

    private HTTPFileUpload.Slot mSlotPacket;
    synchronized AttachmentManager.Slot getSlot(String filename, long size, String mime) {
        HTTPFileUpload.Request request = new HTTPFileUpload.Request(filename, size, mime);
        request.setTo(mService.toBareSmack());

        final Callback.Synchronizer syncer = new Callback.Synchronizer();
        mSlotPacket = null;
        mConn.sendWithCallback(request, new StanzaListener() {
            @Override
            public void processStanza(Stanza packet)
                    throws SmackException.NotConnectedException {
                LOGGER.config("response: "+packet);

                if (!(packet instanceof HTTPFileUpload.Slot)) {
                    LOGGER.warning("response not a slot packet: "+packet);
                    syncer.sync();
                    return;
                }
                mSlotPacket = (HTTPFileUpload.Slot) packet;
                syncer.sync();
            }
        });

        syncer.waitForSync();

        return mSlotPacket != null ?
                new AttachmentManager.Slot(
                        EncodingUtils.toURI(mSlotPacket.getPutUrl()),
                        EncodingUtils.toURI(mSlotPacket.getGetUrl())) :
                new AttachmentManager.Slot();
    }
}
