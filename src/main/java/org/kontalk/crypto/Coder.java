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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Message;
import org.kontalk.client.KonMessageListener;
import org.kontalk.crypto.PGPUtils.PGPCoderKey;
import org.kontalk.model.InMessage;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.OutMessage;
import org.kontalk.model.Contact;
import org.kontalk.system.AccountLoader;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.XMPPUtils;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Static methods for decryption and encryption of a message.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Coder {
    private static final Logger LOGGER = Logger.getLogger(Coder.class.getName());

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

    private static final HashMap<Contact, PGPCoderKey> KEY_MAP = new HashMap<>();

    private static class KeysResult {
        PersonalKey myKey = null;
        PGPCoderKey otherKey = null;
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
    }

    private static class DecryptionResult {
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
        Signing signing = Signing.UNKNOWN;
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
    public static Optional<byte[]> encryptMessage(OutMessage message) {
        return encryptData(message, message.getContent().getPlainText(), "text/plain");
    }

    public static Optional<byte[]> encryptStanza(OutMessage message, String xml) {
        String data = "<xmpp xmlns='jabber:client'>" + xml + "</xmpp>";
        return encryptData(message, data, "application/xmpp+xml");
    }

    private static Optional<byte[]> encryptData(OutMessage message, String data, String mime) {
        if (message.getCoderStatus().getEncryption() != Encryption.DECRYPTED) {
            LOGGER.warning("message does not want to be encrypted");
            return Optional.empty();
        }

        // get keys
        KeysResult keys = getKeys(message.getContact());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setSecurityErrors(keys.errors);
            return Optional.empty();
        }

        // secure the message against the most basic attacks using Message/CPIM
        // [for Android client - dont know if its useful, but doesnt hurt]
        String from = keys.myKey.getUserId();
        String to = keys.otherKey.userID + "; ";

        CPIMMessage cpim = new CPIMMessage(from, to, new Date(), mime, data);
        byte[] plainText;
        try {
            plainText = cpim.toByteArray();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, "UTF-8 not supported", ex);
            plainText = cpim.toString().getBytes();
        }

        ByteArrayInputStream in = new ByteArrayInputStream(plainText);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encryptAndSign(in, out, keys.myKey, keys.otherKey.encryptKey);
        } catch(IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt message", ex);
            message.setSecurityErrors(EnumSet.of(Error.UNKNOWN_ERROR));
            return Optional.empty();
        }

        LOGGER.info("message encryption successful");
        return Optional.of(out.toByteArray());
    }

    public static Optional<File> encryptAttachment(OutMessage message) {
        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in out-message");
            return Optional.empty();
        }
        Attachment attachment = optAttachment.get();

        // get keys
        KeysResult keys = getKeys(message.getContact());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setAttachmentErrors(keys.errors);
            return Optional.empty();
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("kontalk_enc_att", ".dat");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't create temporary file.", ex);
            return Optional.empty();
        }

        try (FileInputStream in = new FileInputStream(attachment.getFile().toFile());
                FileOutputStream out = new FileOutputStream(tempFile)) {
            encryptAndSign(in, out, keys.myKey, keys.otherKey.encryptKey);
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt attachment", ex);
            return Optional.empty();
        }

        LOGGER.info("attachment encryption successful");
        return Optional.of(tempFile);
    }

    /**
     * Decrypt and verify the body of a message. Sets the encryption and signing
     * status of the message and errors that may occur are saved to the message.
     * @param message
     */
    public static void decryptMessage(InMessage message) {
        // signing requires also encryption
        if (!message.getCoderStatus().isEncrypted()) {
            LOGGER.warning("message not encrypted");
            return;
        }

        // get keys
        KeysResult keys = getKeys(message.getContact());
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

        InputStream encryptedIn = new ByteArrayInputStream(encryptedData);
        ByteArrayOutputStream plainOut = new ByteArrayOutputStream();
        DecryptionResult decResult;
        try {
            decResult = decryptAndVerify(encryptedIn, plainOut,
                    keys.myKey, keys.otherKey.signKey);
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't decrypt message", ex);
            return;
        }
        EnumSet<Coder.Error> allErrors = decResult.errors;
        message.setSigning(decResult.signing);

        // parse decrypted CPIM content
        String myUID = keys.myKey.getUserId();
        String senderUID = keys.otherKey.userID;
        String decrText = EncodingUtils.getString(
                plainOut.toByteArray(),
                CPIMMessage.CHARSET);
        ParsingResult parsingResult = parseCPIM(decrText, myUID, senderUID);
        allErrors.addAll(parsingResult.errors);

        // set errors
        message.setSecurityErrors(allErrors);

        if (parsingResult.content != null) {
            // everything went better than expected
            LOGGER.info("message decryption successful");
            message.setDecryptedContent(parsingResult.content);
        } else {
            LOGGER.warning("message decryption failed");
        }
    }

    /**
     * Decrypt and verify a downloaded attachment file. Sets the encryption and
     * signing status of the message attachment and errors that may occur are
     * saved to the message.
     */
    public static void decryptAttachment(InMessage message, Path baseDir) {
        Optional<Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in in-message");
            return;
        }
        Attachment attachment = optAttachment.get();

        // get keys
        KeysResult keys = getKeys(message.getContact());
        if (keys.myKey == null || keys.otherKey == null) {
            message.setAttachmentErrors(keys.errors);
            return;
        }

        // in file
        File inFile = baseDir.resolve(attachment.getFile()).toFile();
        // out file
        String base = FilenameUtils.getBaseName(inFile.getName());
        String ext = FilenameUtils.getExtension(inFile.getName());
        File outFile = baseDir.resolve(base + "_dec." + ext).toFile();
        if (outFile.exists()) {
            LOGGER.warning("encrypted file already exists: "+outFile.getAbsolutePath());
            return;
        }

        // decrypt
        DecryptionResult decResult;
        try (FileInputStream encryptedIn = new FileInputStream(inFile);
                FileOutputStream plainOut = new FileOutputStream(outFile)) {
            decResult = decryptAndVerify(encryptedIn, plainOut,
                    keys.myKey, keys.otherKey.signKey);
        } catch (IOException | PGPException ex){
            LOGGER.log(Level.WARNING, "can't decrypt attachment", ex);
            message.setAttachmentErrors(EnumSet.of(Error.UNKNOWN_ERROR));
            return;
        }
        message.setAttachmentErrors(keys.errors);
        message.setAttachmentSigning(decResult.signing);

        // set new filename
        message.setDecryptedAttachment(outFile.getName());
        LOGGER.info("attachment decryption successful");
    }

    private static KeysResult getKeys(Contact contact) {
        KeysResult result = new KeysResult();

        Optional<PersonalKey> optMyKey = AccountLoader.getInstance().getPersonalKey();
        if (!optMyKey.isPresent()) {
            LOGGER.log(Level.WARNING, "can't get personal key");
            result.errors.add(Error.MY_KEY_UNAVAILABLE);
            return result;
        }
        result.myKey = optMyKey.get();

        Optional<PGPCoderKey> optKey = getKey(contact);
        if (!optKey.isPresent()) {
            LOGGER.warning("key not found for contact: "+contact);
            result.errors.add(Error.KEY_UNAVAILABLE);
            return result;
        }

        result.otherKey = optKey.get();

        return result;
    }

    /**
     * Encrypt, sign and write input stream data to output stream.
     * Input and output stream are closed.
     * @return true on success, else false
     */
    private static void encryptAndSign(
            InputStream plainInput, OutputStream encryptedOutput,
            PersonalKey myKey, PGPPublicKey encryptKey)
            throws IOException, PGPException {

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        //for (PGPPublicKey rcpt : mRecipients)
        encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(encryptKey));

        OutputStream encryptedOut = encGen.open(encryptedOutput, new byte[BUFFER_SIZE]);

        // setup compressed data generator
        PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
        OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

        // setup signature generator
        int algo = myKey.getSigningAlgorithm();
        PGPSignatureGenerator sigGen = new PGPSignatureGenerator(
                new BcPGPContentSignerBuilder(algo, HashAlgorithmTags.SHA256));
        sigGen.init(PGPSignature.BINARY_DOCUMENT, myKey.getPrivateSigningKey());

        PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
        spGen.setSignerUserID(false, myKey.getUserId());
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
        while ((len = plainInput.read(buf)) > 0) {
            literalOut.write(buf, 0, len);
            sigGen.update(buf, 0, len);
        }

        literalGen.close();

        // generate the signature, compress, encrypt and write to the "out" stream
        sigGen.generate().encode(compressedOut);
        compGen.close();
        encGen.close();
    }

    /**
     * Decrypt, verify and write input stream data to output stream.
     */
    private static DecryptionResult decryptAndVerify(
            InputStream encryptedInput, OutputStream plainOutput,
            PersonalKey myKey, PGPPublicKey senderSigningKey)
            throws PGPException, IOException {
        // note: the signature is inside the encrypted data

        DecryptionResult result = new DecryptionResult();

        PGPObjectFactory pgpFactory = new PGPObjectFactory(encryptedInput, PGPUtils.FP_CALC);

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

        PGPObjectFactory plainFactory = new PGPObjectFactory(clear, PGPUtils.FP_CALC);

        Object object = plainFactory.nextObject(); // nullable

        if (!(object instanceof PGPCompressedData)) {
            LOGGER.warning("data packet not compressed");
            result.errors.add(Error.INVALID_DATA);
            return result;
        }

        PGPCompressedData cData = (PGPCompressedData) object;
        PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream(), PGPUtils.FP_CALC);

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
                try {
                    ops.init(new BcPGPContentVerifierBuilderProvider(), senderSigningKey);
                } catch (ClassCastException e) {
                    LOGGER.warning("legacy signature not supported");
                    result.errors.add(Error.INVALID_SIGNATURE_DATA);
                    ops = null;
                }
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
            plainOutput.write(ch);
            if (ops != null)
                ops.update((byte) ch);
        }

        if (ops != null) {
            result = verifySignature(result, pgpFact, ops);
        }

        // verify message integrity
        if (pbe.isIntegrityProtected()) {
            if (!pbe.verify()) {
                LOGGER.warning("integrity check failed");
                result.errors.add(Error.INVALID_INTEGRITY);
            }
        } else {
            LOGGER.warning("data is not integrity protected");
            result.errors.add(Error.NO_INTEGRITY);
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
        if (ops.verify(signature)) {
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
     *
     * The decrypted content of a message is in CPIM format.
     */
    private static ParsingResult parseCPIM(String cpim,
            String myUid, String senderKeyUID) {

        ParsingResult result = new ParsingResult();

        CPIMMessage cpimMessage;
        try {
            cpimMessage = CPIMMessage.parse(cpim);
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
            // XMPP XML format for advanced content (attachments)
            Message m;
            try {
                m = XMPPUtils.parseMessageStanza(content);
            } catch (XmlPullParserException | IOException | SmackException ex) {
                LOGGER.log(Level.WARNING, "can't parse XMPP XML string", ex);
                result.errors.add(Error.INVALID_DATA);
                return result;
            }
            LOGGER.config("decrypted XML: "+m.toXML());
            decryptedContent = KonMessageListener.parseMessageContent(m);
        } else {
            // text/plain MIME type for simple text messages
            decryptedContent = new MessageContent(content);
        }

        result.content = decryptedContent;
        return result;
    }

    private static Optional<PGPCoderKey> getKey(Contact contact) {
        if (KEY_MAP.containsKey(contact)) {
            PGPCoderKey key = KEY_MAP.get(contact);
            if (key.fingerprint.equals(contact.getFingerprint()))
                return Optional.of(key);
        }

        byte[] rawKey = contact.getKey();
        if (rawKey.length == 0) {
            return Optional.empty();
        }

        Optional<PGPCoderKey> optKey = PGPUtils.readPublicKey(rawKey);

        if (optKey.isPresent())
            KEY_MAP.put(contact, optKey.get());

        return optKey;
    }
}
