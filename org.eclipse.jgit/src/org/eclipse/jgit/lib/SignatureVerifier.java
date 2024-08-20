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
import org.eclipse.jgit.api.errors.JGitInternalException;

/**
 * A {@code SignatureVerifier} can verify signatures on git commits and tags.
 *
 * @since 7.0
 */
public interface SignatureVerifier {

	/**
	 * Verifies a given signature for given data.
	 *
	 * @param repository
	 *            the {@link Repository} the data comes from.
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
	 */
	SignatureVerification verify(@NonNull Repository repository,
			@NonNull GpgConfig config, byte[] data, byte[] signatureData)
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
	 * A {@link SignatureVerifier} may cache public keys to speed up
	 * verifying signatures on multiple objects. This clears this cache, if any.
	 */
	void clear();

	/**
	 * A {@code SignatureVerification} returns data about a (positively or
	 * negatively) verified signature.
	 */
	interface SignatureVerification {

		/**
		 * Obtains the name of the verifier that created this verification
		 * result.
		 *
		 * @return the verifier's name
		 * @see SignatureVerifier#getName()
		 */
		String getVerifierName();

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
		 * Obtains a fingerprint of the public key, if known.
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
