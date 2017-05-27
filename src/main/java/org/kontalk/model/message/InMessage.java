/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.kontalk.crypto.Coder;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.Chat;

/**
 * Model for an XMPP message sent to the user.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class InMessage extends KonMessage implements DecryptMessage {
    private static final Logger LOGGER = Logger.getLogger(InMessage.class.getName());

    private final Transmission mTransmission;

    public InMessage(ProtoMessage proto, Chat chat, JID from,
            String xmppID, Optional<Date> serverDate) {
        super(
                chat,
                xmppID,
                proto.getContent(),
                serverDate,
                Status.IN,
                proto.getCoderStatus());

        mTransmission = new Transmission(proto.getContact(), from, mID);
    }

    // used when loading from database
    InMessage(KonMessage.Builder builder) {
        super(builder);

        if (builder.mTransmissions.size() != 1)
            throw new IllegalArgumentException("builder does not contain one transmission");

        mTransmission = builder.mTransmissions.iterator().next();
    }

    @Override
    public Contact getContact() {
        return mTransmission.getContact();
    }

    public JID getJID() {
        return mTransmission.getJID();
    }

    @Override
    public void setSigning(Coder.Signing signing) {
        mCoderStatus.setSigning(signing);
        this.save();
    }

    @Override
    public String getEncryptedContent() {
        return mContent.getEncryptedContent();
    }

    @Override
    public void setDecryptedContent(MessageContent decryptedContent) {
        mContent.setDecryptedContent(decryptedContent);
        mCoderStatus.setDecrypted();
        this.save();
        this.changed(ViewChange.CONTENT);
    }

    @Override
    public Set<Transmission> getTransmissions() {
        return new HashSet<>(Collections.singletonList(mTransmission));
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof InMessage))
            return false;

        InMessage oMessage = (InMessage) o;
        return this.abstractEquals(oMessage) &&
                mTransmission.equals(oMessage.mTransmission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTransmission);
    }
}
