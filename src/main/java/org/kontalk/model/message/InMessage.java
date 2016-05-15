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

import java.util.Arrays;
import org.kontalk.model.chat.Chat;
import org.kontalk.misc.JID;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.kontalk.crypto.Coder;
import org.kontalk.model.Contact;
import org.kontalk.model.message.MessageContent.Attachment;
import org.kontalk.model.message.MessageContent.Preview;

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
    protected InMessage(KonMessage.Builder builder) {
        super(builder);

        if (builder.mTransmissions.size() != 1)
            throw new IllegalArgumentException("builder does not contain one transmission");

        mTransmission = builder.mTransmissions.stream().findAny().get();
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
    public void setDecryptedContent(MessageContent decryptedContent) {
        mContent.setDecryptedContent(decryptedContent);
        mCoderStatus.setDecrypted();
        this.save();
        this.changed(decryptedContent);
    }

    public void setAttachmentFileName(String fileName) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.setFile(fileName);
        this.save();
        // only tell view if file not encrypted
        if (!attachment.getCoderStatus().isEncrypted())
            this.changed(attachment);
     }

    public void setAttachmentSigning(Coder.Signing signing) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.getCoderStatus().setSigning(signing);
        this.save();
    }

    public void setAttachmentDownloadProgress(int p) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.setDownloadProgress(p);
        if (p <= 0)
            this.changed(attachment);
    }

    public void setDecryptedAttachment(String filename) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.setDecryptedFile(filename);
        this.save();
        this.changed(attachment);
    }

    public void setPreviewFilename(String filename) {
        Preview preview = this.getContent().getPreview().orElse(null);
        if (preview == null) {
            LOGGER.warning("no preview !?");
            return;
        }
        preview.setFilename(filename);
        this.save();
        this.changed(preview);
    }

    @Override
    public Set<Transmission> getTransmissions() {
        return new HashSet<>(Arrays.asList(mTransmission));
    }

    @Override
    public boolean equals(Object o) {
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
        int hash = this.abstractHashCode();
        hash = 67 * hash + Objects.hashCode(this.mTransmission);
        return hash;
    }
}
