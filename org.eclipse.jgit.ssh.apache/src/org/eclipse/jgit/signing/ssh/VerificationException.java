/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing.ssh;

/**
 * An exception giving details about a failed
 * {@link SigningKeyDatabase#isAllowed(org.eclipse.jgit.lib.Repository, org.eclipse.jgit.lib.GpgConfig, java.security.PublicKey, String, org.eclipse.jgit.lib.PersonIdent)}
 * validation.
 *
 * @since 7.1
 */
public class VerificationException extends Exception {

	private static final long serialVersionUID = 313760495170326160L;

	private final boolean expired;

	private final String reason;

	/**
	 * Creates a new instance.
	 *
	 * @param expired
	 *            whether the checked public key or certificate was expired
	 * @param reason
	 *            describing the check failure
	 */
	public VerificationException(boolean expired, String reason) {
		this.expired = expired;
		this.reason = reason;
	}

	@Override
	public String getMessage() {
		return reason;
	}

	/**
	 * Tells whether the check failed because the public key was expired.
	 *
	 * @return {@code true} if the check failed because the public key was
	 *         expired, {@code false} otherwise
	 */
	public boolean isExpired() {
		return expired;
	}

	/**
	 * Retrieves the check failure reason.
	 *
	 * @return the reason description
	 */
	public String getReason() {
		return reason;
	}
}
