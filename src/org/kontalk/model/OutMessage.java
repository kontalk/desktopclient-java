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

    OutMessage(KonThread thread,
            User user,
            String text,
            boolean encrypted) {
        super(thread,
                Direction.OUT,
                new Date(),
                text,
                user,
                user.getJID(),
                Packet.nextID(),
                Status.PENDING,
                encrypted);

        mReceiptID = null;
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