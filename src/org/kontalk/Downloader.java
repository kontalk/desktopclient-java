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

package org.kontalk;

import java.io.File;
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
import org.kontalk.model.Account;
import org.kontalk.model.InMessage;
import org.kontalk.model.MessageContent.Attachment;

/**
 * Downloader for attachments.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class Downloader implements Runnable {
    private final static Logger LOGGER = Logger.getLogger(Downloader.class.getName());

    private static Downloader INSTANCE = null;

    private final LinkedBlockingQueue<InMessage> mQueue = new LinkedBlockingQueue<>();

    private final File mBaseDir;
    private DownloadClient mClient = null;

    private Downloader() {
        String dirPath = Kontalk.getConfigDir() + "/attachments";
        mBaseDir = new File(dirPath);
        boolean created = mBaseDir.mkdirs();
        if (created)
            LOGGER.info("created download directory");
    }

    public void queueDownload(InMessage message) {
        boolean added = mQueue.offer(message);
        if (!added) {
            LOGGER.warning("can't add message to download-queue");
        }
    }

    public File getBaseDir() {
        return mBaseDir;
    }

    private void downloadAsync(InMessage message) {
        // TODO reuse download client, currently a ssl exception is thrown on
        // second connection attempt
        //if (mClient == null) {
            try {
                mClient = createClient();
            } catch (KonException | PGPException ex) {
                LOGGER.log(Level.WARNING, "can't create download client", ex);
                return;
            }
        //}

        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in message");
            return;
        }
        Attachment attachment = optAttachment.get();

        String path = mClient.download(attachment.getURL(), mBaseDir);
        if (path.isEmpty()) {
            // could not be downloaded
            return;
        }

        message.setAttachmentFileName(new File(path).getName());

        // decrypt file
        if (attachment.getEncryption() == Coder.Encryption.ENCRYPTED) {
            Coder.processAttachment(message);
        }
    }

    @Override
    public void run() {
        while (true) {
            InMessage m;
            try {
                // blocking
                m = mQueue.take();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "interrupted while waiting ", ex);
                return;
            }
            this.downloadAsync(m);
        }
    }

    public static Downloader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Downloader();
            new Thread(INSTANCE).start();
        }
        return INSTANCE;
    }

    private static DownloadClient createClient() throws KonException, PGPException {
        PersonalKey key = Account.getInstance().getPersonalKey();
        PrivateKey privateKey;
        privateKey = key.getBridgePrivateKey();

        X509Certificate bridgeCert = key.getBridgeCertificate();
        return new DownloadClient(privateKey, bridgeCert);
    }
}
