/*
 * Kontalk Java client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.kontalk.misc.KonException;

/** Some PGP utility method, mainly for use by {@link PersonalKey}. */
public final class PGPUtils {
    private static final Logger LOGGER = Logger.getLogger(PGPUtils.class.getName());

    /** Security provider: Bouncy Castle. */
    public static final String PROVIDER = "BC";

    /** The fingerprint calculator to use whenever it is needed. */
    static final KeyFingerPrintCalculator FP_CALC = new BcKeyFingerprintCalculator();

    /** Singleton for converting a PGP key to a JCA key. */
    private static JcaPGPKeyConverter sKeyConverter;

    private PGPUtils() {}

    /**
     * A contacts public keys for encryption and signing together with UID and
     * fingerprint (from signing key).
     */
    public static final class PGPCoderKey {
        final PGPPublicKey encryptKey;
        public final PGPPublicKey signKey;
        public final String userID;
        public final String fingerprint;
        public final byte[] rawKey;

        PGPCoderKey(PGPPublicKey encryptKey, PGPPublicKey signKey,
                String userID, String fingerprint, byte[] rawKey) {
            this.encryptKey = encryptKey;
            this.signKey = signKey;
            this.userID = userID;
            this.fingerprint = fingerprint;
            this.rawKey = rawKey;
        }
    }

