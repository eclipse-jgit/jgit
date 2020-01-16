/*
 * Copyright (C) 2012, IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when applying a patch fails
 *
 * @since 2.0
 */
public class PatchApplyException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for PatchApplyException
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 */
	public PatchApplyException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for PatchApplyException
	 *
	 * @param message
	 *            error message
	 */
	public PatchApplyException(String message) {
		super(message);
	}

}
