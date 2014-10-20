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

import java.util.EnumSet;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class InMessage extends KonMessage {

    InMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void setDecryptedContent(MessageContent decryptedContent) {
        assert mEncryption == Coder.Encryption.ENCRYPTED;
        mContent.setDecryptedContent(decryptedContent);
        mEncryption = Coder.Encryption.DECRYPTED;
        super.save();
    }

    static class Builder extends KonMessage.Builder {

        Builder(KonThread thread, User user) {
            super(-1, thread, Direction.IN, user);

            mReceiptStatus = Status.IN;

            mCoderErrors = EnumSet.noneOf(Coder.Error.class);
        }

        @Override
        void content(MessageContent content) {
            super.content(content);

            boolean encrypted = !content.getEncryptedContent().isEmpty();
            // no decryption attempt yet
            mEncryption = encrypted ? Coder.Encryption.ENCRYPTED : Coder.Encryption.NOT;
            // if encrypted we don't know yet
            mSigning = encrypted ? Coder.Signing.UNKNOWN : Coder.Signing.NOT;
        }

        @Override
        void receiptStatus(Status status) { throw new UnsupportedOperationException(); }
        @Override
        void encryption(Coder.Encryption encryption) { throw new UnsupportedOperationException(); }
        @Override
        void signing(Coder.Signing signing) { throw new UnsupportedOperationException(); }
        @Override
        void coderErrors(EnumSet<Coder.Error> coderErrors) { throw new UnsupportedOperationException(); }

        @Override
        InMessage build() {
            return new InMessage(this);
        }
    }

}
