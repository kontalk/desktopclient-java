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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.system.Config;
import org.kontalk.misc.KonException;
import org.kontalk.Kontalk;
import org.kontalk.crypto.PGPUtils;
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

    public PersonalKey getPersonalKey() throws KonException {
        if (mKey == null)
            mKey = this.load();
        return mKey;
    }

    private PersonalKey load() throws KonException {
        // read key files
        byte[] publicKeyData = readBytesFromFile(PUBLIC_KEY_FILENAME);
        byte[] privateKeyData = readBytesFromFile(PRIVATE_KEY_FILENAME);
        byte[] bridgeCertData = readBytesFromFile(BRIDGE_CERT_FILENAME);

        // load key
        String password = Config.getInstance().getString(Config.ACC_PASS);
        try {
            return PersonalKey.load(disarm(privateKeyData),
                    disarm(publicKeyData),
                    password,
                    bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "can't load personal key", ex);
            throw new KonException(KonException.Error.RELOAD_KEY, ex);
        }
    }

    public void importAccount(String zipFilePath, String password) throws KonException {
        byte[] publicKeyData;
        byte[] privateKeyData;
        byte[] bridgeCertData;

        // read key files
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            publicKeyData = Account.readBytesFromZip(zipFile, PUBLIC_KEY_FILENAME);
            privateKeyData = Account.readBytesFromZip(zipFile, PRIVATE_KEY_FILENAME);
            bridgeCertData = Account.readBytesFromZip(zipFile, BRIDGE_CERT_FILENAME);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't open zip archive: ", ex);
            throw new KonException(KonException.Error.IMPORT_ARCHIVE, ex);
        }

        // try to load key
        PersonalKey key;
        byte[] encodedPublicKey;
        byte[] encodedPrivateKey;
        try {
            encodedPublicKey = disarm(publicKeyData);
            encodedPrivateKey = disarm(privateKeyData);
            key = PersonalKey.load(encodedPrivateKey,
                    encodedPublicKey,
                    password,
                    bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "can't load personal key", ex);
            throw new KonException(KonException.Error.IMPORT_KEY, ex);
        }

        // key seems valid. Copy to config dir
        writeBytesToFile(encodedPublicKey, PUBLIC_KEY_FILENAME, true);
        writeBytesToFile(bridgeCertData, BRIDGE_CERT_FILENAME, false);
        writePrivateKey(encodedPrivateKey, password);

        // success! use the new key
        mKey = key;
    }

    public void setPassword(String newPassword) throws KonException {
        byte[] privateKeyData = readBytesFromFile(PRIVATE_KEY_FILENAME);
        String oldPassword = Config.getInstance().getString(Config.ACC_PASS);
        writePrivateKey(privateKeyData, oldPassword, newPassword);
        Config.getInstance().setProperty(Config.ACC_PASS, "");
    }

    public void setNewPassword(String oldPassword, String newPassword) throws KonException {
        byte[] privateKeyData = readBytesFromFile(PRIVATE_KEY_FILENAME);
        writePrivateKey(privateKeyData, oldPassword, newPassword);
        Config.getInstance().setProperty(Config.ACC_PASS, "");
    }

    private static byte[] disarm(byte[] key) throws IOException {
        return IOUtils.toByteArray(new ArmoredInputStream(new ByteArrayInputStream(key)));
    }

    private static void writePrivateKey(byte[] privateKeyData, String oldPassword) throws KonException {
        String newPassword = StringUtils.randomString(40);
        writePrivateKey(privateKeyData, oldPassword, newPassword);
        Config.getInstance().setProperty(Config.ACC_PASS, newPassword);
     }

    private static void writePrivateKey(byte[] privateKeyData,
            String oldPassword,
            String newPassword) throws KonException {
        try {
            privateKeyData = PGPUtils.copySecretKeyRingWithNewPassword(privateKeyData,
                    oldPassword, newPassword).getEncoded();
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't change password", ex);
            throw new KonException(KonException.Error.CHANGE_PASSWORD, ex);
        }
        writeBytesToFile(privateKeyData, PRIVATE_KEY_FILENAME, true);
    }

    private static byte[] readBytesFromZip(ZipFile zipFile, String filename) throws KonException {
        ZipEntry zipEntry = zipFile.getEntry(filename);
        byte[] bytes = null;
        try {
            bytes = IOUtils.toByteArray(zipFile.getInputStream(zipEntry));
        } catch (IOException ex) {
            LOGGER.warning("can't read key file from archive: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.IMPORT_READ_FILE, ex);
        }
        return bytes;
    }

    private static byte[] readBytesFromFile(String filename) throws KonException {
        String configDir = Kontalk.getConfigDir();
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(new File(configDir, filename).toPath());
        } catch (IOException ex) {
            LOGGER.warning("can't read key file: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.READ_FILE, ex);
        }
        return bytes;
    }

    private static void writeBytesToFile(byte[] bytes, String filename, boolean armored) throws KonException {
        String configDir = Kontalk.getConfigDir();
        try {
            OutputStream outStream = new FileOutputStream(new File(configDir, filename));
            if (armored)
                outStream = new ArmoredOutputStream(outStream);
            outStream.write(bytes);
            outStream.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't write key file", ex);
            throw new KonException(KonException.Error.WRITE_FILE, ex);
        }
    }

    public static Account getInstance() {
        return INSTANCE;
    }
}
