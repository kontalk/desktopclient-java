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
import java.util.Optional;
import java.util.logging.Logger;
import org.kontalk.crypto.Coder;
import org.kontalk.model.MessageContent.Attachment;

/**
 * Model for a XMPP message that was sent to us.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class InMessage extends KonMessage {
    private final static Logger LOGGER = Logger.getLogger(KonMessage.class.getName());

    /**
     * Create a new incoming message from builder.
     * The message is not saved to database!
     */
    InMessage(KonMessage.Builder builder) {
        super(builder);
    }

    public void setSigning(Coder.Signing signing) {
        mCoderStatus.setSigning(signing);
        this.save();
    }

    public void setDecryptedContent(MessageContent decryptedContent) {
        mContent.setDecryptedContent(decryptedContent);
        mCoderStatus.setDecrypted();
        this.save();
        this.changed(null);
    }

    public void setAttachmentFileName(String fileName) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.setFileName(fileName);
        this.save();
        // only tell view if file not encrypted
        if (!attachment.getCoderStatus().isEncrypted())
            this.changed(attachment);
     }

    public void setAttachmentErrors(EnumSet<Coder.Error> errors) {
        Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.getCoderStatus().setSecurityErrors(errors);
        this.save();
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

        attachment.setDecryptedFilename(filename);
        this.save();
        this.changed(attachment);
    }

    private Attachment getAttachment() {
        Optional<Attachment> optAttachment = this.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment!?");
            return null;
        }
        return optAttachment.get();
    }

    public static class Builder extends KonMessage.Builder {

        public Builder(KonThread thread, User user) {
            super(-1, thread, Direction.IN, user);

            mReceiptStatus = Status.IN;
        }

        @Override
        public void content(MessageContent content) {
            super.content(content);

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
        public void receiptStatus(Status s) { throw new UnsupportedOperationException(); }

        @Override
        public void coderStatus(CoderStatus c) { throw new UnsupportedOperationException(); }

        @Override
        public InMessage build() {
            return new InMessage(this);
        }
    }

}