    public static void registerProvider() {
        // register bouncy castle provider
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public static byte[] mayDisarm(InputStream input) throws IOException {
        return IOUtils.toByteArray(PGPUtil.getDecoderStream(input));
    }

    /**
     * Read a public key from ASCII armored key ring data.
     */
    public static Optional<PGPCoderKey> readPublicKey(String armoredInput) {
        try {
            return readPublicKey(IOUtils.toByteArray(
                    PGPUtil.getDecoderStream(IOUtils.toInputStream(armoredInput, "UTF-8"))));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read armored input", ex);
            return Optional.empty();
        }
    }

    /**
     * Read a public key from key ring byte data.
     */
    public static Optional<PGPCoderKey> readPublicKey(byte[] publicKeyring) {
        PGPPublicKey encryptKey = null;
        PGPPublicKey signKey = null;
        // for legacy keyring
        PGPPublicKey authKey = null;
        String uid = null;
        String fp = null;

        PGPPublicKeyRing keyRing = keyRingOrNull(publicKeyring);
        if (keyRing == null)
            return Optional.empty();

        Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys();
        while (keyIter.hasNext()) {
            PGPPublicKey key = keyIter.next();
            if (key.isMasterKey()) {
                authKey = key;
                fp = Hex.toHexString(key.getFingerprint());
                Iterator<?> uidIt = key.getUserIDs();
                if (uidIt.hasNext())
                    uid = (String) uidIt.next();
                // TODO if more than one UID?
            } else if (isSigningKey(key)) {
                signKey = key;
            } else if (key.isEncryptionKey()) {
                encryptKey = key;
            }
        }

        // legacy: auth key is actually signing key
        if (signKey == null && authKey != null) {
            LOGGER.info("loading legacy public key, uid: "+uid);
            signKey = authKey;
        }

        if (encryptKey == null || signKey == null || uid == null || fp == null) {
            LOGGER.warning("can't find public keys in key ring, uid: "+uid);
            return Optional.empty();
        }
        return Optional.of(new PGPCoderKey(encryptKey, signKey, uid, fp, publicKeyring));
    }

    public static X509Certificate loadX509Cert(byte[] certData)
            throws CertificateException, NoSuchProviderException {

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", PROVIDER);
        InputStream in = new ByteArrayInputStream(certData);

        return (X509Certificate) certFactory.generateCertificate(in);
    }

    private static void ensureKeyConverter() {
        if (sKeyConverter == null)
            sKeyConverter = new JcaPGPKeyConverter().setProvider(PGPUtils.PROVIDER);
    }

    static PrivateKey convertPrivateKey(PGPPrivateKey key) throws PGPException {
        ensureKeyConverter();
    	return sKeyConverter.getPrivateKey(key);
    }

    static PublicKey convertPublicKey(PGPPublicKey key) throws PGPException {
        ensureKeyConverter();
        return sKeyConverter.getPublicKey(key);
    }

    private static int getKeyFlags(PGPPublicKey key) {
        @SuppressWarnings("unchecked")
        Iterator<PGPSignature> sigs = key.getSignatures();
        while (sigs.hasNext()) {
            PGPSignature sig = sigs.next();
            PGPSignatureSubpacketVector subpackets = sig.getHashedSubPackets();
            if (subpackets == null)
                return 0;
            return subpackets.getKeyFlags();
        }
        return 0;
    }

    static boolean isSigningKey(PGPPublicKey key) {
        int keyFlags = getKeyFlags(key);
        return (keyFlags & PGPKeyFlags.CAN_SIGN) == PGPKeyFlags.CAN_SIGN;
    }

    static PGPKeyPair decrypt(PGPSecretKey secretKey, PBESecretKeyDecryptor dec) throws KonException {
        try {
            return new PGPKeyPair(secretKey.getPublicKey(), secretKey.extractPrivateKey(dec));
        } catch (PGPException ex) {
            LOGGER.log(Level.WARNING, "failed", ex);
            throw new KonException(KonException.Error.LOAD_KEY_DECRYPT, ex);
        }
    }

    public static PGPSecretKeyRing copySecretKeyRingWithNewPassword(byte[] privateKeyData,
            char[] oldPassphrase, char[] newPassphrase) throws PGPException, IOException, KonException {

        // load the secret key ring
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, FP_CALC);

        PGPDigestCalculatorProvider calcProv = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(calcProv)
            .setProvider(PGPUtils.PROVIDER)
            .build(oldPassphrase);

        PGPDigestCalculator calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA256);
        PBESecretKeyEncryptor encryptor = new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, calc)
            .setProvider(PROVIDER).build(newPassphrase);

        try {
            return PGPSecretKeyRing.copyWithNewPassword(secRing, decryptor, encryptor);
        } catch (PGPException ex) {
            // treat this special, cause most like the decryption password was wrong
            throw new KonException(KonException.Error.CHANGE_PASS_COPY, ex);
        }
    }

    public static long parseKeyIDFromSignature(String signatureData) {
        Object o;
        try {
            JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(
                    PGPUtil.getDecoderStream(IOUtils.toInputStream(signatureData, "UTF-8")));
            o = pgpFact.nextObject();
            if (o instanceof PGPCompressedData) {
                PGPCompressedData data = (PGPCompressedData) o;
                pgpFact = new JcaPGPObjectFactory(data.getDataStream());
                o = pgpFact.nextObject();
            }
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't get signature object", ex);
            return 0;
        }

        if (!(o instanceof PGPSignatureList)) {
            LOGGER.warning("object not signature list");
            return 0;
        }
        PGPSignatureList signList = (PGPSignatureList) o;
        if (signList.size() > 1) {
            LOGGER.warning("more than one signature in signature list");
        }
        if (signList.isEmpty()) {
            LOGGER.warning("signature list is empty");
            return 0;
        }

        return signList.get(0).getKeyID();
    }

    private static PGPPublicKeyRing keyRingOrNull(byte[] keyData) {
        PGPPublicKeyRingCollection keyRingCollection;
        try {
            keyRingCollection = new PGPPublicKeyRingCollection(keyData, FP_CALC);
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't read public key ring", ex);
            return null;
        }

        if (keyRingCollection.size() > 1) {
            LOGGER.warning("more than one key ring in collection");
        }

        Iterator<PGPPublicKeyRing> keyRingIter = keyRingCollection.getKeyRings();
        if (!keyRingIter.hasNext()) {
            LOGGER.warning("no key ring in collection");
            return null;
        }
        return keyRingIter.next();
    }

    private static final Pattern UID_PATTERN =
            Pattern.compile("(^.+?)( \\((.+)\\))?( <([A-Za-z0-9\\._%+-]+@[A-Za-z0-9\\.-]+)>$)?");

    /**
     * Parses a PGP user id string and returns exactly three strings (in this
     * order): (1) user name, (2) comment and (3) email address.
     * All strings are optional and empty if not found in user id.
     * Email address maybe invalid to RFC-standards.
     */
    public static String[] parseUID(String userID) {
        Matcher matcher = UID_PATTERN.matcher(userID);
        if (!matcher.matches() || matcher.groupCount() < 5)
            return new String[]{"", "", ""};

        return new String[]{StringUtils.defaultString(matcher.group(1)),
            StringUtils.defaultString(matcher.group(3)),
            StringUtils.defaultString(matcher.group(5))};
    }
}
