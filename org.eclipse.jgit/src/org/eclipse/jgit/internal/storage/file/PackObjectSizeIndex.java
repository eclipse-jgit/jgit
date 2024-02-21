/*
 * Copyright (C) 2022, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

/**
 * Index of object sizes in a pack
 *
 * It is not guaranteed that the implementation contains the sizes of all
 * objects (e.g. it could store only objects over certain threshold).
 */
public interface PackObjectSizeIndex {

	/**
	 * Returns the inflated size of the object.
	 *
	 * @param idxOffset
	 *            position in the pack (as returned from PackIndex)
	 * @return size of the object, -1 if not found in the index.
	 */
	long getSize(int idxOffset);

	/**
	 * Number of objects in the index
	 *
	 * @return number of objects in the index
	 */
	long getObjectCount();


	/**
	 * Minimal size of an object to be included in this index
	 *
	 * Cut-off value used at generation time to decide what objects to index.
	 *
	 * @return size in bytes
	 */
	int getThreshold();
}
