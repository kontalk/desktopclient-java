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
import org.jivesoftware.smackx.muc.Affiliate;
import org.jivesoftware.smackx.muc.MUCAffiliation;
import org.kontalk.client.Client;
import org.kontalk.misc.Callback;
import org.kontalk.misc.JID;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.model.GroupChat;
import org.kontalk.model.GroupChat.MUCChat;
import org.kontalk.model.GroupMetaData;
import org.kontalk.model.GroupMetaData.MUCData;

/**
 * Process incoming stanzas related to the MUC protocol.
 *
 * Support for MUC is incomplete!
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class MUCHandler {
    private static final Logger LOGGER = Logger.getLogger(MUCHandler.class.getName());

    private final Control mControl;
    private final Client mClient;

    public MUCHandler(Control control, Client client) {
        mControl = control;
        mClient = client;
    }

    public void onInvite(final JID room, JID invitee, String reason, final String pw) {
        final Optional<Contact> optContact = mControl.getOrCreateContact(invitee);
        if (!optContact.isPresent())
            return;
        final Contact contact = optContact.get();

        // TODO ask user

        mClient.join(room, pw,new Callback.Handler<List<Affiliate>>(){
            @Override
            public void handle(Callback<List<Affiliate>> callback) {
                if (callback.value == null)
                    return;

                MUCData gData = new MUCData(room, pw);
                // TODO ugly casting
                MUCChat chat = (MUCChat) ChatList.getInstance().getOrCreate(gData, contact);

                // TODO
                //GroupCommand create = GroupCommand.create();
                //chat.applyGroupCommand(create, contact);

                // TODO create message
                //InMessage message = new InMessage(chat, contact, create);

                // NOTE: the list only includes affilations of type 'members'
                // and is maybe incomplete
                // members are not removed!
                MUCHandler.this.addMembers(chat, callback.value);
            }
        });
    }

    // TODO unused
    public void onMemberList(JID room, List<Affiliate> members) {
        Optional<GroupChat> optChat = ChatList.getInstance().get(new GroupMetaData.MUCData(room));
        if (!optChat.isPresent() || !(optChat.get() instanceof MUCChat)) {
            LOGGER.warning("can't find MUC chat: "+room);
            return;
        }

        this.addMembers(((MUCChat) optChat.get()), members);
    }

    private void addMembers(MUCChat chat, List<Affiliate> members) {
        List<Contact> contacts = new ArrayList<>(members.size());
        for(Affiliate a: members) {
            // NOTE: nickname MAY included (not included for jabber.ccc.de)
            LOGGER.config("affiliate: "+a.getNick()+" -> "+a.getJid()+" | "+a.getAffiliation());
            Optional<Contact> optContact = mControl.getOrCreateContact(JID.bare(a.getJid()));
            if (!optContact.isPresent())
                continue;

            contacts.add(optContact.get());
        }

        chat.addContacts(contacts);
    }

    public void onPresence(JID room, String nickname, JID jid, MUCAffiliation affiliation) {
        Optional<GroupChat> optChat = ChatList.getInstance().get(new GroupMetaData.MUCData(room));
        if (!optChat.isPresent() || !(optChat.get() instanceof MUCChat)) {
            LOGGER.warning("can't find MUC chat: "+room);
            return;
        }
        MUCChat chat = ((MUCChat) optChat.get());

        chat.addNicknameMapping(nickname, jid);

        if (affiliation != MUCAffiliation.owner &&
                affiliation != MUCAffiliation.admin &&
                affiliation != MUCAffiliation.member) {
            LOGGER.warning("unexpected affiliation: "+affiliation+"; jid="+jid);
            return;
        }

        Optional<Contact> optContact = mControl.getOrCreateContact(jid);
        if (!optContact.isPresent())
            return;
        Contact contact = optContact.get();

        // member list maybe incomplete, fix now
        if (!chat.getAllContacts().contains(contact))
            chat.addContact(contact);
    }
}
