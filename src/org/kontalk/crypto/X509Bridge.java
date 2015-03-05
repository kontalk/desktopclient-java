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

package org.kontalk.crypto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Utility methods for bridging OpenPGP keys with X.509 certificates.<br>
 * Inspired by the Foaf server project.
 * @author Daniele Ricci
 * @see https://svn.java.net/svn/sommer~svn/trunk/misc/FoafServer/pgpx509/src/net/java/dev/sommer/foafserver/utils/PgpX509Bridge.java
 */
public final class X509Bridge {

    private X509Bridge() {
    }

    public static X509Certificate load(byte[] certData)
    		throws CertificateException, NoSuchProviderException {

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", PGPUtils.PROVIDER);
        InputStream in = new ByteArrayInputStream(certData);
        return (X509Certificate) certFactory.generateCertificate(in);
    }
}
