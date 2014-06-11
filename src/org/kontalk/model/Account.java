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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.Configuration;
import org.bouncycastle.openpgp.PGPException;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.Kontalk;
import org.kontalk.crypto.PersonalKey;

public class Account {
    private final static Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    private final static Account INSTANCE = new Account();

    private PersonalKey mKey = null;

    private Account() {
    }

    public void reload() throws KonException {

        Configuration config = KonConf.getInstance();

        // read key files
        byte[] publicKeyData = readBytes(config.getString("account.public_key"));
        byte[] privateKeyData = readBytes(config.getString("account.private_key"));
        byte[] bridgeCertData = readBytes(config.getString("account.bridge_cert"));
        //mBridgeKeyData = readBytes(mConfig.getString("account.bridge_key"));

        // load key
        String passphrase = config.getString("account.passphrase");
        try {
             mKey = PersonalKey.load(privateKeyData, publicKeyData, passphrase, bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.INFO, "can't load personal key", ex);
            throw new KonException(ex);
        }
    }

    public PersonalKey getPersonalKey() {
        return mKey;
    }

    public static Account getInstance() {
        return INSTANCE;
    }

    private static byte[] readBytes(String path) throws KonException {
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (IOException ex) {
            LOGGER.warning("can't read key file: "+ex.getLocalizedMessage());
            throw new KonException(ex);
        }
        return bytes;
    }

}
