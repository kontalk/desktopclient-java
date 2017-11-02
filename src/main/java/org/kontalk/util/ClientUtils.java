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
import java.util.Arrays;
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
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jxmpp.jid.Jid;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.GroupExtension.Member;
import org.kontalk.client.GroupExtension.Type;
import org.kontalk.client.OpenPGPExtension;
import org.kontalk.client.OpenPGPExtension.BodyElement;
import org.kontalk.client.OutOfBandData;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.GroupChat.KonGroupChat;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.MessageContent.InAttachment;
import org.kontalk.model.message.MessageContent.Preview;

/**
 * Static utilities as interface between client and control.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ClientUtils {
    private static final Logger LOGGER = Logger.getLogger(ClientUtils.class.getName());

    private static final List<String> IGNORED_NAMESPACES = Arrays.asList(
            ChatStateExtension.NAMESPACE, DeliveryReceipt.NAMESPACE);

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

        private static MessageIDs create(Message m, Jid jid, String receiptID) {
            return new MessageIDs(
                    JID.fromSmack(jid),
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
                              RosterPacket.ItemType type, boolean subscriptionPending) {
            this.jid = jid;
            this.name = name;
            this.subscription = rosterToModelSubscription(subscriptionPending, type);
        }


        private static Contact.Subscription rosterToModelSubscription(
                boolean subscriptionPending, RosterPacket.ItemType type) {
            if (type == RosterPacket.ItemType.both ||
                    type == RosterPacket.ItemType.to ||
                    type == RosterPacket.ItemType.remove)
                return Contact.Subscription.SUBSCRIBED;

            if (subscriptionPending)
                return Contact.Subscription.PENDING;

            return Contact.Subscription.UNSUBSCRIBED;
        }
    }

    public static MessageContent parseMessageContent(Message m, boolean decrypted) {
        MessageContent.Builder builder = new MessageContent.Builder();

        // parsing only default body
        String plainText = StringUtils.defaultString(m.getBody());
        String encrypted = "";

        if (!decrypted) {
            ExtensionElement e2eExt = m.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
            if (e2eExt instanceof E2EEncryption) {
                // encryption extension (RFC 3923), decrypted later
                encrypted = EncodingUtils.bytesToBase64(((E2EEncryption) e2eExt).getData());
                // remove extension before parsing all others
                m.removeExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
            }
            ExtensionElement openPGPExt = m.getExtension(OpenPGPExtension.ELEMENT_NAME, OpenPGPExtension.NAMESPACE);
            if (openPGPExt instanceof OpenPGPExtension) {
                if (!encrypted.isEmpty()) {
                    LOGGER.info("message contains e2e and OpenPGP element, ignoring e2e");
                }
                encrypted = ((OpenPGPExtension) openPGPExt).getData();
                // remove extension before parsing all others
                m.removeExtension(OpenPGPExtension.ELEMENT_NAME, OpenPGPExtension.NAMESPACE);
            }
        }

        if (!encrypted.isEmpty()) {
            if (!plainText.isEmpty()) {
                LOGGER.config("message contains encryption and body (ignoring body): " + plainText);
                plainText = "";
            }
            builder.encrypted(encrypted);
        }
        addContent(builder, m.getExtensions(), plainText, decrypted);
        return builder.build();
    }

    public static MessageContent extensionsToContent(List<ExtensionElement> elements) {
        MessageContent.Builder builder = new MessageContent.Builder();
        addContent(builder, elements, "", true);
        return builder.build();
    }

    private static void addContent(MessageContent.Builder builder, List<ExtensionElement> elements,
                                   String body, boolean decrypted) {
        String outOfBandURL = null;
        for (ExtensionElement element : elements) {
            if (element instanceof BodyElement) {
                body = ((BodyElement) element).getText();
            } else if (element instanceof BitsOfBinary) {
                // Bits of Binary: preview for file attachment
                BitsOfBinary bob = (BitsOfBinary) element;
                String mime = StringUtils.defaultString(bob.getType());
                byte[] bits = bob.getContents();
                if (bits == null)
                    bits = new byte[0];
                if (mime.isEmpty() || bits.length <= 0)
                    LOGGER.warning("invalid BOB data: " + bob.toXML());
                else
                    builder.preview(new Preview(bits, mime));
            } else if (element instanceof OutOfBandData) { // Out of Band Data: a URI to a file
                OutOfBandData oobData = (OutOfBandData) element;
                URI url;
                try {
                    url = new URI(oobData.getUrl());
                } catch (URISyntaxException ex) {
                    LOGGER.log(Level.WARNING, "can't parse URL", ex);
                    url = URI.create("");
                }

                builder.attachment(new InAttachment(url));

                outOfBandURL = url.toString();
            } else if (element instanceof GroupExtension) { // Kontalk group element and (maybe) command
                GroupExtension group = (GroupExtension) element;
                KonGroupData gid = new KonGroupData(JID.bare(group.getOwner()), group.getID());
                GroupCommand groupCommand = ClientUtils.groupExtensionToGroupCommand(
                        group.getType(), group.getMembers(), group.getSubject()).orElse(null);

                builder.groupData(gid);
                if (groupCommand != null)
                    builder.groupCommand(groupCommand);
            } else {
                if (decrypted || !IGNORED_NAMESPACES.contains(element.getNamespace()))
                    LOGGER.warning("unexpected extension: "
                            + (element == null ? null : element.toXML().toString()));
            }
        }

        // body text is maybe URI, for clients that dont understand OOB,
        // but we do, don't save it twice
        if (body.equals(outOfBandURL))
            body = "";

        if (!body.isEmpty())
            builder.body(body);
    }

    /* Internal to external */
    public static GroupExtension groupCommandToGroupExtension(KonGroupChat chat,
        GroupCommand groupCommand) {
        assert chat.isGroupChat();

        KonGroupData gid = chat.getGroupData();
        switch (groupCommand.getOperation()) {
            case LEAVE:
                // weare leaving
                return new GroupExtension(gid.id, gid.owner.string(), Type.PART);
            case CREATE: {
                Set<Member> members = new HashSet<>();
                groupCommand.getAdded().forEach(added -> members.add(new Member(added.string())));
                return new GroupExtension(gid.id, gid.owner.string(), Type.CREATE,
                        groupCommand.getSubject(), members);
            }
            case SET: {
                Set<Member> members = new HashSet<>();
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
                    // list all remaining members for the new members
                    for (Contact c : chat.getValidContacts()) {
                        JID old = c.getJID();
                        if (!incl.contains(old))
                            members.add(new Member(old.string()));
                    }
                }
                return new GroupExtension(gid.id, gid.owner.string(), Type.SET,
                        groupCommand.getSubject(), members);
            }
            default:
                LOGGER.warning("not implemented: "+groupCommand.getOperation());
                return new GroupExtension(gid.id, gid.owner.string());
        }
    }

    /* External to internal */
    private static Optional<GroupCommand> groupExtensionToGroupCommand(
            Type com, List<Member> members, String subject) {
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
                // ignoring duplicate JIDs (no log output)
                Set<JID> unchanged = new HashSet<>();
                Set<JID> added = new HashSet<>();
                Set<JID> removed = new HashSet<>();
                for (Member m : members) {
                   switch (m.operation) {
                       case NONE: unchanged.add(JID.bare(m.jid)); break;
                       case ADD: added.add(JID.bare(m.jid)); break;
                       case REMOVE: removed.add((JID.bare(m.jid))); break;
                   }
                }
                // sanity check; prioritize 'removed' over 'added'
                removed.stream()
                        .filter(added::contains)
                        .peek(jid -> LOGGER.warning("member added AND removed (removing) " + jid))
                        .forEach(added::remove);

                return Optional.of(GroupCommand.set(
                        new ArrayList<>(unchanged),
                        new ArrayList<>(added),
                        new ArrayList<>(removed),
                        subject));
            case GET:
            case RESULT:
            default:
                // TODO
                return Optional.empty();
        }
    }
}
