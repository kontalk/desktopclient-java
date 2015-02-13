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

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.kontalk.Kontalk;
import org.kontalk.crypto.PGPUtils;

/**
 * Utilities for SASL certificate validation.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class TrustUtils {
    private final static String KEYSTORE_FILE = "truststore.bks";

    private static TrustManager TM = null;
    private static KeyStore KS = null;

    public static TrustManager getBlindTrustManager() {
        if (TM == null) {
            TM = new X509TrustManager() {
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
        return TM;
    }

    /**
     * Load own key store from file.
     */
    public static KeyStore getKeyStore()
            throws IOException,
            NoSuchAlgorithmException,
            CertificateException,
            KeyStoreException,
            NoSuchProviderException {
        if (KS == null) {
            KS = KeyStore.getInstance("BKS", PGPUtils.PROVIDER);
            InputStream in = ClassLoader.getSystemResourceAsStream(Kontalk.RES_PATH + KEYSTORE_FILE);
            KS.load(in, "changeit".toCharArray());
        }
        return KS;
    }
}
