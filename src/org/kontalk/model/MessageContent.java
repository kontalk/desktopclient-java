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
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.crypto.Coder;
import org.kontalk.util.EncodingUtils;

/**
 * All possible content a message can contain.
 * Recursive: A message can contain a decrypted message.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MessageContent {
    private final static Logger LOGGER = Logger.getLogger(MessageContent.class.getName());

    // plain message text, empty string if not present
    private final String mPlainText;
    // attachment a.k.a. file url in plaintext
    private final Optional<Attachment> mOptAttachment;
    // encrypted content, empty string if not present
    private String mEncryptedContent;
    // decrypted message content
    private Optional<MessageContent> mOptDecryptedContent;

    private final static String JSON_PLAIN_TEXT = "plain_text";
    private final static String JSON_ATTACHMENT = "attachment";
    private final static String JSON_ENC_CONTENT = "encrypted_content";
    private final static String JSON_DEC_CONTENT = "decrypted_content";

    public MessageContent(String plainText) {
        this(plainText, Optional.<Attachment>empty(), "");
    }

    public MessageContent(String plainText,
            Optional<Attachment> optAttachment,
            String encryptedContent) {
        this(
                plainText,
                optAttachment,
                encryptedContent,
                Optional.<MessageContent>empty()
        );
    }

    private MessageContent(String plainText,
            Optional<Attachment> optAttachment,
            String encryptedContent,
            Optional<MessageContent> optDecryptedContent) {
        mPlainText = plainText;
        mOptAttachment = optAttachment;
        mEncryptedContent = encryptedContent;
        mOptDecryptedContent = optDecryptedContent;
    }

    /**
     * Get encrypted or plain text content.
     * @return encrypted content if present, else plain text. If there is no
     * plain text either return an empty string.
     */
    public String getText() {
        if (mOptDecryptedContent.isPresent())
            return mOptDecryptedContent.get().getPlainText();
        else
            return mPlainText;
    }

    public String getPlainText() {
        return mPlainText;
    }

    public Optional<Attachment> getAttachment() {
        if (mOptDecryptedContent.isPresent()) {
            if (mOptDecryptedContent.get().getAttachment().isPresent()) {
                return mOptDecryptedContent.get().getAttachment();
            }
        }
        return mOptAttachment;
    }

    public String getEncryptedContent() {
        return mEncryptedContent;
    }

    public void setDecryptedContent(MessageContent decryptedContent) {
        assert !mOptDecryptedContent.isPresent();
        mOptDecryptedContent = Optional.of(decryptedContent);
        // deleting encrypted data!
        mEncryptedContent = "";
    }

    /**
     * Return if there is no content in this message.
     * @return true if there is no content at all, false otherwise
     */
    public boolean isEmpty() {
        return mPlainText.isEmpty() &&
                !mOptAttachment.isPresent() &&
                mEncryptedContent.isEmpty();
    }

    @Override
    public String toString() {
        return "CONT:plain="+mPlainText+",att="+mOptAttachment
                +",encr="+mEncryptedContent+",decr="+mOptDecryptedContent;
    }

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    String toJSONString() {
        JSONObject json = new JSONObject();
        json.put(JSON_PLAIN_TEXT, mPlainText);
        json.put(JSON_ATTACHMENT, mOptAttachment.isPresent() ?
                mOptAttachment.get().toJSONString() :
                null);
        json.put(JSON_ENC_CONTENT, mEncryptedContent);
        json.put(JSON_DEC_CONTENT, mOptDecryptedContent.isPresent() ?
                mOptDecryptedContent.get().toJSONString() :
                null);
        return json.toJSONString();
    }

    static MessageContent fromJSONString(String jsonContent) {
        Object obj = JSONValue.parse(jsonContent);
        try {
            Map<?, ?> map = (Map) obj;
            String plainText = (String) map.get(JSON_PLAIN_TEXT);
            String jsonAttachment = (String) map.get(JSON_ATTACHMENT);
            Optional<Attachment> optAttachment = jsonAttachment == null ?
                    Optional.<Attachment>empty() :
                    Attachment.fromJSONString(jsonAttachment);

            String encryptedContent = (String) map.get(JSON_ENC_CONTENT);
            String jsonDecryptedContent = (String) map.get(JSON_DEC_CONTENT);
            Optional<MessageContent> decryptedContent = jsonDecryptedContent == null ?
                    Optional.<MessageContent>empty() :
                    Optional.of(fromJSONString(jsonDecryptedContent));
            return new MessageContent(plainText,
                    optAttachment,
                    encryptedContent,
                    decryptedContent);
        } catch(ClassCastException ex) {
            LOGGER.log(Level.WARNING, "can't parse JSON message content", ex);
            return new MessageContent("");
        }
    }

    public static class Attachment {
        // URL to file, empty string by default
        private final String mURL;
        // MIME of file, empty string by default
        private final String mMimeType;
        // size of (decrypted) file, -1 by default
        private final long mLength;
        // file name of downloaded and encrypted file, empty string by default
        private String mFileName;
        // coder status of file encryption
        private final CoderStatus mCoderStatus;
        // progress downloaded of (encrypted) file in percent
        // no download/default: -1; unknown size: -2; download abort: -3
        private int mDownloadProgress = -1;

        private final static String JSON_URL = "url";
        private final static String JSON_MIME_TYPE = "mime_type";
        private final static String JSON_LENGTH = "length";
        private final static String JSON_FILE_NAME = "file_name";
        private final static String JSON_ENCRYPTION = "encryption";
        private final static String JSON_SIGNING = "signing";
        private final static String JSON_CODER_ERRORS = "coder_errors";

        // used for incoming attachments
        public Attachment(String url,
                String mimeType,
                long length,
                boolean encrypted) {
            this(
                    url,
                    mimeType,
                    length,
                    "",
                    new CoderStatus(
                        encrypted ? Coder.Encryption.ENCRYPTED : Coder.Encryption.NOT,
                        encrypted ? Coder.Signing.UNKNOWN : Coder.Signing.NOT,
                        EnumSet.noneOf(Coder.Error.class)
                    )
            );
        }

        // used when loading from database.
        private Attachment(String url,
                String mimeType,
                long length,
                String fileName,
                CoderStatus coderStatus)  {
            mURL = url;
            mMimeType = mimeType;
            mLength = length;
            mFileName = fileName;
            mCoderStatus = coderStatus;
        }

        public String getURL() {
            return mURL;
        }

        public String getMimeType() {
            return mMimeType;
        }

       /**
        * Return name of file or empty string if file wasn't downloaded yet.
        */
        public String getFileName() {
            return mFileName;
        }

        void setFileName(String fileName) {
            mFileName = fileName;
        }

        public void setDecryptedFilename(String fileName) {
            mCoderStatus.setDecrypted();
            mFileName = fileName;
        }

        public CoderStatus getCoderStatus() {
            return mCoderStatus;
        }

        void setDownloadProgress(int p) {
            mDownloadProgress = p;
        }

        @Override
        public String toString() {
            return "ATT:url="+mURL+",mime="+mMimeType+",file="+mFileName
                    +",status="+mCoderStatus;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private String toJSONString() {
            JSONObject json = new JSONObject();
            json.put(JSON_URL, mURL);
            json.put(JSON_MIME_TYPE, mMimeType);
            json.put(JSON_LENGTH, mLength);
            json.put(JSON_FILE_NAME, mFileName);
            json.put(JSON_ENCRYPTION, mCoderStatus.getEncryption().ordinal());
            json.put(JSON_SIGNING, mCoderStatus.getSigning().ordinal());
            int errs = EncodingUtils.enumSetToInt(mCoderStatus.getErrors());
            json.put(JSON_CODER_ERRORS, errs);
            return json.toJSONString();
        }

        static Optional<Attachment> fromJSONString(String jsonAttachment) {
            Object obj = JSONValue.parse(jsonAttachment);
            try {
                Map<?, ?> map = (Map) obj;

                String url = (String) map.get(JSON_URL);
                assert url != null;

                String mimeType = (String) map.get(JSON_MIME_TYPE);
                if (mimeType == null) mimeType = "";

                long length = ((Number) map.get(JSON_LENGTH)).longValue();

                String fileName = (String) map.get(JSON_FILE_NAME);
                if (fileName == null) fileName = "";

                Number enc = (Number) map.get(JSON_ENCRYPTION);
                Coder.Encryption encryption = Coder.Encryption.values()[enc.intValue()];

                Number sig = (Number) map.get(JSON_SIGNING);
                Coder.Signing signing = Coder.Signing.values()[sig.intValue()];

                Number err = ((Number) map.get(JSON_CODER_ERRORS));
                EnumSet<Coder.Error> errors = EncodingUtils.intToEnumSet(Coder.Error.class, err.intValue());

                Attachment a = new Attachment(
                        url,
                        mimeType,
                        length,
                        fileName,
                        new CoderStatus(encryption, signing, errors));
                return Optional.of(a);
            } catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON attachment", ex);
                return Optional.empty();
            }
        }
    }
}
