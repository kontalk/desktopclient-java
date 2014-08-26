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
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class InMessage extends KonMessage {
    private final static Logger LOGGER = Logger.getLogger(InMessage.class.getName());

    /**
     * Used when receiving a new message
     */
    InMessage(KonThread thread,
            User user,
            String jid,
            String xmppID,
            Date date,
            String receiptID,
            String text,
            boolean encrypted) {
        super(thread, Direction.IN, date, text, user, jid, xmppID);

        Coder.Encryption encryption = encrypted ? Coder.Encryption.ENCRYPTED
                : Coder.Encryption.NOT;
        // if encrypted we don't know yet
        Coder.Signing signing = encrypted ? null : Coder.Signing.NOT;

        mReceiptStatus = Status.IN;
        mReceiptID = receiptID;

        mEncryption = encryption;
        mSigning = signing;
    }

    public void setDecryptedText(String text) {
        assert mEncryption == Coder.Encryption.ENCRYPTED;
        mText = text;
        mEncryption = Coder.Encryption.DECRYPTED;
        super.save();
    }

}
