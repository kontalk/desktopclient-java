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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * All possible content a message can contain.
 * Recursive: A message can contain a decrypted message.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MessageContent {
    private final static Logger LOGGER = Logger.getLogger(MessageContent.class.getName());

    // plain message text, empty string if not present
    private final String mPlainText;
    // plain file URL, can be null
    private final Attachment mAttachment;
    // encrypted content, empty string if not present
    private String mEncryptedContent;
    // decrypted message, can be null
    private MessageContent mDecryptedContent;

    private final static String JSON_PLAIN_TEXT = "plain_text";
    private final static String JSON_ATTACHMENT = "attachment";
    private final static String JSON_ENC_CONTENT = "encrypted_content";
    private final static String JSON_DEC_CONTENT = "decrypted_content";

    public MessageContent(String plainText) {
        this(plainText, null, "");
    }

    public MessageContent(String plainText,
            Attachment attachment,
            String encryptedContent) {
        mPlainText = plainText;
        mAttachment = attachment;
        mEncryptedContent = encryptedContent;
    }

    private MessageContent(String plainText,
            Attachment attachment,
            String encryptedContent,
            MessageContent decryptedContent) {
        mPlainText = plainText;
        mAttachment = attachment;
        mEncryptedContent = encryptedContent;
        mDecryptedContent = decryptedContent;
    }

    /**
     * Get encrypted or plain text content.
     * @return encrypted content if present, else plain text. If there is no
     * plain text either return an empty string.
     */
    public String getText() {
        // TODO recursive
        if (mDecryptedContent != null)
            return mDecryptedContent.getText();
        else
            return mPlainText;
    }

    public String getPlainText() {
        return mPlainText;
    }

    // TODO nullable
    public Attachment getAttachment() {
        // possible attachment in encrypted content is ignored
        return mAttachment;
    }

    public String getEncryptedContent() {
        return mEncryptedContent;
    }

    public void setDecryptedContent(MessageContent decryptedContent) {
        assert mDecryptedContent == null;
        mDecryptedContent = decryptedContent;
        // deleting encrypted data!
        mEncryptedContent = "";
    }

    public boolean isEmpty() {
        return mPlainText.isEmpty() &&
                mAttachment == null &&
                mEncryptedContent.isEmpty();
    }

    String toJSONString() {
        JSONObject json = new JSONObject();
        json.put(JSON_PLAIN_TEXT, mPlainText);
        json.put(JSON_ATTACHMENT, mAttachment != null ? mAttachment.toJSONString() : null);
        json.put(JSON_ENC_CONTENT, mEncryptedContent);
        json.put(JSON_DEC_CONTENT, mDecryptedContent != null ? mDecryptedContent.toJSONString() : null);
        return json.toJSONString();
    }

    static MessageContent fromJSONString(String jsonContent) {
        Object obj = JSONValue.parse(jsonContent);
        try {
            Map map = (Map) obj;
            String plainText = (String) map.get(JSON_PLAIN_TEXT);
            String jsonAttachment = (String) map.get(JSON_ATTACHMENT);
            Attachment attachment = jsonAttachment == null ?
                    null :
                    Attachment.fromJSONString(jsonAttachment);
            String encryptedContent = (String) map.get(JSON_ENC_CONTENT);
            String jsonDecryptedContent = (String) map.get(JSON_DEC_CONTENT);
            MessageContent decryptedContent = jsonDecryptedContent == null ?
                    null :
                    fromJSONString(jsonDecryptedContent);
            return new MessageContent(plainText,
                    attachment,
                    encryptedContent,
                    decryptedContent);
        } catch(ClassCastException ex) {
            LOGGER.log(Level.WARNING, "can't parse JSON message content", ex);
            return new MessageContent("");
        }
    }

    public static class Attachment {
        private final String mURL;
        private final String mMimeType;
        private final long mLength;
        private final boolean mEncrypted;
        private String mFileName;

        private final static String JSON_URL = "url";
        private final static String JSON_MIME_TYPE = "mime_type";
        private final static String JSON_LENGTH = "length";
        private final static String JSON_ENCRYPTED = "encrypted";
        private final static String JSON_FILE_NAME = "file_name";

        // used for incoming attachments
        public Attachment(String url,
                String mimeType,
                long length,
                boolean encrypted) {
            this(url, mimeType, length, encrypted, "");
        }

        // used when loading from database.
        public Attachment(String url,
                String mimeType,
                long length,
                boolean encrypted,
                String fileName)  {
            // URL to file, empty string by default
            mURL = url;
            // MIME of file, empty string by default
            mMimeType = mimeType;
            // size of file, -1 by default
            mLength = length;
            // is file encrypted? false by default
            mEncrypted = encrypted;
            // file name of downloaded and encrypted file, empty string by default
            mFileName = fileName;
        }

        public String getURL() {
            return mURL;
        }

        public boolean isEncrypted() {
            return mEncrypted;
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

        private String toJSONString() {
            JSONObject json = new JSONObject();
            json.put(JSON_URL, mURL);
            json.put(JSON_MIME_TYPE, mMimeType);
            json.put(JSON_LENGTH, mLength);
            json.put(JSON_ENCRYPTED, mEncrypted);
            json.put(JSON_FILE_NAME, mFileName);
            return json.toJSONString();
        }

        // TODO nullable
        static Attachment fromJSONString(String jsonAttachment) {
            Object obj = JSONValue.parse(jsonAttachment);
            try {
                Map map = (Map) obj;
                String url = (String) map.get(JSON_URL);
                assert url != null;
                String mimeType = (String) map.get(JSON_MIME_TYPE);
                if (mimeType == null) mimeType = "";
                long length = (Long) map.get(JSON_LENGTH);
                boolean encrypted = (Boolean) map.get(JSON_ENCRYPTED);
                String fileName = (String) map.get(JSON_FILE_NAME);
                if (fileName == null) fileName = "";
                return new Attachment(url, mimeType, length, encrypted, fileName);
            } catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON file url", ex);
                return null;
            }
        }
    }
}
