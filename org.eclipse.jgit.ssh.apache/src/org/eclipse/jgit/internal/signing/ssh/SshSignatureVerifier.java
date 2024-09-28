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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.signing.ssh.CachingSigningKeyDatabase;
import org.eclipse.jgit.signing.ssh.SigningKeyDatabase;
import org.eclipse.jgit.signing.ssh.VerificationException;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SignatureVerifier} for SSH signatures.
 */
public class SshSignatureVerifier implements SignatureVerifier {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshSignatureVerifier.class);

	private static final byte[] OBJECT = { 'o', 'b', 'j', 'e', 'c', 't', ' ' };

	private static final byte[] TREE = { 't', 'r', 'e', 'e', ' ' };

	private static final byte[] TYPE = { 't', 'y', 'p', 'e', ' ' };

	@Override
	public String getName() {
		return "ssh"; //$NON-NLS-1$
	}

	@Override
	public SignatureVerification verify(Repository repository, GpgConfig config,
			byte[] data, byte[] signatureData) throws IOException {
		// This is a bit stupid. SSH signatures do not store a signer, nor a
		// time the signature was created. So we must use the committer's or
		// tagger's PersonIdent, but here we have neither. But... if we see
		// that the data is a commit or tag, then we can parse the PersonIdent
		// from the data.
		//
		// Note: we cannot assume that absent a principal recorded in the
		// allowedSignersFile or on a certificate that the key used to sign the
		// commit belonged to the committer.
		PersonIdent gitIdentity = getGitIdentity(data);
		Date signatureDate = null;
		Instant signatureInstant = null;
		if (gitIdentity != null) {
			signatureDate = gitIdentity.getWhen();
			signatureInstant = gitIdentity.getWhenAsInstant();
		}

		TrustLevel trust = TrustLevel.NEVER;
		byte[] decodedSignature;
		try {
			decodedSignature = dearmor(signatureData);
		} catch (IllegalArgumentException e) {
			return new SignatureVerification(getName(), signatureDate, null,
					null, null, false, false, trust,
					MessageFormat.format(SshdText.get().signInvalidSignature,
							e.getLocalizedMessage()));
		}
		int start = RawParseUtils.match(decodedSignature, 0,
				SshSignatureConstants.MAGIC);
		if (start < 0) {
			return new SignatureVerification(getName(), signatureDate, null,
					null, null, false, false, trust,
					SshdText.get().signInvalidMagic);
		}
		ByteArrayBuffer signature = new ByteArrayBuffer(decodedSignature, start,
				decodedSignature.length - start);

		long version = signature.getUInt();
		if (version != SshSignatureConstants.VERSION) {
			return new SignatureVerification(getName(), signatureDate, null,
					null, null, false, false, trust,
					MessageFormat.format(SshdText.get().signInvalidVersion,
							Long.toString(version)));
		}

		PublicKey key = signature.getPublicKey();
		String fingerprint;
		if (key instanceof OpenSshCertificate cert) {
			fingerprint = KeyUtils.getFingerPrint(cert.getCertPubKey());
			String message = SshCertificateUtils.verify(cert, signatureInstant);
			if (message != null) {
				return new SignatureVerification(getName(), signatureDate, null,
						fingerprint, null, false, false, trust, message);
			}
		} else {
			fingerprint = KeyUtils.getFingerPrint(key);
		}

		String namespace = signature.getString();
		if (!SshSignatureConstants.NAMESPACE.equals(namespace)) {
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					MessageFormat.format(SshdText.get().signInvalidNamespace,
							namespace));
		}

		signature.getString(); // Skip the reserved field
		String hashAlgorithm = signature.getString();
		byte[] hash;
		try {
			hash = MessageDigest
					.getInstance(hashAlgorithm.toUpperCase(Locale.ROOT))
					.digest(data);
		} catch (NoSuchAlgorithmException e) {
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					MessageFormat.format(
							SshdText.get().signUnknownHashAlgorithm,
							hashAlgorithm));
		}
		ByteArrayBuffer rawSignature = new ByteArrayBuffer(
				signature.getBytes());
		if (signature.available() > 0) {
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					SshdText.get().signGarbageAtEnd);
		}

		String signatureAlgorithm = rawSignature.getString();
		switch (signatureAlgorithm) {
		case KeyPairProvider.SSH_DSS:
		case KeyPairProvider.SSH_DSS_CERT:
		case KeyPairProvider.SSH_RSA:
		case KeyPairProvider.SSH_RSA_CERT:
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					MessageFormat.format(SshdText.get().signInvalidAlgorithm,
							signatureAlgorithm));
		}

		String keyType = KeyUtils
				.getSignatureAlgorithm(KeyUtils.getKeyType(key), key);
		if (!KeyUtils.getCanonicalKeyType(keyType)
				.equals(KeyUtils.getCanonicalKeyType(signatureAlgorithm))) {
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					MessageFormat.format(
							SshdText.get().signMismatchedSignatureAlgorithm,
							keyType, signatureAlgorithm));
		}

		BuiltinSignatures factory = BuiltinSignatures
				.fromFactoryName(signatureAlgorithm);
		if (factory == null || !factory.isSupported()) {
			return new SignatureVerification(getName(), signatureDate, null,
					fingerprint, null, false, false, trust,
					MessageFormat.format(
							SshdText.get().signUnknownSignatureAlgorithm,
							signatureAlgorithm));
		}

		boolean valid;
		String message = null;
		try {
			Signature verifier = factory.create();
			verifier.initVerifier(null,
					key instanceof OpenSshCertificate cert
							? cert.getCertPubKey()
							: key);
			// Feed it the data
			Buffer toSign = new ByteArrayBuffer();
			toSign.putRawBytes(SshSignatureConstants.MAGIC);
			toSign.putString(SshSignatureConstants.NAMESPACE);
			toSign.putUInt(0); // reserved: zero-length string
			toSign.putString(hashAlgorithm);
			toSign.putBytes(hash);
			verifier.update(null, toSign.getCompactData());
			valid = verifier.verify(null, rawSignature.getBytes());
		} catch (Exception e) {
			LOG.warn("{}", SshdText.get().signLogFailure, e); //$NON-NLS-1$
			valid = false;
			message = SshdText.get().signSeeLog;
		}
		boolean expired = false;
		String principal = null;
		if (valid) {
			if (rawSignature.available() > 0) {
				valid = false;
				message = SshdText.get().signGarbageAtEnd;
			} else {
				SigningKeyDatabase database = SigningKeyDatabase.getInstance();
				if (database.isRevoked(repository, config, key)) {
					valid = false;
					if (key instanceof OpenSshCertificate certificate) {
						message = MessageFormat.format(
								SshdText.get().signCertificateRevoked,
								KeyUtils.getFingerPrint(
										certificate.getCaPubKey()));
					} else {
						message = SshdText.get().signKeyRevoked;
					}
				} else {
					// This may turn a positive verification into a failed one.
					try {
						principal = database.isAllowed(repository, config, key,
								SshSignatureConstants.NAMESPACE, gitIdentity);
						if (!StringUtils.isEmptyOrNull(principal)) {
							trust = TrustLevel.FULL;
						} else {
							valid = false;
							message = SshdText.get().signNoPrincipalMatched;
							trust = TrustLevel.UNKNOWN;
						}
					} catch (VerificationException e) {
						valid = false;
						message = e.getMessage();
						expired = e.isExpired();
					} catch (IOException e) {
						LOG.warn("{}", SshdText.get().signLogFailure, e); //$NON-NLS-1$
						valid = false;
						message = SshdText.get().signSeeLog;
					}
				}
			}
		}
		return new SignatureVerification(getName(), signatureDate, null,
				fingerprint, principal, valid, expired, trust, message);
	}

	private static PersonIdent getGitIdentity(byte[] rawObject) {
		// Data from a commit will start with "tree ID\n".
		int i = RawParseUtils.match(rawObject, 0, TREE);
		if (i > 0) {
			i = RawParseUtils.committer(rawObject, 0);
			if (i < 0) {
				return null;
			}
			return RawParseUtils.parsePersonIdent(rawObject, i);
		}
		// Data from a tag will start with "object ID\ntype ".
		i = RawParseUtils.match(rawObject, 0, OBJECT);
		if (i > 0) {
			i = RawParseUtils.nextLF(rawObject, i);
			i = RawParseUtils.match(rawObject, i, TYPE);
			if (i > 0) {
				i = RawParseUtils.tagger(rawObject, 0);
				if (i < 0) {
					return null;
				}
				return RawParseUtils.parsePersonIdent(rawObject, i);
			}
		}
		return null;
	}

	private static byte[] dearmor(byte[] data) {
		int start = RawParseUtils.match(data, 0,
				SshSignatureConstants.ARMOR_HEAD);
		if (start > 0) {
			if (data[start] == '\r') {
				start++;
			}
			if (data[start] == '\n') {
				start++;
			}
		}
		int end = data.length;
		if (end > start + 1 && data[end - 1] == '\n') {
			end--;
			if (end > start + 1 && data[end - 1] == '\r') {
				end--;
			}
		}
		end = end - SshSignatureConstants.ARMOR_END.length;
		if (end >= 0 && end >= start
				&& RawParseUtils.match(data, end,
						SshSignatureConstants.ARMOR_END) >= 0) {
			// end is fine: on the first the character of the end marker
		} else {
			// No end marker.
			end = data.length;
		}
		if (start < 0) {
			start = 0;
		}
		return Base64.decode(data, start, end - start);
	}

	@Override
	public void clear() {
		SigningKeyDatabase database = SigningKeyDatabase.getInstance();
		if (database instanceof CachingSigningKeyDatabase caching) {
			caching.clearCache();
		}
	}
}
