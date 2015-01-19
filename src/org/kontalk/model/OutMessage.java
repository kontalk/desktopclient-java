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

import java.util.Date;
import java.util.EnumSet;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.crypto.Coder;

/**
 * Model for a XMPP message that we are sending.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class OutMessage extends KonMessage {
    private final static Logger LOGGER = Logger.getLogger(OutMessage.class.getName());

    OutMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void setStatus(Status status) {
        if (status == Status.SENT && mReceiptStatus != Status.PENDING)
            LOGGER.warning("unexpected new status of sent message: "+status);
        if (status == Status.RECEIVED && mReceiptStatus != Status.SENT)
            LOGGER.warning("unexpected new status of received message: "+status);
        mReceiptStatus = status;
        this.save();
        this.changed();
    }

public static class Builder extends KonMessage.Builder {

        public Builder(KonThread thread, User user, boolean encrypted) {
            super(-1, thread, Direction.OUT, user);

            mJID = user.getJID();
            mXMPPID = "Kon_" + StringUtils.randomString(8);
            mDate = new Date();
            mReceiptStatus = Status.PENDING;

            mCoderStatus = new CoderStatus(
                // outgoing messages are never saved encrypted
                encrypted ? Coder.Encryption.DECRYPTED : Coder.Encryption.NOT,
                // if we want encryption we also want signing, doesn't hurt
                encrypted ? Coder.Signing.SIGNED : Coder.Signing.NOT,
                // of course, no errors
                EnumSet.noneOf(Coder.Error.class)
            );
        }

        @Override
        public void jid(String jid) { throw new UnsupportedOperationException(); }
        @Override
        public void xmppID(String xmppID) { throw new UnsupportedOperationException(); }

        @Override
        public void date(Date date) { throw new UnsupportedOperationException(); }
        @Override
        public void receiptStatus(Status status) { throw new UnsupportedOperationException(); }

        @Override
        public void coderStatus(CoderStatus c) { throw new UnsupportedOperationException(); }

        @Override
        public OutMessage build() {
            return new OutMessage(this);
        }
    }

}
