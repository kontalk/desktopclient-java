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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.client.Client;
import org.kontalk.model.Chat;
import org.kontalk.model.Contact;

/**
 * Manager handling own chat status for all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatStateManager {

    private static final int COMPOSING_TO_PAUSED = 15; // seconds

    private final Client mClient;
    private final Map<Chat, MyChatState> mChatStateCache = new HashMap<>();
    private final Timer mTimer = new Timer();

    public ChatStateManager(Client client) {
        mClient = client;
    }

    void handleOwnChatStateEvent(Chat chat, ChatState state) {
        if (!mChatStateCache.containsKey(chat)) {
            if (state == ChatState.gone)
                // weare and stay at the default state
                return;
            mChatStateCache.put(chat, new MyChatState(chat));
        }

        mChatStateCache.get(chat).handleState(state);
    }

    void imGone() {
        for (MyChatState chatState : mChatStateCache.values())
            chatState.handleState(ChatState.gone);
    }

    private class MyChatState {
        private final Chat mChat;
        private ChatState mCurrentState;
        private TimerTask mScheduledStateSet = null;

        private MyChatState(Chat chat) {
            mChat = chat;
        }

        private void handleState(ChatState state) {
            if (mScheduledStateSet != null)
                // whatever we wanted to set next, thats obsolete now
                mScheduledStateSet.cancel();

            if (state != mCurrentState)
                this.setNewState(state);

            if (state == ChatState.composing) {
                mScheduledStateSet = new TimerTask() {
                    @Override
                    public void run() {
                        // TODO use 'inactive' instead of 'paused' for now as
                        // 'inactive' currently wont be send at all
                        MyChatState.this.handleState(ChatState.inactive);
                    }
                };
                mTimer.schedule(mScheduledStateSet,
                        TimeUnit.SECONDS.toMillis(COMPOSING_TO_PAUSED));
            }
        }

        private void setNewState(ChatState state) {
            // currently set states from XEP-0085: active, inactive, composing
            mCurrentState = state;

            Set<Contact> contacts = mChat.getContacts();

            if (contacts.size() > 1 || state == ChatState.active)
                // don't send for groups
                // 'active' is send inside a message
                return;

            for (Contact contact : contacts)
                if (!contact.isMe() && !contact.isBlocked())
                    mClient.sendChatState(contact.getJID(),
                            mChat.getXMPPID(),
                            state);
        }
    }

}
