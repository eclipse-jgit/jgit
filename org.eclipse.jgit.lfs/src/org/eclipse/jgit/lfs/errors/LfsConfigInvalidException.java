/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.errors;

import java.io.IOException;

/**
 * Thrown when a LFS configuration problem has been detected (i.e. unable to
 * find the remote LFS repository URL).
 *
 * @since 4.11
 */
public class LfsConfigInvalidException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for LfsConfigInvalidException.
	 *
	 * @param msg
	 *            the error description
	 */
	public LfsConfigInvalidException(String msg) {
		super(msg);
	}

	/**
	 * Constructor for LfsConfigInvalidException.
	 *
	 * @param msg
	 *            the error description
	 * @param e
	 *            cause of this exception
	 * @since 5.0
	 */
	public LfsConfigInvalidException(String msg, Exception e) {
		super(msg, e);
	}

}
