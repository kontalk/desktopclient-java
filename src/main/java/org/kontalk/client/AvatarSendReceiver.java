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

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.pubsub.Item;
import org.jivesoftware.smackx.pubsub.ItemsExtension;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PubSubElementType;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.jivesoftware.smackx.pubsub.packet.PubSubNamespace;
import org.kontalk.misc.JID;
import org.kontalk.system.AvatarHandler;

/**
 * Manage publishing and requesting user avatars (XEP-0084).
 *
 * Metadata notification events are incoming as PubSub messages from message
 * listener.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarSendReceiver {
    private static final Logger LOGGER = Logger.getLogger(AvatarSendReceiver.class.getName());

    static final String NOTIFY_FEATURE = "urn:xmpp:avatar:metadata+notify";
    static final String METADATA_NODE = "urn:xmpp:avatar:metadata";

    private static final String DATA_NODE = "urn:xmpp:avatar:data";

    static {
        ProviderManager.addExtensionProvider(
                AvatarMetadataExtension.ELEMENT_NAME,
                AvatarMetadataExtension.NAMESPACE,
                new AvatarMetadataExtension.Provider());

        ProviderManager.addExtensionProvider(
                AvatarDataExtension.ELEMENT_NAME,
                AvatarDataExtension.NAMESPACE,
                new AvatarDataExtension.Provider());

    }

    private final KonConnection mConn;
    private final AvatarHandler mHandler;

    AvatarSendReceiver(KonConnection conn, AvatarHandler handler) {
        mConn = conn;
        mHandler = handler;
    }

    // TODO beta.kontalk.net does not support this, untested
    void publish(String id, byte[] data) {
        if (!mConn.isAuthenticated()) {
            LOGGER.info("not logged in");
            return;
        }

        PubSubManager mPubSubManager = PubSubManager.getInstance(mConn, mConn.getServiceName());
        LeafNode node;
        try {
            node = mPubSubManager.createNode(DATA_NODE);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException |
                InterruptedException ex) {
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
                SmackException.NotConnectedException |
                InterruptedException ex) {
            LOGGER.log(Level.WARNING, "can't send item", ex);
            return;
        }

        // TODO
        LOGGER.warning("not implemented");
        // publish meta data...
    }

    boolean delete() {
        if (!mConn.isAuthenticated()) {
            LOGGER.info("not logged in");
            return false;
        }

        // TODO
        LOGGER.warning("not implemented");
        return false;
    }

    void processMetadataEvent(JID jid, ItemsExtension itemsExt) {
        List<? extends ExtensionElement> items = itemsExt.getItems();
        if (items.isEmpty()) {
            LOGGER.warning("no items in items event");
            return;
        }

        // there should be only one item
        ExtensionElement e = items.get(0);
        if (!(e instanceof PayloadItem)) {
            LOGGER.warning("element not a payloaditem");
            return;
        }

        PayloadItem item = (PayloadItem) e;
        ExtensionElement metadataExt = item.getPayload();
        if (!(metadataExt instanceof AvatarMetadataExtension)) {
            LOGGER.warning("payload not avatar metadata");
            return;
        }
        AvatarMetadataExtension metadata = (AvatarMetadataExtension) metadataExt;
        List<AvatarMetadataExtension.Info> infos = metadata.getInfos();
        if (infos.isEmpty()) {
            // this means the contact disabled avatar publishing
            mHandler.onNotify(jid, "");
            return;
        }
        // assuming infos are always in the same order
        for (AvatarMetadataExtension.Info info : infos) {
            if (AvatarHandler.SUPPORTED_TYPES.contains(info.getType())) {
                mHandler.onNotify(jid, info.getId());
                break;
            } else {
                LOGGER.info("image type not supported: "+info.getType());
            }
        }
    }

    void requestAndListen(final JID jid, final String id) {
        // I dont get how to use this here
        //PubSubManager manager = new PubSubManager(conn);

        PubSub request = new PubSub(jid.toBareSmack(), IQ.Type.get, PubSubNamespace.BASIC);

        request.addExtension(
                new ItemsExtension(
                        ItemsExtension.ItemsElementType.items,
                        DATA_NODE,
                        Collections.singletonList(new Item(id))));

        // handle response
        StanzaListener callback = new StanzaListener() {
            @Override
            public void processStanza(Stanza packet)
                    throws SmackException.NotConnectedException {

                if (!(packet instanceof PubSub)) {
                    LOGGER.warning("response not a pubsub packet");
                    return;
                }
                PubSub pubSub = (PubSub) packet;

                ExtensionElement itemsExt = pubSub.getExtension(PubSubElementType.ITEMS);
                if (!(itemsExt instanceof ItemsExtension)) {
                    LOGGER.warning("no items extension in response");
                    return;
                }

                ItemsExtension items = (ItemsExtension) itemsExt;
                List<? extends ExtensionElement> itemsList = items.getItems();
                if (itemsList.isEmpty()) {
                    // TODO why this happens?
                    LOGGER.warning("no items in itemlist");
                    return;
                }

                // there should be only one item
                ExtensionElement e = itemsList.get(0);
                if (!(e instanceof PayloadItem)) {
                    LOGGER.warning("element not a payloaditem");
                    return;
                }

                PayloadItem item = (PayloadItem) e;
                ExtensionElement dataExt = item.getPayload();
                if (!(dataExt instanceof AvatarDataExtension)) {
                    LOGGER.warning("payload not avatar data");
                    return;
                }

                AvatarDataExtension avatarExt = (AvatarDataExtension) dataExt;

                byte[] avatarData = avatarExt.getData();
                if (avatarData.length == 0) {
                    LOGGER.warning("no avatar data in packet");
                    return;
                }

                mHandler.onData(jid, id, avatarData);
            }
        };

        mConn.sendWithCallback(request, callback);
    }
}
