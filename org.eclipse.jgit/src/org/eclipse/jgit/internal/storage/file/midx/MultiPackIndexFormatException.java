/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

import java.io.IOException;

/**
 * Thrown when a MultiPackIndex file's format is different from we expected
 */
public class MultiPackIndexFormatException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct an exception.
	 *
	 * @param why
	 * 		description of the type of error.
	 */
	MultiPackIndexFormatException(String why) {
		super(why);
	}
}
