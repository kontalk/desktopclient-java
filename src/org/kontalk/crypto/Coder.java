/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.Base64;
import org.kontalk.model.Account;
import org.kontalk.model.KonMessage;
import org.kontalk.model.User;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.XMPPUtils;

/**
 * Static methods for decryption and encryption of a message.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class Coder {
    private final static Logger LOGGER = Logger.getLogger(Coder.class.getName());

    /** Encryption status of a message. */
    public static enum Encryption {NOT, ENCRYPTED, DECRYPTED}
    /** Signing status of a message. */
    public static enum Signing {NOT, SIGNED, VERIFIED}

    /**
     * Errors that can occur during de-/encryption and verification.
     */
    public static enum Error {
        /** Some unknown error. */
        UNKNOWN_ERROR,
        /** Public key of sender not found. */
        KEY_UNAVAILABLE,
        /** Key data invalid. */
        INVALID_KEY,
        /** My private key does not match. */
        INVALID_PRIVATE_KEY,
        /** Invalid data / parsing failed. */
        INVALID_DATA,
        /** Integrity check failed. */
        INVALID_INTEGRITY,
        /** Invalid data / parsing failed of signature. */
        INVALID_SIGNATURE_DATA,
        /** Signature does not match sender. */
        INVALID_SIGNATURE,
        /** Recipient user id in decrypted data does not match id in my key. */
        INVALID_RECIPIENT,
        /** Sender user id in decrypted data does not match id in sender key. */
        INVALID_SENDER,

        //INVALID_MIME,
        //INVALID_TIMESTAMP,
    }

    /** Buffer size for encryption. It should always be a power of 2. */
    private static final int BUFFER_SIZE = 1 << 8;

    /**
     * Creates encrypted and signed message body. Errors that may occur are
     * saved to the message.
     * @param message
     * @return the encrypted and signed text.
     */
    public static byte[] processOutMessage(KonMessage message) {
        if (message.getEncryption() != Encryption.ENCRYPTED ||
                message.getDir() != KonMessage.Direction.OUT) {
            LOGGER.warning("message does not want to be encrypted");
            return null;
        }

        LOGGER.fine("encrypting message...");

        // clear security errors
        message.resetSecurityErrors();

        // get my key
        PersonalKey myKey = Account.getInstance().getPersonalKey();
        if (myKey == null) {
            LOGGER.warning("can't get personal key");
            message.addSecurityError(Error.UNKNOWN_ERROR);
            return null;
        }

        // get receiver key
        PGPPublicKey receiverKey = getKey(message);
        if (receiverKey == null)
            return null;

        // secure the message against the most basic attacks using Message/CPIM
        String from = myKey.getUserId(null);
        String to = PGP.getUserId(receiverKey, null) + "; ";
        String mime = "text/plain";
        CPIMMessage cpim = new CPIMMessage(from, to, new Date(), mime, message.getText());
        byte[] plainText = cpim.toByteArray();

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        //for (PGPPublicKey rcpt : mRecipients)
        encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(receiverKey));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(plainText);
        try { // catch all io and pgp exceptions

            OutputStream encryptedOut = encGen.open(out, new byte[BUFFER_SIZE]);

            // setup compressed data generator
            PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

            // setup signature generator
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator
                    (new BcPGPContentSignerBuilder(myKey.getSignKeyPair()
                        .getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1));
            sigGen.init(PGPSignature.BINARY_DOCUMENT, myKey.getSignKeyPair().getPrivateKey());

            PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
            spGen.setSignerUserID(false, myKey.getUserId(null));
            sigGen.setUnhashedSubpackets(spGen.generate());

            sigGen.generateOnePassVersion(false).encode(compressedOut);

            // Initialize literal data generator
            PGPLiteralDataGenerator literalGen = new PGPLiteralDataGenerator();
            OutputStream literalOut = literalGen.open(
                compressedOut,
                PGPLiteralData.BINARY,
                "",
                new Date(),
                new byte[BUFFER_SIZE]);

            // read the "in" stream, compress, encrypt and write to the "out" stream
            // this must be done if clear data is bigger than the buffer size
            // but there are other ways to optimize...
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buf)) > 0) {
                literalOut.write(buf, 0, len);
                try {
                    sigGen.update(buf, 0, len);
                } catch (SignatureException ex) {
                        LOGGER.log(Level.WARNING, "can't read data for signature", ex);
                        message.addSecurityError(Error.INVALID_SIGNATURE_DATA);
                        return null;
                }
            }

            in.close();
            literalGen.close();

            // gsenerate the signature, compress, encrypt and write to the "out" stream
            try {
                sigGen.generate().encode(compressedOut);
            } catch (SignatureException ex) {
                LOGGER.log(Level.WARNING, "can't create signature", ex);
                message.addSecurityError(Error.INVALID_SIGNATURE_DATA);
                return null;
            }
            compGen.close();
            encGen.close();

        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt message", ex);
            message.addSecurityError(Error.UNKNOWN_ERROR);
            return null;
        }

        LOGGER.fine("encryption successful");
        return out.toByteArray();
    }

    /**
     * Decrypts and verifies the body of a message. Errors that may occur are
     * saved to the message.
     * @param message
     */
    public static void processInMessage(KonMessage message) {
        // signing requires also encryption
        if (message.getEncryption() != Encryption.ENCRYPTED ||
                message.getDir() != KonMessage.Direction.IN) {
            LOGGER.warning("message not encrypted");
            return;
        }
        LOGGER.fine("decrypting encrypted message...");

        // clear security errors
        message.resetSecurityErrors();

        // get my key
        PersonalKey myKey = Account.getInstance().getPersonalKey();

        // get sender key
        PGPPublicKey senderKey = getKey(message);
        if (senderKey == null)
            return;

        // decrypt
        String decryptedBody = decryptAndVerify(message, myKey, senderKey);

        // parse encrypted CPIM content
        String myUID = myKey.getUserId(null);
        String senderUID = PGP.getUserId(senderKey, null);
        String text = parseCPIM(message, decryptedBody, myUID, senderUID);

        // TODO we may have a decrypted message stanza, process it
        //parseText(message);

        // check for errors that occured
        if (message.getSecurityErrors().isEmpty()) {
            // everything went better than expected
            LOGGER.fine("decryption successful");
            // TODO really overwrite?
            message.setDecryptedText(text);
        } else {
            LOGGER.warning("decryption failed");
        }
    }

    private static PGPPublicKey getKey(KonMessage message) {
        User user = message.getUser();
        if (!user.hasKey()) {
            LOGGER.warning("key not found for user, id: "+user.getID());
            message.addSecurityError(Error.KEY_UNAVAILABLE);
            return null;
        }
        PGPPublicKeyRing ring;
        try {
            ring = PGP.readPublicKeyring(user.getKey());
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't get keyring", ex);
            message.addSecurityError(Error.INVALID_KEY);
            return null;
        }
        PGPPublicKey senderKey = PGP.getMasterKey(ring);
        if (senderKey == null) {
            LOGGER.warning("can't find masterkey in keyring");
            message.addSecurityError(Error.INVALID_KEY);
            return null;
        }
        return senderKey;
    }

    private static String decryptAndVerify(KonMessage message,
            PersonalKey myKey,
            PGPPublicKey senderKey) {
        // note: the signature is inside the encrypted data

        byte[] encryptedData = Base64.decode(message.getText());

        PGPObjectFactory pgpFactory = new PGPObjectFactory(encryptedData);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try { // catch all io and pgp exceptions

            // the first object might be a PGP marker packet
            Object o = pgpFactory.nextObject();
            if (!(o instanceof PGPEncryptedDataList)) {
                o = pgpFactory.nextObject();
            }

            if (!(o instanceof PGPEncryptedDataList)) {
                LOGGER.warning("can't find encrypted data list in data");
                message.addSecurityError(Error.INVALID_DATA);
                return null;
            }
            PGPEncryptedDataList encDataList = (PGPEncryptedDataList) o;

            // check if secret key matches our encryption keyID
            Iterator<PGPPublicKeyEncryptedData> it = encDataList.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;
            long ourKeyID = myKey.getEncryptKeyPair().getPrivateKey().getKeyID();
            while (sKey == null && it.hasNext()) {
                pbe = it.next();
                if (pbe.getKeyID() == ourKeyID)
                    sKey = myKey.getEncryptKeyPair().getPrivateKey();
            }
            if (sKey == null || pbe == null) {
                LOGGER.warning("private key for messsage not found");
                message.addSecurityError(Error.INVALID_PRIVATE_KEY);
                return null;
            }

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            PGPObjectFactory plainFactory = new PGPObjectFactory(clear);

            Object object = plainFactory.nextObject();

            if (!(object instanceof PGPCompressedData)) {
                LOGGER.warning("data packet not compressed");
                message.addSecurityError(Error.INVALID_DATA);
                return null;
            }

            PGPCompressedData cData = (PGPCompressedData) object;
            PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

            object = pgpFact.nextObject();

            // the first object could be the signature list
            PGPOnePassSignature ops = null;
            if (object instanceof PGPOnePassSignatureList) {
                // there is a signature list, so we assume the message is signed
                // (makes sense)
                message.setSigning(Signing.SIGNED);

                // TODO out of bound exception possible?
                ops = ((PGPOnePassSignatureList) object).get(0);
                ops.init(new BcPGPContentVerifierBuilderProvider(), senderKey);
                object = pgpFact.nextObject();
            } else {
                LOGGER.warning("signature list not found");
                message.setSigning(Signing.NOT);
            }

            if (!(object instanceof PGPLiteralData)) {
                LOGGER.warning("unknown packet type: " + object.getClass().getName());
                message.addSecurityError(Error.INVALID_DATA);
                return null;
            }

            PGPLiteralData ld = (PGPLiteralData) object;
            InputStream unc = ld.getInputStream();
            int ch;
            while ((ch = unc.read()) >= 0) {
                outputStream.write(ch);
                if (ops != null)
                    try {
                        ops.update((byte) ch);
                    } catch (SignatureException ex) {
                        LOGGER.log(Level.WARNING, "can't read signature", ex);
                }
            }

            if (ops != null) {
                // verify signature
                object = pgpFact.nextObject();

                if (!(object instanceof PGPSignatureList)) {
                    LOGGER.warning("invalid signature packet");
                    message.addSecurityError(Error.INVALID_SIGNATURE_DATA);
                } else {
                    // TODO out of bound exception possible?
                    PGPSignature signature = ((PGPSignatureList) object).get(0);
                    boolean verified = false;
                    try {
                        verified = ops.verify(signature);
                    } catch (SignatureException ex) {
                        LOGGER.log(Level.WARNING, "can't verify signature", ex);
                    }
                    if (verified) {
                        // signature verification successful!
                        message.setSigning(Signing.VERIFIED);
                    } else {
                        LOGGER.warning("signature verification failed");
                        message.addSecurityError(Error.INVALID_SIGNATURE);
                    }
                }
            }

            // verify message integrity
            if (pbe.isIntegrityProtected()) {
                if (!pbe.verify()) {
                    LOGGER.warning("message integrity check failed");
                    message.addSecurityError(Error.INVALID_INTEGRITY);
                }
            } else {
                LOGGER.warning("message is not integrity protected");
            }

        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't decrypt message", ex);
            message.addSecurityError(Error.UNKNOWN_ERROR);
        }

        return outputStream.toString();
    }

    /**
     * Parse and verify CPIM ( https://tools.ietf.org/html/rfc3860 ).
     */
    private static String parseCPIM(KonMessage message,
            String text,
            String myUid,
            String senderKeyUID) {

        CPIMMessage cpimMessage;
        try {
            cpimMessage = CPIMMessage.parse(text);
        } catch (ParseException pe) {
            LOGGER.warning("can't find valid CPIM data");
            message.addSecurityError(Error.INVALID_DATA);
            return null;
        }

        String mime = cpimMessage.getMime();

        // check mime type
        // why is that necessary here?
        //if (!mime.equalsIgnoreCase("text/plain") &&
        //        !mime.equalsIgnoreCase(XMPPUtils.XML_XMPP_TYPE)) {
        //    LOGGER.warning("MIME type mismatch");
        //}
        // check that the recipient matches the full uid of the personal key

        if (!myUid.equals(cpimMessage.getTo())) {
            LOGGER.warning("destination does not match personal key");
            message.addSecurityError(Error.INVALID_RECIPIENT);
        }
        // check that the sender matches the full uid of the sender's key
        if (!senderKeyUID.equals(cpimMessage.getFrom())) {
            LOGGER.warning("sender doesn't match sender's key");
            message.addSecurityError(Error.INVALID_SENDER);
        }

        // TODO check DateTime (possibly compare it with <delay/>)

        String content = cpimMessage.getBody().toString();
        String plainText;
        if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(mime)) {
            LOGGER.fine("CPIM body has XMPP XML format");
            Message m;
            try {
                m = XMPPUtils.parseMessageStanza(content);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "can't parse XMPP XML string", ex);
                return null;
            }
            plainText = m.getBody() != null ? m.getBody() : null;
        } else {
            LOGGER.fine("CPIM body MIME type: "+mime);
            plainText = content;
        }

        return plainText;
    }

}
