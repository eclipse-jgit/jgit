/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import java.io.IOException;

/**
 * Thrown when a commit-graph file's format is different from we expected
 */
public class CommitGraphFormatException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct an exception.
	 *
	 * @param why
	 *            description of the type of error.
	 */
	CommitGraphFormatException(String why) {
		super(why);
	}
}
