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

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;

/**
 * Model for an XMPP message that we are sending.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class OutMessage extends KonMessage {
    private static final Logger LOGGER = Logger.getLogger(OutMessage.class.getName());

    OutMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void setStatus(Status status) {
        if (status == Status.IN) {
            LOGGER.warning("wrong argument status 'IN'");
            return;
        }
        if (status == Status.SENT && mStatus != Status.PENDING)
            LOGGER.warning("unexpected new status of sent message: "+status);
        if (status == Status.RECEIVED && mStatus != Status.SENT)
            LOGGER.warning("unexpected new status of received message: "+status);
        mStatus = status;
        if (status != Status.PENDING)
            mServerDate = Optional.of(new Date());
        this.save();
        this.changed(mStatus);
    }

    public void setServerError(String condition, String text) {
        if (mStatus != Status.SENT)
            LOGGER.warning("unexpected status of message with error: "+mStatus);
        mServerError = new KonMessage.ServerError(condition, text);
        this.setStatus(Status.ERROR);
    }

    public void setAttachmentURL(URI url) {
        MessageContent.Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.setURL(url);
        this.save();
    }

public static class Builder extends KonMessage.Builder {

        public Builder(Chat chat, Contact[] contacts, boolean encrypted) {
            super(-1, chat, Status.PENDING, new Date());

            mContacts = new HashMap<>();
            for (Contact c: contacts)
                mContacts.put(c, c.getJID());

            mXMPPID = "Kon_" + StringUtils.randomString(8);
            mServerDate = Optional.empty();

            mCoderStatus = encrypted ?
                CoderStatus.createToEncrypt() :
                CoderStatus.createInsecure();
        }

        @Override
        public void xmppID(String xmppID) { throw new UnsupportedOperationException(); }

        @Override
        public void serverDate(Date date) { throw new UnsupportedOperationException(); }

        @Override
        public void coderStatus(CoderStatus c) { throw new UnsupportedOperationException(); }

        @Override
        public OutMessage build() {
            return new OutMessage(this);
        }
    }

}
