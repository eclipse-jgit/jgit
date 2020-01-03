/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

/**
 * Indicates a text string is not a valid Git style configuration.
 */
public class ConfigInvalidException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an invalid configuration error.
	 *
	 * @param message
	 *            why the configuration is invalid.
	 */
	public ConfigInvalidException(String message) {
		super(message);
	}

	/**
	 * Construct an invalid configuration error.
	 *
	 * @param message
	 *            why the configuration is invalid.
	 * @param cause
	 *            root cause of the error.
	 */
	public ConfigInvalidException(String message, Throwable cause) {
		super(message, cause);
	}
}
