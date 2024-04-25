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

import org.bouncycastle.openpgp.PGPException;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

interface SignatureFormatHandler<T> {
	Optional<ParsedSignature<T>> parseSignature(@NonNull GpgConfig config, byte[] data,
					byte[] signatureData) throws IOException, PGPException;

	SignatureFormatHandler.VerificationResult verifySignature(byte[] data, BouncyCastleGpgPublicKey publicKey, SignatureFormatHandler.ParsedSignature<T> signature, String fingerprint, String user);

	class ParsedSignature<T> {
		final Date creationTime;
		final String keyId;
		final String fingerprint;
		final String signer;
		final T lowLevelSignature;

		public ParsedSignature(Date creationTime, String keyId, String fingerprint, String signer, T lowLevelSignature) {
			this.creationTime = creationTime;
			this.keyId = keyId;
			this.fingerprint = fingerprint;
			this.signer = signer;
			this.lowLevelSignature = lowLevelSignature;
		}
	}

	class VerificationResult implements GpgSignatureVerifier.SignatureVerification {

		private final Date creationDate;

		private final String signer;

		private final String keyUser;

		private final String fingerprint;

		private final boolean verified;

		private final boolean expired;

		private final @NonNull GpgSignatureVerifier.TrustLevel trustLevel;

		private final String message;

		public VerificationResult(Date creationDate, String signer,
					  String fingerprint, String user, boolean verified,
					  boolean expired, @NonNull GpgSignatureVerifier.TrustLevel trust, String message) {
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
		public GpgSignatureVerifier.TrustLevel getTrustLevel() {
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
