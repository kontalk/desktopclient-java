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

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.logging.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.kontalk.misc.KonException;
import org.kontalk.util.EncodingUtils;

/**
 * Personal asymmetric encryption key.
 */
public final class PersonalKey {
    private final static Logger LOGGER = Logger.getLogger(PersonalKey.class.getName());

    /** (Server) Authentication key. */
    private final PGPKeyPair mAuthKey;
    /** Signing key. */
    private final PGPKeyPair mSignKey;
    /** En-/decryption key. */
    private final PGPKeyPair mEncryptKey;
    /** X.509 bridge certificate. */
    private final X509Certificate mBridgeCert;

    private PersonalKey(PGPKeyPair authKP,
            PGPKeyPair signKP,
            PGPKeyPair encryptKP,
            X509Certificate bridgeCert) {
        mAuthKey = authKP;
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

    PGPPublicKey getPublicEncryptionKey() {
        return mEncryptKey.getPublicKey();
    }

    public X509Certificate getBridgeCertificate() {
        return mBridgeCert;
    }

    public PrivateKey getBridgePrivateKey() throws PGPException {
    	return PGPUtils.convertPrivateKey(mAuthKey.getPrivateKey());
    }

    /** Returns the first user ID on the key that matches the given network. */
    public String getUserId() {
        PGPPublicKey key = mAuthKey.getPublicKey();
        Iterator<?> uidIt = key.getUserIDs();
        if (!uidIt.hasNext())
            throw new IllegalStateException("no UID in personal key");
        return (String) uidIt.next();
    }

    public String getFingerprint() {
    	return EncodingUtils.bytesToHex(mAuthKey.getPublicKey().getFingerprint());
    }

    /** Creates a {@link PersonalKey} from private and public key byte buffers. */
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
            }
            else {
                // sub keys: encryption and signing / legacy: only encryption
                int keyFlags = PGPUtils.getKeyFlags(key.getPublicKey());
                if ((keyFlags & PGPKeyFlags.CAN_SIGN) == PGPKeyFlags.CAN_SIGN)
                    signKey = key;
                else
                    encrKey = key;
            }
        }
        // legacy: auth key is actually signing key
        if (signKey == null && authKey != null && authKey.isSigningKey()) {
            LOGGER.info("loading legacy key");
            signKey = authKey;
        }

        if (authKey == null || signKey == null || encrKey == null)
            throw new KonException(KonException.Error.LOAD_KEY,
                    new PGPException("could not found all keys in key data"));

        // decrypt private
        PGPDigestCalculatorProvider calcProv = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(calcProv)
            .setProvider(PGPUtils.PROVIDER)
            .build(passphrase);
        PGPKeyPair authKeyPair = PGPUtils.decrypt(authKey, decryptor);
        PGPKeyPair signKeyPair = PGPUtils.decrypt(signKey, decryptor);
        PGPKeyPair encryptKeyPair = PGPUtils.decrypt(encrKey, decryptor);

        // X.509 bridge certificate
        X509Certificate bridgeCert = PGPUtils.loadX509Cert(bridgeCertData);

        return new PersonalKey(authKeyPair, signKeyPair, encryptKeyPair, bridgeCert);
    }
}
