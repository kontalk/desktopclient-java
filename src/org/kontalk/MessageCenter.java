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

package org.kontalk;

import java.util.Date;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.crypto.Coder;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageList;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MessageCenter {

    private final static MessageCenter INSTANCE = new MessageCenter();

    public OutMessage newOutMessage(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        MessageContent content = new MessageContent(text);
        OutMessage.Builder builder = new OutMessage.Builder(thread, user, encrypted);
        builder.content(content);
        OutMessage newMessage = builder.build();
        thread.addMessage(newMessage);

        MessageList.getInstance().add(newMessage);
        return newMessage;
    }

    public void newInMessage(String from,
            String xmppID,
            String xmppThreadID,
            Date date,
            String receiptID,
            MessageContent content) {
        String jid = StringUtils.parseBareAddress(from);
        UserList userList = UserList.getInstance();
        User user = userList.containsUserWithJID(jid) ? userList.getUserByJID(jid) : userList.addUser(jid, null);
        ThreadList threadList = ThreadList.getInstance();
        KonThread thread = threadList.getThreadByXMPPID(xmppThreadID);
        if (thread == null) {
            thread = threadList.getThreadByUser(user);
        }
        InMessage.Builder builder = new InMessage.Builder(thread, user);
        builder.jid(from);
        builder.xmppID(xmppID);
        builder.date(date);
        builder.receiptID(receiptID);
        builder.content(content);
        InMessage newMessage = builder.build();
        Coder.processInMessage(newMessage);
        if (!newMessage.getSecurityErrors().isEmpty()) {
            Kontalk.getInstance().handleSecurityErrors(newMessage);
        }
        Downloader.getInstance().queueDownload(newMessage);
        thread.addMessage(newMessage);
        MessageList.getInstance().add(newMessage);
    }

    public static MessageCenter getInstance() {
        return INSTANCE;
    }
}
