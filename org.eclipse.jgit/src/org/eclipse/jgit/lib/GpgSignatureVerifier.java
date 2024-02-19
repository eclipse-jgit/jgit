/*
 * Copyright (C) 2021, 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Date;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * A {@code GpgSignatureVerifier} can verify GPG signatures on git commits and
 * tags.
 *
 * @since 5.11
 */
public interface GpgSignatureVerifier {

	/**
	 * Verifies the signature on a signed commit or tag.
	 *
	 * @param object
	 *            to verify
	 * @param config
	 *            the {@link GpgConfig} to use
	 * @return a {@link SignatureVerification} describing the outcome of the
	 *         verification, or {@code null} if the object was not signed
	 * @throws IOException
	 *             if an error occurs getting a public key
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if signature verification fails
	 */
	@Nullable
	SignatureVerification verifySignature(@NonNull RevObject object,
			@NonNull GpgConfig config) throws IOException;

	/**
	 * Verifies a given signature for given data.
	 *
	 * @param config
	 *            the {@link GpgConfig}
	 * @param data
	 *            the signature is for
	 * @param signatureData
	 *            the ASCII-armored signature
	 * @return a {@link SignatureVerification} describing the outcome
	 * @throws IOException
	 *             if the signature cannot be parsed
	 * @throws JGitInternalException
	 *             if signature verification fails
	 * @since 6.9
	 */
	default SignatureVerification verify(@NonNull GpgConfig config, byte[] data,
			byte[] signatureData) throws IOException {
		// Default implementation for backwards compatibility; override as
		// appropriate
		return verify(data, signatureData);
	}

	/**
	 * Verifies a given signature for given data.
	 *
	 * @param data
	 *            the signature is for
	 * @param signatureData
	 *            the ASCII-armored signature
	 * @return a {@link SignatureVerification} describing the outcome
	 * @throws IOException
	 *             if the signature cannot be parsed
	 * @throws JGitInternalException
	 *             if signature verification fails
	 * @deprecated since 6.9, use {@link #verify(GpgConfig, byte[], byte[])}
	 *             instead
	 */
	@Deprecated
	public SignatureVerification verify(byte[] data, byte[] signatureData)
			throws IOException;

	/**
	 * Retrieves the name of this verifier. This should be a short string
	 * identifying the engine that verified the signature, like "gpg" if GPG is
	 * used, or "bc" for a BouncyCastle implementation.
	 *
	 * @return the name
	 */
	@NonNull
	String getName();

	/**
	 * A {@link GpgSignatureVerifier} may cache public keys to speed up
	 * verifying signatures on multiple objects. This clears this cache, if any.
	 */
	void clear();

	/**
	 * A {@code SignatureVerification} returns data about a (positively or
	 * negatively) verified signature.
	 */
	interface SignatureVerification {

		// Data about the signature.

		@NonNull
		Date getCreationDate();

		// Data from the signature used to find a public key.

		/**
		 * Obtains the signer as stored in the signature, if known.
		 *
		 * @return the signer, or {@code null} if unknown
		 */
		String getSigner();

		/**
		 * Obtains the short or long fingerprint of the public key as stored in
		 * the signature, if known.
		 *
		 * @return the fingerprint, or {@code null} if unknown
		 */
		String getKeyFingerprint();

		// Some information about the found public key.

		/**
		 * Obtains the OpenPGP user ID associated with the key.
		 *
		 * @return the user id, or {@code null} if unknown
		 */
		String getKeyUser();

		/**
		 * Tells whether the public key used for this signature verification was
		 * expired when the signature was created.
		 *
		 * @return {@code true} if the key was expired already, {@code false}
		 *         otherwise
		 */
		boolean isExpired();

		/**
		 * Obtains the trust level of the public key used to verify the
		 * signature.
		 *
		 * @return the trust level
		 */
		@NonNull
		TrustLevel getTrustLevel();

		// The verification result.

		/**
		 * Tells whether the signature verification was successful.
		 *
		 * @return {@code true} if the signature was verified successfully;
		 *         {@code false} if not.
		 */
		boolean getVerified();

		/**
		 * Obtains a human-readable message giving additional information about
		 * the outcome of the verification.
		 *
		 * @return the message, or {@code null} if none set.
		 */
		String getMessage();
	}

	/**
	 * The owner's trust in a public key.
	 */
	enum TrustLevel {
		UNKNOWN, NEVER, MARGINAL, FULL, ULTIMATE
	}
}
