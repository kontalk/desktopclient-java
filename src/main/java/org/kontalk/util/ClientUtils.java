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

package org.kontalk.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.GroupExtension.Member;
import org.kontalk.client.GroupExtension.Type;
import org.kontalk.client.OutOfBandData;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.GroupChat.KonGroupChat;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.MessageContent.Attachment;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.MessageContent.GroupCommand.OP;
import org.kontalk.model.message.MessageContent.Preview;

/**
 * Static utilities as interface between client and control.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ClientUtils {
    private static final Logger LOGGER = Logger.getLogger(ClientUtils.class.getName());

    /**
     * Message attributes for identifying the chat for a message.
     * KonGroupData is missing here as this could be part of the encrypted content.
     */
    public static class MessageIDs {
        public final JID jid;
        public final String xmppID;
        public final String xmppThreadID;

        private MessageIDs(JID jid, String xmppID, String threadID) {
            this.jid = jid;
            this.xmppID = xmppID;
            this.xmppThreadID = threadID;
        }

        public static MessageIDs from(Message m) {
            return from(m, "");
        }

        public static MessageIDs from(Message m, String receiptID) {
            return create(m, m.getFrom(), receiptID);
        }

        public static MessageIDs to(Message m) {
            return create(m, m.getTo(), "");
        }

        private static MessageIDs create(Message m, String jid, String receiptID) {
            return new MessageIDs(
                    JID.full(StringUtils.defaultString(jid)),
                    !receiptID.isEmpty() ? receiptID :
                            StringUtils.defaultString(m.getStanzaId()),
                    StringUtils.defaultString(m.getThread()));
        }

        @Override
        public String toString() {
            return "IDs:jid="+jid+",xmpp="+xmppID+",thread="+xmppThreadID;
        }
    }

    public static class KonRosterEntry {
        public final JID jid;
        public final String name;
        public final Contact.Subscription subscription;

        public KonRosterEntry(JID jid, String name,
                              RosterPacket.ItemType type, RosterPacket.ItemStatus status) {
            this.jid = jid;
            this.name = name;
            this.subscription = rosterToModelSubscription(status, type);
        }


        private static Contact.Subscription rosterToModelSubscription(
                RosterPacket.ItemStatus status, RosterPacket.ItemType type) {
            if (type == RosterPacket.ItemType.both ||
                    type == RosterPacket.ItemType.to ||
                    type == RosterPacket.ItemType.remove)
                return Contact.Subscription.SUBSCRIBED;

            if (status == RosterPacket.ItemStatus.SUBSCRIPTION_PENDING)
                return Contact.Subscription.PENDING;

            return Contact.Subscription.UNSUBSCRIBED;
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
            attachment = MessageContent.Attachment.incoming(url,
                    oobData.getLength(),
                    oobData.isEncrypted());

            // body text is maybe URI, for clients that dont understand OOB,
            // but we do, don't save it twice
            if (plainText.equals(url.toString()));
                plainText = "";
        }

        // group command
        KonGroupData gid = null;
        GroupCommand groupCommand = null;
        ExtensionElement groupExt = m.getExtension(GroupExtension.ELEMENT_NAME,
                GroupExtension.NAMESPACE);
        if (groupExt instanceof GroupExtension) {
            GroupExtension group = (GroupExtension) groupExt;
            gid = new KonGroupData(JID.bare(group.getOwner()), group.getID());
            groupCommand = ClientUtils.groupExtensionToGroupCommand(
                    group.getType(), group.getMembers(), group.getSubject()).orElse(null);
        }

        return new MessageContent.Builder(plainText, encrypted)
                .attachment(attachment)
                .preview(preview)
                .groupData(gid)
                .groupCommand(groupCommand).build();
    }

    /* Internal to external */
    public static GroupExtension groupCommandToGroupExtension(KonGroupChat chat,
        GroupCommand groupCommand) {
        assert chat.isGroupChat();

        KonGroupData gid = chat.getGroupData();
        OP op = groupCommand.getOperation();
        switch (op) {
            case LEAVE:
                // weare leaving
                return new GroupExtension(gid.id, gid.owner.string(), Type.PART);
            case CREATE:
            case SET:
            default:
                Type command;
                Set<Member> members = new HashSet<>();
                String subject = groupCommand.getSubject();
                if (op == OP.CREATE) {
                    command = Type.CREATE;
                    groupCommand.getAdded().stream().forEach(added ->
                        members.add(new Member(added.string())));
                } else {
                    command = Type.SET;
                    Set<JID> incl = new HashSet<>();
                    for (JID added : groupCommand.getAdded()) {
                        incl.add(added);
                        members.add(new Member(added.string(), Member.Operation.ADD));
                    }
                    for (JID removed : groupCommand.getRemoved()) {
                        incl.add(removed);
                        members.add(new Member(removed.string(), Member.Operation.REMOVE));
                    }
                    if (!groupCommand.getAdded().isEmpty()) {
                        // list all remaining member for the new member
                        for (Contact c : chat.getValidContacts()) {
                            JID old = c.getJID();
                            if (!incl.contains(old))
                                members.add(new Member(old.string()));
                        }
                    }
                }

                return new GroupExtension(gid.id,
                        gid.owner.string(),
                        command,
                        subject,
                        members);
        }
    }

    /* External to internal */
    public static Optional<GroupCommand> groupExtensionToGroupCommand(
            Type com,
            List<Member> members,
            String subject) {

        switch (com) {
            case NONE:
                return Optional.empty();
            case CREATE:
                List<JID> jids = members.stream()
                        .peek(m -> { if (m.operation != Member.Operation.NONE) {
                            LOGGER.warning("unexpected member operation in "
                                    + "create command: " + m.operation + " " + m.jid);
                            }})
                        .map(m -> JID.bare(m.jid))
                        .collect(Collectors.toList());
                return Optional.of(GroupCommand.create(jids, subject));
            case PART:
                return Optional.of(GroupCommand.leave());
            case SET:
                // TODO we have to get the group chat here
                // * to find out if we are already member
                // * to find out if the subject is really new or only included
                // for new members
                // ignoring duplicate JIDs (no log output)
                Set<JID> added = new HashSet<>();
                Set<JID> removed = new HashSet<>();
                for (Member m : members) {
                   switch (m.operation) {
                       case NONE: // treat unchanged members as if they are new
                       // falltrough
                       case ADD: added.add(JID.bare(m.jid)); break;
                       case REMOVE: removed.add((JID.bare(m.jid))); break;
                   }
                }
                // sanity check; prioritize 'removed' over 'added'
                removed.stream()
                        .filter(jid -> added.contains(jid))
                        .peek(jid -> LOGGER.warning(
                                "member added AND removed (removing) " + jid))
                        .forEach(jid -> added.remove(jid));
                return Optional.of(GroupCommand.set(
                        new ArrayList<>(added),
                        new ArrayList<>(removed),
                        // for now we assume the subject wasn't changed
                        ""));
            case GET:
            case RESULT:
            default:
                // TODO
                return Optional.empty();
        }
    }
}
