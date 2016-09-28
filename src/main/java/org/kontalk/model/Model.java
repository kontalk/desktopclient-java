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

package org.kontalk.model;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.kontalk.misc.JID;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.ChatList;
import org.kontalk.model.message.InMessage;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.OutMessage;
import org.kontalk.model.message.ProtoMessage;
import org.kontalk.persistence.Config;
import org.kontalk.persistence.Database;
import org.kontalk.util.ClientUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Model {
    private static final Logger LOGGER = Logger.getLogger(Model.class.getName());

    private static Model INSTANCE = null;
    private static Path APP_DIR;
    private static Database DATABASE;

    private final ContactList mContactList;
    private final ChatList mChatList;
    private final Account mAccount;

    private Model(Database db, Path appDir) {
        DATABASE = db;
        APP_DIR = appDir;

        mAccount = new Account(APP_DIR, Config.getInstance());
        mContactList = new ContactList();
        mChatList = new ChatList();

        Avatar.createStorageDir(appDir);
    }

    public static Model setup(Database db, Path appDir) {
        if (INSTANCE != null) {
            LOGGER.warning("already set up");
            return INSTANCE;
        }

        return INSTANCE = new Model(db, appDir);
    }

    public Account account() {
        return mAccount;
    }

    public ContactList contacts() {
        return mContactList;
    }

    public ChatList chats() {
        return mChatList;
    }

    public void load() {
        // order matters!
        Map<Integer, Contact> contactMap = mContactList.load();
        mChatList.load(contactMap);
    }

    public void setUserJID(JID jid) {
        Config.getInstance().setProperty(Config.ACC_JID, jid.string());

        if (!mContactList.contains(jid)) {
            LOGGER.info("creating user contact, jid: "+jid);
            mContactList.create(jid, "");
        }
    }

    public Optional<InMessage> createInMessage(ProtoMessage protoMessage,
            Chat chat, ClientUtils.MessageIDs ids, Optional<Date> serverDate) {
        InMessage newMessage = new InMessage(protoMessage, chat, ids.jid,
        ids.xmppID, serverDate);

        if (newMessage.getID() <= 0)
            return Optional.empty();

        if (chat.getMessages().contains(newMessage)) {
            LOGGER.info("message already in chat, dropping this one");
            return Optional.empty();
        }
        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.warning("can't add message to chat");
            return Optional.empty();
        }
        return Optional.of(newMessage);
    }

    public Optional<OutMessage> createOutMessage(Chat chat,
            List<Contact> contacts, MessageContent content) {
        OutMessage newMessage = new OutMessage(chat, contacts, content,
                chat.isSendEncrypted());

        boolean added = chat.addMessage(newMessage);
        if (!added) {
            LOGGER.warning("could not add outgoing message to chat");
            return Optional.empty();
        }
        return Optional.of(newMessage);
    }

    static Path appDir() {
        if (APP_DIR == null)
            throw new IllegalStateException("model not set up");

        return APP_DIR;
    }

    public static Database database(){
        if (DATABASE == null)
            throw new IllegalStateException("model not set up");

        return DATABASE;
    }

    public static JID getUserJID() {
        return JID.bare(Config.getInstance().getString(Config.ACC_JID));
    }

    public void onShutDown() {
        mContactList.onShutDown();
    }
}
