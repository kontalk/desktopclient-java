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

package org.kontalk.system;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPException;
import org.kontalk.client.HTTPFileClient;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.Coder.Encryption;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageContent.Preview;
import org.kontalk.model.OutMessage;
import org.kontalk.util.MediaUtils;

/**
 * Up- and download service for attachment files.
 *
 * Also takes care of de- and encrypting attachments.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class AttachmentManager implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AttachmentManager.class.getName());

    private static final String ATT_DIRNAME = "attachments";
    private static final String PREVIEW_DIRNAME = "preview";

    public static final Dimension THUMBNAIL_DIM = new Dimension(300, 200);
    public static final String THUMBNAIL_MIME = "image/jpeg";

    // server and Android client do not want other types
    public static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "text/plain",
            "text/x-vcard",
            "text/vcard",
            "image/gif",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "audio/3gpp",
            "audio/mpeg3",
            "audio/wav");

    // TODO get this from server
    private static final String UPLOAD_URL = "https://beta.kontalk.net:5980/upload";

    private final LinkedBlockingQueue<Task> mQueue = new LinkedBlockingQueue<>();

    private final Control mControl;
    private final Path mAttachmentDir;
    private final Path mPreviewDir;

    private static class Task {

        private Task() {}

        static final class UploadTask extends Task {
            final OutMessage message;

            public UploadTask(OutMessage message) {
                this.message = message;
            }
        }

        static final class DownloadTask extends Task {
            final InMessage message;

            public DownloadTask(InMessage message) {
                this.message = message;
            }
        }
    }

    private AttachmentManager(Path baseDir, Control control) {
        mControl = control;
        mAttachmentDir = baseDir.resolve(ATT_DIRNAME);
        if (mAttachmentDir.toFile().mkdir())
            LOGGER.info("created attachment directory");

        mPreviewDir = baseDir.resolve(PREVIEW_DIRNAME);
        if (mPreviewDir.toFile().mkdir())
            LOGGER.info("created preview directory");
    }

    void queueUpload(OutMessage message) {
        boolean added = mQueue.offer(new Task.UploadTask(message));
        if (!added) {
            LOGGER.warning("can't add upload message to queue");
        }
    }

    void queueDownload(InMessage message) {
        boolean added = mQueue.offer(new Task.DownloadTask(message));
        if (!added) {
            LOGGER.warning("can't add download message to queue");
        }
    }

    private void uploadAsync(OutMessage message) {
        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in message to upload");
            return;
        }
        Attachment attachment = optAttachment.get();

        // if text will be encrypted, always encrypt attachment too
        boolean encrypt = message.getCoderStatus().getEncryption() == Encryption.DECRYPTED;
        File file;
        if (encrypt){
            Optional<File> optFile = Coder.encryptAttachment(message);
            if (!optFile.isPresent())
                return;
            file = optFile.get();
        } else
            file = attachment.getFile().toFile();

        HTTPFileClient client = createClientOrNull();
        if (client == null)
            return;

        URI url = client.upload(file, URI.create(UPLOAD_URL),
                // this isn't correct, but the server can't handle the truth
                /*encrypt ? "application/octet-stream" :*/ attachment.getMimeType(),
                encrypt);

        // delete temp file
        if (encrypt)
            file.delete();

        if (url.toString().isEmpty()) {
            LOGGER.warning("upload failed, attachment: "+attachment);
            message.setStatus(KonMessage.Status.ERROR);
            // TODO tell view
            return;
        }

        message.setAttachmentURL(url);

        LOGGER.info("upload successful, URL="+url);

        // make sure not to loop
        if (attachment.hasURL())
            mControl.sendMessage(message);
    }

    private void downloadAsync(final InMessage message) {
        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in message to download");
            return;
        }
        Attachment attachment = optAttachment.get();

        HTTPFileClient client = createClientOrNull();
        if (client == null)
            return;

        HTTPFileClient.ProgressListener listener = new HTTPFileClient.ProgressListener() {
            @Override
            public void updateProgress(int p) {
                message.setAttachmentDownloadProgress(p);
            }
        };

        Path path = client.download(attachment.getURL(), mAttachmentDir, listener);
        if (path.toString().isEmpty()) {
            LOGGER.warning("download failed, URL="+attachment.getURL());
            return;
        }

        LOGGER.info("download successful, saved to file: "+path);

        message.setAttachmentFileName(path.getFileName().toString());

        // decrypt file
        if (attachment.getCoderStatus().isEncrypted()) {
            Coder.decryptAttachment(message, mAttachmentDir);
        }
    }

    public void savePreview(InMessage message) {
        Optional<Preview> optPreview = message.getContent().getPreview();
        if (!optPreview.isPresent()) {
            LOGGER.warning("no preview in message: "+message);
            return;
        }
        Preview preview = optPreview.get();
        String id = Integer.toString(message.getID());
        String dotExt = MediaUtils.extensionForMIME(preview.getMimeType());
        String filename = id + "_bob" + dotExt;
        this.writePreview(preview, filename);

        message.setPreviewFilename(filename);
    }

    Path filePath(Attachment attachment) {
        Path path = attachment.getFile();
        if (path.toString().isEmpty())
            return Paths.get("");
        return path.isAbsolute() ? path : mAttachmentDir.resolve(path);
    }

    private void writePreview(Preview preview, String filename) {
        File newFile = mPreviewDir.resolve(filename).toFile();
        try {
            FileUtils.writeByteArrayToFile(newFile, preview.getData());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't save preview file", ex);
            return;
        }

        LOGGER.info("to file: "+newFile);
    }

    @Override
    public void run() {
        while (true) {
            Task t;
            try {
                // blocking
                t = mQueue.take();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "interrupted while waiting ", ex);
                return;
            }
            if (t instanceof Task.UploadTask) {
                this.uploadAsync(((Task.UploadTask) t).message);
            } else if (t instanceof Task.DownloadTask) {
                this.downloadAsync(((Task.DownloadTask) t).message);
            }
        }
    }

    static AttachmentManager create(Path baseDir, Control control) {
        AttachmentManager downloader = new AttachmentManager(baseDir, control);

        new Thread(downloader).start();

        return downloader;
    }

    /**
     * Create a new attachment for a given file denoted by its path.
     */
    static Attachment attachmentOrNull(Path path) {
        File file = path.toFile();
        if (!file.isFile() || !file.canRead()) {
            LOGGER.warning("invalid attachment file: "+path);
            return null;
        }
        String mimeType;
        try {
            mimeType = Files.probeContentType(path);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't get attachment mime type", ex);
            return null;
        }
        long length = file.length();
        if (length <= 0) {
            LOGGER.warning("invalid attachment file size: "+length);
            return null;
        }
        return new Attachment(path, mimeType, length);
    }

    private static HTTPFileClient createClientOrNull(){
        Optional<PersonalKey> optKey = AccountLoader.getInstance().getPersonalKey();
        if (!optKey.isPresent()) {
            LOGGER.log(Level.WARNING, "personal key not loaded");
            return null;
        }
        PersonalKey key = optKey.get();
        PrivateKey privateKey;
        try {
            privateKey = key.getBridgePrivateKey();
        } catch (PGPException ex) {
            LOGGER.log(Level.WARNING, "can't get private bridge key", ex);
            return null;
        }
        X509Certificate bridgeCert = key.getBridgeCertificate();
        boolean validateCertificate = Config.getInstance().getBoolean(Config.SERV_CERT_VALIDATION);

        return new HTTPFileClient(privateKey,
                bridgeCert,
                validateCertificate);
    }
}
