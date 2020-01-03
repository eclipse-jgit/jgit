/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.internal.storage.pack.ObjectToPack;

/**
 * A previously selected representation is no longer available.
 */
public class StoredObjectRepresentationNotAvailableException extends Exception { //TODO remove unused ObjectToPack in 5.0
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an error for an object.
	 *
	 * @param otp
	 *            the object whose current representation is no longer present.
	 * @param cause
	 *            cause
	 * @since 4.10
	 */
	public StoredObjectRepresentationNotAvailableException(ObjectToPack otp,
			Throwable cause) {
		super(cause);
		// Do nothing.
	}
}
