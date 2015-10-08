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
import org.kontalk.model.Chat;
import org.kontalk.model.Chat.GID;
import org.kontalk.model.Contact;
import org.kontalk.misc.JID;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.model.MessageContent.GroupCommand.OP;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ClientUtils {
    private static final Logger LOGGER = Logger.getLogger(ClientUtils.class.getName());

    /**
     * Message attributes to identify the chat for a message.
     */
    public static class MessageIDs {
        public final JID jid;
        public final String xmppID;
        public final String xmppThreadID;
        //public final Optional<GroupID> groupID;

        private MessageIDs(JID jid, String xmppID, String threadID) {
            this.jid = jid;
            this.xmppID = xmppID;
            this.xmppThreadID = threadID;
        }

        public static MessageIDs from(Message m) {
            return from(m, "");
        }

        public static MessageIDs from(Message m, String receiptID) {
            return new MessageIDs(
                    // TODO
                    JID.full(StringUtils.defaultString(m.getFrom())),
                    !receiptID.isEmpty() ? receiptID :
                            StringUtils.defaultString(m.getStanzaId()),
                    StringUtils.defaultString(m.getThread()));
        }

        @Override
        public String toString() {
            return "IDs:jid="+jid+",xmpp="+xmppID+",thread="+xmppThreadID;
        }
    }

    public static GroupExtension groupCommandToGroupExtension(Chat chat,
        GroupCommand groupCommand) {
        assert chat.isGroupChat();

        Optional<GID> optGID = chat.getGID();
        if (!optGID.isPresent()) {
            LOGGER.warning("no GID");
            return new GroupExtension("", "");
        }
        GID gid = optGID.get();

        OP op = groupCommand.getOperation();
        switch (op) {
            case LEAVE:
                // weare leaving
                return new GroupExtension(gid.id, gid.ownerJID, Command.LEAVE);
            case CREATE:
            case SET:
                Set<Member> member = new HashSet<>();
                Command command;
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
                        for (Contact c : chat.getContacts()) {
                            JID old = c.getJID();
                            if (!incl.contains(old))
                                member.add(new Member(old.string()));
                        }
                    }
                }

                return new GroupExtension(gid.id,
                        gid.ownerJID,
                        command,
                        member.toArray(new Member[0]));
            default:
                // can not happen
                return null;
        }
    }

    public static GroupCommand groupExtensionToGroupCommand(String ownerJID,
            String id,
            Command com,
            Member[] members) {
        GID gid = new GID(ownerJID, id);

        if (com == GroupExtension.Command.CREATE) {
            List<JID> jids = new ArrayList<>(members.length);
            for (Member m: members)
                jids.add(JID.bare(m.jid));
            return new GroupCommand(gid, jids.toArray(new JID[0]));
        } else if (com == GroupExtension.Command.LEAVE) {
            return new GroupCommand(gid);
        }

        // TODO
        return new GroupCommand(gid, new JID[0], new JID[0]);
    }
}
