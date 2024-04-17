package org.eclipse.jgit.gpg.bc.internal;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Base64;
import org.eclipse.jgit.lib.AbstractGpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.Date;

/**
 * A {@link GpgSignatureVerifier} to verify X509 signatures.
 */
public class SMIMESignatureVerifier extends AbstractGpgSignatureVerifier {

    static final int BEGIN_DELIMITER_LEN = "-----BEGIN SIGNED MESSAGE-----\n".length();
    static final int END_DELIMITER_LEN = "\n-----END SIGNED MESSAGE-----".length();

    @Override
    public SignatureVerification verify(byte[] data, byte[] signatureData) throws IOException {
        byte[] signedContent = new byte[signatureData.length - BEGIN_DELIMITER_LEN - END_DELIMITER_LEN];
        System.arraycopy(signatureData, BEGIN_DELIMITER_LEN,
                signedContent, 0, signedContent.length);
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(data), Base64.decode(signedContent));

            SignerInformation signerInfo = cmsSignedData.getSignerInfos().getSigners().iterator().next();

            X509CertificateHolder certificateHolder = (X509CertificateHolder) cmsSignedData.getCertificates().getMatches(signerInfo.getSID()).iterator().next();

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certificateHolder.getEncoded()));

            boolean verified = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(cert));

            Date signingTime = getSigningTime(signerInfo);

            return new VerificationResult(signingTime,
                    "signer",
                    "fingeprint",
                    "some user",
                    verified,
                    false,
                    TrustLevel.UNKNOWN,
                    "My message");
        } catch (CMSException cmsException) {
            throw new IOException("Incorrect key");
        } catch (CertificateException | OperatorCreationException e) {
            throw new IOException("Incorrect Certificate");
        }
    }

    private Date getSigningTime(SignerInformation signerInfo) throws IOException {
        byte[] signingTimeAttribute = signerInfo.getSignedAttributes().get(org.bouncycastle.asn1.cms.CMSAttributes.signingTime).getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded();
        return org.bouncycastle.asn1.cms.Time.getInstance(ASN1Primitive.fromByteArray(signingTimeAttribute)).getDate();
    }

    @Override
    public String getName() {
        return "bc";
    }

    @Override
    public void clear() {

    }
}
