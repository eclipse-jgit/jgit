/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.resolver;

import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates that the requested service requires authentication that
 * the current user has not provided.
 * <p>
 * This corresponds to response code
 * {@code HttpServletResponse.SC_UNAUTHORIZED}.
 */
public class ServiceNotAuthorizedException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for ServiceNotAuthorizedException.
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable} object.
	 * @since 4.1
	 */
	public ServiceNotAuthorizedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for ServiceNotAuthorizedException.
	 *
	 * @param message
	 *            error message
	 * @since 4.1
	 */
	public ServiceNotAuthorizedException(String message) {
		super(message);
	}

	/**
	 * Indicates that the requested service requires authentication.
	 */
	public ServiceNotAuthorizedException() {
		super(JGitText.get().unauthorized);
	}
}
