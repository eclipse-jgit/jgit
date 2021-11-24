/*
 * Copyright (C) 2010, 2021 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.errors;

/**
 * A previously selected representation is no longer available.
 */
public class StoredObjectRepresentationNotAvailableException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance.
	 *
	 * @param cause
	 *            {@link Throwable} that caused this exception
	 * @since 6.0
	 */
	public StoredObjectRepresentationNotAvailableException(Throwable cause) {
		super(cause);
	}
}
