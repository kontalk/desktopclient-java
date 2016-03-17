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
import java.nio.file.Path;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.util.EncodingUtils;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Message;
import org.kontalk.client.KonMessageListener;
import org.kontalk.model.message.MessageContent;
import org.kontalk.model.message.DecryptMessage;
import org.kontalk.model.message.InMessage;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.XMPPUtils;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Decrypt message content. Message parameter is internally changed by methods.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class Decryptor {
    private static final Logger LOGGER = Logger.getLogger(Decryptor.class.getName());

    private static class DecryptionResult {
        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);
        Coder.Signing signing = Coder.Signing.UNKNOWN;
    }

    private final DecryptMessage mMessage;
    private final PersonalKey mMyKey;

    // nullable
    private final PGPUtils.PGPCoderKey mSenderKey;

    Decryptor(PersonalKey myKey, DecryptMessage message) {
        mMyKey = myKey;
        mMessage = message;

        // if sender signing key not found -> can decrypt but not verify
        mSenderKey = Coder.contactkey(message.getContact()).orElse(null);
    }

    // note: signing requires also encryption
    boolean decryptMessage() {
        if (!mMessage.isEncrypted()) {
            LOGGER.warning("message not encrypted");
            return false;
        }

        // decrypt
        String encryptedContent = mMessage.getContent().getEncryptedContent();
        if (encryptedContent.isEmpty()) {
            LOGGER.warning("no encrypted data in encrypted message");
        }
        byte[] encryptedData = org.kontalk.util.EncodingUtils.base64ToBytes(encryptedContent);

        InputStream encryptedIn = new ByteArrayInputStream(encryptedData);
        ByteArrayOutputStream plainOut = new ByteArrayOutputStream();
        DecryptionResult decResult;
        try {
            decResult = decryptAndVerify(encryptedIn,
                    plainOut,
                    mMyKey.getPrivateEncryptionKey(),
                    mSenderKey != null ?
                            Optional.of(mSenderKey.signKey) :
                            Optional.<PGPPublicKey>empty());
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't decrypt message", ex);
            return false;
        }
        EnumSet<Coder.Error> allErrors = decResult.errors;
        mMessage.setSigning(decResult.signing);

        // parse decrypted CPIM content
        String myUID = mMyKey.getUserId();
        String senderUID = mSenderKey != null ?
                mSenderKey.userID :
                null;
        String decrText = EncodingUtils.getString(
                plainOut.toByteArray(),
                CPIMMessage.CHARSET);
        MessageContent content = this.parseCPIMOrNull(decrText, myUID, Optional.ofNullable(senderUID));

        // set errors
        mMessage.setSecurityErrors(allErrors);

        if (content != null) {
            // everything went better than expected
            LOGGER.info("message decryption successful");
            mMessage.setDecryptedContent(content);
            return true;
        } else {
            LOGGER.warning("message decryption failed");
            return false;
        }
    }

    void decryptAttachment(Path baseDir) {
        // TODO ugly
        if (!(mMessage instanceof InMessage)) {
            LOGGER.warning("message not incoming message");
            return;
        }
        InMessage inMessage = (InMessage) mMessage;

        MessageContent.Attachment attachment = inMessage.getContent().getAttachment().orElse(null);
        if (attachment == null) {
            LOGGER.warning("no attachment in in-message");
            return;
        }

        if (mMyKey == null) {
            mMessage.setSecurityErrors(EnumSet.of(Coder.Error.MY_KEY_UNAVAILABLE));
            return;
        }

        // in file
        File inFile = baseDir.resolve(attachment.getFilePath()).toFile();
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
            decResult = decryptAndVerify(encryptedIn,
                    plainOut,
                    mMyKey.getPrivateEncryptionKey(),
                    mSenderKey != null ? Optional.of(mSenderKey.signKey) :
                            Optional.<PGPPublicKey>empty());
        } catch (IOException | PGPException ex){
            LOGGER.log(Level.WARNING, "can't decrypt attachment", ex);
            inMessage.setAttachmentErrors(EnumSet.of(Coder.Error.UNKNOWN_ERROR));
            return;
        }
        inMessage.setAttachmentErrors(decResult.errors);
        inMessage.setAttachmentSigning(decResult.signing);

        // set new filename
        inMessage.setDecryptedAttachment(outFile.getName());
        LOGGER.info("attachment decryption successful");
    }

    /** Decrypt, verify and write input stream data to output stream. */
    private static DecryptionResult decryptAndVerify(
            InputStream encryptedInput, OutputStream plainOutput,
            PGPPrivateKey myKey, Optional<PGPPublicKey> senderSigningKey)
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
            result.errors.add(Coder.Error.INVALID_DATA);
            return result;
        }
        PGPEncryptedDataList encDataList = (PGPEncryptedDataList) o;

        // check if secret key matches our encryption keyID
        Iterator<?> it = encDataList.getEncryptedDataObjects();
        PGPPrivateKey sKey = null;
        PGPPublicKeyEncryptedData pbe = null;
        long myKeyID = myKey.getKeyID();
        while (sKey == null && it.hasNext()) {
            Object i = it.next();
            if (!(i instanceof PGPPublicKeyEncryptedData))
                continue;
            pbe = (PGPPublicKeyEncryptedData) i;
            if (pbe.getKeyID() == myKeyID)
                sKey = myKey;
        }
        if (sKey == null || pbe == null) {
            LOGGER.warning("private key for message not found");
            result.errors.add(Coder.Error.INVALID_PRIVATE_KEY);
            return result;
        }

        InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

        PGPObjectFactory plainFactory = new PGPObjectFactory(clear, PGPUtils.FP_CALC);

        Object object = plainFactory.nextObject(); // nullable
        if (object instanceof PGPCompressedData) {
            PGPCompressedData cData = (PGPCompressedData) object;
            plainFactory = new PGPObjectFactory(cData.getDataStream(), PGPUtils.FP_CALC);
            object = plainFactory.nextObject(); // nullable
        }

        // the first object could be the signature list
        // get signature from it
        PGPOnePassSignature ops = null;
        if (object instanceof PGPOnePassSignatureList) {
            PGPOnePassSignatureList signatureList = (PGPOnePassSignatureList) object;
            // there is a signature list, so we assume the message is signed
            // (makes sense)
            result.signing = Coder.Signing.SIGNED;

            if (signatureList.isEmpty()) {
                LOGGER.warning("signature list is empty");
                result.errors.add(Coder.Error.INVALID_SIGNATURE_DATA);
            } else if (senderSigningKey.isPresent()) {
                ops = signatureList.get(0);
                try {
                    ops.init(new BcPGPContentVerifierBuilderProvider(), senderSigningKey.get());
                } catch (ClassCastException e) {
                    LOGGER.warning("legacy signature not supported");
                    result.errors.add(Coder.Error.INVALID_SIGNATURE_DATA);
                    ops = null;
                }
            }
            object = plainFactory.nextObject(); // nullable
        } else {
            LOGGER.warning("signature list not found");
            result.signing = Coder.Signing.NOT;
        }

        if (!(object instanceof PGPLiteralData)) {
            LOGGER.warning("unknown packet type: " + object.getClass().getName());
            result.errors.add(Coder.Error.INVALID_DATA);
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
            result = verifySignature(result, plainFactory, ops);
        }

        // verify message integrity
        if (pbe.isIntegrityProtected()) {
            if (!pbe.verify()) {
                LOGGER.warning("integrity check failed");
                result.errors.add(Coder.Error.INVALID_INTEGRITY);
            }
        } else {
            LOGGER.warning("data is not integrity protected");
            result.errors.add(Coder.Error.NO_INTEGRITY);
        }

        return result;
    }

    private static DecryptionResult verifySignature(DecryptionResult result,
            PGPObjectFactory pgpFact,
            PGPOnePassSignature ops) throws PGPException, IOException {
        Object object = pgpFact.nextObject(); // nullable
        if (!(object instanceof PGPSignatureList)) {
            LOGGER.warning("invalid signature packet");
            result.errors.add(Coder.Error.INVALID_SIGNATURE_DATA);
            return result;
        }

        PGPSignatureList signatureList = (PGPSignatureList) object;
        if (signatureList.isEmpty()) {
            LOGGER.warning("no signature in signature list");
            result.errors.add(Coder.Error.INVALID_SIGNATURE_DATA);
            return result;
        }

        PGPSignature signature = signatureList.get(0);
        // TODO signature.getCreationTime()
        if (ops.verify(signature)) {
            // signature verification successful!
            result.signing = Coder.Signing.VERIFIED;
        } else {
            LOGGER.warning("signature verification failed");
            result.errors.add(Coder.Error.INVALID_SIGNATURE);
        }
        return result;
    }

    /**
     * Parse and verify CPIM ( https://tools.ietf.org/html/rfc3860 ).
     *
     * The decrypted content of a message is in CPIM format.
     */
    private MessageContent parseCPIMOrNull(String cpim,
            String myUid, Optional<String> senderKeyUID) {

        CPIMMessage cpimMessage;
        try {
            cpimMessage = CPIMMessage.parse(cpim);
        } catch (ParseException ex) {
            LOGGER.log(Level.WARNING, "can't find valid CPIM data", ex);
            mMessage.setSecurityErrors(EnumSet.of(Coder.Error.INVALID_DATA));
            return null;
        }

        EnumSet<Coder.Error> errors = EnumSet.noneOf(Coder.Error.class);

        String mime = cpimMessage.getMime();

        // check mime type
        // why is that necessary here?
        //if (!mime.equalsIgnoreCase("text/plain") &&
        //        !mime.equalsIgnoreCase(XMPPUtils.XML_XMPP_TYPE)) {
        //    LOGGER.warning("MIME type mismatch");
        //}

        // check that the recipient matches the full uid of the personal key
        if (!StringUtils.defaultString(cpimMessage.getTo()).contains(myUid)) {
            LOGGER.warning("destination does not match personal key");
            errors.add(Coder.Error.INVALID_RECIPIENT);
        }
        // check that the sender matches the full uid of the sender's key
        if (senderKeyUID.isPresent() &&
                !senderKeyUID.get().equals(cpimMessage.getFrom())) {
            LOGGER.warning("sender doesn't match sender's key");
            errors.add(Coder.Error.INVALID_SENDER);
        }

        // TODO check DateTime (possibly compare it with <delay/>)

        String content = cpimMessage.getBody().toString();
        MessageContent decryptedContent;
        if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(mime)) {
            // XMPP XML format for advanced content (attachments)
            Message m;
            try {
                m = XMPPUtils.parseMessageStanza(content);
            } catch (XmlPullParserException | IOException | SmackException ex) {
                LOGGER.log(Level.WARNING, "can't parse XMPP XML string", ex);
                errors.add(Coder.Error.INVALID_DATA);
                mMessage.setSecurityErrors(errors);
                return null;
            }
            LOGGER.config("decrypted XML: "+m.toXML());
            decryptedContent = KonMessageListener.parseMessageContent(m);
        } else {
            // text/plain MIME type for simple text messages
            decryptedContent = MessageContent.plainText(content);
        }

        mMessage.setSecurityErrors(errors);
        return decryptedContent;
    }
}
