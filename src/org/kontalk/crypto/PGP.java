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
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.Iterator;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUserAttributeSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.kontalk.util.EncodingUtils;

//import android.os.Parcel;

/** Some PGP utility method, mainly for use by {@link PersonalKey}. */
public final class PGP {

    /** Security provider: Bouncy Castle. */
    public static final String PROVIDER = "BC";

    /** Default EC curve used. */
    private static final String EC_CURVE = "P-256";

    /** Default RSA key length used. */
    private static final int RSA_KEY_LENGTH = 2048;

    // temporary flag for ECC experimentation
    private static final boolean EXPERIMENTAL_ECC = false;

    /** Singleton for converting a PGP key to a JCA key. */
    private static JcaPGPKeyConverter sKeyConverter;

    private PGP() {
    }

    public static final class PGPDecryptedKeyPairRing {
        /* Master (signing) key. */
        PGPKeyPair signKey;
        /* Sub (encryption) key. */
        PGPKeyPair encryptKey;

        public PGPDecryptedKeyPairRing(PGPKeyPair sign, PGPKeyPair encrypt) {
            this.signKey = sign;
            this.encryptKey = encrypt;
        }
    }

    public static final class PGPKeyPairRing {
        public PGPPublicKeyRing publicKey;
        public PGPSecretKeyRing secretKey;

        PGPKeyPairRing(PGPPublicKeyRing publicKey, PGPSecretKeyRing secretKey) {
            this.publicKey = publicKey;
            this.secretKey = secretKey;
        }
    }

    public static void registerProvider() {
        // register bouncy castle provider
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    /** Creates an ECDSA/ECDH key pair. */
    public static PGPDecryptedKeyPairRing create()
            throws NoSuchAlgorithmException, NoSuchProviderException, PGPException, InvalidAlgorithmParameterException {

        KeyPairGenerator gen;
        PGPKeyPair encryptKp, signKp;

        if (EXPERIMENTAL_ECC) {

            gen = KeyPairGenerator.getInstance("ECDH", PROVIDER);
            gen.initialize(new ECGenParameterSpec(EC_CURVE));

            encryptKp = new JcaPGPKeyPair(PGPPublicKey.ECDH, gen.generateKeyPair(), new Date());

            gen = KeyPairGenerator.getInstance("ECDSA", PROVIDER);
            gen.initialize(new ECGenParameterSpec(EC_CURVE));

            signKp = new JcaPGPKeyPair(PGPPublicKey.ECDSA, gen.generateKeyPair(), new Date());
        }

        else {

            gen = KeyPairGenerator.getInstance("RSA", PROVIDER);
            gen.initialize(RSA_KEY_LENGTH);

            encryptKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, gen.generateKeyPair(), new Date());

            gen = KeyPairGenerator.getInstance("RSA", PROVIDER);
            gen.initialize(RSA_KEY_LENGTH);

            signKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, gen.generateKeyPair(), new Date());
        }

