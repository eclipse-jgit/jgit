/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.midx;

import org.eclipse.jgit.lib.AnyObjectId;

/**
 * The MultiPackIndex is a supplemental data structure that accelerates objects
 * retrieval.
 */
public interface MultiPackIndex {

	/**
	 * Obtain the array of packfiles in the MultiPackIndex.
	 *
	 * @return array of packfiles in the MultiPackIndex.
	 */
	String[] getPackNames();

	/**
	 * Does this index contains the object
	 *
	 * @return true of the index knows this the object
	 */
	boolean hasObject(AnyObjectId oid);

	/**
	 * Obtain the ObjectOffset in the MultiPackIndex.
	 *
	 * @param objectId
	 *            objectId to read.
	 * @return ObjectOffset from the MultiPackIndex.
	 */
	PackOffset find(AnyObjectId objectId);

	/**
	 * Object offset in data chunk.
	 */
	class PackOffset {

		int packId;

		long offset;

		public PackOffset(int packId, long offset) {
			this.packId = packId;
			this.offset = offset;
		}

		public int getPackId() {
			return packId;
		}

		public long getOffset() {
			return offset;
		}
	}
}
