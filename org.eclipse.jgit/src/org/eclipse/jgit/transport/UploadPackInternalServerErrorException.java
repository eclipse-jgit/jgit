/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * UploadPack has already reported an error to the client.
 */
public class UploadPackInternalServerErrorException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Initialize a new exception.
	 *
	 * @param why
	 *            root cause.
	 */
	public UploadPackInternalServerErrorException(Throwable why) {
		initCause(why);
	}
}
