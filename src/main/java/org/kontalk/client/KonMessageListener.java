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

import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.pubsub.EventElement;
import org.jivesoftware.smackx.pubsub.EventElementType;
import org.jivesoftware.smackx.pubsub.ItemsExtension;
import org.jivesoftware.smackx.pubsub.NodeExtension;
import org.jivesoftware.smackx.pubsub.packet.PubSubNamespace;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.kontalk.misc.JID;
import org.kontalk.model.message.MessageContent;
import org.kontalk.system.Control;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.ClientUtils.MessageIDs;

/**
 * Listen and handle all incoming XMPP message packets.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final public class KonMessageListener implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(KonMessageListener.class.getName());

    private final Client mClient;
    private final Control mControl;
    private final AvatarSendReceiver mAvatarHandler;

    static {
        ProviderManager.addExtensionProvider(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE, new E2EEncryption.Provider());
        ProviderManager.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        ProviderManager.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
        ProviderManager.addExtensionProvider(GroupExtension.ELEMENT_NAME, GroupExtension.NAMESPACE, new GroupExtension.Provider());
    }

    KonMessageListener(Client client, Control control, AvatarSendReceiver avatarHandler) {
        mClient = client;
        mControl = control;
        mAvatarHandler = avatarHandler;
    }

    @Override
    public void processPacket(Stanza packet) {
        Message m = (Message) packet;

        Message.Type type = m.getType();
        // check for delivery receipt (XEP-0184)
        if (type == Message.Type.normal || type == Message.Type.chat) {
            DeliveryReceipt receipt = DeliveryReceipt.from(m);
            if (receipt != null) {
                // HOORAY! our message was received
                this.processReceiptMessage(m, receipt);
                return;
            }
        }

        if (type == Message.Type.chat) {
            // somebody has news for us
            this.processChatMessage(m);
            return;
        }

        if (type == Message.Type.error) {
            LOGGER.warning("got error message: "+m);

            XMPPError error = m.getError();
            if (error == null) {
                LOGGER.warning("error message does not contain error");
                return;
            }
            String text = StringUtils.defaultString(error.getDescriptiveText());
            mControl.onMessageError(MessageIDs.from(m), error.getCondition(), text);
            return;
        }

        if (type == Message.Type.headline) {
            this.processHeadlineMessage(m);
            return;
        }

        LOGGER.warning("unhandled message: "+m);
    }

    private void processReceiptMessage(Message m, DeliveryReceipt receipt) {
        LOGGER.config("message: "+m);
        String receiptID = receipt.getId();
        if (receiptID == null || receiptID.isEmpty()) {
            LOGGER.warning("message has invalid receipt ID: "+receiptID);
        } else {
            mControl.onMessageReceived(MessageIDs.from(m, receiptID));
        }
        // we ignore anything else that might be in this message
    }

    private void processChatMessage(Message m) {
        LOGGER.config("message: "+m);
        // note: thread and subject are null if message comes from the Kontalk
        // Android client

        // TODO a message can contain all sorts of extensions, we should loop
        // over all of them

        // timestamp
        // delayed deliver extension is the first the be processed
        // because it's used also in delivery receipts
        // first: new XEP-0203 specification
        ExtensionElement delay = DelayInformation.from(m);
        // fallback: obsolete XEP-0091 specification
        if (delay == null) {
            delay = m.getExtension("x", "jabber:x:delay");
        }
        Optional<Date> optServerDate = Optional.empty();
        if (delay instanceof DelayInformation) {
                Date date = ((DelayInformation) delay).getStamp();
                if (date.after(new Date()))
                    LOGGER.warning("delay time is in future: "+date);
                optServerDate = Optional.of(date);
        }

        MessageIDs ids = MessageIDs.from(m);

        // process possible chat state notification (XEP-0085)
        ExtensionElement csExt = m.getExtension(ChatStateExtension.NAMESPACE);
        ChatState chatState = null;
        if (csExt != null) {
            chatState = ((ChatStateExtension) csExt).getChatState();
            mControl.onChatStateNotification(ids,
                    optServerDate,
                    chatState);
        }

        // must be an incoming message

        // get content/text from body and/or encryption/url extension
        MessageContent content = ClientUtils.parseMessageContent(m);

        // make sure not to save a message without content
        if (content.isEmpty()) {
            if (chatState == null) {
                LOGGER.warning("can't find any content in message");
            } else if (chatState == ChatState.active) {
                LOGGER.info("only active chat state");
            }
            return;
        }

        // add message
        mControl.onNewInMessage(ids, optServerDate, content);

        // send a 'received' for a receipt request (XEP-0184)
        DeliveryReceiptRequest request = DeliveryReceiptRequest.from(m);
        if (request != null && !ids.xmppID.isEmpty()) {
            Message received = new Message(m.getFrom(), Message.Type.chat);
            received.addExtension(new DeliveryReceipt(ids.xmppID));
            mClient.sendPacket(received);
        }
    }

    private void processHeadlineMessage(Message m) {
        LOGGER.config("message: "+m);

        // this should be a pubsub event
        PubSubNamespace ns = PubSubNamespace.EVENT;
        ExtensionElement eventExt = m.getExtension(ns.getFragment(), ns.getXmlns());
        if (eventExt instanceof EventElement){
            EventElement event = (EventElement) eventExt;

            if (event.getEventType() == EventElementType.items) {
                NodeExtension extension = event.getEvent();
                if (extension instanceof ItemsExtension) {
                    ItemsExtension items = (ItemsExtension) extension;
                    if (items.getNode().equals(AvatarSendReceiver.METADATA_NODE)) {
                        mAvatarHandler.processMetadataEvent(JID.full(m.getFrom()), items);
                        return;
                    }
                }
            }
        }

        LOGGER.warning("unhandled");
    }
}
