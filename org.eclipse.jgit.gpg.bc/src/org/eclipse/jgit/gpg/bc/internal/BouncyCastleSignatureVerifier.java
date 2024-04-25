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

import java.io.IOException;
import java.security.Security;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.AbstractGpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.util.LRUMap;
import org.eclipse.jgit.util.StringUtils;

/**
 * A {@link GpgSignatureVerifier} to verify GPG signatures using BouncyCastle.
 */
public class BouncyCastleSignatureVerifier
	extends AbstractGpgSignatureVerifier {

	// To support more efficient signature verification of multiple objects we
	// cache public keys once found in a LRU cache.

	private static final Object NO_KEY = new Object();

	private LRUMap<String, Object> byFingerprint = new LRUMap<>(16, 200);

	private LRUMap<String, Object> bySigner = new LRUMap<>(16, 200);

	private static void registerBouncyCastleProviderIfNecessary() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	/**
	 * Creates a new instance and registers the BouncyCastle security provider
	 * if needed.
	 */
	public BouncyCastleSignatureVerifier() {
		registerBouncyCastleProviderIfNecessary();
	}

	@Override
	public String getName() {
		return "bc"; //$NON-NLS-1$
	}

	@Override
	public SignatureVerification verify(@NonNull GpgConfig config, byte[] data,
					    byte[] signatureData)
		throws IOException {
		SignatureFormatHandler<Object> signatureHandler = new GpgSignatureFormatHandler();
		SignatureFormatHandler.ParsedSignature<Object> signature;
		String fingerprint = null;
		try {
			Optional<SignatureFormatHandler.ParsedSignature<Object>> maybeSignature = signatureHandler.parseSignature(config, data, signatureData);
			if (maybeSignature.isEmpty()) {
				throw new JGitInternalException(BCText.get().nonSignatureError);
			}
			signature = maybeSignature.get();
		} catch (Exception e) {
			throw new JGitInternalException(BCText.get().signatureParseError,
				e);
		}

		Date signatureCreatedAt = signature.creationTime;
		if (signature.fingerprint == null && signature.signer == null && signature.keyId == null) {
			return new SignatureFormatHandler.VerificationResult(signatureCreatedAt, null, null, null,
				false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				BCText.get().signatureNoKeyInfo);
		}
		if (signature.fingerprint != null && signature.keyId != null
			&& !signature.fingerprint.endsWith(signature.keyId)) {
			return new SignatureFormatHandler.VerificationResult(signatureCreatedAt, signature.signer, signature.fingerprint,
				signature.signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				MessageFormat.format(BCText.get().signatureInconsistent,
					signature.keyId, signature.fingerprint));
		}
		if (signature.fingerprint == null && signature.keyId != null) {
			fingerprint = signature.keyId;
		} else {
			fingerprint = signature.fingerprint;
		}

		// Try to find the public key
		String keySpec = '<' + signature.signer + '>';
		Object cached = null;
		BouncyCastleGpgPublicKey publicKey = null;
		try {
			cached = byFingerprint.get(fingerprint);
			if (cached != null) {
				if (cached instanceof BouncyCastleGpgPublicKey) {
					publicKey = (BouncyCastleGpgPublicKey) cached;
				}
			} else if (!StringUtils.isEmptyOrNull(signature.signer)) {
				cached = bySigner.get(signature.signer);
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
				byFingerprint.put(signature.keyId, NO_KEY);
				if (signature.signer != null) {
					bySigner.put(signature.signer, NO_KEY);
				}
			}
			return new SignatureFormatHandler.VerificationResult(signatureCreatedAt, signature.signer,
				fingerprint, signature.signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				BCText.get().signatureNoPublicKey);
		}
		if (fingerprint != null && !publicKey.isExactMatch()) {
			// We did find _some_ signing key for the signer, but it doesn't
			// match the given fingerprint.
			return new SignatureFormatHandler.VerificationResult(signatureCreatedAt, signature.signer,
				fingerprint, signature.signer, false, false, GpgSignatureVerifier.TrustLevel.UNKNOWN,
				MessageFormat.format(BCText.get().signatureNoSigningKey,
					fingerprint));
		}
		if (cached == null) {
			byFingerprint.put(fingerprint, publicKey);
			byFingerprint.put(signature.keyId, publicKey);
			if (signature.signer != null) {
				bySigner.put(signature.signer, publicKey);
			}
		}
		String user = null;
		List<String> userIds = publicKey.getUserIds();
		if (userIds != null && !userIds.isEmpty()) {
			if (!StringUtils.isEmptyOrNull(signature.signer)) {
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
		} else if (signature.signer != null) {
			user = signature.signer;
		}
		return signatureHandler.verifySignature(data, publicKey, signature, fingerprint, user);
	}



	@Override
	public SignatureVerification verify(byte[] data, byte[] signatureData)
		throws IOException {
		throw new UnsupportedOperationException(
			"Call verify(GpgConfig, byte[], byte[]) instead."); //$NON-NLS-1$
	}

	@Override
	public void clear() {
		byFingerprint.clear();
		bySigner.clear();
	}
}
