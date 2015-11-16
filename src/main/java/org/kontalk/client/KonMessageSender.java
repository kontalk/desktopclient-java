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
import java.util.Optional;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.kontalk.crypto.Coder;
import org.kontalk.misc.JID;
import org.kontalk.model.Chat;
import org.kontalk.model.GroupChat;
import org.kontalk.model.KonMessage;
import org.kontalk.model.MessageContent;
import org.kontalk.model.OutMessage;
import org.kontalk.model.Transmission;
import org.kontalk.system.Control;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.EncodingUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class KonMessageSender {
    private static final Logger LOGGER = Logger.getLogger(KonMessageSender.class.getName());

    private final Client mClient;
    private final Control mControl;

    KonMessageSender(Client client, Control control) {
        mClient = client;
        mControl = control;
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
        Optional<MessageContent.Attachment> optAtt = content.getAttachment();
        if (optAtt.isPresent() && !optAtt.get().hasURL()) {
            LOGGER.warning("attachment not uploaded");
            message.setStatus(KonMessage.Status.ERROR);
            return false;
        }

        boolean encrypted =
                message.getCoderStatus().getEncryption() != Coder.Encryption.NOT ||
                message.getCoderStatus().getSigning() != Coder.Signing.NOT;

        Chat chat = message.getChat();

        Message protoMessage = encrypted ? new Message() : rawMessage(content, chat, false);

        protoMessage.setType(Message.Type.chat);
        protoMessage.setStanzaId(message.getXMPPID());
        String threadID = chat.getXMPPID();
        if (!threadID.isEmpty())
            protoMessage.setThread(threadID);

        // extensions

        // TODO with group chat? (for muc "NOT RECOMMENDED")
        if (!chat.isGroupChat())
            protoMessage.addExtension(new DeliveryReceiptRequest());

        if (sendChatState)
            protoMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (encrypted) {
            Optional<byte[]> encryptedData = content.isComplex() || chat.isGroupChat() ?
                        Coder.encryptStanza(message,
                                rawMessage(content, chat, true).toXML().toString()) :
                        Coder.encryptMessage(message);
            // check also for security errors just to be sure
            if (!encryptedData.isPresent() ||
                    !message.getCoderStatus().getErrors().isEmpty()) {
                LOGGER.warning("encryption failed");
                message.setStatus(KonMessage.Status.ERROR);
                mControl.handleSecurityErrors(message);
                return false;
            }
            protoMessage.addExtension(new E2EEncryption(encryptedData.get()));
        }

        // transmission specific
        Transmission[] transmissions = message.getTransmissions();
        ArrayList<Message> sendMessages = new ArrayList<>(transmissions.length);
        for (Transmission transmission: message.getTransmissions()) {
            Message sendMessage = protoMessage.clone();
            JID to = transmission.getJID();
            if (!to.isValid()) {
                LOGGER.warning("invalid JID: "+to);
                return false;
            }
            sendMessage.setTo(to.string());
            sendMessages.add(sendMessage);
        }

        //return mClient.sendPackets(sendMessages.toArray(new Message[0]));
        return true;
    }

    private static Message rawMessage(MessageContent content, Chat chat, boolean encrypted) {
        Message smackMessage = new Message();

        // text
        String text = content.getPlainText();
        if (!text.isEmpty())
            smackMessage.setBody(content.getPlainText());

        // attachment
        Optional<MessageContent.Attachment> optAtt = content.getAttachment();
        if (optAtt.isPresent()) {
            MessageContent.Attachment att = optAtt.get();

            OutOfBandData oobData = new OutOfBandData(att.getURL().toString(),
                    att.getMimeType(), att.getLength(), encrypted);
            smackMessage.addExtension(oobData);

            Optional<MessageContent.Preview> optPreview = content.getPreview();
            if (optPreview.isPresent()) {
                MessageContent.Preview preview = optPreview.get();
                String data = EncodingUtils.bytesToBase64(preview.getData());
                BitsOfBinary bob = new BitsOfBinary(preview.getMimeType(), data);
                smackMessage.addExtension(bob);
            }
        }

        // group command
        if (chat instanceof GroupChat) {
            GroupChat groupChat = (GroupChat) chat;
            GroupChat.GID gid = groupChat.getGID();
            Optional<MessageContent.GroupCommand> optGroupCommand = content.getGroupCommand();
            smackMessage.addExtension(optGroupCommand.isPresent() ?
                    ClientUtils.groupCommandToGroupExtension(groupChat, optGroupCommand.get()) :
                    new GroupExtension(gid.id, gid.ownerJID.string()));
        }

        return smackMessage;
    }
}
