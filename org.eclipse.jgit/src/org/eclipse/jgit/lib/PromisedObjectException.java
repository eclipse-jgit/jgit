/*
 * Copyright (C) 2026, Kyln contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;

/**
 * Typed failure reported by a {@link PromisedObjectResolver}.
 * <p>
 * This class is part of an experimental prototyped feature (JGit promisor
 * resolution); the API is not yet frozen and may change without notice.
 *
 * @since 8.0
 */
public class PromisedObjectException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * Reason why a promise could not be resolved.
	 */
	public enum Reason {
		/** The store does not know the promised object. */
		NOT_FOUND,

		/** The store returned content that is not the promised object. */
		INTEGRITY,

		/** The store did not answer within the allowed time. */
		TIMEOUT,

		/** The caller is not allowed to read the promised object. */
		AUTHORIZATION
	}

	private final Reason reason;

	/**
	 * Creates an exception.
	 *
	 * @param reason
	 *            typed reason for the failure.
	 * @param message
	 *            description of the failure; should identify the repository
	 *            namespace and object involved.
	 */
	public PromisedObjectException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	/**
	 * Creates an exception with an underlying cause.
	 *
	 * @param reason
	 *            typed reason for the failure.
	 * @param message
	 *            description of the failure; should identify the repository
	 *            namespace and object involved.
	 * @param cause
	 *            underlying failure.
	 */
	public PromisedObjectException(Reason reason, String message,
			Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	/**
	 * Get the typed reason for the failure.
	 *
	 * @return the reason, never {@code null}.
	 */
	public Reason getReason() {
		return reason;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		String s = getClass().getName();
		String message = getLocalizedMessage();
		return reason + ": " + (message == null ? s : s + ": " + message); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
