/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Date;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.SignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.SignatureVerifier.TrustLevel;

/**
 * A default implementation of a {@link SignatureVerification}.
 *
 * @since 7.0
 */
public class SignatureVerificationResult implements SignatureVerification {

	private final String verifierName;

	private final Date creationDate;

	private final String signer;

	private final String keyUser;

	private final String fingerprint;

	private final boolean verified;

	private final boolean expired;

	private final @NonNull TrustLevel trustLevel;

	private final String message;

	/**
	 * Creates a new instance from the arguments.
	 *
	 * @param verifierName
	 *            the name of the verifier
	 * @param creationDate
	 *            the date the signature was created
	 * @param signer
	 *            the signer
	 * @param fingerprint
	 *            the key fingerprint
	 * @param user
	 *            the user associated with the key
	 * @param verified
	 *            whether the signature verified positively
	 * @param expired
	 *            whether the signature is expired
	 * @param trust
	 *            the trust level of the signature
	 * @param message
	 *            additional user-facing message
	 */
	public SignatureVerificationResult(String verifierName, Date creationDate, String signer,
			String fingerprint, String user, boolean verified,
			boolean expired, @NonNull TrustLevel trust,
			String message) {
		this.verifierName = verifierName;
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
	public String getVerifierName() {
		return verifierName;
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