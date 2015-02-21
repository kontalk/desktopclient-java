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

package org.kontalk.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.kontalk.Kontalk;
import org.kontalk.crypto.PGPUtils;

/**
 * Utilities for SASL certificate validation.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class TrustUtils {
    private final static Logger LOGGER = Logger.getLogger(TrustUtils.class.getName());

    private final static String TRUSSTORE_FILE = "truststore.bks";

    private static TrustManager BLIND_TM = null;
    private static KeyStore MERGED_TS = null;

    /**
     * Get a custom SSL context for secure server connections. The key store of
     * the context contains the private key and bridge certificate. The trust
     * manager contains system and own certificates or blindly accepts every
     * server certificate.
     */
    public static SSLContext getCustomSSLContext(
            PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean validateCertificate)
            throws KeyStoreException,
            IOException,
            NoSuchAlgorithmException,
            CertificateException,
            UnrecoverableKeyException,
            NoSuchProviderException,
            KeyManagementException {
        // in-memory keystore
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null, null);
        keystore.setKeyEntry("private",
                privateKey,
                new char[0],
                new Certificate[] { bridgeCert });

        // key managers
        KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmFactory.init(keystore, new char[0]);

        KeyManager[] km = kmFactory.getKeyManagers();

        // trust managers
        TrustManager[] tm;
        if (validateCertificate) {
            // use modified truststore
            TrustManagerFactory tmFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init(getTrustStore());
            tm = tmFactory.getTrustManagers();
        } else {
            // trust everything!
            tm = new TrustManager[] { getBlindTrustManager() };
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(km, tm, null);
        return ctx;
    }

    private static TrustManager getBlindTrustManager() {
        if (BLIND_TM == null) {
            BLIND_TM = new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                }
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                }
            };
        }
        return BLIND_TM;
    }

    /**
     * Load own trust store from file and system.
     * Return certificate store containing merged content from own keystore file
     * (containing certificate for CAcert.org) with system certificates (if any).
     */
    private static KeyStore getTrustStore() throws KeyStoreException {
        if (MERGED_TS == null) {
            // note: there is no default truststore we can get from the JSSE
            MERGED_TS = KeyStore.getInstance(KeyStore.getDefaultType());

            // load system keys
            String path = System.getProperty("javax.net.ssl.trustStore");
            if (path == null) {
                path = System.getProperty("java.home") + File.separator + "lib"
                    + File.separator + "security" + File.separator
                    + "cacerts";
            }
            try {
                MERGED_TS.load(new FileInputStream(path), null);
            } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
                LOGGER.log(Level.WARNING, "can't load system keys", ex);
            }

            // add own certs
            try {
                KeyStore myTS = KeyStore.getInstance("BKS", PGPUtils.PROVIDER);
                InputStream in = ClassLoader.getSystemResourceAsStream(Kontalk.RES_PATH + TRUSSTORE_FILE);
                myTS.load(in, "changeit".toCharArray());
                Enumeration<String> aliases = myTS.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    Certificate cert = myTS.getCertificate(alias);

                    if (MERGED_TS.containsAlias(alias))
                        LOGGER.info("overwriting system certificate: "+alias);

                    MERGED_TS.setCertificateEntry(alias, cert);
                }
            } catch (CertificateException |
                    IOException |
                    KeyStoreException |
                    NoSuchAlgorithmException |
                    NoSuchProviderException ex) {
                LOGGER.log(Level.WARNING, "can't add certificates from own truststore", ex);
            }
        }
        return MERGED_TS;
    }
}
