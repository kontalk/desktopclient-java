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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Level;
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
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.kontalk.misc.JID;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.model.MessageContent.Preview;
import org.kontalk.system.Control;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.ClientUtils.MessageIDs;
import org.kontalk.util.EncodingUtils;

/**
 * Listen and handle all incoming XMPP message packets.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final public class KonMessageListener implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(KonMessageListener.class.getName());

    // plain text body added by Android client

    private final Client mClient;
    private final Control mControl;

    KonMessageListener(Client client, Control control) {
        mClient = client;
        mControl = control;

        ProviderManager.addExtensionProvider(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE, new E2EEncryption.Provider());
        ProviderManager.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        ProviderManager.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
        ProviderManager.addExtensionProvider(GroupExtension.ELEMENT_NAME, GroupExtension.NAMESPACE, new GroupExtension.Provider());
    }

    @Override
    public void processPacket(Stanza packet) {
        Message m = (Message) packet;

        // check for delivery receipt (XEP-0184)
        if (m.getType() == Message.Type.normal ||
                m.getType() == Message.Type.chat) {
            DeliveryReceipt receipt = DeliveryReceipt.from(m);
            if (receipt != null) {
                // HOORAY! our message was received
                this.processReceiptMessage(m, receipt);
                return;
            }
        }

        if (m.getType() == Message.Type.chat) {
            // somebody has news for us
            this.processChatMessage(m);
            return;
        }

        if (m.getType() == Message.Type.error) {
            LOGGER.warning("got error message: "+m);

            XMPPError error = m.getError();
            if (error == null) {
                LOGGER.warning("error message does not contain error");
                return;
            }
            String text = StringUtils.defaultString(error.getDescriptiveText());
            mControl.setMessageError(MessageIDs.from(m), error.getCondition(), text);
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
            mControl.setReceived(MessageIDs.from(m, receiptID));
        }
        // we ignore anything else that might be in this message
    }

    private void processChatMessage(Message m) {
        LOGGER.config("message: "+m);
        // note: thread and subject are null if message comes from the Kontalk
        // Android client

        String threadID = m.getThread() != null ? m.getThread() : "";

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

        // process possible chat state notification (XEP-0085)
        ExtensionElement csExt = m.getExtension(ChatStateExtension.NAMESPACE);
        ChatState chatState = null;
        if (csExt != null) {
            chatState = ((ChatStateExtension) csExt).getChatState();
            mControl.processChatState(JID.bare(m.getFrom()),
                    threadID,
                    optServerDate,
                    chatState);
        }

        // must be an incoming message

        // get content/text from body and/or encryption/url extension
        MessageContent content = parseMessageContent(m);

        // make sure not to save a message without content
        if (content.isEmpty()) {
            if (chatState == null)
                LOGGER.warning("can't find any content in message");
            return;
        }

        MessageIDs ids = MessageIDs.from(m);

        // add message
        boolean success = mControl.newInMessage(ids, optServerDate, content);

        // on success, send a 'received' for a request (XEP-0184)
        DeliveryReceiptRequest request = DeliveryReceiptRequest.from(m);
        if (request != null && success && !ids.xmppID.isEmpty()) {
            Message received = new Message(m.getFrom(), Message.Type.chat);
            received.addExtension(new DeliveryReceipt(ids.xmppID));
            mClient.sendPacket(received);
        }
    }

    public static MessageContent parseMessageContent(Message m) {
        // default body
        String plainText = StringUtils.defaultString(m.getBody());

        // encryption extension (RFC 3923), decrypted later
        String encrypted = "";
        ExtensionElement encryptionExt = m.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
        if (encryptionExt instanceof E2EEncryption) {
            if (m.getBody() != null)
                LOGGER.config("message contains encryption and body (ignoring body): "+m.getBody());
            E2EEncryption encryption = (E2EEncryption) encryptionExt;
            encrypted = EncodingUtils.bytesToBase64(encryption.getData());
        }

        // Bits of Binary: preview for file attachment
        Preview preview = null;
        ExtensionElement bobExt = m.getExtension(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE);
        if (bobExt instanceof BitsOfBinary) {
            BitsOfBinary bob = (BitsOfBinary) bobExt;
            String mime = StringUtils.defaultString(bob.getType());
            byte[] bits = bob.getContents();
            if (bits == null)
                bits = new byte[0];
            if (mime.isEmpty() || bits.length <= 0)
                LOGGER.warning("invalid BOB data: "+bob.toXML());
            else
                preview = new Preview(bits, mime);
        }

        // Out of Band Data: a URI to a file
        Attachment attachment = null;
        ExtensionElement oobExt = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
        if (oobExt instanceof OutOfBandData) {
            OutOfBandData oobData = (OutOfBandData) oobExt;
            URI url;
            try {
                url = new URI(oobData.getUrl());
            } catch (URISyntaxException ex) {
                LOGGER.log(Level.WARNING, "can't parse URL", ex);
                url = URI.create("");
            }
            attachment = new MessageContent.Attachment(url,
                    oobData.getMime() != null ? oobData.getMime() : "",
                    oobData.getLength(),
                    oobData.isEncrypted());
        }

        // group command
        GroupCommand groupCommand = null;
        ExtensionElement groupExt = m.getExtension(GroupExtension.ELEMENT_NAME, GroupExtension.NAMESPACE);
        if (groupExt instanceof GroupExtension) {
            GroupExtension group = (GroupExtension) groupExt;
            groupCommand = ClientUtils.groupExtensionToGroupCommand(
                    JID.bare(group.getOwner()), group.getID(),
                    group.getCommand(), group.getMember());
        }

        return new MessageContent(plainText, encrypted, attachment, preview, groupCommand);
    }
}
