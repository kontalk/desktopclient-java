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

package org.kontalk.model.message;

import java.util.EnumSet;
import org.kontalk.crypto.Coder;
import org.kontalk.model.Contact;

/**
 * An incoming message not saved to database for decryption.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ProtoMessage implements DecryptMessage {

    private final Contact mContact;
    private final CoderStatus mCoderStatus;
    private final MessageContent mContent;

    public ProtoMessage(Contact contact, MessageContent content) {
        mContact = contact;
        mContent = content;

        boolean encrypted = !content.getEncryptedContent().isEmpty();
        mCoderStatus = new CoderStatus(
            // no decryption attempt yet
            encrypted ? Coder.Encryption.ENCRYPTED : Coder.Encryption.NOT,
            // if encrypted we don't know yet
            encrypted ? Coder.Signing.UNKNOWN : Coder.Signing.NOT,
            // no errors
            EnumSet.noneOf(Coder.Error.class)
        );
    }

    @Override
    public Contact getContact() {
        return mContact;
    }

    public CoderStatus getCoderStatus() {
        return mCoderStatus;
    }

    @Override
    public boolean isEncrypted() {
        return mCoderStatus.isEncrypted();
    }

    @Override
    public MessageContent getContent() {
        return mContent;
    }

    @Override
    public void setDecryptedContent(MessageContent content) {
        mContent.setDecryptedContent(content);
        mCoderStatus.setDecrypted();
    }

    @Override
    public void setSigning(Coder.Signing signing) {
        mCoderStatus.setSigning(signing);
    }

    @Override
    public void setSecurityErrors(EnumSet<Coder.Error> errors) {
        mCoderStatus.setSecurityErrors(errors);
    }

}
