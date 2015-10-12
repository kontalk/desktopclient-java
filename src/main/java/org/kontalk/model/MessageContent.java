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

import org.kontalk.misc.JID;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.crypto.Coder;
import org.kontalk.model.Chat.GID;
import org.kontalk.util.EncodingUtils;

/**
 * All possible content a message can contain.
 * Recursive: A message can contain a decrypted message.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class MessageContent {
    private static final Logger LOGGER = Logger.getLogger(MessageContent.class.getName());

    // plain message text, empty string if not present
    private final String mPlainText;
    // encrypted content, empty string if not present
    private String mEncryptedContent;
    // attachment (file url, path and metadata)
    private final Optional<Attachment> mOptAttachment;
    // small preview file of attachment
    private Optional<Preview> mOptPreview;
    // group command
    private final Optional<GroupCommand> mOptGroupCommand;
    // decrypted message content
    private Optional<MessageContent> mOptDecryptedContent;

    private static final String JSON_PLAIN_TEXT = "plain_text";
    private static final String JSON_ENC_CONTENT = "encrypted_content";
    private static final String JSON_ATTACHMENT = "attachment";
    private static final String JSON_PREVIEW = "preview";
    private static final String JSON_GROUP_COMMAND = "group_command";
    private static final String JSON_DEC_CONTENT = "decrypted_content";

    // used for decrypted content of incoming messages, outgoing messages
    // and as fallback
    public MessageContent(String plainText) {
        this(plainText, "", null, null, null);
    }

    // used for outgoing messages
    public MessageContent(String plainText, Attachment attachment) {
        this(plainText, "", attachment, null, null);
    }

    // used for outgoing group commands
    public MessageContent(GroupCommand group) {
        this("", "", null, null, group);
    }

    // used for incoming messages
    public MessageContent(String plainText, String encrypted,
            Attachment attachment, Preview preview, GroupCommand group) {
        this(plainText, encrypted, attachment, preview, group, null);
    }

    // used when loading from db
    private MessageContent(String plainText, String encrypted,
            Attachment attachment, Preview preview, GroupCommand group,
            MessageContent decryptedContent) {
        mPlainText = plainText;
        mEncryptedContent = encrypted;
        mOptAttachment = Optional.ofNullable(attachment);
        mOptPreview = Optional.ofNullable(preview);
        mOptGroupCommand = Optional.ofNullable(group);
        mOptDecryptedContent = Optional.ofNullable(decryptedContent);
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
        if (mOptDecryptedContent.isPresent() &&
                mOptDecryptedContent.get().getAttachment().isPresent()) {
            return mOptDecryptedContent.get().getAttachment();
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

    public Optional<Preview> getPreview() {
        if (mOptDecryptedContent.isPresent() &&
                mOptDecryptedContent.get().getPreview().isPresent()) {
            return mOptDecryptedContent.get().getPreview();
        }
        return mOptPreview;
    }

    void setPreview(Preview preview) {
        if (mOptPreview.isPresent()) {
            LOGGER.warning("preview already present, not overwriting");
            return;
        }
        mOptPreview = Optional.of(preview);
    }

    public Optional<GroupCommand> getGroupCommand() {
        if (mOptDecryptedContent.isPresent() &&
                mOptDecryptedContent.get().getGroupCommand().isPresent()) {
            return mOptDecryptedContent.get().getGroupCommand();
        }
        return mOptGroupCommand;
    }

    /**
     * Return if there is no content in this message.
     * @return true if there is no content at all, false otherwise
     */
    public boolean isEmpty() {
        return mPlainText.isEmpty() &&
                mEncryptedContent.isEmpty() &&
                !mOptAttachment.isPresent() &&
                !mOptPreview.isPresent() &&
                !mOptDecryptedContent.isPresent();
    }

    public boolean isComplex() {
        return mOptAttachment.isPresent() || mOptGroupCommand.isPresent();
    }

    @Override
    public String toString() {
        return "CONT:plain="+mPlainText+",encr="+mEncryptedContent
                +",att="+mOptAttachment+",gc="+mOptGroupCommand
                +",decr="+mOptDecryptedContent;
    }

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    String toJSON() {
        JSONObject json = new JSONObject();

        EncodingUtils.putJSON(json, JSON_PLAIN_TEXT, mPlainText);

        if (mOptAttachment.isPresent())
            json.put(JSON_ATTACHMENT, mOptAttachment.get().toJSONString());

        EncodingUtils.putJSON(json, JSON_ENC_CONTENT, mEncryptedContent);

        if (mOptPreview.isPresent())
            json.put(JSON_PREVIEW, mOptPreview.get().toJSON());

        if (mOptGroupCommand.isPresent())
            json.put(JSON_GROUP_COMMAND, mOptGroupCommand.get().toJSON());

        if (mOptDecryptedContent.isPresent())
            json.put(JSON_DEC_CONTENT, mOptDecryptedContent.get().toJSON());

        return json.toJSONString();
    }

    static MessageContent fromJSONString(String jsonContent) {
        Object obj = JSONValue.parse(jsonContent);
        try {
            Map<?, ?> map = (Map) obj;

            String plainText = EncodingUtils.getJSONString(map, JSON_PLAIN_TEXT);

            String encrypted = EncodingUtils.getJSONString(map, JSON_ENC_CONTENT);

            String att = (String) map.get(JSON_ATTACHMENT);
            Attachment attachment = att == null ? null : Attachment.fromJSONOrNull(att);

            String pre = (String) map.get(JSON_PREVIEW);
            Preview preview = pre == null ? null : Preview.fromJSONOrNull(pre);

            String gc = (String) map.get(JSON_GROUP_COMMAND);
            GroupCommand groupCommand = gc == null ? null : GroupCommand.fromJSONOrNull(gc);

            String jsonDecryptedContent = (String) map.get(JSON_DEC_CONTENT);
            MessageContent decryptedContent = jsonDecryptedContent == null ?
                    null :
                    fromJSONString(jsonDecryptedContent);

            return new MessageContent(plainText,
                    encrypted,
                    attachment,
                    preview,
                    groupCommand,
                    decryptedContent);
        } catch(ClassCastException ex) {
            LOGGER.log(Level.WARNING, "can't parse JSON message content", ex);
            return new MessageContent("");
        }
    }

    public static class Attachment {

        private static final String JSON_URL = "url";
        private static final String JSON_MIME_TYPE = "mime_type";
        private static final String JSON_LENGTH = "length";
        private static final String JSON_FILENAME = "file_name";
        private static final String JSON_ENCRYPTION = "encryption";
        private static final String JSON_SIGNING = "signing";
        private static final String JSON_CODER_ERRORS = "coder_errors";

        // URL for file download, empty string by default
        private URI mURL;
        // file name of downloaded file or path to upload file, empty by default
        private Path mFile;
        // MIME of file, empty string by default
        private final String mMimeType;
        // size of (decrypted) file in bytes, -1 by default
        private final long mLength;
        // coder status of file encryption
        private final CoderStatus mCoderStatus;
        // progress downloaded of (encrypted) file in percent
        private int mDownloadProgress = -1;

        // used for outgoing attachments
        public Attachment(Path path, String mimeType, long length) {
            this(URI.create(""), path, mimeType, length,
                    CoderStatus.createInsecure());
        }

        // used for incoming attachments
        public Attachment(URI url, String mimeType, long length,
                boolean encrypted) {
            this(url, Paths.get(""), mimeType, length,
                    encrypted ? CoderStatus.createEncrypted() :
                            CoderStatus.createInsecure()
            );
        }

        // used when loading from database.
        private Attachment(URI url, Path file,
                String mimeType, long length,
                CoderStatus coderStatus)  {
            mURL = url;
            mFile = file;
            mMimeType = mimeType;
            mLength = length;
            mCoderStatus = coderStatus;
        }

        public boolean hasURL() {
            return !mURL.toString().isEmpty();
        }

        public URI getURL() {
            return mURL;
        }

        public void setURL(URI url){
            mURL = url;
        }

        public String getMimeType() {
            return mMimeType;
        }

        public long getLength() {
            return mLength;
        }

       /**
        * Return the filename (download) or path to the local file (upload).
        */
        public Path getFile() {
            return mFile;
        }

        void setFile(String fileName) {
            mFile = Paths.get(fileName);
        }

        void setDecryptedFile(String fileName) {
            mCoderStatus.setDecrypted();
            mFile = Paths.get(fileName);
        }

        public CoderStatus getCoderStatus() {
            return mCoderStatus;
        }

        /** Download progress in percent. <br>
         * -1: no download/default <br>
         *  0: download started... <br>
         * 100: ...download finished <br>
         * -2: unknown size <br>
         * -3: download aborted
         */
        public int getDownloadProgress() {
            return mDownloadProgress;
        }

        /** Set download progress. See .getDownloadProgress() */
        void setDownloadProgress(int p) {
            mDownloadProgress = p;
        }

        @Override
        public String toString() {
            return "{ATT:url="+mURL+",file="+mFile+",mime="+mMimeType
                    +",status="+mCoderStatus+"}";
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private String toJSONString() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_URL, mURL.toString());
            EncodingUtils.putJSON(json, JSON_MIME_TYPE, mMimeType);
            json.put(JSON_LENGTH, mLength);
            EncodingUtils.putJSON(json, JSON_FILENAME, mFile.toString());
            json.put(JSON_ENCRYPTION, mCoderStatus.getEncryption().ordinal());
            json.put(JSON_SIGNING, mCoderStatus.getSigning().ordinal());
            int errs = EncodingUtils.enumSetToInt(mCoderStatus.getErrors());
            json.put(JSON_CODER_ERRORS, errs);
            return json.toJSONString();
        }

        private static Attachment fromJSONOrNull(String json) {
            Object obj = JSONValue.parse(json);
            try {
                Map<?, ?> map = (Map) obj;

                URI url = URI.create(EncodingUtils.getJSONString(map, JSON_URL));

                String mimeType = EncodingUtils.getJSONString(map, JSON_MIME_TYPE);

                long length = ((Number) map.get(JSON_LENGTH)).longValue();

                Path file = Paths.get(EncodingUtils.getJSONString(map, JSON_FILENAME));

                Number enc = (Number) map.get(JSON_ENCRYPTION);
                Coder.Encryption encryption = Coder.Encryption.values()[enc.intValue()];

                Number sig = (Number) map.get(JSON_SIGNING);
                Coder.Signing signing = Coder.Signing.values()[sig.intValue()];

                Number err = ((Number) map.get(JSON_CODER_ERRORS));
                EnumSet<Coder.Error> errors = EncodingUtils.intToEnumSet(Coder.Error.class, err.intValue());

                return new Attachment(url, file, mimeType, length,
                        new CoderStatus(encryption, signing, errors));
            } catch (ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON attachment", ex);
                return null;
            }
        }
    }

    public static class Preview {

        private static final String JSON_FILENAME= "filename";
        private static final String JSON_MIME_TYPE = "mime_type";

        private final byte[] mData;
        private String mFilename = "";
        private final String mMimeType;

        // used for incoming
        public Preview(byte[] data, String mimeType) {
            mData = data;
            mMimeType = mimeType;
        }

        // used for outgoing / self created
        public Preview(byte[] data, String filename, String mimeType) {
            mData = data;
            mFilename = filename;
            mMimeType = mimeType;
        }

        private Preview(String filename, String mimeType) {
            mData = new byte[0];
            mFilename = filename;
            mMimeType = mimeType;
        }

        public byte[] getData() {
            return mData;
        }

        public String getFilename() {
            return mFilename;
        }

        void setFilename(String filename) {
            mFilename = filename;
        }

        public String getMimeType() {
            return mMimeType;
        }

        public void save(int messageID) {
            Integer.toString(messageID);
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private String toJSON() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_MIME_TYPE, mMimeType);
            EncodingUtils.putJSON(json, JSON_FILENAME, mFilename);
            return json.toJSONString();
        }

        private static Preview fromJSONOrNull(String json) {
            Object obj = JSONValue.parse(json);
            try {
                Map<?, ?> map = (Map) obj;
                String filename = EncodingUtils.getJSONString(map, JSON_FILENAME);
                String mimeType = EncodingUtils.getJSONString(map, JSON_MIME_TYPE);
                return new Preview(filename, mimeType);
            }  catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON preview", ex);
                return null;
            }
        }

        @Override
        public String toString() {
            return "{PRE:fn="+mFilename+",mime="+mMimeType+"}";
        }
    }

    public static class GroupCommand {
        private static final String JSON_OP = "op";
        private static final String JSON_ADDED = "added";
        private static final String JSON_REMOVED = "removed";
        private static final String JSON_SUBJECT = "subj";

        public enum OP {
            CREATE,
            SET,
            LEAVE
        }

        private final Optional<GID> mOptGID;
        private final OP mOP;
        private final JID[] mJIDsAdded;
        private final JID[] mJIDsRemoved;
        private final String mSubject;

        /** Group creation. */
        public GroupCommand(GID gid, JID[] added) {
            this(gid, OP.CREATE, added, new JID[0], "");
        }

        /** Group creation with subject. */
        public GroupCommand(JID[] added, String subject) {
            this(null, OP.CREATE, added, new JID[0], subject);
        }

        public GroupCommand(GID gid, JID[] added, String subject) {
            this(gid, OP.CREATE, added, new JID[0], subject);
        }

        /** Member list changed. */
        public GroupCommand(GID gid, JID[] added, JID[] removed) {
            this(gid, OP.SET, added, removed, "");
        }

        /** Member left. Identified by sender JID */
        public GroupCommand(GID gid) {
            this(gid, OP.LEAVE, new JID[0], new JID[0], "");
        }

        private GroupCommand(GID gid, OP operation, JID[] added, JID[] removed, String subject) {
            mOptGID = Optional.ofNullable(gid);
            mOP = operation;
            mJIDsAdded = added;
            mJIDsRemoved = removed;
            mSubject = subject;
        }

        public Optional<GID> getGID() {
            return mOptGID;
        }

        public OP getOperation() {
            return mOP;
        }

        public JID[] getAdded() {
            return mJIDsAdded;
        }

        public JID[] getRemoved() {
            return mJIDsRemoved;
        }

        public String getSubject() {
            return mSubject;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private String toJSON() {
            JSONObject json = new JSONObject();
            json.put(JSON_OP, mOP.ordinal());
            EncodingUtils.putJSON(json, JSON_SUBJECT, mSubject);

            List<String> added = new ArrayList(mJIDsAdded.length);
            for (JID jid: mJIDsAdded)
                added.add(jid.string());
            json.put(JSON_ADDED, added);

            List<String> removed = new ArrayList(mJIDsRemoved.length);
            for (JID jid: mJIDsAdded)
                removed.add(jid.string());
            json.put(JSON_REMOVED, removed);

            return json.toJSONString();
        }

        // using legacy lib
        @SuppressWarnings("unchecked")
        private static GroupCommand fromJSONOrNull(String json) {
            Object obj = JSONValue.parse(json);
            try {
                Map<?, ?> map = (Map) obj;

                Number o = (Number) map.get(JSON_OP);
                OP op = OP.values()[o.intValue()];

                List<String> a = (List<String>) map.get(JSON_ADDED);
                List<JID> added = new ArrayList<>(a.size());
                for (String s: a)
                    added.add(JID.bare(s));

                List<String> r = (List<String>) map.get(JSON_REMOVED);
                List<JID> removed = new ArrayList<>(r.size());
                for (String s: r)
                    removed.add(JID.bare(s));

                String subj = EncodingUtils.getJSONString(map, JSON_SUBJECT);

                return new GroupCommand(null, op,
                        added.toArray(new JID[0]), removed.toArray(new JID[0]),
                        subj);
             }  catch (NullPointerException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON group command", ex);
                LOGGER.log(Level.WARNING, "JSON='"+json+"'");
                return null;
            }
        }

        @Override
        public String toString() {
            return "{GC:op="+mOP+",subj="+mSubject+"}";
        }
    }
}
