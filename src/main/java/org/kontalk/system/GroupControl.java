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

package org.kontalk.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.GroupChat;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.GroupCommand;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
interface GroupControl {

    void onCreate(List<Contact> contacts, String subject);

    void onSetSubject(String subject);

    boolean beforeDelete();

    // warning: call this only once for each group command!
    void onInMessage(MessageContent content, Contact sender);

    class KonChatControl implements GroupControl {
        private static final Logger LOGGER = Logger.getLogger(KonChatControl.class.getName());

        private final Control mControl;
        private final GroupChat mChat;

        KonChatControl(Control control, GroupChat chat) {
            mControl = control;
            mChat = chat;
        }

        @Override
        public void onCreate(List<Contact> contacts, String subject) {
            // send create group command
            List<JID> jids = new ArrayList<>(contacts.size());
            for (Contact c: contacts)
                jids.add(c.getJID());

            mControl.createAndSendMessage(mChat,
                    MessageContent.groupCommand(
                            MessageContent.GroupCommand.create(jids.toArray(new JID[0]),subject)
                    )
            );
        }

        @Override
        public void onSetSubject(String subject) {
            mControl.createAndSendMessage(mChat, MessageContent.groupCommand(
                    MessageContent.GroupCommand.set(new JID[0], new JID[0], subject)));
        }

        @Override
        public boolean beforeDelete() {
            if (!mChat.isValid())
                return true;

            // note: group chats are not 'deleted', were just leaving them
            return mControl.createAndSendMessage(mChat,
                    MessageContent.groupCommand(GroupCommand.leave()));
        }

        @Override
        public void onInMessage(MessageContent content, Contact sender) {
            Optional<GroupCommand> optCom = content.getGroupCommand();
            if (!optCom.isPresent()) {
                return;
            }

            // TODO ignore message if it contains unexpected group command

            GroupCommand command = optCom.get();

            // NOTE: chat was selected/created by GID so we can be sure message and
            // chat GIDs match
            GroupChat.GID gid = mChat.getGID();
            MessageContent.GroupCommand.OP op = command.getOperation();

            // validation check
            if (op != MessageContent.GroupCommand.OP.LEAVE) {
                // sender must be owner
                if (!gid.ownerJID.equals(sender.getJID())) {
                    LOGGER.warning("sender not owner");
                    return;
                }
            }

            if (op == MessageContent.GroupCommand.OP.CREATE ||
                    op == MessageContent.GroupCommand.OP.SET) {
                // add contacts if necessary
                // TODO design problem here: we need at least the public keys, but user
                // might dont wanna have group members in contact list
                for (JID jid : command.getAdded()) {
                    boolean succ = mControl.getOrCreateContact(jid, "").isPresent();
                    if (!succ)
                        LOGGER.warning("can't create contact, JID: "+jid);
                }
            }

            mChat.applyGroupCommand(command, sender);
        }
    }
}
