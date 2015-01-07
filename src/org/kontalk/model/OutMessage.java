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
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class OutMessage extends KonMessage {

    OutMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void updateByStatus(Status status) {
        // TODO
//        if (status == Status.SENT)
//            assert mReceiptStatus == Status.PENDING;
//        if (status == Status.RECEIVED)
//            assert mReceiptStatus == Status.SENT;
        mReceiptStatus = status;
        this.save();
        this.changed();
    }

public static class Builder extends KonMessage.Builder {

        public Builder(KonThread thread, User user, boolean encrypted) {
            super(-1, thread, Direction.OUT, user);

            mJID = user.getJID();
            mXMPPID = StringUtils.randomString(6);
            mDate = new Date();
            mReceiptStatus = Status.PENDING;

            // outgoing messages are never saved encrypted
            mEncryption = encrypted ? Coder.Encryption.DECRYPTED : Coder.Encryption.NOT;
            // if we want encryption we also want signing, doesn't hurt
            mSigning = encrypted ? Coder.Signing.SIGNED : Coder.Signing.NOT;

            mCoderErrors = EnumSet.noneOf(Coder.Error.class);
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
        public void encryption(Coder.Encryption encryption) { throw new UnsupportedOperationException(); }
        @Override
        public void signing(Coder.Signing signing) { throw new UnsupportedOperationException(); }
        @Override
        public void coderErrors(EnumSet<Coder.Error> coderErrors) { throw new UnsupportedOperationException(); }

        @Override
        public OutMessage build() {
            return new OutMessage(this);
        }
    }

}
