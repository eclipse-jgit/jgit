/*
 * Copyright (C) 2024 GerritForge, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.asn1.cms.Time;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.sha1.SHA1;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * A {@link GpgSignatureVerifier} to verify X509 signatures.
 */
public class BouncyCastleCMSSignatureVerifier extends BouncyCastleAbstractGpgSignatureVerifier  {

    static final int BEGIN_DELIMITER_LEN = "-----BEGIN SIGNED MESSAGE-----\n".length();
    static final int END_DELIMITER_LEN = "\n-----END SIGNED MESSAGE-----".length();
    /**
     * Creates a new instance and registers the BouncyCastle security provider
     * if needed.
     */
    public BouncyCastleCMSSignatureVerifier() {registerBouncyCastleProviderIfNecessary();}

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
            byte[] encodedCertificate = certificateHolder.getEncoded();
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(encodedCertificate));
            boolean verified = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(cert));
            Date signingTime = getSigningTime(signerInfo);
            SHA1 sha1 = SHA1.newInstance();
            sha1.update(encodedCertificate);

            return new VerificationResult(signingTime,
                    cert.getIssuerX500Principal().getName(),
                    ObjectId.fromRaw(sha1.digest()).name(),
                    cert.getSubjectX500Principal().getName(),
                    verified,
                    new Date().after(cert.getNotAfter()),
                    TrustLevel.UNKNOWN,
                    null);
        } catch (Exception e) {
            throw new IOException("SMIME signature verification failed", e);
        }
    }

    private Date getSigningTime(SignerInformation signerInfo) throws IOException {
        byte[] signingTimeAttribute = signerInfo.getSignedAttributes().get(CMSAttributes.signingTime).getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded();
        return Time.getInstance(ASN1Primitive.fromByteArray(signingTimeAttribute)).getDate();
    }

    @Override
    public String getName() {
        return "bc";
    }

    @Override
    public void clear() {

    }
}
