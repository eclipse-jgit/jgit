/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.security.PublicKey;
import java.text.MessageFormat;
import java.time.Instant;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with OpenSSH certificates.
 */
final class SshCertificateUtils {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshCertificateUtils.class);

	/**
	 * Verifies a certificate: checks that it is a user certificate and has a
	 * valid signature, and if a time is given, that the certificate is valid at
	 * that time.
	 *
	 * @param certificate
	 *            {@link OpenSshCertificate} to verify
	 * @param signatureTime
	 *            {@link Instant} to check whether the certificate is valid at
	 *            that time; maybe {@code null}, in which case the valid-time
	 *            check is skipped.
	 * @return {@code null} if the certificate is valid; otherwise a descriptive
	 *         message
	 */
	static String verify(OpenSshCertificate certificate,
			Instant signatureTime) {
		if (!OpenSshCertificate.Type.USER.equals(certificate.getType())) {
			return MessageFormat.format(SshdText.get().signNotUserCertificate,
					KeyUtils.getFingerPrint(certificate.getCaPubKey()));
		}
		String message = verifySignature(certificate);
		if (message == null && signatureTime != null) {
			message = checkExpiration(certificate, signatureTime);
		}
		return message;
	}

	/**
	 * Verifies the signature on a certificate.
	 *
	 * @param certificate
	 *            {@link OpenSshCertificate} to verify
	 * @return {@code null} if the signature is valid; otherwise a descriptive
	 *         message
	 */
	static String verifySignature(OpenSshCertificate certificate) {
		// Verify the signature on the certificate.
		//
		// Note that OpenSSH certificates do not support chaining.
		//
		// ssh-keygen refuses to create a certificate for a certificate, so the
		// certified key cannot be another OpenSshCertificate. Additionally,
		// when creating a certificate ssh-keygen loads the CA private key to
		// make the signature and reconstructs the public key that it stores in
		// the certificate from that, so the CA public key also cannot be an
		// OpenSshCertificate.
		PublicKey caKey = certificate.getCaPubKey();
		PublicKey certifiedKey = certificate.getCertPubKey();
		if (caKey == null
				|| caKey instanceof OpenSshCertificate
				|| certifiedKey == null
				|| certifiedKey instanceof OpenSshCertificate) {
			return SshdText.get().signCertificateInvalid;
		}
		// Verify that key type and algorithm match
		String keyType = KeyUtils.getKeyType(caKey);
		String certAlgorithm = certificate.getSignatureAlgorithm();
		if (!KeyUtils.getCanonicalKeyType(keyType)
				.equals(KeyUtils.getCanonicalKeyType(certAlgorithm))) {
			return MessageFormat.format(
					SshdText.get().signCertAlgorithmMismatch, keyType,
					KeyUtils.getFingerPrint(certificate.getCaPubKey()),
					certAlgorithm);
		}
		BuiltinSignatures factory = BuiltinSignatures
				.fromFactoryName(certAlgorithm);
		if (factory == null || !factory.isSupported()) {
			return MessageFormat.format(SshdText.get().signCertAlgorithmUnknown,
					KeyUtils.getFingerPrint(certificate.getCaPubKey()),
					certAlgorithm);
		}
		Signature signer = factory.create();
		try {
			signer.initVerifier(null, caKey);
			signer.update(null, getBlob(certificate));
			if (signer.verify(null, certificate.getRawSignature())) {
				return null;
			}
		} catch (Exception e) {
			LOG.warn("{}", SshdText.get().signLogFailure, e); //$NON-NLS-1$
			return SshdText.get().signSeeLog;
		}
		return MessageFormat.format(SshdText.get().signCertificateInvalid,
				KeyUtils.getFingerPrint(certificate.getCaPubKey()));
	}

	private static byte[] getBlob(OpenSshCertificate certificate) {
		// Theoretically, this should be just certificate.getMessage(). But
		// Apache MINA sshd has a bug and may return additional bytes if the
		// certificate is not the first thing in the buffer it was read from.
		// As a work-around, re-create the signed blob from scratch.
		//
		// This may be replaced by return certificate.getMessage() once the
		// upstream bug is fixed.
		//
		// See https://github.com/apache/mina-sshd/issues/618
		Buffer tmp = new ByteArrayBuffer();
		tmp.putString(certificate.getKeyType());
		tmp.putBytes(certificate.getNonce());
		tmp.putRawPublicKeyBytes(certificate.getCertPubKey());
		tmp.putLong(certificate.getSerial());
		tmp.putInt(certificate.getType().getCode());
		tmp.putString(certificate.getId());
		Buffer list = new ByteArrayBuffer();
		list.putStringList(certificate.getPrincipals(), false);
		tmp.putBytes(list.getCompactData());
		tmp.putLong(certificate.getValidAfter());
		tmp.putLong(certificate.getValidBefore());
		tmp.putCertificateOptions(certificate.getCriticalOptions());
		tmp.putCertificateOptions(certificate.getExtensions());
		tmp.putString(certificate.getReserved());
		Buffer inner = new ByteArrayBuffer();
		inner.putRawPublicKey(certificate.getCaPubKey());
		tmp.putBytes(inner.getCompactData());
		return tmp.getCompactData();
	}

	/**
	 * Checks whether a certificate is valid at a given time.
	 *
	 * @param certificate
	 *            {@link OpenSshCertificate} to check
	 * @param signatureTime
	 *            {@link Instant} to check
	 * @return {@code null} if the certificate is valid at the given instant;
	 *         otherwise a descriptive message
	 */
	static String checkExpiration(OpenSshCertificate certificate,
			@NonNull Instant signatureTime) {
		long instant = signatureTime.getEpochSecond();
		if (Long.compareUnsigned(instant, certificate.getValidAfter()) < 0) {
			return MessageFormat.format(SshdText.get().signCertificateTooEarly,
					KeyUtils.getFingerPrint(certificate.getCaPubKey()));
		} else if (Long.compareUnsigned(instant,
				certificate.getValidBefore()) > 0) {
			return MessageFormat.format(SshdText.get().signCertificateExpired,
					KeyUtils.getFingerPrint(certificate.getCaPubKey()));
		}
		return null;
	}
}