        return new PGPDecryptedKeyPairRing(signKp, encryptKp);
    }

    /** Creates public and secret keyring for a given keypair. */
    public static PGPKeyPairRing store(PGPDecryptedKeyPairRing pair,
            String id,
            String passphrase)
                throws PGPException {

        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, pair.signKey,
            id, sha1Calc, null, null,
            new JcaPGPContentSignerBuilder(pair.signKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1),
            new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc)
                .setProvider(PROVIDER).build(passphrase.toCharArray()));

        keyRingGen.addSubKey(pair.encryptKey);

        PGPSecretKeyRing secRing = keyRingGen.generateSecretKeyRing();
        PGPPublicKeyRing pubRing = keyRingGen.generatePublicKeyRing();

        return new PGPKeyPairRing(pubRing, secRing);
    }

    /** Signs a public key with the given secret key. */
    public static PGPPublicKey signPublicKey(PGPKeyPair secret, PGPPublicKey keyToBeSigned, String id)
            throws PGPException, IOException, SignatureException {

    	return signPublicKey(secret, keyToBeSigned, id, PGPSignature.CASUAL_CERTIFICATION);
    }

    /** Signs a public key with the given secret key. */
    public static PGPPublicKey signPublicKey(PGPKeyPair secret, PGPPublicKey keyToBeSigned, String id, int certification)
            throws PGPException, IOException, SignatureException {

        PGPPrivateKey pgpPrivKey = secret.getPrivateKey();

        PGPSignatureGenerator       sGen = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(secret.getPublicKey().getAlgorithm(),
                PGPUtil.SHA1).setProvider(PROVIDER));

        sGen.init(certification, pgpPrivKey);

        return PGPPublicKey.addCertification(keyToBeSigned, id, sGen.generateCertification(id, keyToBeSigned));
    }

    /** Signs and add the given user attributes to the given public key. */
    public static PGPPublicKey signUserAttributes(PGPKeyPair secret, PGPPublicKey keyToBeSigned, PGPUserAttributeSubpacketVector attributes)
    		throws PGPException, SignatureException {

        return signUserAttributes(secret, keyToBeSigned, attributes, PGPSignature.POSITIVE_CERTIFICATION);
    }

    /** Signs and add the given user attributes to the given public key. */
    public static PGPPublicKey signUserAttributes(PGPKeyPair secret, PGPPublicKey keyToBeSigned, PGPUserAttributeSubpacketVector attributes, int certification)
    		throws PGPException, SignatureException {

        PGPPrivateKey pgpPrivKey = secret.getPrivateKey();

        PGPSignatureGenerator       sGen = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(secret.getPublicKey().getAlgorithm(),
                PGPUtil.SHA1).setProvider(PROVIDER));

        sGen.init(certification, pgpPrivKey);

        return PGPPublicKey.addCertification(keyToBeSigned, attributes,
        		sGen.generateCertification(attributes, keyToBeSigned));
    }

    public static PGPPublicKey revokeUserAttributes(PGPKeyPair secret, PGPPublicKey keyToBeSigned, PGPUserAttributeSubpacketVector attributes)
    		throws SignatureException, PGPException {

		return PGP.signUserAttributes(secret, keyToBeSigned, attributes,
				PGPSignature.CERTIFICATION_REVOCATION);
    }

    /** Revokes the given key. */
    public static PGPPublicKey revokeKey(PGPKeyPair secret)
            throws PGPException, IOException, SignatureException {

        PGPPrivateKey pgpPrivKey = secret.getPrivateKey();
        PGPPublicKey pgpPubKey = secret.getPublicKey();

        PGPSignatureGenerator       sGen = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(secret.getPublicKey().getAlgorithm(),
                PGPUtil.SHA1).setProvider(PROVIDER));

        sGen.init(PGPSignature.KEY_REVOCATION, pgpPrivKey);

        return PGPPublicKey.addCertification(pgpPubKey, sGen.generateCertification(pgpPubKey));
    }

    public static String getFingerprint(PGPPublicKey publicKey) {
    	return EncodingUtils.bytesToHex(publicKey.getFingerprint());
    }

    public static String getFingerprint(byte[] publicKeyring) throws IOException, PGPException {
    	PGPPublicKey pk = getMasterKey(publicKeyring);
    	return EncodingUtils.bytesToHex(pk.getFingerprint());
    }

    /** Returns the first user ID on the key that matches the given hostname. */
    public static String getUserId(PGPPublicKey key, String host) {
        // TODO ehm :)
        return (String) key.getUserIDs().next();
    }

    /** Returns the first user ID on the key that matches the given hostname. */
    public static String getUserId(byte[] publicKeyring, String host) throws IOException, PGPException {
    	PGPPublicKey pk = getMasterKey(publicKeyring);
    	return getUserId(pk, host);
    }

    /** Returns the first master key found in the given public keyring.
     *  Return null if no masterkey was found.
     */
    public static PGPPublicKey getMasterKey(PGPPublicKeyRing publicKeyring) {
        @SuppressWarnings("unchecked")
        Iterator<PGPPublicKey> iter = publicKeyring.getPublicKeys();
        while (iter.hasNext()) {
            PGPPublicKey pk = iter.next();
            if (pk.isMasterKey())
                return pk;
        }

        return null;
    }

    /** Returns the first master key found in the given public keyring. */
    public static PGPPublicKey getMasterKey(byte[] publicKeyring) throws IOException, PGPException {
    	return getMasterKey(readPublicKeyring(publicKeyring));
    }

    public static PGPPublicKey getEncryptionKey(PGPPublicKeyRing publicKeyring) {
        @SuppressWarnings("unchecked")
		Iterator<PGPPublicKey> iter = publicKeyring.getPublicKeys();
        while (iter.hasNext()) {
            PGPPublicKey pk = iter.next();
            if (pk.isEncryptionKey())
                return pk;
        }

        return null;
    }

    public static PGPPublicKeyRing readPublicKeyring(byte[] publicKeyring) throws IOException, PGPException {
        PGPObjectFactory reader = new PGPObjectFactory(publicKeyring);
        Object o = reader.nextObject();
        while (o != null) {
            if (o instanceof PGPPublicKeyRing)
            	return (PGPPublicKeyRing) o;

            o = reader.nextObject();
        }

        throw new PGPException("invalid keyring data.");
    }

    private static void ensureKeyConverter() {
    	if (sKeyConverter == null)
    		sKeyConverter = new JcaPGPKeyConverter().setProvider(PGP.PROVIDER);
    }

    public static PrivateKey convertPrivateKey(PGPPrivateKey key) throws PGPException {
    	ensureKeyConverter();
    	return sKeyConverter.getPrivateKey(key);
    }

    @SuppressWarnings("unchecked")
	public static PrivateKey convertPrivateKey(byte[] privateKeyData, String passphrase)
    		throws PGPException, IOException {

        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        // load the secret key ring
        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, fpr);

        // search and decrypt the master (signing key)
        // secret keys
        Iterator<PGPSecretKey> skeys = secRing.getSecretKeys();
        while (skeys.hasNext()) {
            PGPSecretKey key = skeys.next();
            PGPSecretKey sec = secRing.getSecretKey();

            if (key.isMasterKey())
                return convertPrivateKey(sec.extractPrivateKey(decryptor));
        }

        throw new PGPException("no suitable private key found.");
    }

    public static PublicKey convertPublicKey(PGPPublicKey key) throws PGPException {
    	ensureKeyConverter();
    	return sKeyConverter.getPublicKey(key);
    }

    public static PGPSecretKeyRing copySecretKeyRingWithNewPassword(byte[] privateKeyData,
            String oldPassphrase, String newPassphrase) throws PGPException, IOException {

        // load the secret key ring
        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, fpr);

        return copySecretKeyRingWithNewPassword(secRing, oldPassphrase, newPassphrase);
    }

    public static PGPSecretKeyRing copySecretKeyRingWithNewPassword(PGPSecretKeyRing secRing,
            String oldPassphrase, String newPassphrase) throws PGPException {

        PGPDigestCalculatorProvider sha1CalcProv = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1CalcProv)
            .setProvider(PGP.PROVIDER)
            .build(oldPassphrase.toCharArray());

        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PBESecretKeyEncryptor encryptor = new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc)
            .setProvider(PROVIDER).build(newPassphrase.toCharArray());

        return PGPSecretKeyRing.copyWithNewPassword(secRing, decryptor, encryptor);
    }


}
