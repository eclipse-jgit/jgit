/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.bouncycastle.bcpg.sig.IssuerFingerprint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.util.LRUMap;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;

/**
 * A {@link GpgSignatureVerifier} to verify GPG signatures using BouncyCastle.
 */
public class BouncyCastleGpgSignatureVerifier implements GpgSignatureVerifier {

	private static void registerBouncyCastleProviderIfNecessary() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	/**
	 * Creates a new instance and registers the BouncyCastle security provider
	 * if needed.
	 */
	public BouncyCastleGpgSignatureVerifier() {
		registerBouncyCastleProviderIfNecessary();
	}

	// To support more efficient signature verification of multiple objects we
	// cache public keys once found in a LRU cache.

	private static final Object NO_KEY = new Object();

	private LRUMap<String, Object> byFingerprint = new LRUMap<>(16, 200);

	private LRUMap<String, Object> bySigner = new LRUMap<>(16, 200);

	@Override
	public String getName() {
		return "bc"; //$NON-NLS-1$
	}

	@Override
	@Nullable
	public SignatureVerification verifySignature(@NonNull RevObject object,
			@NonNull GpgConfig config) throws IOException {
		if (object instanceof RevCommit) {
			RevCommit commit = (RevCommit) object;
			byte[] signatureData = commit.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = commit.getRawBuffer();
			// Now remove the GPG signature
			byte[] header = { 'g', 'p', 'g', 's', 'i', 'g' };
			int start = RawParseUtils.headerStart(header, raw, 0);
			if (start < 0) {
				return null;
			}
			int end = RawParseUtils.headerEnd(raw, start);
			// start is at the beginning of the header's content
			start -= header.length + 1;
			// end is on the terminating LF; we need to skip that, too
			if (end < raw.length) {
				end++;
			}
			byte[] data = new byte[raw.length - (end - start)];
			System.arraycopy(raw, 0, data, 0, start);
			System.arraycopy(raw, end, data, start, raw.length - end);
			return verify(data, signatureData);
		} else if (object instanceof RevTag) {
			RevTag tag = (RevTag) object;
			byte[] signatureData = tag.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = tag.getRawBuffer();
			// The signature is just tacked onto the end of the message, which
			// is last in the buffer.
			byte[] data = Arrays.copyOfRange(raw, 0,
					raw.length - signatureData.length);
			return verify(data, signatureData);
		}
		return null;
	}

	static PGPSignature parseSignature(InputStream in)
			throws IOException, PGPException {
		try (InputStream sigIn = PGPUtil.getDecoderStream(in)) {
			JcaPGPObjectFactory pgpFactory = new JcaPGPObjectFactory(sigIn);
			Object obj = pgpFactory.nextObject();
			if (obj instanceof PGPCompressedData) {
				obj = new JcaPGPObjectFactory(
						((PGPCompressedData) obj).getDataStream()).nextObject();
			}
			if (obj instanceof PGPSignatureList) {
				return ((PGPSignatureList) obj).get(0);
			}
			return null;
		}
	}

