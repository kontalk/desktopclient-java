/*
 * Kontalk Java client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.crypto.PersonalKey;

public final class Account {
    private final static Logger LOGGER = Logger.getLogger(Account.class.getName());

    private static final String PUBLIC_KEY_FILENAME = "kontalk-public.asc";
    private static final String PRIVATE_KEY_FILENAME = "kontalk-private.asc";
    private static final String BRIDGE_CERT_FILENAME = "kontalk-login.crt";

    private final static Account INSTANCE = new Account();

    private PersonalKey mKey = null;

    private Account() {
    }

    public void reload() throws KonException {

        Configuration config = KonConf.getInstance();

        byte[] publicKeyData;
        byte[] privateKeyData;
        byte[] bridgeCertData;

        // read key files
        // TODO: copy files to config dir?
        try (ZipFile zipFile = new ZipFile(config.getString(KonConf.ACC_ARCHIVE))) {
            publicKeyData = Account.readBytesFromZip(zipFile, PUBLIC_KEY_FILENAME);
            privateKeyData = Account.readBytesFromZip(zipFile, PRIVATE_KEY_FILENAME);
            bridgeCertData = Account.readBytesFromZip(zipFile, BRIDGE_CERT_FILENAME);
        } catch (IOException ex) {
            LOGGER.warning("can't read from zip archive: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.ACCOUNT_ARCHIVE, ex);
        }

        // load key
        String passphrase = config.getString("account.passphrase");
        try {
             mKey = PersonalKey.load(
                     new ArmoredInputStream(new ByteArrayInputStream(privateKeyData)),
                     new ArmoredInputStream(new ByteArrayInputStream(publicKeyData)),
                     passphrase,
                     bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.INFO, "can't load personal key", ex);
            throw new KonException(KonException.Error.ACCOUNT_KEY, ex);
        }
    }

    public PersonalKey getPersonalKey() {
        return mKey;
    }

    public static Account getInstance() {
        return INSTANCE;
    }

    private static byte[] readBytesFromZip(ZipFile zipFile, String filename) throws KonException {
        ZipEntry zipEntry = zipFile.getEntry(filename);
        try {
            return IOUtils.toByteArray(zipFile.getInputStream(zipEntry));
        } catch (IOException ex) {
            LOGGER.warning("can't read key file from archive: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.ACCOUNT_FILE, ex);
        }
    }
}
