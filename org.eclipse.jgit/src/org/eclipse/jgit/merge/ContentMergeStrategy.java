/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

/**
 * How to handle content conflicts.
 *
 * @since 5.12
 */
public enum ContentMergeStrategy {

	/** Produce a conflict. */
	CONFLICT,

	/** Resolve the conflict hunk using the ours version. */
	OURS,

	/** Resolve the conflict hunk using the theirs version. */
	THEIRS
}