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
import java.util.logging.Logger;
import org.kontalk.client.MUCManager;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.GroupChat;
import org.kontalk.model.GroupChat.KonGroupChat;
import org.kontalk.model.GroupChat.MUCChat;
import org.kontalk.model.GroupMetaData.KonGroupData;
import org.kontalk.model.GroupMetaData.MUCData;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.GroupCommand;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class GroupControl {
    private static final Logger LOGGER = Logger.getLogger(GroupControl.class.getName());

    private final Control mControl;

    GroupControl(Control control) {
        mControl = control;
    }

    abstract class ChatControl<C extends GroupChat> {

        protected final C mChat;

        private ChatControl(C chat) {
            mChat = chat;
        }

        abstract void onCreate();

        abstract void onSetSubject(String subject);

        abstract boolean beforeDelete();

        //
        abstract void onInMessage(GroupCommand command, Contact sender);
    }

    final class KonChatControl extends ChatControl<KonGroupChat> {

        private KonChatControl(KonGroupChat chat) {
            super(chat);
        }

        @Override
        void onCreate() {
            Contact[] contacts = mChat.getValidContacts();

            // send create group command
            List<JID> jids = new ArrayList<>(contacts.length);
            for (Contact c: contacts)
                jids.add(c.getJID());

            mControl.createAndSendMessage(mChat,
                    MessageContent.groupCommand(
                            MessageContent.GroupCommand.create(
                                    jids.toArray(new JID[0]),
                                    mChat.getSubject())
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
        public void onInMessage(GroupCommand command, Contact sender) {
            // TODO ignore message if it contains unexpected group command (?)

            // NOTE: chat was selected/created by GID so we can be sure message
            // and chat GIDs match
            KonGroupData gid = mChat.getGroupData();
            MessageContent.GroupCommand.OP op = command.getOperation();

            // validation check
            if (op != MessageContent.GroupCommand.OP.LEAVE) {
                // sender must be owner
                if (!gid.owner.equals(sender.getJID())) {
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
                    boolean succ = mControl.getOrCreateContact(jid).isPresent();
                    if (!succ)
                        LOGGER.warning("can't create contact, JID: "+jid);
                }
            }

            mChat.applyGroupCommand(command, sender);
        }
    }

    final class MUCControl extends ChatControl<MUCChat> {

        private MUCControl(MUCChat chat) {
            super(chat);
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onSetSubject(String subject) {
            // TODO
        }

        @Override
        public boolean beforeDelete() {
            // TODO
            return true;
        }

        @Override
        public void onInMessage(GroupCommand command, Contact sender) {
        }
    }

    ChatControl getInstanceFor(GroupChat chat) {
        // TODO
        return (chat instanceof KonGroupChat) ?
                new KonChatControl((KonGroupChat) chat) :
        //        new MUCControl((MUCChat) chat);
                null;
    }

    static KonGroupData newKonGroupData(JID myJID) {
        return new KonGroupData(myJID,
                org.jivesoftware.smack.util.StringUtils.randomString(8));
    }

    static MUCData newMUCGroupData() {
        // TODO
        return new MUCData(
                JID.bare("kon_testroom_2", MUCManager.SERVICE),
                "secretpw");
    }
}
