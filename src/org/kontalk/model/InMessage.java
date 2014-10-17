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
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class InMessage extends KonMessage {

    /**
     * Used when receiving a new message.
     * TODO change to builder
     */
    InMessage(KonThread thread,
            User user,
            String jid,
            String xmppID,
            Date date,
            String receiptID,
            MessageContent content) {
        super(thread,
                Direction.IN,
                date,
                content,
                user,
                jid,
                xmppID,
                Status.IN,
                receiptID,
                !content.getEncryptedContent().isEmpty());
    }

    /**
     * Used when loading from database
     */
    InMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void setDecryptedContent(MessageContent decryptedContent) {
        assert mEncryption == Coder.Encryption.ENCRYPTED;
        mContent.setDecryptedContent(decryptedContent);
        mEncryption = Coder.Encryption.DECRYPTED;
        super.save();
    }

}
