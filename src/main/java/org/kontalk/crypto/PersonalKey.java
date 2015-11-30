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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Hex;
import org.kontalk.misc.KonException;

/**
 * Personal PGP key(s).
 */
public final class PersonalKey {
    private static final Logger LOGGER = Logger.getLogger(PersonalKey.class.getName());

    /** (Server) Authentication key. */
    private final PGPPublicKey mAuthKey;
    /** (Server) Login key. */
    private final PrivateKey mLoginKey;
    /** Signing key. */
    private final PGPKeyPair mSignKey;
    /** En-/decryption key. */
    private final PGPKeyPair mEncryptKey;
    /** X.509 bridge certificate. */
    private final X509Certificate mBridgeCert;

    private PersonalKey(PGPKeyPair authKP,
            PGPKeyPair signKP,
            PGPKeyPair encryptKP,
            X509Certificate bridgeCert) throws PGPException {
        mAuthKey = authKP.getPublicKey();
        mLoginKey = PGPUtils.convertPrivateKey(authKP.getPrivateKey());
        mSignKey = signKP;
        mEncryptKey = encryptKP;
        mBridgeCert = bridgeCert;
    }

    PGPPrivateKey getPrivateEncryptionKey() {
        return mEncryptKey.getPrivateKey();
    }

    PGPPrivateKey getPrivateSigningKey() {
        return mSignKey.getPrivateKey();
    }

    int getSigningAlgorithm() {
        return mSignKey.getPublicKey().getAlgorithm();
    }

    PGPPublicKey getPublicEncryptionKey() {
        return mEncryptKey.getPublicKey();
    }

    public X509Certificate getBridgeCertificate() {
        return mBridgeCert;
    }

    public PrivateKey getServerLoginKey() {
        return mLoginKey;
    }

    /** Returns the first user ID in the key. */
    public String getUserId() {
        Iterator<?> uidIt = mAuthKey.getUserIDs();
        if (!uidIt.hasNext())
            throw new IllegalStateException("no UID in personal key");
        return (String) uidIt.next();
    }

    public String getFingerprint() {
        return Hex.toHexString(mAuthKey.getFingerprint());
    }

    /** Creates a {@link PersonalKey} from private keyring data.
     * X.509 bridge certificate is created from key data.
     */
    public static PersonalKey load(byte[] privateKeyData,
            char[] passphrase)
        throws KonException, IOException, PGPException, CertificateException, NoSuchProviderException {
        return load(privateKeyData, passphrase, null);
    }

    /** Creates a {@link PersonalKey} from private keyring data. */
    @SuppressWarnings("unchecked")
    public static PersonalKey load(byte[] privateKeyData,
            char[] passphrase,
            byte[] bridgeCertData)
            throws KonException, IOException, PGPException, CertificateException, NoSuchProviderException {
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, PGPUtils.FP_CALC);

        PGPSecretKey authKey = null;
        PGPSecretKey signKey = null;
        PGPSecretKey encrKey = null;

        // assign from key ring
        Iterator<PGPSecretKey> skeys = secRing.getSecretKeys();
        while (skeys.hasNext()) {
            PGPSecretKey key = skeys.next();
            if (key.isMasterKey()) {
                // master key: authentication / legacy: signing
                authKey = key;
            } else if (PGPUtils.isSigningKey(key.getPublicKey())) {
                // sub keys: encryption and signing / legacy: only encryption
                signKey = key;
            } else if (key.getPublicKey().isEncryptionKey()) {
                encrKey = key;
            }
        }
        // legacy: auth key is actually signing key
        if (signKey == null && authKey != null && authKey.isSigningKey()) {
            LOGGER.info("legacy key");
            signKey = authKey;
        }

        if (authKey == null || signKey == null || encrKey == null) {
            LOGGER.warning("something could not be found, "
                    +"sign="+signKey+ ", auth="+authKey+", encr="+encrKey);
            throw new KonException(KonException.Error.LOAD_KEY,
                    new PGPException("could not find all keys in key data"));
        }

        // decrypt private keys
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(
                // TODO need this?
                new JcaPGPDigestCalculatorProviderBuilder().build()
        )
                .setProvider(PGPUtils.PROVIDER)
                .build(passphrase);
        PGPKeyPair authKeyPair = PGPUtils.decrypt(authKey, decryptor);
        PGPKeyPair signKeyPair = PGPUtils.decrypt(signKey, decryptor);
        PGPKeyPair encryptKeyPair = PGPUtils.decrypt(encrKey, decryptor);

        // X.509 bridge certificate
        X509Certificate bridgeCert = bridgeCertData == null ?
                createX509Certificate(authKeyPair, signKeyPair, encryptKeyPair) :
                PGPUtils.loadX509Cert(bridgeCertData);

        return new PersonalKey(authKeyPair, signKeyPair, encryptKeyPair, bridgeCert);
    }

    private static X509Certificate createX509Certificate(
            PGPKeyPair authKP,
            PGPKeyPair signKP,
            PGPKeyPair encryptKP) throws IOException, KonException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        authKP.getPublicKey().encode(out);
        signKP.getPublicKey().encode(out);
        encryptKP.getPublicKey().encode(out);
        byte[] publicKeyRingData = out.toByteArray();

        try {
            return X509Bridge.createCertificate(authKP, publicKeyRingData);
        } catch (InvalidKeyException | IllegalStateException | NoSuchAlgorithmException |
                SignatureException | CertificateException | NoSuchProviderException |
                PGPException | IOException | OperatorCreationException ex) {
            LOGGER.log(Level.WARNING, "can't create X.509 certificate");
            throw new KonException(KonException.Error.LOAD_KEY, ex);
        }
    }
}
