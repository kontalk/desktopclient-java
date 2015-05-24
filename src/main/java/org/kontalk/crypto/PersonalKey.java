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
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.kontalk.util.EncodingUtils;

/**
 * Personal asymmetric encryption key.
 */
public final class PersonalKey {
    /** Decrypted master (signing) key. */
    private final PGPKeyPair mSignKey;
    /** Decrypted sub (encryption) key. */
    private final PGPKeyPair mEncryptKey;
    /** X.509 bridge certificate. */
    private final X509Certificate mBridgeCert;

    private PersonalKey(PGPKeyPair signKp, PGPKeyPair encryptKp, X509Certificate bridgeCert) {
        mSignKey = signKp;
        mEncryptKey = encryptKp;
        mBridgeCert = bridgeCert;
    }

    PGPPrivateKey getPrivateEncryptionKey() {
        return mEncryptKey.getPrivateKey();
    }

    PGPPublicKey getPublicEncryptionKey() {
        return mEncryptKey.getPublicKey();
    }

    public X509Certificate getBridgeCertificate() {
        return mBridgeCert;
    }

    public PrivateKey getBridgePrivateKey() throws PGPException {
    	return PGPUtils.convertPrivateKey(mSignKey.getPrivateKey());
    }

    /** Returns the first user ID on the key that matches the given network. */
    public String getUserId() {
        PGPPublicKey key = mSignKey.getPublicKey();
        Iterator<?> uidIt = key.getUserIDs();
        if (!uidIt.hasNext())
            throw new IllegalStateException("no UID in personal key");
        return (String) uidIt.next();
    }

    public String getFingerprint() {
    	return EncodingUtils.bytesToHex(mSignKey.getPublicKey().getFingerprint());
    }

    /** Creates a {@link PersonalKey} from private and public key byte buffers. */
    @SuppressWarnings("unchecked")
    public static PersonalKey load(byte[] privateKeyData,
            byte[] publicKeyData,
            String passphrase,
            byte[] bridgeCertData)
            throws PGPException, IOException, CertificateException, NoSuchProviderException {

        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, fpr);
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, fpr);

        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGPUtils.PROVIDER)
            .build(passphrase.toCharArray());

        PGPKeyPair signKp, encryptKp;

        PGPPublicKey  signPub = null;
        PGPPrivateKey signPriv = null;
        PGPPublicKey   encPub = null;
        PGPPrivateKey  encPriv = null;

        // public keys
        Iterator<PGPPublicKey> pkeys = pubRing.getPublicKeys();
        while (pkeys.hasNext()) {
            PGPPublicKey key = pkeys.next();
            if (key.isMasterKey()) {
                // master (signing) key
                signPub = key;
            }
            else {
                // sub (encryption) key
                encPub = key;
            }
        }

        // secret keys
        Iterator<PGPSecretKey> skeys = secRing.getSecretKeys();
        while (skeys.hasNext()) {
            PGPSecretKey key = skeys.next();
            if (key.isMasterKey()) {
                // master (signing) key
                signPriv = key.extractPrivateKey(decryptor);
            }
            else {
                // sub (encryption) key
                encPriv = key.extractPrivateKey(decryptor);
            }
        }

        // X.509 bridge certificate
        X509Certificate bridgeCert = X509Bridge.load(bridgeCertData);

        if (encPriv == null ||
                encPub == null ||
                signPriv == null ||
                signPub == null ||
                bridgeCert == null)
            throw new PGPException("invalid key data");

        signKp = new PGPKeyPair(signPub, signPriv);
        encryptKp = new PGPKeyPair(encPub, encPriv);
        return new PersonalKey(signKp, encryptKp, bridgeCert);
    }
}
