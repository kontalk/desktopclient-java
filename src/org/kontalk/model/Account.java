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

import org.kontalk.crypto.PersonalKey;
import org.bouncycastle.openpgp.PGPException;
import org.kontalk.KontalkException;
import org.kontalk.MyKontalk;

public class Account {
    private final static Logger LOGGER = Logger.getLogger(MyKontalk.class.getName());
    
    private final Configuration mConfig;
    private final PersonalKey mKey;
    

    public Account(Configuration config) throws KontalkException {
        mConfig = config;
        
        // read key files
        byte[] publicKeyData = readBytes(mConfig.getString("account.public_key"));
        byte[] privateKeyData = readBytes(mConfig.getString("account.private_key"));
        byte[] bridgeCertData = readBytes(mConfig.getString("account.bridge_cert"));
        //mBridgeKeyData = readBytes(mConfig.getString("account.bridge_key"));
        
        // load key
        String passphrase = mConfig.getString("account.passphrase");
        try {
             mKey = PersonalKey.load(privateKeyData, publicKeyData, passphrase, bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.INFO, "can't load personal key", ex);
            throw new KontalkException(ex);
        }
    }
    
    public PersonalKey getPersonalKey() {
        return mKey;
    }
    
    private static byte[] readBytes(String path) throws KontalkException {
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (IOException ex) {
            LOGGER.warning("can't read key file: "+ex.getLocalizedMessage());
            throw new KontalkException(ex);
        }
        return bytes;
    }

}
