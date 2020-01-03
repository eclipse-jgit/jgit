/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

/**
 * Thrown by DirCache code when entries overlap in impossible way.
 *
 * @since 4.2
 */
public class DirCacheNameConflictException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	private final String path1;
	private final String path2;

	/**
	 * Construct an exception for a specific path.
	 *
	 * @param path1
	 *            one path that conflicts.
	 * @param path2
	 *            another path that conflicts.
	 */
	public DirCacheNameConflictException(String path1, String path2) {
		super(path1 + ' ' + path2);
		this.path1 = path1;
		this.path2 = path2;
	}

	/**
	 * Get one of the paths that has a conflict
	 *
	 * @return one of the paths that has a conflict
	 */
	public String getPath1() {
		return path1;
	}

	/**
	 * Get another path that has a conflict
	 *
	 * @return another path that has a conflict
	 */
	public String getPath2() {
		return path2;
	}
}
