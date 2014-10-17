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
import org.jivesoftware.smack.packet.Packet;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class OutMessage extends KonMessage{

    /**
     * Used when creating new message.
     * TODO change to builder
     * @param thread
     * @param user
     * @param content
     * @param encrypted
     */
    OutMessage(KonThread thread,
            User user,
            MessageContent content,
            boolean encrypted) {
        super(thread,
                Direction.OUT,
                new Date(),
                content,
                user,
                user.getJID(),
                Packet.nextID(),
                Status.PENDING,
                "",
                encrypted);
    }

    /**
     * Used when loading from database
     */
    OutMessage(KonMessage.Builder builder) {
        super(builder);
    }

    void updateBySentReceipt(String receiptID) {
        assert mReceiptStatus == Status.PENDING;
        assert mReceiptID.isEmpty();
        mReceiptID = receiptID;
        mReceiptStatus = Status.SENT;
        this.save();
        this.setChanged();
        this.notifyObservers();
    }

    void updateByReceivedReceipt() {
        assert mReceiptStatus == Status.SENT;
        assert !mReceiptID.isEmpty();
        mReceiptStatus = Status.RECEIVED;
        this.save();
        this.setChanged();
        this.notifyObservers();
    }

}
