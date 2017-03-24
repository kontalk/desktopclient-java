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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.jid.Jid;
import org.kontalk.client.OpenPGPExtension.SignCryptElement;
import org.kontalk.misc.JID;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat.KonGroupChat;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.MessageContent.OutAttachment;
import org.kontalk.model.message.MessageContent.Preview;
import org.kontalk.model.message.OutMessage;
import org.kontalk.model.message.Transmission;
import org.kontalk.util.MessageUtils.SendTask;
import org.kontalk.util.MessageUtils.SendTask.Encryption;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.EncodingUtils;
import org.kontalk.client.OpenPGPExtension.BodyElement;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class KonMessageSender {
    private static final Logger LOGGER = Logger.getLogger(KonMessageSender.class.getName());

    private static final int RPAD_LENGTH_RANGE = 40;

    private final Client mClient;

    KonMessageSender(Client client) {
        mClient = client;
    }

    boolean sendMessage(SendTask task, Optional<Jid> multiAddressHost) {
        OutMessage message = task.message;

        // check for correct receipt status and reset it
        KonMessage.Status status = message.getStatus();
        assert status == KonMessage.Status.PENDING || status == KonMessage.Status.ERROR;
        message.setStatus(KonMessage.Status.PENDING);

        if (!mClient.isConnected()) {
            LOGGER.info("not sending message(s), not connected");
            return false;
        }

        MessageContent content = message.getContent();
        OutAttachment att = content.getOutAttachment().orElse(null);
        if (att != null && !att.hasURL()) {
            LOGGER.warning("attachment not uploaded");
            message.setStatus(KonMessage.Status.ERROR);
            return false;
        }

        boolean encrypted = task.encryption != Encryption.NONE;

        Chat chat = message.getChat();
        Message smackMessage = encrypted ? new Message() : rawMessage(content, chat, false);

        smackMessage.setType(Message.Type.chat);
        smackMessage.setStanzaId(message.getXMPPID());
        String threadID = chat.getXMPPID();
        if (!threadID.isEmpty())
            smackMessage.setThread(threadID);

        // extensions

        // not with group chat (at least not for Kontalk groups or MUC)
        if (!chat.isGroupChat())
            smackMessage.addExtension(new DeliveryReceiptRequest());

        if (smackMessage.getBody() == null)
            // TEMP: server bug workaround, always include body
            smackMessage.setBody(encrypted ?
                    // Using implicit Enum.toString()
                    "This message is encrypted using OpenPGP (" + task.encryption +")." : "dummy");

        if (task.sendChatState)
            smackMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (encrypted) {
            String encryptedData = task.getEncryptedData();
            if (encryptedData.isEmpty()) {
                LOGGER.warning("no encrypted data");
                return false;
            }

            ExtensionElement encryptionExtension;
            switch(task.encryption) {
                case RFC3923: encryptionExtension = new E2EEncryption(encryptedData); break;
                case XEP0373: encryptionExtension = new E2EEncryption(encryptedData); break;
                default:
                    LOGGER.warning("unknown encryption: " + task.encryption);
                    return false;
            }
            smackMessage.addExtension(encryptionExtension);
        }

        List<JID> JIDs = message.getTransmissions().stream()
                .map(Transmission::getJID)
                .collect(Collectors.toList());

        if (JIDs.size() > 1 && multiAddressHost.isPresent()) {
            // send one message to multiple receiver using XEP-0033
            smackMessage.setTo(multiAddressHost.get());
            MultipleAddresses addresses = new MultipleAddresses();
            for (JID to: JIDs) {
                addresses.addAddress(MultipleAddresses.Type.to, to.toBareSmack(), null, null, false, null);
            }
            smackMessage.addExtension(addresses);

            return mClient.sendPacket(smackMessage);
        } else {
            // only one receiver or fallback: send one message to each receiver
            ArrayList<Message> sendMessages = new ArrayList<>();
            for (JID to: JIDs) {
                Message sendMessage = smackMessage.clone();
                sendMessage.setTo(to.toBareSmack());
                sendMessages.add(sendMessage);
            }

            return mClient.sendPackets(sendMessages.toArray(new Message[0]));
        }
    }

    public static String getEncryptionPayloadRFC3923(MessageContent content, Chat chat) {
        return rawMessage(content, chat, true).toXML().toString();
    }

    private static Message rawMessage(MessageContent content, Chat chat, boolean encrypted) {
        Message smackMessage = new Message();

        // text body
        String text = content.getPlainText();
        OutAttachment att = content.getOutAttachment().orElse(null);
        if (text.isEmpty() && att != null) {
            // use attachment URL as body
            text = att.getURL().toString();
        }
        if (!text.isEmpty())
            smackMessage.setBody(text);

        extensionsForContent(content, chat, encrypted)
                .forEach(smackMessage::addExtension);

        return smackMessage;
    }

    /** Get XEP-0373 signcrypt plaintext as XML string. */
    public static String getSignCryptElement(OutMessage message) {
        List<String> tos = message.getTransmissions().stream()
                .map(t -> t.getJID().string())
                .collect(Collectors.toList());
        int rpadLength = new SecureRandom().nextInt(RPAD_LENGTH_RANGE);

        List<ExtensionElement> contentElements = new ArrayList<>();
        MessageContent content = message.getContent();
        String text = content.getPlainText();
        if (!text.isEmpty())
            contentElements.add(new BodyElement(text));
        contentElements.addAll(extensionsForContent(content, message.getChat(), true));

        return new SignCryptElement(tos, new Date(), rpadLength, contentElements)
                .toXML().toString();
    }

    private static List<ExtensionElement> extensionsForContent(MessageContent content, Chat chat,
                                                               boolean encrypted) {
        List<ExtensionElement> elements = new ArrayList<>();
        // attachment
        OutAttachment att = content.getOutAttachment().orElse(null);
        if (att != null) {
            OutOfBandData oobData = new OutOfBandData(att.getURL().toString(),
                    att.getMimeType(), att.getLength(), encrypted);
            elements.add(oobData);

            Preview preview = content.getPreview().orElse(null);
            if (preview != null) {
                String data = EncodingUtils.bytesToBase64(preview.getData());
                BitsOfBinary bob = new BitsOfBinary(preview.getMimeType(), data);
                elements.add(bob);
            }
        }

        // group command
        if (chat instanceof KonGroupChat) {
            KonGroupChat groupChat = (KonGroupChat) chat;
            KonGroupData gid = groupChat.getGroupData();
            MessageContent.GroupCommand groupCommand = content.getGroupCommand().orElse(null);
            elements.add(groupCommand != null ?
                    ClientUtils.groupCommandToGroupExtension(groupChat, groupCommand) :
                    new GroupExtension(gid.id, gid.owner.string()));
        }

        return elements;
    }

}