	@Override
	public SignatureVerification verify(byte[] data, byte[] signatureData)
			throws IOException {
		PGPSignature signature = null;
		String fingerprint = null;
		String signer = null;
		String keyId = null;
		try (InputStream sigIn = new ByteArrayInputStream(signatureData)) {
			signature = parseSignature(sigIn);
			if (signature != null) {
				// Try to figure out something to find the public key with.
				if (signature.hasSubpackets()) {
					PGPSignatureSubpacketVector packets = signature
							.getHashedSubPackets();
					IssuerFingerprint fingerprintPacket = packets
							.getIssuerFingerprint();
					if (fingerprintPacket != null) {
						fingerprint = Hex
								.toHexString(fingerprintPacket.getFingerprint())
								.toLowerCase(Locale.ROOT);
					}
					signer = packets.getSignerUserID();
					if (signer != null) {
						signer = BouncyCastleGpgSigner.extractSignerId(signer);
					}
				}
				keyId = Long.toUnsignedString(signature.getKeyID(), 16)
						.toLowerCase(Locale.ROOT);
			} else {
				throw new JGitInternalException(BCText.get().nonSignatureError);
			}
		} catch (PGPException e) {
			throw new JGitInternalException(BCText.get().signatureParseError,
					e);
		}
		Date signatureCreatedAt = signature.getCreationTime();
		if (fingerprint == null && signer == null && keyId == null) {
			return new VerificationResult(signatureCreatedAt, null, null, null,
					false, false, TrustLevel.UNKNOWN,
					BCText.get().signatureNoKeyInfo);
		}
		if (fingerprint != null && keyId != null
				&& !fingerprint.endsWith(keyId)) {
			return new VerificationResult(signatureCreatedAt, signer, fingerprint,
					null, false, false, TrustLevel.UNKNOWN,
					MessageFormat.format(BCText.get().signatureInconsistent,
							keyId, fingerprint));
		}
		if (fingerprint == null && keyId != null) {
			fingerprint = keyId;
		}
		// Try to find the public key
		String keySpec = '<' + signer + '>';
		Object cached = null;
		PGPPublicKey publicKey = null;
		try {
			cached = byFingerprint.get(fingerprint);
			if (cached != null) {
				if (cached instanceof PGPPublicKey) {
					publicKey = (PGPPublicKey) cached;
				}
			} else if (!StringUtils.isEmptyOrNull(signer)) {
				cached = bySigner.get(signer);
				if (cached != null) {
					if (cached instanceof PGPPublicKey) {
						publicKey = (PGPPublicKey) cached;
					}
				}
			}
			if (cached == null) {
				publicKey = BouncyCastleGpgKeyLocator.findPublicKey(fingerprint,
						keySpec);
			}
		} catch (IOException | PGPException e) {
			throw new JGitInternalException(
					BCText.get().signatureKeyLookupError, e);
		}
		if (publicKey == null) {
			if (cached == null) {
				byFingerprint.put(fingerprint, NO_KEY);
				byFingerprint.put(keyId, NO_KEY);
				if (signer != null) {
					bySigner.put(signer, NO_KEY);
				}
			}
			return new VerificationResult(signatureCreatedAt, signer,
					fingerprint, null, false, false, TrustLevel.UNKNOWN,
					BCText.get().signatureNoPublicKey);
		}
		if (cached == null) {
			byFingerprint.put(fingerprint, publicKey);
			byFingerprint.put(keyId, publicKey);
			if (signer != null) {
				bySigner.put(signer, publicKey);
			}
		}
		String user = null;
		Iterator<String> userIds = publicKey.getUserIDs();
		if (!StringUtils.isEmptyOrNull(signer)) {
			while (userIds.hasNext()) {
				String userId = userIds.next();
				if (BouncyCastleGpgKeyLocator.containsSigningKey(userId,
						keySpec)) {
					user = userId;
					break;
				}
			}
		}
		if (user == null) {
			userIds = publicKey.getUserIDs();
			if (userIds.hasNext()) {
				user = userIds.next();
			}
		}
		boolean expired = false;
		long validFor = publicKey.getValidSeconds();
		if (validFor > 0 && signatureCreatedAt != null) {
			Instant expiredAt = publicKey.getCreationTime().toInstant()
					.plusSeconds(validFor);
			expired = expiredAt.isBefore(signatureCreatedAt.toInstant());
		}
		// Trust data is not defined in OpenPGP; the format is implementation
		// specific. We don't use the GPG trustdb but simply the trust packet
		// on the public key, if present. Even if present, it may or may not
		// be set.
		byte[] trustData = publicKey.getTrustData();
		TrustLevel trust = parseGpgTrustPacket(trustData);
		boolean verified = false;
		try {
			signature.init(
					new JcaPGPContentVerifierBuilderProvider()
							.setProvider(BouncyCastleProvider.PROVIDER_NAME),
					publicKey);
			signature.update(data);
			verified = signature.verify();
		} catch (PGPException e) {
			throw new JGitInternalException(
					BCText.get().signatureVerificationError, e);
		}
		return new VerificationResult(signatureCreatedAt, signer, fingerprint, user,
				verified, expired, trust, null);
	}

	private TrustLevel parseGpgTrustPacket(byte[] packet) {
		if (packet == null || packet.length < 6) {
			// A GPG trust packet has at least 6 bytes.
			return TrustLevel.UNKNOWN;
		}
		if (packet[2] != 'g' || packet[3] != 'p' || packet[4] != 'g') {
			// Not a GPG trust packet
			return TrustLevel.UNKNOWN;
		}
		int trust = packet[0] & 0x0F;
		switch (trust) {
		case 0: // No determined/set
		case 1: // Trust expired; i.e., calculation outdated or key expired
		case 2: // Undefined: not enough information to set
			return TrustLevel.UNKNOWN;
		case 3:
			return TrustLevel.NEVER;
		case 4:
			return TrustLevel.MARGINAL;
		case 5:
			return TrustLevel.FULL;
		case 6:
			return TrustLevel.ULTIMATE;
		default:
			return TrustLevel.UNKNOWN;
		}
	}

	@Override
	public void clear() {
		byFingerprint.clear();
		bySigner.clear();
	}

	private static class VerificationResult implements SignatureVerification {

		private final Date creationDate;

		private final String signer;

		private final String keyUser;

		private final String fingerprint;

		private final boolean verified;

		private final boolean expired;

		private final @NonNull TrustLevel trustLevel;

		private final String message;

		public VerificationResult(Date creationDate, String signer,
				String fingerprint, String user, boolean verified,
				boolean expired, @NonNull TrustLevel trust, String message) {
			this.creationDate = creationDate;
			this.signer = signer;
			this.fingerprint = fingerprint;
			this.keyUser = user;
			this.verified = verified;
			this.expired = expired;
			this.trustLevel = trust;
			this.message = message;
		}

		@Override
		public Date getCreationDate() {
			return creationDate;
		}

		@Override
		public String getSigner() {
			return signer;
		}

		@Override
		public String getKeyUser() {
			return keyUser;
		}

		@Override
		public String getKeyFingerprint() {
			return fingerprint;
		}

		@Override
		public boolean isExpired() {
			return expired;
		}

		@Override
		public TrustLevel getTrustLevel() {
			return trustLevel;
		}

		@Override
		public String getMessage() {
			return message;
		}

		@Override
		public boolean getVerified() {
			return verified;
		}
	}
}
