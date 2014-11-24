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
import org.kontalk.KonException;
import org.kontalk.client.KonMessageListener;
import org.kontalk.model.Account;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.MessageContent;
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

    /**
     * Encryption status of a message.
     * Do not modify, only add! Ordinal used in database.
     */
    public static enum Encryption {NOT, ENCRYPTED, DECRYPTED}

    /**
     * Signing status of a message.
     * Do not modify, only add! Ordinal used in database.
     */
    public static enum Signing {NOT, SIGNED, VERIFIED, UNKNOWN}

    /**
     * Errors that can occur during de-/encryption and verification.
     * Do not modify, only add! Ordinal used in database.
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

    // please do not instantiate me
    private Coder() {
        throw new AssertionError();
    }

    /**
     * Creates encrypted and signed message body. Errors that may occur are
     * saved to the message.
     * @param message
     * @return the encrypted and signed text.
     */
    public static byte[] processOutMessage(KonMessage message) {
        if (message.getEncryption() != Encryption.DECRYPTED ||
                message.getDir() != KonMessage.Direction.OUT) {
            LOGGER.warning("message does not want to be encrypted");
            return null;
        }

        LOGGER.fine("encrypting message...");

        // clear security errors
        message.resetSecurityErrors();

        // get my key
        PersonalKey myKey;
        try {
            myKey = Account.getInstance().getPersonalKey();
        } catch (KonException ex) {
            LOGGER.log(Level.WARNING, "can't get personal key", ex);
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
        // TODO encrypt more possible content
        String text = message.getContent().getPlainText();
        CPIMMessage cpim = new CPIMMessage(from, to, new Date(), mime, text);
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

            // generate the signature, compress, encrypt and write to the "out" stream
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
    public static void processInMessage(InMessage message) {
        // signing requires also encryption
        if (message.getEncryption() != Encryption.ENCRYPTED ||
                message.getDir() != KonMessage.Direction.IN) {
            LOGGER.warning("message not encrypted");
            return;
        }
        LOGGER.info("decrypting encrypted message...");

        // clear security errors
        message.resetSecurityErrors();

        // get my key
        PersonalKey myKey;
        try {
            myKey = Account.getInstance().getPersonalKey();
        } catch (KonException ex) {
            LOGGER.log(Level.WARNING, "can't get personal key", ex);
            message.addSecurityError(Error.UNKNOWN_ERROR);
            return;
        }

        // get sender key
        PGPPublicKey senderKey = getKey(message);
        if (senderKey == null)
            return;

        // decrypt
        String decryptedBody = decryptAndVerify(message, myKey, senderKey);

        // parse encrypted CPIM content
        String myUID = myKey.getUserId(null);
        String senderUID = PGP.getUserId(senderKey, null);
        MessageContent decryptedContent = parseCPIM(message, decryptedBody, myUID, senderUID);

        // TODO we may have a decrypted message stanza, process it
        //parseText(message);

        // check for errors that occured
        if (message.getSecurityErrors().isEmpty()) {
            // everything went better than expected
            LOGGER.info("decryption successful");
            message.setDecryptedContent(decryptedContent);
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

        String encryptedContent = message.getContent().getEncryptedContent();
        byte[] encryptedData = Base64.decode(encryptedContent);

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
            Iterator<?> it = encDataList.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;
            long ourKeyID = myKey.getEncryptKeyPair().getPrivateKey().getKeyID();
            while (sKey == null && it.hasNext()) {
                Object i = it.next();
                if (!(i instanceof PGPPublicKeyEncryptedData))
                    continue;
                pbe = (PGPPublicKeyEncryptedData) it.next();
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
    private static MessageContent parseCPIM(KonMessage message,
            String text,
            String myUid,
            String senderKeyUID) {

        CPIMMessage cpimMessage;
        try {
            cpimMessage = CPIMMessage.parse(text);
        } catch (ParseException ex) {
            LOGGER.log(Level.WARNING, "can't find valid CPIM data", ex);
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
        MessageContent decryptedContent;
        if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(mime)) {
            LOGGER.info("CPIM body has XMPP XML format");
            Message m;
            try {
                m = XMPPUtils.parseMessageStanza(content);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "can't parse XMPP XML string", ex);
                return null;
            }
            LOGGER.info("decrypted message content: "+m.toXML());
            decryptedContent = KonMessageListener.parseMessageContent(m);
        } else {
            LOGGER.info("CPIM body MIME type: "+mime);
            decryptedContent = new MessageContent(content);
        }

        return decryptedContent;
    }

}
