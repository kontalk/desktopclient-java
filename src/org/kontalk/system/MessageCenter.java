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

import org.kontalk.system.Downloader;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.crypto.Coder;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage.Status;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageList;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Central message handler as interface between controller and data model.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MessageCenter {
    private final static Logger LOGGER = Logger.getLogger(MessageCenter.class.getName());

    private static MessageCenter INSTANCE = null;

    private final Kontalk mModel;

    private MessageCenter(Kontalk model) {
        mModel = model;
    }

    /**
     * All-in-one method for a new outgoing message (except sending): Create,
     * save and process the message.
     * @return the new created message
     */
    public OutMessage newOutMessage(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        MessageContent content = new MessageContent(text);
        OutMessage.Builder builder = new OutMessage.Builder(thread, user, encrypted);
        builder.content(content);
        OutMessage newMessage = builder.build();
        thread.addMessage(newMessage);

        boolean added = MessageList.getInstance().add(newMessage);
        if (!added) {
            LOGGER.warning("could not add outgoing message to message list");
        }
        return newMessage;
    }

    /**
     * All-in-one method for a new incoming message (except handling server
     * receipts): Create, save and process the message.
     * @return true on success or message is a duplicate, false on unexpected failure
     */
    public boolean newInMessage(String from,
            String xmppID,
            String xmppThreadID,
            Date date,
            MessageContent content) {
        // get model references for this message
        String jid = XmppStringUtils.parseBareJid(from);
        Optional<User> optUser = getUser(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("can't get user for message");
            return false;
        }
        User user = optUser.get();
        KonThread thread = getThread(xmppThreadID, user);

        // generate own XMPP ID if not included in message
        if (xmppID.isEmpty())
            xmppID = "_kon_" + StringUtils.randomString(8);

        InMessage.Builder builder = new InMessage.Builder(thread, user);
        builder.jid(from);
        builder.xmppID(xmppID);
        builder.date(date);
        builder.content(content);
        InMessage newMessage = builder.build();

        boolean added = MessageList.getInstance().add(newMessage);
        if (!added) {
            LOGGER.info("message already in message list, dropping this one");
            return true;
        }
        newMessage.save();

        // decrypt content
        Coder.processInMessage(newMessage);
        if (!newMessage.getCoderStatus().getErrors().isEmpty()) {
            mModel.handleSecurityErrors(newMessage);
        }

        // download attachment if url is included
        if (newMessage.getContent().getAttachment().isPresent())
            Downloader.getInstance().queueDownload(newMessage);

        // this will effect the view, so we are doing this last
        thread.addMessage(newMessage);

        return newMessage.getID() >= -1;
    }

    /**
     * Set the receipt status of a message.
     * @param xmppID XMPP ID of message
     * @param status new receipt status of message
     */
    public void setMessageStatus(String xmppID, Status status) {
        Optional<OutMessage> optMessage = MessageList.getInstance().getUncompletedMessage(xmppID);
        if (!optMessage.isPresent()) {
            LOGGER.warning("can't find message");
            return;
        }
        optMessage.get().setStatus(status);
    }

    /**
     * Inform model (and view) about a received chat state notification.
     */
    public void processChatState(String from,
            String xmppThreadID,
            Date date,
            String chatStateString) {
        long diff = new Date().getTime() - date.getTime(); // milliseconds
        if (diff > TimeUnit.SECONDS.toMillis(10)) {
            // too old
            return;
        }

        ChatState chatState;
        try {
            chatState = ChatState.valueOf(chatStateString);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "can't parse chat state ", ex);
            return;
        }

        // get model references for this chat state message
        String jid = XmppStringUtils.parseBareJid(from);
        Optional<User> optUser = getUser(jid);
        if (!optUser.isPresent()) {
            LOGGER.warning("can't get user for chat state message");
            return;
        }
        User user = optUser.get();
        KonThread thread = getThread(xmppThreadID, user);

        thread.setChatState(user, chatState);
    }

    private static Optional<User> getUser(String jid) {
        UserList userList = UserList.getInstance();
        Optional<User> optUser = userList.containsUserWithJID(jid) ?
        userList.getUserByJID(jid) :
        userList.addUser(jid, "");
        return optUser;
    }

    private static KonThread getThread(String xmppThreadID, User user) {
        ThreadList threadList = ThreadList.getInstance();
        Optional<KonThread> optThread = threadList.getThreadByXMPPID(xmppThreadID);
        return optThread.orElse(threadList.getThreadByUser(user));
    }

    public static void initialize(Kontalk model) {
        if (INSTANCE != null) {
            LOGGER.warning("message center already initialized");
            return;
        }
        INSTANCE = new MessageCenter(model);
    }

    public static MessageCenter getInstance() {
        if (INSTANCE == null) {
            LOGGER.warning("message center not initialized");
            throw new IllegalStateException();
        }
        return INSTANCE;
    }
}
