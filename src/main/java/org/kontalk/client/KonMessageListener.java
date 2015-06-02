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

import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.kontalk.model.KonMessage.Status;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.system.Control;

/**
 * Listen and handle all incoming XMPP message packets.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final public class KonMessageListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(KonMessageListener.class.getName());

    // plain text body added by Android client

    private final Client mClient;
    private final Control mControl;

    KonMessageListener(Client client, Control control) {
        mClient = client;
        mControl = control;

        ProviderManager.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        //ProviderManager.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
        ProviderManager.addExtensionProvider(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE, new E2EEncryption.Provider());
    }

    @Override
    public void processPacket(Packet packet) {
        Message m = (Message) packet;
        if (m.getType() == Message.Type.chat) {
            // somebody has news for us
            this.processChatMessage(m);
        } else if (m.getType() == Message.Type.error) {
            LOGGER.warning("got error message: "+m.toXML());
            String xmppID = m.getPacketID();
            if (xmppID == null || xmppID.isEmpty()) {
                LOGGER.warning("error message has invalid XMPP ID: "+xmppID);
                return;
            }
            XMPPError error = m.getError();
            if (error == null) {
                LOGGER.warning("error message does not contain error");
                return;
            }
            String text = StringUtils.defaultString(error.getDescriptiveText());
            mControl.setMessageError(xmppID, error.getCondition(), text);
        } else {
            LOGGER.warning("unknown message type: "+m.getType());
        }
    }

    private void processChatMessage(Message m) {
        LOGGER.info("got message: "+StringUtils.abbreviate(m.toXML().toString(), 300));
        // note: thread and subject are null if message comes from the Kontalk
        // Android client

        String threadID = m.getThread() != null ? m.getThread() : "";

        // TODO a message can contain all sorts of extensions, we should loop
        // over all of them

        // timestamp
        // delayed deliver extension is the first the be processed
        // because it's used also in delivery receipts
        // first: new XEP-0203 specification
        PacketExtension delay = m.getExtension(DelayInformation.ELEMENT,
                DelayInformation.NAMESPACE);
        // fallback: obsolete XEP-0091 specification
        if (delay == null) {
            delay = m.getExtension("x", "jabber:x:delay");
        }
        Optional<Date> serverDate = Optional.empty();
        if (delay != null && delay instanceof DelayInformation) {
                Date date = ((DelayInformation) delay).getStamp();
                if (date.after(new Date()))
                    LOGGER.warning("delay time is in future: "+date);
                serverDate = Optional.of(date);
        }

        // process possible chat state notification (XEP-0085)
        PacketExtension chatstate = m.getExtension(ChatStateExtension.NAMESPACE);
        if (chatstate != null) {
            LOGGER.info("got chatstate: " + chatstate.getElementName());
            mControl.processChatState(m.getFrom(),
                    threadID,
                    serverDate,
                    chatstate.getElementName());
            if (!chatstate.getElementName().equals(ChatState.active.name()))
                // we assume there is no other content
                return;
        }

        // check for delivery receipt (XEP-0184)
        DeliveryReceipt receipt = DeliveryReceipt.from(m);
        if (receipt != null) {
            // HOORAY! our message was received
            String receiptID = receipt.getId();
            if (receiptID == null || receiptID.isEmpty()) {
                LOGGER.warning("message has invalid receipt ID: "+receiptID);
            } else {
                mControl.setMessageStatus(receiptID, Status.RECEIVED);
            }
            // we ignore anything else that might be in this message
            return;
        }

        // must be an incoming message

        // get content/text from body and/or encryption/url extension
        MessageContent content = parseMessageContent(m);

        // make sure not to save a message without content
        if (content.isEmpty()) {
            LOGGER.warning("can't find any content in message");
            return;
        }

        String xmppID = m.getPacketID() != null ? m.getPacketID() : "";
        if (xmppID.isEmpty()) {
            LOGGER.warning("message does not have a XMPP ID");
        }

        // add message
        boolean success = mControl.newInMessage(m.getFrom(),
                xmppID,
                threadID,
                serverDate,
                content);

        // on success, send a 'received' for a request (XEP-0184)
        DeliveryReceiptRequest request = DeliveryReceiptRequest.from(m);
        if (request != null && success && !xmppID.isEmpty()) {
            Message received = new Message(m.getFrom(), Message.Type.chat);
            received.addExtension(new DeliveryReceipt(xmppID));
            mClient.sendPacket(received);
        }
    }

    public static MessageContent parseMessageContent(Message m) {
        // default body
        String plainText = m.getBody() != null ? m.getBody() : "";

        // encryption extension (RFC 3923), decrypted later
        String encryptedContent = "";
        PacketExtension encryptionExt = m.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
        if (encryptionExt != null && encryptionExt instanceof E2EEncryption) {
            if (m.getBody() != null)
                LOGGER.info("message contains encryption and body (ignoring body): "+m.getBody());
            E2EEncryption encryption = (E2EEncryption) encryptionExt;
            encryptedContent = Base64.getEncoder().encodeToString(encryption.getData());
        }

        // Out of Band Data: a URI to a file
        Optional<Attachment> optAttachment = Optional.empty();
        PacketExtension oobExt = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
        if (oobExt!= null && oobExt instanceof OutOfBandData) {
            LOGGER.info("Parsing Out of Band Data");
            OutOfBandData oobData = (OutOfBandData) oobExt;
            Attachment attachment = new MessageContent.Attachment(oobData.getUrl(),
                    oobData.getMime() != null ? oobData.getMime() : "",
                    oobData.getLength(),
                    oobData.isEncrypted());
            optAttachment = Optional.of(attachment);
        }
        return new MessageContent(plainText, optAttachment, encryptedContent);
    }
}
