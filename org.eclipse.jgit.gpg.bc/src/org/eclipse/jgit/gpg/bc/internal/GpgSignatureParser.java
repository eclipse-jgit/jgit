/*
 * Copyright (C) 2024, GerritForge Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.gpg.bc.internal;

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
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.util.LRUMap;
import org.eclipse.jgit.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class GpgSignatureParser implements SignatureParser {

	private static final String SIGNATURE_PREFIX = "-----BEGIN PGP SIGNATURE-----";

	private static final GpgSignatureParser SINGLETON = new GpgSignatureParser();

	// To support more efficient signature verification of multiple objects we
	// cache public keys once found in a LRU cache.

	private static final Object NO_KEY = new Object();

	private LRUMap<String, Object> byFingerprint = new LRUMap<>(16, 200);

	private LRUMap<String, Object> bySigner = new LRUMap<>(16, 200);

	static {
		SignatureParserProvider.registerSignatureParser(SIGNATURE_PREFIX, SINGLETON);
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
	public GpgSignatureVerifier.SignatureVerification verify(GpgConfig config, byte[] data, byte[] signatureData) throws IOException {
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
		} catch (NumberFormatException | PGPException e) {
			throw new JGitInternalException(BCText.get().signatureParseError,
				e);
		}
		Date signatureCreatedAt = signature.getCreationTime();
		if (fingerprint == null && signer == null && keyId == null) {
			return new VerificationResult(signatureCreatedAt, null, null, null,
				false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				BCText.get().signatureNoKeyInfo);
		}
		if (fingerprint != null && keyId != null
			&& !fingerprint.endsWith(keyId)) {
			return new VerificationResult(signatureCreatedAt, signer, fingerprint,
				signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				MessageFormat.format(BCText.get().signatureInconsistent,
					keyId, fingerprint));
		}
		if (fingerprint == null && keyId != null) {
			fingerprint = keyId;
		}
		// Try to find the public key
		String keySpec = '<' + signer + '>';
		Object cached = null;
		BouncyCastleGpgPublicKey publicKey = null;
		try {
			cached = byFingerprint.get(fingerprint);
			if (cached != null) {
				if (cached instanceof BouncyCastleGpgPublicKey) {
					publicKey = (BouncyCastleGpgPublicKey) cached;
				}
			} else if (!StringUtils.isEmptyOrNull(signer)) {
				cached = bySigner.get(signer);
				if (cached != null) {
					if (cached instanceof BouncyCastleGpgPublicKey) {
						publicKey = (BouncyCastleGpgPublicKey) cached;
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
				fingerprint, signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				BCText.get().signatureNoPublicKey);
		}
		if (fingerprint != null && !publicKey.isExactMatch()) {
			// We did find _some_ signing key for the signer, but it doesn't
			// match the given fingerprint.
			return new VerificationResult(signatureCreatedAt, signer,
				fingerprint, signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				MessageFormat.format(BCText.get().signatureNoSigningKey,
					fingerprint));
		}
		if (cached == null) {
			byFingerprint.put(fingerprint, publicKey);
			byFingerprint.put(keyId, publicKey);
			if (signer != null) {
				bySigner.put(signer, publicKey);
			}
		}
		String user = null;
		List<String> userIds = publicKey.getUserIds();
		if (userIds != null && !userIds.isEmpty()) {
			if (!StringUtils.isEmptyOrNull(signer)) {
				for (String userId : publicKey.getUserIds()) {
					if (BouncyCastleGpgKeyLocator.containsSigningKey(userId,
						keySpec)) {
						user = userId;
						break;
					}
				}
			}
			if (user == null) {
				user = userIds.get(0);
			}
		} else if (signer != null) {
			user = signer;
		}
		PGPPublicKey pubKey = publicKey.getPublicKey();
		boolean expired = false;
		long validFor = pubKey.getValidSeconds();
		if (validFor > 0 && signatureCreatedAt != null) {
			Instant expiredAt = pubKey.getCreationTime().toInstant()
				.plusSeconds(validFor);
			expired = expiredAt.isBefore(signatureCreatedAt.toInstant());
		}
		// Trust data is not defined in OpenPGP; the format is implementation
		// specific. We don't use the GPG trustdb but simply the trust packet
		// on the public key, if present. Even if present, it may or may not
		// be set.
		byte[] trustData = pubKey.getTrustData();
		GpgSignatureVerifier.TrustLevel trust = parseGpgTrustPacket(trustData);
		boolean verified = false;
		try {
			signature.init(
				new JcaPGPContentVerifierBuilderProvider()
					.setProvider(BouncyCastleProvider.PROVIDER_NAME),
				pubKey);
			signature.update(data);
			verified = signature.verify();
		} catch (PGPException e) {
			throw new JGitInternalException(
				BCText.get().signatureVerificationError, e);
		}
		return new VerificationResult(signatureCreatedAt, signer, fingerprint, user,
			verified, expired, trust, null);
	}

	private GpgSignatureVerifier.TrustLevel parseGpgTrustPacket(byte[] packet) {
		if (packet == null || packet.length < 6) {
			// A GPG trust packet has at least 6 bytes.
			return GpgSignatureVerifier.TrustLevel.UNKNOWN;
		}
		if (packet[2] != 'g' || packet[3] != 'p' || packet[4] != 'g') {
			// Not a GPG trust packet
			return GpgSignatureVerifier.TrustLevel.UNKNOWN;
		}
		int trust = packet[0] & 0x0F;
		switch (trust) {
			case 0: // No determined/set
			case 1: // Trust expired; i.e., calculation outdated or key expired
			case 2: // Undefined: not enough information to set
				return GpgSignatureVerifier.TrustLevel.UNKNOWN;
			case 3:
				return GpgSignatureVerifier.TrustLevel.NEVER;
			case 4:
				return GpgSignatureVerifier.TrustLevel.MARGINAL;
			case 5:
				return GpgSignatureVerifier.TrustLevel.FULL;
			case 6:
				return GpgSignatureVerifier.TrustLevel.ULTIMATE;
			default:
				return GpgSignatureVerifier.TrustLevel.UNKNOWN;
		}
	}

	@Override
	public void clear() {
		byFingerprint.clear();
		bySigner.clear();
	}
}
