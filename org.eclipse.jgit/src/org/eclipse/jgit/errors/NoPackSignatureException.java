/*
 * Copyright (C) 2017, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Thrown when a PackFile is found not to contain the pack signature defined by
 * git.
 *
 * @since 4.5
 */
public class NoPackSignatureException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an exception.
	 *
	 * @param why
	 *            description of the type of error.
	 */
	public NoPackSignatureException(String why) {
		super(why);
	}
}
