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

package org.kontalk.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.packet.Message;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.GroupExtension.Command;
import org.kontalk.client.GroupExtension.Member;
import org.kontalk.model.Contact;
import org.kontalk.misc.JID;
import org.kontalk.model.chat.GroupChat.KonGroupChat;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.MessageContent.GroupCommand.OP;

/**
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

    /* Internal to external */
    public static GroupExtension groupCommandToGroupExtension(KonGroupChat chat,
        GroupCommand groupCommand) {
        assert chat.isGroupChat();

        KonGroupData gid = chat.getGroupData();
        OP op = groupCommand.getOperation();
        switch (op) {
            case LEAVE:
                // weare leaving
                return new GroupExtension(gid.id, gid.owner.string(), Command.LEAVE);
            case CREATE:
            case SET:
                Command command;
                Set<Member> member = new HashSet<>();
                String subject = groupCommand.getSubject();
                if (op == OP.CREATE) {
                    command = Command.CREATE;
                    for (JID added : groupCommand.getAdded())
                        member.add(new Member(added.string()));
                } else {
                    command = Command.SET;
                    Set<JID> incl = new HashSet<>();
                    for (JID added : groupCommand.getAdded()) {
                        incl.add(added);
                        member.add(new Member(added.string(), Member.Type.ADD));
                    }
                    for (JID removed : groupCommand.getRemoved()) {
                        incl.add(removed);
                        member.add(new Member(removed.string(), Member.Type.REMOVE));
                    }
                    if (groupCommand.getAdded().length > 0) {
                        // list all remaining member for the new member
                        for (Contact c : chat.getValidContacts()) {
                            JID old = c.getJID();
                            if (!incl.contains(old))
                                member.add(new Member(old.string()));
                        }
                    }
                }

                return new GroupExtension(gid.id,
                        gid.owner.string(),
                        command,
                        member.toArray(new Member[0]),
                        subject);
            default:
                // can not happen
                return null;
        }
    }

    /* External to internal */
    public static Optional<GroupCommand> groupExtensionToGroupCommand(
            Command com,
            Member[] members,
            String subject) {

        switch (com) {
            case NONE:
                return Optional.empty();
            case CREATE:
                List<JID> jids = new ArrayList<>(members.length);
                for (Member m: members)
                    jids.add(JID.bare(m.jid));
                return Optional.of(GroupCommand.create(jids.toArray(new JID[0]), subject));
            case LEAVE:
                return Optional.of(GroupCommand.leave());
            case SET:
                // TODO
                return Optional.of(GroupCommand.set(new JID[0], new JID[0], subject));
            case GET:
            case RESULT:
            default:
                // TODO
                return Optional.empty();
        }
    }
}
