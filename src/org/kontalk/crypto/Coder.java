/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.Base64;
import org.kontalk.model.Account;
import org.kontalk.model.KonMessage;
import org.kontalk.model.User;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.XMPPUtils;

/**
 * Static methods for decryption and encryption of an message.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class Coder {
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

    public static void processMessage(KonMessage message) {
        // signing requires also encryption
        if (message.getEncryption() != Encryption.ENCRYPTED) {
            LOGGER.warning("message not encrypted");
            return;
        }
        LOGGER.fine("decrypting encrypted message...");

        // clear security errors
        message.resetSecurityErrors();

        // get my key
        PersonalKey myKey = Account.getInstance().getPersonalKey();

        // get sender key
        User user = message.getUser();
        if (!user.hasKey()) {
            LOGGER.warning("key not found for user, id: "+user.getID());
            message.addSecurityError(Error.KEY_UNAVAILABLE);
            return;
        }
        PGPPublicKeyRing ring;
        try {
            ring = PGP.readPublicKeyring(user.getKey());
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't get keyring", ex);
            message.addSecurityError(Error.INVALID_KEY);
            return;
        }
        PGPPublicKey senderKey = PGP.getMasterKey(ring);
        if (senderKey == null) {
            LOGGER.warning("can't find masterkey in keyring");
            message.addSecurityError(Error.INVALID_KEY);
            return;
        }

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
            LOGGER.info("decryption successful");
            // TODO really overwrite?
            message.setDecryptedText(text);
        } else {
            LOGGER.warning("decryption failed");
        }
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

        String content = cpimMessage.getBody();
        String plainText;
        if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(mime)) {
            LOGGER.fine("CPIM body has xml xmpp format");
            Message m;
            try {
                m = XMPPUtils.parseMessageStanza(content);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "can't parse xmpp xml string", ex);
                return null;
            }
            plainText = m.getBody() != null ? m.getBody() : null;
        } else {
            LOGGER.fine("CPIM body mime type: "+mime);
            plainText = content;
        }

        return plainText;
    }

}
