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

package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.crypto.Coder;
import org.kontalk.system.Database;
import org.kontalk.util.EncodingUtils;

/**
 * Messages of chat.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ChatMessages {
    private static final Logger LOGGER = Logger.getLogger(ChatMessages.class.getName());

    private final Chat mChat;
    private final NavigableSet<KonMessage> mSet =
        Collections.synchronizedNavigableSet(new TreeSet<KonMessage>());

    private boolean mLoaded = false;

    public ChatMessages(Chat chat) {
        mChat = chat;
    }

    private void ensureLoaded() {
        if (mLoaded)
            return;

        this.loadMessages();
        mLoaded = true;
    }

    private void loadMessages() {
        Database db = Database.getInstance();
        KonMessage.Direction[] dirValues = KonMessage.Direction.values();
        KonMessage.Status[] statusValues = KonMessage.Status.values();
        Coder.Encryption[] encryptionValues = Coder.Encryption.values();
        Coder.Signing[] signingValues = Coder.Signing.values();
        String where = KonMessage.COL_CHAT_ID + " == " + mChat.getID();
        try (ResultSet resultSet = db.execSelectWhereInsecure(KonMessage.TABLE,
                where)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");

                int dirIndex = resultSet.getInt(KonMessage.COL_DIR);
                KonMessage.Direction dir = dirValues[dirIndex];
                int contactID = resultSet.getInt(KonMessage.COL_CONTACT_ID);
                Optional<Contact> optContact = ContactList.getInstance().get(contactID);
                if (!optContact.isPresent()) {
                    LOGGER.warning("can't find contact in db, id: "+contactID);
                    continue;
                }
                String jid = resultSet.getString(KonMessage.COL_JID);
                String xmppID = Database.getString(resultSet, KonMessage.COL_XMPP_ID);
                Date date = new Date(resultSet.getLong(KonMessage.COL_DATE));
                int statusIndex = resultSet.getInt(KonMessage.COL_REC_STAT);
                KonMessage.Status status = statusValues[statusIndex];
                String jsonContent = resultSet.getString(KonMessage.COL_CONTENT);
                MessageContent content = MessageContent.fromJSONString(jsonContent);

                int encryptionIndex = resultSet.getInt(KonMessage.COL_ENCR_STAT);
                Coder.Encryption encryption = encryptionValues[encryptionIndex];
                int signingIndex = resultSet.getInt(KonMessage.COL_SIGN_STAT);
                Coder.Signing signing = signingValues[signingIndex];
                int errorFlags = resultSet.getInt(KonMessage.COL_COD_ERR);
                EnumSet<Coder.Error> coderErrors = EncodingUtils.intToEnumSet(
                        Coder.Error.class, errorFlags);
                CoderStatus coderStatus = new CoderStatus(encryption, signing, coderErrors);
                String jsonServerError = resultSet.getString(KonMessage.COL_SERV_ERR);
                KonMessage.ServerError serverError =
                        KonMessage.ServerError.fromJSON(jsonServerError);
                long sDate = resultSet.getLong(KonMessage.COL_SERV_DATE);
                Optional<Date> serverDate = sDate == 0 ?
                        Optional.<Date>empty() :
                        Optional.of(new Date(sDate));

                KonMessage.Builder builder = new KonMessage.Builder(id, mChat,
                        dir, optContact.get(), date);
                builder.jid(jid);
                builder.xmppID(xmppID);
                builder.serverDate(serverDate);
                builder.receiptStatus(status);
                builder.content(content);
                builder.coderStatus(coderStatus);
                builder.serverError(serverError);

                KonMessage newMessage = builder.build();

                this.addSilent(newMessage);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load messages from db", ex);
        }
    }

    /**
     * Add message to chat without notifying other components.
     */
    boolean add(KonMessage message) {
        this.ensureLoaded();

        return this.addSilent(message);
    }

    private boolean addSilent(KonMessage message) {
        // see KonMessage.equals()
        if (mSet.contains(message)) {
            LOGGER.warning("message already in chat: " + message);
            return false;
        }
        boolean added = mSet.add(message);
        return added;
    }

    public NavigableSet<KonMessage> getAll() {
        this.ensureLoaded();

        return mSet;
    }

    /**
     * Get all outgoing messages with status "PENDING" for this chat.
     */
    public SortedSet<OutMessage> getPending() {
        this.ensureLoaded();

        SortedSet<OutMessage> s = new TreeSet<>();
        // TODO performance, probably additional map needed
        // TODO use lambda in near future
        for (KonMessage m : mSet) {
            if (m.getReceiptStatus() == KonMessage.Status.PENDING &&
                    m instanceof OutMessage) {
                s.add((OutMessage) m);
            }
        }
        return s;
    }

    /**
     * Get the newest (ie last received) outgoing message.
     */
    public Optional<OutMessage> getLast(String xmppID) {
        this.ensureLoaded();

        // TODO performance
        OutMessage message = null;
        for (KonMessage m: mSet.descendingSet()) {
            if (m.getXMPPID().equals(xmppID) && m instanceof OutMessage) {
                message = (OutMessage) m;
            }
        }

        if (message == null) {
            return Optional.empty();
        }

        return Optional.of(message);
    }
}
