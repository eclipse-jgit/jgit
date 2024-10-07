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
 * An index over multiple packs
 */
public interface MultiPackIndex {

	/**
	 * Obtain the array of packfiles in the MultiPackIndex.
	 * <p>
	 * The pack ids correspond to positions in this list.
	 *
	 * @return array of packnames refered in this multipak index
	 */
	String[] getPackNames();

	/**
	 * Does this index contains the object
	 *
	 * @return true of the index knows this the object
	 */
	boolean hasObject(AnyObjectId oid);

	/**
	 * Obtain the location of the object
	 *
	 * @param objectId
	 *            objectId to read.
	 * @return location or null if not found.
	 */
	PackOffset find(AnyObjectId objectId);

	/**
	 * (packId, offset) coordinates of an object
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
