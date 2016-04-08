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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.kontalk.misc.JID;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat.KonGroupChat;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.OutMessage;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.EncodingUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class KonMessageSender {
    private static final Logger LOGGER = Logger.getLogger(KonMessageSender.class.getName());

    private final Client mClient;

    KonMessageSender(Client client) {
        mClient = client;
    }

    boolean sendMessage(OutMessage message, boolean sendChatState) {
        // check for correct receipt status and reset it
        KonMessage.Status status = message.getStatus();
        assert status == KonMessage.Status.PENDING || status == KonMessage.Status.ERROR;
        message.setStatus(KonMessage.Status.PENDING);

        if (!mClient.isConnected()) {
            LOGGER.info("not sending message(s), not connected");
            return false;
        }

        MessageContent content = message.getContent();
        MessageContent.Attachment att = content.getAttachment().orElse(null);
        if (att != null && !att.hasURL()) {
            LOGGER.warning("attachment not uploaded");
            message.setStatus(KonMessage.Status.ERROR);
            return false;
        }

        boolean encrypted = message.isSendEncrypted();

        Chat chat = message.getChat();

        Message protoMessage = encrypted ? new Message() : rawMessage(content, chat, false);

        protoMessage.setType(Message.Type.chat);
        protoMessage.setStanzaId(message.getXMPPID());
        String threadID = chat.getXMPPID();
        if (!threadID.isEmpty())
            protoMessage.setThread(threadID);

        // extensions

        // not with group chat (at least not for Kontalk groups or MUC)
        if (!chat.isGroupChat())
            protoMessage.addExtension(new DeliveryReceiptRequest());

        // TEMP: server bug workaround, always include body
        if (protoMessage.getBody() == null)
            protoMessage.setBody("dummy");

        if (sendChatState)
            protoMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (encrypted) {
            byte[] encryptedData = content.getEncryptedData().orElse(null);
            if (encryptedData == null) {
                LOGGER.warning("no encrypted data");
                return false;
            }
            protoMessage.addExtension(new E2EEncryption(encryptedData));
        }

        List<JID> JIDs = message.getTransmissions().stream()
                .map(t -> t.getJID())
                .collect(Collectors.toList());

        String multiAddressHost = mClient.multiAddressHost();
        if (JIDs.size() > 1 && !multiAddressHost.isEmpty()) {
            // send one message to multiple receiver using XEP-0033
            protoMessage.setTo(multiAddressHost);
            MultipleAddresses addresses = new MultipleAddresses();
            for (JID to: JIDs) {
                addresses.addAddress(MultipleAddresses.Type.to, to.string(), null, null, false, null);
            }
            protoMessage.addExtension(addresses);

            return mClient.sendPacket(protoMessage);
        }

        // onle one receiver or fallback: send one message to each receiver
        ArrayList<Message> sendMessages = new ArrayList<>();
        for (JID to: JIDs) {
            Message sendMessage = protoMessage.clone();
            sendMessage.setTo(to.string());
            sendMessages.add(sendMessage);
        }

        return mClient.sendPackets(sendMessages.toArray(new Message[0]));
    }

    public static Message rawMessage(MessageContent content, Chat chat, boolean encrypted) {
        Message smackMessage = new Message();

        // text
        String text = content.getPlainText();
        if (!text.isEmpty())
            smackMessage.setBody(content.getPlainText());

        // attachment
        MessageContent.Attachment att = content.getAttachment().orElse(null);
        if (att != null) {
            OutOfBandData oobData = new OutOfBandData(att.getURL().toString(),
                    att.getMimeType(), att.getLength(), encrypted);
            smackMessage.addExtension(oobData);

            MessageContent.Preview preview = content.getPreview().orElse(null);
            if (preview != null) {
                String data = EncodingUtils.bytesToBase64(preview.getData());
                BitsOfBinary bob = new BitsOfBinary(preview.getMimeType(), data);
                smackMessage.addExtension(bob);
            }
        }

        // group command
        if (chat instanceof KonGroupChat) {
            KonGroupChat groupChat = (KonGroupChat) chat;
            KonGroupData gid = groupChat.getGroupData();
            MessageContent.GroupCommand groupCommand = content.getGroupCommand().orElse(null);
            smackMessage.addExtension(groupCommand != null ?
                    ClientUtils.groupCommandToGroupExtension(groupChat, groupCommand) :
                    new GroupExtension(gid.id, gid.owner.string()));
        }

        return smackMessage;
    }
}
