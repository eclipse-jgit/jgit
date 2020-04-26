/*
 * Copyright (C) 2020, Matthias Sohn <matthias.sohn@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when an optional service is not available
 *
 * @since 5.8
 */
public class ServiceUnavailableException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for ServiceUnavailableException
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 */
	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for ServiceUnavailableException
	 *
	 * @param message
	 *            error message
	 */
	public ServiceUnavailableException(String message) {
		super(message);
	}
}
