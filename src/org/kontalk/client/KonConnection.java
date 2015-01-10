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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;


/**
 * XMPP Connection to a Kontalk Server.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class KonConnection extends XMPPTCPConnection {
    private final static Logger LOGGER = Logger.getLogger(KonConnection.class.getName());

    private final static String RESSOURCE = "Kontalk_Desktop";
    // TODO
    private final static boolean ACCEPT_ANY_CERTIFICATE = true;

    protected EndpointServer mServer;

    public KonConnection(EndpointServer server,
            PrivateKey privateKey,
            X509Certificate bridgeCert) {
        super(buildConfiguration(
        RESSOURCE,
        server,
        privateKey,
        bridgeCert,
        ACCEPT_ANY_CERTIFICATE));

        // enable SM without resumption (XEP-0198)
        this.setUseStreamManagement(true);
        this.setUseStreamManagementResumption(false);

        mServer = server;
    }

    private static XMPPTCPConnectionConfiguration buildConfiguration(
            String resource,
            EndpointServer server,
            PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean acceptAnyCertificate) {
        XMPPTCPConnectionConfiguration.XMPPTCPConnectionConfigurationBuilder builder =
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
            // we need the roster
            .setRosterLoadedAtLogin(true)
            // enable compression
            .setCompressionEnabled(true)
            // enable encryption
            .setSecurityMode(SecurityMode.required)
            // we will send a custom presence
            // -> no, send initial default presence
            //.setSendPresence(false)
            // disable session initiation
            .setLegacySessionDisabled(true);

        // setup SSL
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");

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

            // blacklist PLAIN mechanism
            SASLAuthentication.blacklistSASLMechanism("PLAIN");

            // trust managers
            TrustManager[] tm;
            if (acceptAnyCertificate) {
                tm = new TrustManager[] {
                    new X509TrustManager() {
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
                    }
                };
            } else {
                // builtin keystore
                TrustManagerFactory tmFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                tmFactory.init(ks);

                tm = tmFactory.getTrustManagers();
            }

            ctx.init(km, tm, null);
            builder.setCustomSSLContext(ctx);
            // Note: SASL EXTERNAL is already enabled in Smack
        } catch (NoSuchAlgorithmException |
                KeyStoreException |
                IOException |
                CertificateException |
                UnrecoverableKeyException |
                KeyManagementException ex) {
            LOGGER.log(Level.WARNING, "can't setup SSL connection", ex);
        }

        return builder.build();
    }

    @Override
    public void disconnect() {
        LOGGER.info("disconnecting (no presence)");
        try {
            super.disconnect();
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.info("can't disconnect, not connected");
        }
    }

    @Override
    public synchronized void disconnect(Presence presence) {
        LOGGER.log(Level.INFO, "disconnecting ({0})", presence.toXML());
        try {
            super.disconnect(presence);
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.info("can't disconnect, not connected");
        }
    }

    public String getDestination() {
        return this.getConfiguration().getServiceName();
    }
}
