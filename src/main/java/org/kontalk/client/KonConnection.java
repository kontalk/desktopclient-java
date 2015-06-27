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

package org.kontalk.client;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration.Builder;
import org.kontalk.util.TrustUtils;


/**
 * XMPP Connection to a Kontalk Server.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class KonConnection extends XMPPTCPConnection {
    private final static Logger LOGGER = Logger.getLogger(KonConnection.class.getName());

    private final static String RESSOURCE = "Kontalk_Desktop";

    public KonConnection(EndpointServer server,
            PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean validateCertificate) {
        super(buildConfiguration(RESSOURCE,
                server,
                privateKey,
                bridgeCert,
                validateCertificate)
        );

        // blacklist PLAIN mechanism
        SASLAuthentication.blacklistSASLMechanism("PLAIN");

        // enable SM without resumption (XEP-0198)
        this.setUseStreamManagement(true);
        this.setUseStreamManagementResumption(false);
    }

    private static XMPPTCPConnectionConfiguration buildConfiguration(
            String resource,
            EndpointServer server,
            PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean validateCertificate) {
        Builder builder =
            XMPPTCPConnectionConfiguration.builder();

        builder
            .setHost(server.getHost())
            .setPort(server.getPort())
            .setServiceName(server.getNetwork())
            .setResource(resource)
            // the dummy value is not actually used
            // server does authentification based purely on the pgp key
            .setUsernameAndPassword(null, "dummy")
            .setCallbackHandler(new CallbackHandler() {
                @Override
                public void handle(Callback[] callbacks)
                        throws IOException, UnsupportedCallbackException {
                    for (Callback cb : callbacks)
                        LOGGER.info("got callback!?: " + cb);
                }
            })
            // enable compression
            .setCompressionEnabled(true)
            // enable encryption
            .setSecurityMode(SecurityMode.required);

        // setup SSL
        if (!validateCertificate)
            LOGGER.warning("disabling SSL certificate validation");
        try {
            SSLContext sslContext = TrustUtils.getCustomSSLContext(privateKey,
                    bridgeCert,
                    validateCertificate);
            builder.setCustomSSLContext(sslContext);
            // Note: SASL EXTERNAL is already enabled in Smack
        } catch (NoSuchAlgorithmException |
                KeyStoreException |
                IOException |
                CertificateException |
                UnrecoverableKeyException |
                KeyManagementException |
                NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "can't setup SSL connection", ex);
        }

        return builder.build();
    }

    @Override
    public void disconnect() {
        LOGGER.info("disconnecting");
        super.disconnect();
    }

    public String getDestination() {
        return this.getConfiguration().getServiceName();
    }
}
