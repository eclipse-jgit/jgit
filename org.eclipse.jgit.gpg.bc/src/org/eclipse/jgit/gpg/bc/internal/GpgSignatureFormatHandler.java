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
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

class GpgSignatureFormatHandler implements SignatureFormatHandler<Object> {

	public Optional<ParsedSignature<Object>> parseSignature(@NonNull GpgConfig config, byte[] data,
							byte[] signatureData)
		throws IOException, PGPException {

		Optional<PGPSignature> gpgSignature = Optional.empty();

		try (InputStream sigIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(signatureData))) {
			JcaPGPObjectFactory pgpFactory = new JcaPGPObjectFactory(sigIn);
			Object obj = pgpFactory.nextObject();
			if (obj instanceof PGPCompressedData) {
				obj = new JcaPGPObjectFactory(
					((PGPCompressedData) obj).getDataStream()).nextObject();
			}
			if (obj instanceof PGPSignatureList) {
				gpgSignature = Optional.of(((PGPSignatureList) obj).get(0));
			}
		}

		return gpgSignature.filter(PGPSignature::hasSubpackets)
			.map(sig -> {
				String fingerprint;
				String signer;
				String keyId;

				// Try to figure out something to find the public key with.
				PGPSignatureSubpacketVector packets = sig
					.getHashedSubPackets();
				IssuerFingerprint fingerprintPacket = packets.getIssuerFingerprint();
				fingerprint = fingerprintPacket == null ? null :
					Hex.toHexString(fingerprintPacket.getFingerprint())
						.toLowerCase(Locale.ROOT);
				String signerUserId = packets.getSignerUserID();
				signer = signerUserId == null ? null :
					BouncyCastleGpgSigner.extractSignerId(signerUserId);

				keyId = Long.toUnsignedString(sig.getKeyID(), 16)
					.toLowerCase(Locale.ROOT);

				return new ParsedSignature<>(sig.getCreationTime(), keyId, fingerprint, signer, sig);
			});
	}

	public SignatureFormatHandler.VerificationResult verifySignature(byte[] data, BouncyCastleGpgPublicKey publicKey, ParsedSignature<Object> signature, String fingerprint, String user) {
		PGPPublicKey pubKey = publicKey.getPublicKey();
		boolean expired = false;
		long validFor = pubKey.getValidSeconds();
		if (validFor > 0 && signature.creationTime != null) {
			Instant expiredAt = pubKey.getCreationTime().toInstant()
				.plusSeconds(validFor);
			expired = expiredAt.isBefore(signature.creationTime.toInstant());
		}
		// Trust data is not defined in OpenPGP; the format is implementation
		// specific. We don't use the GPG trustdb but simply the trust packet
		// on the public key, if present. Even if present, it may or may not
		// be set.
		byte[] trustData = pubKey.getTrustData();
		GpgSignatureVerifier.TrustLevel trust = parseGpgTrustPacket(trustData);
		boolean verified = false;
		try {
			PGPSignature pgpSignature = (PGPSignature) signature.lowLevelSignature;
			pgpSignature.init(
				new JcaPGPContentVerifierBuilderProvider()
					.setProvider(BouncyCastleProvider.PROVIDER_NAME),
				pubKey);
			pgpSignature.update(data);
			verified = pgpSignature.verify();
		} catch (PGPException e) {
			throw new JGitInternalException(
				BCText.get().signatureVerificationError, e);
		}
		return new SignatureFormatHandler.VerificationResult(signature.creationTime, signature.signer, fingerprint, user,
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
}
