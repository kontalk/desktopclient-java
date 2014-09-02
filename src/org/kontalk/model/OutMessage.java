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
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Packet;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class OutMessage extends KonMessage{
    private final static Logger LOGGER = Logger.getLogger(OutMessage.class.getName());

    OutMessage(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        super(thread, Direction.OUT, new Date(), text, user, user.getJID(), Packet.nextID());

        mReceiptStatus = KonMessage.Status.PENDING;
        mReceiptID = null;

        // if we want encryption we also want signing, doesn't hurt
        if (encrypted) {
            mEncryption = Coder.Encryption.ENCRYPTED;
            mSigning = Coder.Signing.SIGNED;
        } else {
            mEncryption = Coder.Encryption.NOT;
            mSigning = Coder.Signing.NOT;
        }
    }

    void updateBySentReceipt(String receiptID) {
        assert mReceiptStatus == Status.PENDING;
        assert mReceiptID == null;
        mReceiptID = receiptID;
        mReceiptStatus = Status.SENT;
        this.save();
        this.setChanged();
        this.notifyObservers();
    }

    void updateByReceivedReceipt() {
        assert mReceiptStatus == Status.SENT;
        assert mReceiptID != null;
        mReceiptStatus = Status.RECEIVED;
        this.save();
        this.setChanged();
        this.notifyObservers();
    }

}
