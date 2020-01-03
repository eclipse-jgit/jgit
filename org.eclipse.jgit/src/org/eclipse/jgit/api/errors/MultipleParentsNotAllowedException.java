/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

/**
 * The commit to be cherry-pick'ed did not have exactly one parent
 */
public class MultipleParentsNotAllowedException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for MultipleParentsNotAllowedException.
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 */
	public MultipleParentsNotAllowedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for MultipleParentsNotAllowedException.
	 *
	 * @param message
	 *            error message
	 */
	public MultipleParentsNotAllowedException(String message) {
		super(message);
	}
}
