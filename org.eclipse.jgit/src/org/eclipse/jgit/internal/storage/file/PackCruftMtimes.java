/*
 * Copyright (C) 2026, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

/**
 * Modification times of objects in a cruft pack.
 *
 * <p>
 * Parallels the pack index: entry {@code i} gives the mtime of the object at
 * position {@code i} in the primary (SHA-1-sorted) pack index.
 */
public interface PackCruftMtimes {

	/**
	 * Get the modification time of an object.
	 *
	 * @param indexPosition
	 *            position in the primary pack index (0-based)
	 * @return modification time as seconds since the Unix epoch
	 */
	long getMtime(int indexPosition);

	/**
	 * Number of objects covered by this mtimes file.
	 *
	 * @return object count
	 */
	int getObjectCount();
}
