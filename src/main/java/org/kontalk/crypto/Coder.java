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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.util.EncodingUtils;
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
import org.kontalk.system.Downloader;
import org.kontalk.client.KonMessageListener;
import org.kontalk.crypto.PGPUtils.PGPCoderKey;
import org.kontalk.model.InMessage;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.OutMessage;
import org.kontalk.model.User;
import org.kontalk.system.AccountLoader;
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
        /** Own personal key not found. */
        MY_KEY_UNAVAILABLE,
        /** Public key of sender not found. */
        KEY_UNAVAILABLE,
        /** Key data invalid. */
        INVALID_KEY,
        /** My private key does not match. */
        INVALID_PRIVATE_KEY,
        /** Invalid data / parsing failed. */
        INVALID_DATA,
        /** No integrity protection found. */
        NO_INTEGRITY,
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

    private static class KeysResult {
        PersonalKey myKey = null;
        PGPCoderKey otherKey = null;
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
    }

    private static class DecryptionResult {
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
        Signing signing = Signing.UNKNOWN;
        boolean decrypted = false;
    }

    private static class ParsingResult {
        MessageContent content = null;
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
    }

    // please do not instantiate me
    private Coder() {
        throw new AssertionError();
    }

    /**
     * Creates encrypted and signed message body.
     * Errors that may occur are saved to the message.
     * @param message
     * @return the encrypted and signed text.
     */
    public static Optional<byte[]> processOutMessage(OutMessage message) {
        if (message.getCoderStatus().getEncryption() != Encryption.DECRYPTED) {
            LOGGER.warning("message does not want to be encrypted");
            return Optional.empty();
        }

        LOGGER.info("encrypting message...");

        // get keys
        KeysResult keys = getKeys(message.getUser());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setSecurityErrors(keys.errors);
            return Optional.empty();
        }

        // secure the message against the most basic attacks using Message/CPIM
        String from = keys.myKey.getUserId();
        String to = keys.otherKey.userID + "; ";
        String mime = "text/plain";
        // TODO encrypt more possible content
        String text = message.getContent().getPlainText();
        CPIMMessage cpim = new CPIMMessage(from, to, new Date(), mime, text);
        byte[] plainText;
        try {
            plainText = cpim.toByteArray();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, "UTF-8 not supported", ex);
            plainText = cpim.toString().getBytes();
        }

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        //for (PGPPublicKey rcpt : mRecipients)
        encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(keys.otherKey.encryptKey));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(plainText);
        try { // catch all io and pgp exceptions

            OutputStream encryptedOut = encGen.open(out, new byte[BUFFER_SIZE]);

            // setup compressed data generator
            PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

            // setup signature generator
            int algo = keys.myKey.getPublicEncryptionKey().getAlgorithm();
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator(
                    new BcPGPContentSignerBuilder(algo, HashAlgorithmTags.SHA256));
            sigGen.init(PGPSignature.BINARY_DOCUMENT, keys.myKey.getPrivateSigningKey());

            PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
            spGen.setSignerUserID(false, keys.myKey.getUserId());
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
                        message.setSecurityErrors(EnumSet.of(Error.INVALID_SIGNATURE_DATA));
                        return Optional.empty();
                }
            }

            in.close();
            literalGen.close();

            // generate the signature, compress, encrypt and write to the "out" stream
            try {
                sigGen.generate().encode(compressedOut);
            } catch (SignatureException ex) {
                LOGGER.log(Level.WARNING, "can't create signature", ex);
                message.setSecurityErrors(EnumSet.of(Error.INVALID_SIGNATURE_DATA));
                return Optional.empty();
            }
            compGen.close();
            encGen.close();

        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt message", ex);
            message.setSecurityErrors(EnumSet.of(Error.UNKNOWN_ERROR));
            return Optional.empty();
        }

        LOGGER.info("encryption successful");
        return Optional.of(out.toByteArray());
    }

    /**
     * Decrypt and verify the body of a message. Sets the encryption and signing
     * status of the message and errors that may occur are saved to the message.
     * @param message
     */
    public static void processInMessage(InMessage message) {
        // signing requires also encryption
        if (!message.getCoderStatus().isEncrypted()) {
            LOGGER.warning("message not encrypted");
            return;
        }
        LOGGER.info("decrypting encrypted message...");

        // get keys
        KeysResult keys = getKeys(message.getUser());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setSecurityErrors(keys.errors);
            return;
        }

        // decrypt
        String encryptedContent = message.getContent().getEncryptedContent();
        if (encryptedContent.isEmpty()) {
            LOGGER.warning("no encrypted data in encrypted message");
        }
        byte[] encryptedData = Base64.getDecoder().decode(encryptedContent);
        InputStream encryptedStream = new ByteArrayInputStream(encryptedData);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DecryptionResult decResult = decryptAndVerify(encryptedStream,
                outStream,
                keys.myKey,
                keys.otherKey.signKey);
        EnumSet<Coder.Error> allErrors = decResult.errors;
        message.setSigning(decResult.signing);

        // parse
        ParsingResult parsingResult = null;
        if (decResult.decrypted) {
            // parse encrypted CPIM content
            String myUID = keys.myKey.getUserId();
            String senderUID = keys.otherKey.userID;
            String encrText = EncodingUtils.getString(
                    outStream.toByteArray(),
                    CPIMMessage.CHARSET);
            parsingResult = parseCPIM(encrText, myUID, senderUID);
            allErrors.addAll(parsingResult.errors);
        }

        // set errors
        message.setSecurityErrors(allErrors);

        if (parsingResult != null && parsingResult.content != null) {
            // everything went better than expected
            LOGGER.info("decryption successful");
            message.setDecryptedContent(parsingResult.content);
        } else {
            LOGGER.warning("decryption failed");
        }
    }

    /**
     * Decrypt and verify a downloaded attachment file. Sets the encryption and
     * signing status of the message attachment and errors that may occur are
     * saved to the message.
     * @param message
     */
    public static void processAttachment(InMessage message) {
        if (!message.getContent().getAttachment().isPresent()) {
            LOGGER.warning("no attachment in message");
            return;
        }

        Attachment attachment = message.getContent().getAttachment().get();

        if (!attachment.getCoderStatus().isEncrypted()) {
            LOGGER.warning("attachment not encrypted");
            return;
        }

        if (attachment.getFileName().isEmpty()) {
            LOGGER.warning("no filename in attachment");
            return;
        }

        File baseDir = Downloader.getInstance().getBaseDir();
        File inFile = new File(baseDir, attachment.getFileName());

        InputStream encryptedStream;
        try {
            encryptedStream = new FileInputStream(inFile);
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.WARNING,
                    "attachment file not found: "+inFile.getAbsolutePath(),
                    ex);
            return;
        }

        LOGGER.info("decrypting encrypted attachment...");

        // get keys
        KeysResult keys = getKeys(message.getUser());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setAttachmentErrors(keys.errors);
            return;
        }

        // open out stream
        String base = FilenameUtils.getBaseName(inFile.getName());
        String ext = FilenameUtils.getExtension(inFile.getName());
        File outFile = new File(baseDir, base + "_dec." + ext);
        if (outFile.exists()) {
            LOGGER.warning("encrypted file already exists: "+outFile.getAbsolutePath());
            return;
        }
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(outFile);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't open output file", ex);
            return;
        }

        // decrypt
        DecryptionResult decResult = decryptAndVerify(encryptedStream,
                outStream,
                keys.myKey,
                keys.otherKey.signKey);
        message.setAttachmentErrors(keys.errors);
        message.setAttachmentSigning(decResult.signing);

        // check for errors
        if (!decResult.decrypted) {
            LOGGER.info("attachment decryption failed");
            return;
        }

        // set new filename
        message.setDecryptedAttachment(outFile.getName());
        LOGGER.info("attachment decryption successful");
    }

    private static KeysResult getKeys(User user) {
        KeysResult result = new KeysResult();

        Optional<PersonalKey> optMyKey = AccountLoader.getInstance().getPersonalKey();
        if (!optMyKey.isPresent()) {
            LOGGER.log(Level.WARNING, "can't get personal key");
            result.errors.add(Error.MY_KEY_UNAVAILABLE);
            return result;
        }
        result.myKey = optMyKey.get();

        if (!user.hasKey()) {
            LOGGER.warning("key not found for user, id: "+user.getID());
            result.errors.add(Error.KEY_UNAVAILABLE);
            return result;
        }

        Optional<PGPCoderKey> optKey = PGPUtils.readPublicKey(user.getKey());
        if (!optKey.isPresent()) {
            LOGGER.warning("can't get sender key");
            result.errors.add(Error.INVALID_KEY);
            return result;
        }
        result.otherKey = optKey.get();

        return result;
    }

    /**
     * Decrypt, verify and write input stream to output stream.
     * Output stream is closed.
     */
    private static DecryptionResult decryptAndVerify(InputStream encryptedStream,
            OutputStream outStream,
            PersonalKey myKey,
            PGPPublicKey senderSigningKey) {
        // note: the signature is inside the encrypted data

        DecryptionResult result = new DecryptionResult();

        PGPObjectFactory pgpFactory = new PGPObjectFactory(encryptedStream);

        try { // catch all IO and PGP exceptions

            // the first object might be a PGP marker packet
            Object o = pgpFactory.nextObject(); // nullable
            if (!(o instanceof PGPEncryptedDataList)) {
                o = pgpFactory.nextObject(); // nullable
            }

            if (!(o instanceof PGPEncryptedDataList)) {
                LOGGER.warning("can't find encrypted data list in data");
                result.errors.add(Error.INVALID_DATA);
                return result;
            }
            PGPEncryptedDataList encDataList = (PGPEncryptedDataList) o;

            // check if secret key matches our encryption keyID
            Iterator<?> it = encDataList.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;
            long myKeyID = myKey.getPrivateEncryptionKey().getKeyID();
            while (sKey == null && it.hasNext()) {
                Object i = it.next();
                if (!(i instanceof PGPPublicKeyEncryptedData))
                    continue;
                pbe = (PGPPublicKeyEncryptedData) i;
                if (pbe.getKeyID() == myKeyID)
                    sKey = myKey.getPrivateEncryptionKey();
            }
            if (sKey == null || pbe == null) {
                LOGGER.warning("private key for message not found");
                result.errors.add(Error.INVALID_PRIVATE_KEY);
                return result;
            }

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            PGPObjectFactory plainFactory = new PGPObjectFactory(clear);

            Object object = plainFactory.nextObject(); // nullable

            if (!(object instanceof PGPCompressedData)) {
                LOGGER.warning("data packet not compressed");
                result.errors.add(Error.INVALID_DATA);
                return result;
            }

            PGPCompressedData cData = (PGPCompressedData) object;
            PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

            object = pgpFact.nextObject(); // nullable

            // the first object could be the signature list
            // get signature from it
            PGPOnePassSignature ops = null;
            if (object instanceof PGPOnePassSignatureList) {
                PGPOnePassSignatureList signatureList = (PGPOnePassSignatureList) object;
                // there is a signature list, so we assume the message is signed
                // (makes sense)
                result.signing = Signing.SIGNED;

                if (signatureList.isEmpty()) {
                    LOGGER.warning("signature list is empty");
                    result.errors.add(Error.INVALID_SIGNATURE_DATA);
                } else {
                    ops = signatureList.get(0);
                    ops.init(new BcPGPContentVerifierBuilderProvider(), senderSigningKey);
                }
                object = pgpFact.nextObject(); // nullable
            } else {
                LOGGER.warning("signature list not found");
                result.signing = Signing.NOT;
            }

            if (!(object instanceof PGPLiteralData)) {
                LOGGER.warning("unknown packet type: " + object.getClass().getName());
                result.errors.add(Error.INVALID_DATA);
                return result;
            }

            PGPLiteralData ld = (PGPLiteralData) object;
            InputStream unc = ld.getInputStream();
            int ch;
            while ((ch = unc.read()) >= 0) {
                outStream.write(ch);
                if (ops != null)
                    try {
                        ops.update((byte) ch);
                    } catch (SignatureException ex) {
                        LOGGER.log(Level.WARNING, "can't read signature", ex);
                }
            }
            outStream.close();
            result.decrypted = true;

            if (ops != null) {
                result = verifySignature(result, pgpFact, ops);
            }

            // verify message integrity
            if (pbe.isIntegrityProtected()) {
                if (!pbe.verify()) {
                    LOGGER.warning("message integrity check failed");
                    result.errors.add(Error.INVALID_INTEGRITY);
                }
            } else {
                LOGGER.warning("message is not integrity protected");
                result.errors.add(Error.NO_INTEGRITY);
            }

        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't decrypt message", ex);
            result.errors.add(Error.UNKNOWN_ERROR);
        }

        return result;
    }

    private static DecryptionResult verifySignature(DecryptionResult result,
            PGPObjectFactory pgpFact,
            PGPOnePassSignature ops) throws PGPException, IOException {
        Object object = pgpFact.nextObject(); // nullable
        if (!(object instanceof PGPSignatureList)) {
            LOGGER.warning("invalid signature packet");
            result.errors.add(Error.INVALID_SIGNATURE_DATA);
            return result;
        }

        PGPSignatureList signatureList = (PGPSignatureList) object;
        if (signatureList.isEmpty()) {
            LOGGER.warning("no signature in signature list");
            result.errors.add(Error.INVALID_SIGNATURE_DATA);
            return result;
        }

        PGPSignature signature = signatureList.get(0);
        boolean verified = false;
        try {
            verified = ops.verify(signature);
        } catch (SignatureException ex) {
            LOGGER.log(Level.WARNING, "can't verify signature", ex);
        }
        if (verified) {
            // signature verification successful!
            result.signing = Signing.VERIFIED;
        } else {
            LOGGER.warning("signature verification failed");
            result.errors.add(Error.INVALID_SIGNATURE);
        }
        return result;
    }

    /**
     * Parse and verify CPIM ( https://tools.ietf.org/html/rfc3860 ).
     */
    private static ParsingResult parseCPIM(
            String text,
            String myUid,
            String senderKeyUID) {

        ParsingResult result = new ParsingResult();

        CPIMMessage cpimMessage;
        try {
            cpimMessage = CPIMMessage.parse(text);
        } catch (ParseException ex) {
            LOGGER.log(Level.WARNING, "can't find valid CPIM data", ex);
            result.errors.add(Error.INVALID_DATA);
            return result;
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
            result.errors.add(Error.INVALID_RECIPIENT);
        }
        // check that the sender matches the full uid of the sender's key
        if (!senderKeyUID.equals(cpimMessage.getFrom())) {
            LOGGER.warning("sender doesn't match sender's key");
            result.errors.add(Error.INVALID_SENDER);
        }
        // maybe add: check DateTime (possibly compare it with <delay/>)

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

        result.content = decryptedContent;
        return result;
    }
}
