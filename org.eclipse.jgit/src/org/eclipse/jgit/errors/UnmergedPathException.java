/*
 * Copyright (C) 2008-2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates one or more paths in a DirCache have non-zero stages present.
 */
public class UnmergedPathException extends IOException {
	private static final long serialVersionUID = 1L;

	private final DirCacheEntry entry;

	/**
	 * Create a new unmerged path exception.
	 *
	 * @param dce
	 *            the first non-zero stage of the unmerged path.
	 */
	public UnmergedPathException(DirCacheEntry dce) {
		super(MessageFormat.format(JGitText.get().unmergedPath, dce.getPathString()));
		entry = dce;
	}

	/**
	 * Get the first non-zero stage of the unmerged path
	 *
	 * @return the first non-zero stage of the unmerged path
	 */
	public DirCacheEntry getDirCacheEntry() {
		return entry;
	}
}
