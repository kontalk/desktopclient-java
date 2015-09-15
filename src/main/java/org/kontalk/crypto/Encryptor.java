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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import static org.kontalk.crypto.Coder.contactkeyOrNull;
import org.kontalk.model.Contact;
import org.kontalk.model.MessageContent;
import org.kontalk.model.OutMessage;
import org.kontalk.model.Transmission;
import org.kontalk.util.CPIMMessage;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Encryptor {
    private static final Logger LOGGER = Logger.getLogger(Encryptor.class.getName());

    // should always be a power of 2
    private static final int BUFFER_SIZE = 1 << 8;

    private Encryptor() {
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
        if (message.getCoderStatus().getEncryption() != Coder.Encryption.DECRYPTED) {
            LOGGER.warning("message does not want to be encrypted");
            return Optional.empty();
        }

        // get keys
        // TODO equal code
        PersonalKey myKey = Coder.myKeyOrNull();
        if (myKey == null) {
            message.setSecurityErrors(EnumSet.of(Coder.Error.MY_KEY_UNAVAILABLE));
            return Optional.empty();
        }
        List<Contact> contacts = new ArrayList<>(message.getTransmissions().length);
        for (Transmission t : message.getTransmissions())
            contacts.add(t.getContact());
        PGPUtils.PGPCoderKey[] receiverKeys = receiverKeysOrNull(contacts.toArray(new Contact[0]));
        if (receiverKeys == null) {
            message.setSecurityErrors(EnumSet.of(Coder.Error.KEY_UNAVAILABLE));
            return Optional.empty();
        }

        // secure the message against the most basic attacks using Message/CPIM
        // [for Android client - dont know if its useful, but doesnt hurt]
        String from = myKey.getUserId();
        StringBuilder to = new StringBuilder();
        for (PGPUtils.PGPCoderKey k : receiverKeys)
            to.append(k.userID).append("; ");

        CPIMMessage cpim = new CPIMMessage(from, to.toString(), new Date(), mime, data);
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
            encryptAndSign(in, out, myKey, receiverKeys);
        } catch(IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt message", ex);
            message.setSecurityErrors(EnumSet.of(Coder.Error.UNKNOWN_ERROR));
            return Optional.empty();
        }

        LOGGER.info("message encryption successful");
        return Optional.of(out.toByteArray());
    }

    public static Optional<File> encryptAttachment(OutMessage message) {
        Optional<MessageContent.Attachment> optAttachment = message.getContent().getAttachment();
        if (!optAttachment.isPresent()) {
            LOGGER.warning("no attachment in out-message");
            return Optional.empty();
        }
        MessageContent.Attachment attachment = optAttachment.get();

        // get keys
        // TODO equal code
        PersonalKey myKey = Coder.myKeyOrNull();
        if (myKey == null) {
            message.setSecurityErrors(EnumSet.of(Coder.Error.MY_KEY_UNAVAILABLE));
            return Optional.empty();
        }
        List<Contact> contacts = new ArrayList<>(message.getTransmissions().length);
        for (Transmission t : message.getTransmissions())
            contacts.add(t.getContact());
        PGPUtils.PGPCoderKey[] receiverKeys = receiverKeysOrNull(contacts.toArray(new Contact[0]));
        if (receiverKeys == null) {
            message.setSecurityErrors(EnumSet.of(Coder.Error.KEY_UNAVAILABLE));
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
            encryptAndSign(in, out, myKey, receiverKeys);
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't encrypt attachment", ex);
            return Optional.empty();
        }

        LOGGER.info("attachment encryption successful");
        return Optional.of(tempFile);
    }

    private static PGPUtils.PGPCoderKey[] receiverKeysOrNull(Contact[] contacts) {
        List<PGPUtils.PGPCoderKey> keys = new ArrayList<>(contacts.length);
        for (Contact c : contacts) {
            PGPUtils.PGPCoderKey k = contactkeyOrNull(c);
            if (k == null)
                return null;
            keys.add(k);
        }
        return keys.toArray(new PGPUtils.PGPCoderKey[0]);
    }

    /**
     * Encrypt, sign and write input stream data to output stream.
     * Input and output stream are closed.
     * @return true on success, else false
     */
    private static void encryptAndSign(
            InputStream plainInput, OutputStream encryptedOutput,
            PersonalKey myKey, PGPUtils.PGPCoderKey[] receiverKeys)
            throws IOException, PGPException {

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        for (PGPUtils.PGPCoderKey key : receiverKeys)
            encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(key.encryptKey));

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
}
