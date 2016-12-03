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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DLSequence;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;
import org.bouncycastle.operator.bc.BcDSAContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

/**
 * Utility methods for bridging OpenPGP keys with X.509 certificates.<br>
 * Inspired by the Foaf server project.
 * https://svn.java.net/svn/sommer~svn/trunk/misc/FoafServer/pgpx509/src/net/java/dev/sommer/foafserver/utils/PgpX509Bridge.java
 * @author Daniele Ricci
 */
public class X509Bridge {

    private static final String DN_COMMON_PART_O = "OpenPGP to X.509 Bridge";
    private static final String PEM_TYPE_CERTIFICATE = "CERTIFICATE";

    private X509Bridge() {}

    public static byte[] encode(X509Certificate cert) throws CertificateEncodingException, IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (PemWriter writer = new PemWriter(new OutputStreamWriter(stream))) {
            writer.writeObject(new PemObject(X509Bridge.PEM_TYPE_CERTIFICATE, cert.getEncoded()));
        }
        return stream.toByteArray();
    }

    public static X509Certificate createCertificate(PGPKeyPair keyPair, byte[] publicKeyRingData)
        throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException,
        SignatureException, CertificateException, NoSuchProviderException, PGPException, IOException, OperatorCreationException {

        X500NameBuilder x500NameBuilder = new X500NameBuilder();

        /*
         * The X.509 Name to be the subject DN is prepared.
         * The CN is extracted from the Secret Key user ID.
         */

        x500NameBuilder.addRDN(BCStyle.O, DN_COMMON_PART_O);

        PGPPublicKey publicKey = keyPair.getPublicKey();

        List<String> xmppAddrs = new LinkedList<>();
        for (@SuppressWarnings("unchecked") Iterator<Object> it = publicKey.getUserIDs(); it.hasNext();) {
            String attrib = it.next().toString();
            x500NameBuilder.addRDN(BCStyle.CN, attrib);
            // extract email for the subjectAltName
            String email = PGPUtils.parseUID(attrib)[2];
            if (!email.isEmpty())
                xmppAddrs.add(email);
        }

        X500Name x509name = x500NameBuilder.build();

        /*
         * To check the signature from the certificate on the recipient side,
         * the creation time needs to be embedded in the certificate.
         * It seems natural to make this creation time be the "not-before"
         * date of the X.509 certificate.
         * Unlimited PGP keys have a validity of 0 second. In this case,
         * the "not-after" date will be the same as the not-before date.
         * This is something that needs to be checked by the service
         * receiving this certificate.
         */
        Date creationTime = publicKey.getCreationTime();
        Date validTo = null;
        if (publicKey.getValidSeconds()>0)
           validTo = new Date(creationTime.getTime() + 1000L * publicKey.getValidSeconds());

        return createCertificate(
                PGPUtils.convertPublicKey(publicKey),
                PGPUtils.convertPrivateKey(keyPair.getPrivateKey()),
                x509name,
                creationTime, validTo,
                xmppAddrs,
                publicKeyRingData);
    }

    /**
     * Creates a self-signed certificate from a public and private key. The
     * (critical) key-usage extension is set up with: digital signature,
     * non-repudiation, key-encipherment, key-agreement and certificate-signing.
     * The (non-critical) Netscape extension is set up with: SSL client and
     * S/MIME. A URI subjectAltName may also be set up.
     *
     * @param pubKey
     *            public key
     * @param privKey
     *            private key
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid
     *            (defaults to start date and time if null)
     * @param subjectAltNames
     *            URIs to be placed in subjectAltName
     * @return self-signed certificate
     */
    private static X509Certificate createCertificate(PublicKey pubKey,
            PrivateKey privKey, X500Name subject,
            Date startDate, Date endDate, List<String> subjectAltNames, byte[] publicKeyData)
        throws InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException,
        NoSuchProviderException, IOException, OperatorCreationException {

        /*
         * Sets the signature algorithm.
         */
        BcContentSignerBuilder signerBuilder;
        String pubKeyAlgorithm = pubKey.getAlgorithm();
        if (pubKeyAlgorithm.equals("DSA")) {
            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder()
                .find("SHA1WithDSA");
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder()
                .find(sigAlgId);
            signerBuilder = new BcDSAContentSignerBuilder(sigAlgId, digAlgId);
        }
        else if (pubKeyAlgorithm.equals("RSA")) {
            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder()
                .find("SHA1WithRSAEncryption");
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder()
                .find(sigAlgId);
            signerBuilder = new BcRSAContentSignerBuilder(sigAlgId, digAlgId);
        }
        else {
            throw new RuntimeException(
                    "Algorithm not recognised: " + pubKeyAlgorithm);
        }


        AsymmetricKeyParameter keyp = PrivateKeyFactory.createKey(privKey.getEncoded());
        ContentSigner signer = signerBuilder.build(keyp);

        /*
         * Sets up the validity dates.
         */
        if (startDate == null) {
            startDate = new Date(System.currentTimeMillis());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
            /*
             * Sets up the subject distinguished name.
             * Since it's a self-signed certificate, issuer and subject are the
             * same.
             */
            subject,
            /*
             * The serial-number of this certificate is 1. It makes sense
             * because it's self-signed.
             */
            BigInteger.ONE,
            startDate,
            endDate,
            subject,
            /*
             * Sets the public-key to embed in this certificate.
             */
            SubjectPublicKeyInfo.getInstance(pubKey.getEncoded())
        );

        /*
         * Adds the Basic Constraint (CA: true) extension.
         */
        certBuilder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(true));

        /*
         * Adds the Key Usage extension.
         */
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment | KeyUsage.keyAgreement | KeyUsage.keyCertSign));

        /*
         * Adds the Netscape certificate type extension.
         */
        certBuilder.addExtension(MiscObjectIdentifiers.netscapeCertType,
                false, new NetscapeCertType(
                NetscapeCertType.sslClient | NetscapeCertType.smime));

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        /*
         * Adds the subject key identifier extension.
         */
        SubjectKeyIdentifier subjectKeyIdentifier =
                extUtils.createSubjectKeyIdentifier(pubKey);
        certBuilder.addExtension(Extension.subjectKeyIdentifier,
                false, subjectKeyIdentifier);

        /*
         * Adds the authority key identifier extension.
         */
        AuthorityKeyIdentifier authorityKeyIdentifier =
                extUtils.createAuthorityKeyIdentifier(pubKey);
        certBuilder.addExtension(Extension.authorityKeyIdentifier,
                false, authorityKeyIdentifier);

        /*
         * Adds the subject alternative-name extension.
         */
        if (subjectAltNames != null && subjectAltNames.size() > 0) {
            GeneralName[] names = new GeneralName[subjectAltNames.size()];
            for (int i = 0; i < names.length; i++)
                names[i] = new GeneralName(GeneralName.otherName,
                    new XmppAddrIdentifier(subjectAltNames.get(i)));

            certBuilder.addExtension(Extension.subjectAlternativeName,
                    false, new GeneralNames(names));
        }

        /*
         * Adds the PGP public key block extension.
         */
        SubjectPGPPublicKeyInfo publicKeyExtension =
            new SubjectPGPPublicKeyInfo(publicKeyData);
        certBuilder.addExtension(SubjectPGPPublicKeyInfo.OID, false, publicKeyExtension);

        /*
         * Creates and sign this certificate with the private key
         * corresponding to the public key of the certificate
         * (hence the name "self-signed certificate").
         */
        X509CertificateHolder holder = certBuilder.build(signer);

        /*
         * Checks that this certificate has indeed been correctly signed.
         */
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
        cert.verify(pubKey);

        return cert;
    }

    /**
    * A custom X.509 extension for a PGP public key.
    * @author Daniele Ricci
    */
   private static class SubjectPGPPublicKeyInfo extends ASN1Object {

       // based on UUID 24e844a0-6cbc-11e3-8997-0002a5d5c51b
       static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("2.25.49058212633447845622587297037800555803");

       private final DERBitString keyData;

       public SubjectPGPPublicKeyInfo(ASN1Encodable publicKey) throws IOException {
           keyData = new DERBitString(publicKey);
       }

       public SubjectPGPPublicKeyInfo(byte[] publicKey) {
           keyData = new DERBitString(publicKey);
       }

       public DERBitString getPublicKeyData()
       {
           return keyData;
       }

       @Override
       public ASN1Primitive toASN1Primitive() {
           return keyData;
       }
    }

    private static class XmppAddrIdentifier extends DLSequence {
        static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.8.5");

        XmppAddrIdentifier(String jid) {
            super(new ASN1Encodable[] {
                OID,
                new DERUTF8String(jid)
            });
        }
    }
}
