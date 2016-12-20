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

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.crypto.Coder;
import org.kontalk.misc.JID;
import org.kontalk.model.Model;
import org.kontalk.model.chat.GroupMetaData.KonGroupData;
import org.kontalk.system.AttachmentManager;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.MediaUtils;

/**
 * All possible content a message can contain.
 * Recursive: A message can contain a decrypted message.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class MessageContent {
    private static final Logger LOGGER = Logger.getLogger(MessageContent.class.getName());

    // plain message text, empty string if not present
    private final String mPlainText;
    // encrypted content, empty string if not present
    private String mEncryptedContent;
    // temporary encrypted data, not saved to database
    private byte[] mEncryptedData;
    // attachment (file url, path and metadata)
    private final Attachment mAttachment;
    // small preview file of attachment
    private Preview mPreview;
    // group id
    private final KonGroupData mGroupData;
    // group command
    private final GroupCommand mGroupCommand;
    // decrypted message content
    private MessageContent mDecryptedContent;

    private static final String JSON_PLAIN_TEXT = "plain_text";
    private static final String JSON_ENC_CONTENT = "encrypted_content";
    private static final String JSON_ATTACHMENT = "attachment";
    private static final String JSON_PREVIEW = "preview";
    private static final String JSON_GROUP_COMMAND = "group_command";
    private static final String JSON_DEC_CONTENT = "decrypted_content";

    // used for decrypted content of incoming messages, outgoing messages
    // and as fallback
    public static MessageContent plainText(String plainText) {
        return new Builder(plainText, "").build();
    }

    // used for outgoing messages
    public static MessageContent outgoing(String plainText, Attachment attachment) {
        return new Builder(plainText, "").attachment(attachment).build();
    }

    // used for outgoing group commands
    public static MessageContent groupCommand(GroupCommand group) {
        return new Builder("", "").groupCommand(group).build();
    }

    // used when loading from db
    private MessageContent(Builder builder) {
        mPlainText = builder.mPlainText;
        mEncryptedContent = builder.mEncrypted;
        mAttachment = builder.mAttachment;
        mPreview = builder.mPreview;
        mGroupData = builder.mGroupData;
        mGroupCommand = builder.mGroup;
        mDecryptedContent = builder.mDecrypted;
    }

    /**
     * Get encrypted or plain text content.
     * @return encrypted content if present, else plain text. If there is no
     * plain text either returns an empty string.
     */
    public String getText() {
        if (mDecryptedContent != null)
            return mDecryptedContent.getPlainText();
        else
            return mPlainText;
    }

    public String getPlainText() {
        return mPlainText;
    }

    public Optional<Attachment> getAttachment() {
        if (mDecryptedContent != null &&
                mDecryptedContent.getAttachment().isPresent()) {
            return mDecryptedContent.getAttachment();
        }
        return Optional.ofNullable(mAttachment);
    }

    public Optional<InAttachment> getInAttachment() {
        Attachment att = this.getAttachment().orElse(null);
        return !(att instanceof InAttachment)? Optional.empty() : Optional.of((InAttachment) att);
    }

    public Optional<OutAttachment> getOutAttachment() {
        Attachment att = this.getAttachment().orElse(null);
        return !(att instanceof OutAttachment)? Optional.empty() : Optional.of((OutAttachment) att);
    }

    String getEncryptedContent() {
        return mEncryptedContent;
    }

    void setDecryptedContent(MessageContent decryptedContent) {
        assert mDecryptedContent == null;
        mDecryptedContent = decryptedContent;
        // deleting encrypted data!
        mEncryptedContent = "";
    }

    public Optional<byte[]> getEncryptedData() {
        return Optional.ofNullable(mEncryptedData);
    }

    public void setEncryptedData(byte[] encryptedData) {
        mEncryptedData = encryptedData;
    }

    public Optional<Preview> getPreview() {
        if (mDecryptedContent != null &&
                mDecryptedContent.getPreview().isPresent()) {
            return mDecryptedContent.getPreview();
        }
        return Optional.ofNullable(mPreview);
    }

    void setPreview(Preview preview) {
        if (mPreview != null) {
            LOGGER.warning("preview already present, not overwriting");
            return;
        }
        mPreview = preview;
    }

    public Optional<KonGroupData> getGroupData() {
        if (mDecryptedContent != null &&
                mDecryptedContent.getGroupData().isPresent()) {
            return mDecryptedContent.getGroupData();
        }
        return Optional.ofNullable(mGroupData);
    }

    public Optional<GroupCommand> getGroupCommand() {
        if (mDecryptedContent != null &&
                mDecryptedContent.getGroupCommand().isPresent()) {
            return mDecryptedContent.getGroupCommand();
        }
        return Optional.ofNullable(mGroupCommand);
    }

    /**
     * Return if there is no content in this message.
     * @return true if there is no content at all, false otherwise
     */
    public boolean isEmpty() {
        return mPlainText.isEmpty() &&
                mEncryptedContent.isEmpty() &&
                mAttachment == null &&
                mPreview == null &&
                mDecryptedContent == null &&
                mGroupCommand == null;
    }

    public boolean isComplex() {
        return mAttachment != null || mGroupCommand != null;
    }

    @Override
    public String toString() {
        return "CONT:plain="+mPlainText+",encr="+mEncryptedContent
                +",att="+mAttachment+",gd="+mGroupData+",gc="+mGroupCommand
                +",decr="+mDecryptedContent;
    }

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    String toJSON() {
        JSONObject json = new JSONObject();

        EncodingUtils.putJSON(json, JSON_PLAIN_TEXT, mPlainText);

        if (mAttachment != null)
            json.put(JSON_ATTACHMENT, mAttachment.toJSONString());

        EncodingUtils.putJSON(json, JSON_ENC_CONTENT, mEncryptedContent);

        if (mPreview != null)
            json.put(JSON_PREVIEW, mPreview.toJSON());

        if (mGroupCommand != null)
            json.put(JSON_GROUP_COMMAND, mGroupCommand.toJSON());

        if (mDecryptedContent != null)
            json.put(JSON_DEC_CONTENT, mDecryptedContent.toJSON());

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

            return new Builder(plainText, encrypted)
                    .attachment(attachment)
                    .preview(preview)
                    .groupCommand(groupCommand)
                    .decryptedContent(decryptedContent).build();
        } catch(ClassCastException ex) {
            LOGGER.log(Level.WARNING, "can't parse JSON message content", ex);
            return plainText("");
        }
    }

    public abstract static class Attachment extends Observable {
        protected static final String JSON_URL = "url";
        protected static final String JSON_FILENAME = "file_name";

        protected void changed(boolean repeat) {
            this.setChanged();
            this.notifyObservers(repeat);
        }

        public abstract String getFilename();

        public abstract Path getFilePath();

        public abstract String getMimeType();

        /** Download progress in percent. <br>
         * -1: no download/default <br>
         *  0: download started... <br>
         * 100: ...download finished <br>
         * -2: unknown size <br>
         * -3: download aborted
         */
        public abstract int getDownloadProgress();

        public abstract boolean isEncrypted();

        protected abstract String toJSONString();

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        private static Attachment fromJSONOrNull(String json) {
            Object obj = JSONValue.parse(json);
            try {
                Map<?, ?> map = (Map) obj;
                return map.containsKey(InAttachment.JSON_ENCRYPTION) ?
                        InAttachment.fromJSON(map) :
                        OutAttachment.fromJSON(map);
            } catch (ClassCastException | InvalidPathException ex) {
                LOGGER.log(Level.WARNING, "can't parse JSON attachment", ex);
                return null;
            }
        }
    }

    public static final class InAttachment extends Attachment {
        private static final String JSON_ENCRYPTION = "encryption";
        private static final String JSON_SIGNING = "signing";
        private static final String JSON_CODER_ERRORS = "coder_errors";

        // URL for file download
        private final URI mURL;
        // file name of downloaded file, empty by default
        private String mFilename;
        // coder status of file encryption, known after file is downloaded
        protected CoderStatus mCoderStatus;

        // progress downloaded of (encrypted) file in percent
        private int mDownloadProgress = -1;

        public InAttachment(URI url) {
            this(url, "",
                    // TODO we don't know, but value is not used anyway
                    CoderStatus.createInsecure());
        }

        // used when loading from database.
        private InAttachment(URI url, String filename, CoderStatus coderStatus)  {
            mURL = url;
            mFilename = filename;
            mCoderStatus = coderStatus;
        }

        public URI getURL() {
            return mURL;
        }

        @Override
        public int getDownloadProgress() {
            return mDownloadProgress;
        }

        /** Set download progress. See getDownloadProgress() */
        public void setDownloadProgress(int p) {
            mDownloadProgress = p;
            if (p <= 0)
                this.changed(true);
        }

        @Override
        public String getFilename() {
            return mFilename;
        }

        public void setFile(String fileName, boolean encrypted) {
            mFilename = fileName;
            mCoderStatus = encrypted ? CoderStatus.createEncrypted() : CoderStatus.createInsecure();
            this.changed(!encrypted);
        }

        @Override
        public Path getFilePath() {
            return path(mFilename, AttachmentManager.ATT_DIRNAME);
        }

        public void setDecryptedFile(String filename) {
            mCoderStatus.setDecrypted();
            mFilename = filename;
            this.changed(true);
        }

        public void setErrors(EnumSet<Coder.Error> errors) {
            mCoderStatus.setSecurityErrors(errors);
            this.changed(false);
        }

        public void setSigning(Coder.Signing signing) {
            mCoderStatus.setSigning(signing);
            this.changed(false);
        }

        @Override
        public boolean isEncrypted() {
            return mCoderStatus.isEncrypted();
        }

        @Override
        public String getMimeType() {
            // guess from file
            return MediaUtils.mimeForFile(this.getFilePath());
        }

        @Override
        public String toString() {
            return "{IOATT:url="+mURL+",file="+mFilename+",status="+mCoderStatus+"}";
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        @Override
        protected String toJSONString() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_URL, mURL.toString());
            EncodingUtils.putJSON(json, JSON_FILENAME, mFilename.toString());
            json.put(JSON_ENCRYPTION, mCoderStatus.getEncryption().ordinal());
            json.put(JSON_SIGNING, mCoderStatus.getSigning().ordinal());
            int errs = EncodingUtils.enumSetToInt(mCoderStatus.getErrors());
            json.put(JSON_CODER_ERRORS, errs);
            return json.toJSONString();
        }

        private static InAttachment fromJSON(Map<?, ?> map) {
            URI url = URI.create(EncodingUtils.getJSONString(map, JSON_URL));
            String filename = EncodingUtils.getJSONString(map, JSON_FILENAME);

            Number enc = (Number) map.get(JSON_ENCRYPTION);
            Coder.Encryption encryption = Coder.Encryption.values()[enc.intValue()];
            Number sig = (Number) map.get(JSON_SIGNING);
            Coder.Signing signing = Coder.Signing.values()[sig.intValue()];
            Number err = ((Number) map.get(JSON_CODER_ERRORS));
            EnumSet<Coder.Error> errors = EncodingUtils.intToEnumSet(Coder.Error.class, err.intValue());

            return new InAttachment(url, filename, new CoderStatus(encryption, signing, errors));
        }
    }

    public static final class OutAttachment extends Attachment {
        private static final String JSON_MIME_TYPE = "mime_type";
        private static final String JSON_LENGTH = "length";

        // URL for file download, empty string by default
        private URI mURL;
        // path to upload file
        private Path mFile;
        // MIME of file, empty string by default
        private String mMimeType;
        // size of (decrypted) upload file in bytes, -1 by default
        private long mLength;

        /** URI, length and (maybe) new MIME type are set after upload. */
        public OutAttachment(Path path, String mimeType) {
            this(URI.create(""), path, mimeType, -1);
        }

        // used when loading from database.
        private OutAttachment(URI url, Path file,
                String mimeType, long length)  {
            mURL = url;
            mFile = file;
            mMimeType = mimeType;
            mLength = length;
        }

        public boolean hasURL() {
            return !mURL.toString().isEmpty();
        }

        public URI getURL() {
            return mURL;
        }

        @Override
        public String getFilename() {
            return mFile.getFileName().toString();
        }

        public void setUploaded(URI url, String mime, long length){
            mURL = url;
            mMimeType = mime;
            mLength = length;
            this.changed(false);
        }

        @Override
        public String getMimeType() {
            return mMimeType;
        }

        public long getLength() {
            return mLength;
        }

        public Path getFilePath() {
            return mFile;
        }

        @Override
        public int getDownloadProgress() {
            return -1;
        }

        @Override
        public boolean isEncrypted() {
            return false;
        }

        @Override
        public String toString() {
            return "{OATT:url="+mURL+",file="+mFile+",mime="+mMimeType+",length="+mLength+"}";
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings("unchecked")
        @Override
        protected String toJSONString() {
            JSONObject json = new JSONObject();
            EncodingUtils.putJSON(json, JSON_URL, mURL.toString());
            EncodingUtils.putJSON(json, JSON_MIME_TYPE, mMimeType);
            json.put(JSON_LENGTH, mLength);
            EncodingUtils.putJSON(json, JSON_FILENAME, mFile.toString());
            return json.toJSONString();
        }

        private static OutAttachment fromJSON(Map<?, ?> map) {
            URI url = URI.create(EncodingUtils.getJSONString(map, JSON_URL));
            Path file = Paths.get(EncodingUtils.getJSONString(map, JSON_FILENAME));
            String mimeType = EncodingUtils.getJSONString(map, JSON_MIME_TYPE);
            long length = ((Number) map.get(JSON_LENGTH)).longValue();

            return new OutAttachment(url, file, mimeType, length);
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

        public Path getImagePath() {
            return !MediaUtils.isImage(mMimeType) ? Paths.get("") :
                    path(mFilename, AttachmentManager.PREVIEW_DIRNAME);
        }

        void setFilename(String filename) {
            mFilename = filename;
        }

        public String getMimeType() {
            return mMimeType;
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

        // ordinals used in database
        public enum OP {
            CREATE,
            SET,
            LEAVE
        }

        private final OP mOP;
        private final List<JID> mAdded;
        private final List<JID> mRemoved;
        private final String mSubject;

        /** Group creation. */
        public static GroupCommand create(List<JID> added, String subject) {
            return new GroupCommand(OP.CREATE, added, Collections.emptyList(), subject);
        }

        /** Group changed. */
        public static GroupCommand set(List<JID> added, List<JID> removed, String subject) {
            return new GroupCommand(OP.SET, added, removed, subject);
        }

        public static GroupCommand set(String subject) {
            return new GroupCommand(OP.SET, Collections.emptyList(), Collections.emptyList(), subject);
        }

        /** Member left. Identified by sender JID */
        public static GroupCommand leave() {
            return new GroupCommand(OP.LEAVE, Collections.emptyList(), Collections.emptyList(), "");
        }

        private GroupCommand(OP operation, List<JID> added, List<JID> removed, String subject) {
            mOP = operation;
            mAdded = added;
            mRemoved = removed;
            mSubject = subject;
        }

        public OP getOperation() {
            return mOP;
        }

        public List<JID> getAdded() {
            return mAdded;
        }

        public boolean isAddingMe() {
            JID myJID = Model.getUserJID();
            return mAdded.stream().anyMatch(jid -> jid.equals(myJID));
        }

        public List<JID> getRemoved() {
            return mRemoved;
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

            List<String> added = mAdded.stream()
                    .map(JID::string)
                    .collect(Collectors.toList());
            json.put(JSON_ADDED, added);

            List<String> removed = mRemoved.stream()
                    .map(JID::string)
                    .collect(Collectors.toList());
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

                String subj = EncodingUtils.getJSONString(map, JSON_SUBJECT);

                List<String> a = (List<String>) map.get(JSON_ADDED);
                List<JID> added = a.stream()
                        .map(JID::bare)
                        .collect(Collectors.toList());

                List<String> r = (List<String>) map.get(JSON_REMOVED);
                List<JID> removed = r.stream()
                        .map(JID::bare)
                        .collect(Collectors.toList());

                return new GroupCommand(op, added, removed, subj);
            } catch (NullPointerException | ClassCastException ex) {
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

    public static class Builder {
        final String mPlainText;
        final String mEncrypted;

        private Attachment mAttachment = null;
        private Preview mPreview = null;
        private KonGroupData mGroupData = null;
        private GroupCommand mGroup = null;
        private MessageContent mDecrypted = null;

        public Builder(String plainText, String encrypted) {
            this.mPlainText = plainText;
            this.mEncrypted = encrypted;
        }

        public Builder attachment(Attachment attachment) {
            mAttachment = attachment; return this; }

        public Builder preview(Preview preview) {
            mPreview = preview; return this; }

        public Builder groupData(KonGroupData gData) {
            mGroupData = gData; return this; }

        public Builder groupCommand(GroupCommand group) {
            mGroup = group; return this; }

        private Builder decryptedContent(MessageContent decrypted) {
            mDecrypted = decrypted; return this; }

        public MessageContent build() {
            return new MessageContent(this);
        }
    }

    private static Path path(String filename, String dirName) {
        return filename.isEmpty() ? Paths.get("") :
                Model.appDir().resolve(dirName).resolve(filename);
    }
}
