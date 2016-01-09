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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.muc.Affiliate;
import org.jivesoftware.smackx.muc.MUCAffiliation;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.packet.MUCItem;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.kontalk.misc.Callback;
import org.kontalk.misc.JID;
import org.kontalk.system.MUCHandler;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class MUCManager {
    private static final Logger LOGGER = Logger.getLogger(MUCManager.class.getName());

    public static final String SERVICE = "conference.jabber.ccc.de";

    private final MultiUserChatManager mManager;
    private final MUCHandler mHandler;

    MUCManager(KonConnection conn, MUCHandler mucHandler) {
        mManager = MultiUserChatManager.getInstanceFor(conn);
        mHandler = mucHandler;

        StanzaListener mucUserListener = new StanzaListener() {
            @Override
            public void processPacket(Stanza packet) {
                LOGGER.info("MUC meta message: "+packet);

                Message message = (Message) packet;

                MUCUser mucUser = MUCUser.from(message);
                //MultiUserChat muc = mManager.getMultiUserChat(packet.getFrom());
                MUCUser.Invite invite = mucUser.getInvite();
                if (invite != null) {
                    mHandler.onInvite(JID.bare(message.getFrom()),
                            JID.bare(invite.getFrom()),
                            StringUtils.defaultString(invite.getReason()),
                            StringUtils.defaultString(mucUser.getPassword()));
                    return;
                }

                if (mucUser.getDecline() != null) {
                    // TODO
                }
                if (mucUser.getStatus() != null) {
                    // TODO
                }
                if (mucUser.getItem() != null) {
                    // TODO
                }

                LOGGER.warning("unhandled");
            }
        };
        conn.addAsyncStanzaListener(mucUserListener,
                new AndFilter(StanzaTypeFilter.MESSAGE, new StanzaExtensionFilter(new MUCUser())));
    }

    boolean create(JID room, String subject, String password) {
        MultiUserChat muc = this.getMUC(room);

        try {
            muc.create(JID.me().local());
        } catch (XMPPException.XMPPErrorException | SmackException ex) {
            LOGGER.log(Level.WARNING, "didn't work", ex);
            // ignore
            //return;
        }

        // Get the the room's configuration form
        Form form;
        try {
            form = muc.getConfigurationForm();
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't get config form", ex);
            return false;
        }

        // Create a new form to submit based on the original form
        Form submitForm = form.createAnswerForm();

        // Add default answers to the form to submit
        for (FormField field : form.getFields()) {
            if (!FormField.Type.hidden.equals(field.getType()) && field.getVariable() != null) {
                // Sets the default value as the answer
                submitForm.setDefaultAnswer(field.getVariable());
            }
        }

//        for (FormField field: form.getFields()) {
//            System.out.println(field.getVariable()+ " [[ "+field.getLabel()+" ]] "+field.getValues()+" || "+field.getOptions()+" ## "+field.getType());
//        }

        // standard values for all new rooms:

        // persistent (== do not destroy when last occupant(!) leaves)
        submitForm.setAnswer("muc#roomconfig_persistentroom", true);
        // private
        submitForm.setAnswer("muc#roomconfig_publicroom", false);
        // password protected
        submitForm.setAnswer("muc#roomconfig_passwordprotectedroom", true);
        // members-only (== only user in member list can enter)
        submitForm.setAnswer("muc#roomconfig_membersonly", true);
        // unmoderated (== no voice stuff)
        submitForm.setAnswer("muc#roomconfig_moderatedroom", false);
        // non-Anonymous (== send JIDs in presence stanzas for everybody)
        submitForm.setAnswer("muc#roomconfig_whois", Arrays.asList("anyone"));

        // only moderators can change subject
        submitForm.setAnswer("muc#roomconfig_changesubject", false);
        // participant list is not public (room is private anyway)
        submitForm.setAnswer("public_list", false);
        // only moderator can invite (room is member-only anyway)
        submitForm.setAnswer("muc#roomconfig_allowinvites", false);

        // (probably) not important:
        // muc#roomconfig_maxusers [200] || [5, 10, 20, 30, 50, 100, 200]
        // members_by_default [[ Default users as participants ]] [1] || []
        // allow_private_messages [[ Allow users to send private messages ]] [1] || []
        // allow_private_messages_from_visitors [[ Allow visitors to send private messages to ]] [anyone] || [nobody, moderators only, anyone]
        // allow_query_users [[ Allow users to query other users ]] [1] || []

        // custom values:

        // subject (==title)
        submitForm.setAnswer("muc#roomconfig_roomname", subject);
        // password
        submitForm.setAnswer("muc#roomconfig_roomsecret", password);

        try {
            muc.sendConfigurationForm(submitForm);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't send config", ex);
            return false;
        }

        // assume everything went alright if were here
        return true;
    }

    void join(JID room, String password, Callback.Handler<List<Affiliate>> cbh) {
        LOGGER.info("room: "+room);
        MultiUserChat muc = this.getMUC(room);
        
        try {
            muc.join(JID.me().local(), password);
        } catch (XMPPException.XMPPErrorException | SmackException ex) {
            LOGGER.log(Level.WARNING, "failed", ex);
            return;
        }

        // NOTE: only SHOULD for normal occupants;
        List<Affiliate> members = new ArrayList<>();
        try {
            // forbidden:
            //muc.getOwners();
            //muc.getAdmins();
            members.addAll(muc.getMembers());
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't get member list", ex);
            return;
        }

        cbh.handle(new Callback<>(members));
    }

    // TODO unused
    boolean decline(JID room, JID invitee) {
        LOGGER.info("room: "+room+"; invitee: "+invitee);
        try {
            mManager.decline(room.string(), invitee.string(), "you suck");
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "failed", ex);
            return false;
        }
        return true;
    }

    private MultiUserChat getMUC(JID room) {
        MultiUserChat muc = mManager.getMultiUserChat(room.string());

        // possible listeners for this room..

        // messages with muc#user
        //muc.addInvitationRejectionListener(null);
        // messages type group chat
        //muc.addMessageListener(null);
        // presence stanzas
        //muc.addParticipantListener(null); -> see below
        //muc.addUserStatusListener(null);
        // message with <subject>
        //muc.addSubjectUpdatedListener(null);

        muc.addParticipantListener(new org.jivesoftware.smack.PresenceListener() {
            @Override
            public void processPresence(Presence presence) {
                LOGGER.config("MUC presence: "+presence);
                JID from = JID.full(presence.getFrom());
                MUCUser mucUser = MUCUser.from(presence);
                if (mucUser != null) {
                    MUCItem item = mucUser.getItem();
                    if (item == null) {
                        LOGGER.warning("no item in presence");
                        return;
                    }
                    String j = StringUtils.defaultString(item.getJid());
                    if (j.isEmpty()) {
                        LOGGER.warning("no JID in item");
                        return;
                    }
                    JID jid = JID.full(j);
                    MUCAffiliation affiliation = item.getAffiliation();
                    if (affiliation == null) {
                        LOGGER.warning("no affiliation in item");
                        return;
                    }
                    mHandler.onPresence(from.toBare(), from.resource(), jid, affiliation);
                }
            }
        });

        return muc;
    }
}
