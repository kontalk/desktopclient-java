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

package org.kontalk.system;

import org.kontalk.model.Account;
import java.io.IOException;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.PrivateKeyReceiver;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.Callback;
import org.kontalk.misc.KonException;
import org.kontalk.util.EncodingUtils;

/**
 * Import and set user account from various sources.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class AccountImporter extends Observable implements Callback.Handler<String>{
    private static final Logger LOGGER = Logger.getLogger(AccountImporter.class.getName());

    static final String PRIVATE_KEY_FILENAME = "kontalk-private.asc";

    private final Account mAccount;

    private char[] mPassword = null;
    private boolean mAborted = false;

    AccountImporter(Account account) {
        mAccount = account;
    }

    public void fromZipFile(String zipFilePath, char[] password) {
        // read key files
        byte[] privateKeyData;
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            privateKeyData = readBytesFromZip(zipFile, PRIVATE_KEY_FILENAME);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't open zip archive: ", ex);
            this.changed(new KonException(KonException.Error.IMPORT_ARCHIVE, ex));
            return;
        } catch (KonException ex) {
            this.changed(ex);
            return;
        }

        this.set(privateKeyData, password);
    }

    // note: with disarming if needed
    private static byte[] readBytesFromZip(ZipFile zipFile, String filename) throws KonException {
        ZipEntry zipEntry = zipFile.getEntry(filename);
        byte[] bytes = null;
        try {
            bytes = PGPUtils.mayDisarm(zipFile.getInputStream(zipEntry));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read key file from archive: ", ex);
            throw new KonException(KonException.Error.IMPORT_READ_FILE, ex);
        }
        return bytes;
    }

    public void fromServer(String host, int port, boolean validateCertificate,
            String token, char[] password) {
        mPassword = password;
        // send private key request
        EndpointServer server = new EndpointServer(host, port);
        PrivateKeyReceiver receiver = new PrivateKeyReceiver(this);
        mAborted = false;
        receiver.sendRequest(server, validateCertificate, token);

        // wait for response... continue with handle callback
    }

    public void abort() {
        // receiver will always terminate after some time, just ignore response
        mAborted = true;
    }

    @Override
    public void handle(Callback<String> callback) {
        if (mAborted)
            return;

        if (callback.exception.isPresent()) {
            this.changed(callback.exception);
            return;
        }

        this.set(EncodingUtils.base64ToBytes(callback.value), mPassword);
    }

    private void set(byte[] privateKeyData, char[] password) {
        try {
            mAccount.setAccount(privateKeyData, password);
        } catch (KonException ex) {
            this.changed(ex);
            return;
        }
        // report success
        this.changed(null);
    }

    private void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }
}
