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
 * Indicates the request service is not enabled on a repository.
 */
public class ServiceNotEnabledException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for ServiceNotEnabledException.
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable} object.
	 * @since 4.1
	 */
	public ServiceNotEnabledException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for ServiceNotEnabledException.
	 *
	 * @param message
	 *            error message
	 * @since 4.1
	 */
	public ServiceNotEnabledException(String message) {
		super(message);
	}

	/**
	 * Indicates the request service is not available.
	 */
	public ServiceNotEnabledException() {
		super(JGitText.get().serviceNotEnabledNoName);
	}
}
