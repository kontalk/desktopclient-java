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

import java.io.File;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.kontalk.client.DownloadClient;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.InMessage;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.OutMessage;

/**
 * Up- and download service for attachment files.
 *
 * Also takes care of de- and encrypting attachments.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class Downloader implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Downloader.class.getName());

    private final LinkedBlockingQueue<Task> mQueue = new LinkedBlockingQueue<>();

    private final File mBaseDir;

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

    private Downloader(Path dirPath) {
        mBaseDir = dirPath.toFile();
        boolean created = mBaseDir.mkdirs();
        if (created)
            LOGGER.info("created attachment directory");
    }

    public void queueDownload(InMessage message) {
        boolean added = mQueue.offer(new Task.DownloadTask(message));
        if (!added) {
            LOGGER.warning("can't add message to download-queue");
        }
    }

    public File getBaseDir() {
        return mBaseDir;
    }

    private void uploadAsync(OutMessage message) {
        // TODO
    }

    private void downloadAsync(final InMessage message) {
        Optional<PersonalKey> optKey = AccountLoader.getInstance().getPersonalKey();
        if (!optKey.isPresent()) {
            LOGGER.log(Level.WARNING, "personal key not loaded");
            return;
        }
        PersonalKey key = optKey.get();
        PrivateKey privateKey;
        try {
            privateKey = key.getBridgePrivateKey();
        } catch (PGPException ex) {
            LOGGER.log(Level.WARNING, "can't get private bridge key", ex);
            return;
        }
        X509Certificate bridgeCert = key.getBridgeCertificate();
        boolean validateCertificate = Config.getInstance().getBoolean(Config.SERV_CERT_VALIDATION);
        DownloadClient.ProgressListener listener = new DownloadClient.ProgressListener() {
            @Override
            public void updateProgress(int p) {
                message.setAttachmentDownloadProgress(p);
            }
        };
        DownloadClient client = new DownloadClient(privateKey,
                bridgeCert,
                validateCertificate,
                listener);

        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in message");
            return;
        }
        Attachment attachment = optAttachment.get();

        String path = client.download(attachment.getURL(), mBaseDir);
        if (path.isEmpty()) {
            // could not be downloaded
            return;
        }

        message.setAttachmentFileName(new File(path).getName());

        // decrypt file
        if (attachment.getCoderStatus().isEncrypted()) {
            Coder.decryptAttachment(message, mBaseDir.toPath());
        }
    }

    Path getAttachmentDir() {
        return mBaseDir.toPath();
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

    static Downloader create(Path downloadDir) {
        Downloader downloader = new Downloader(downloadDir);

        new Thread(downloader).start();

        return downloader;
    }
}
