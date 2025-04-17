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

import java.util.Set;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

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
	 * @param oid
	 *            object id
	 * @return true of the index knows this the object
	 */
	boolean hasObject(AnyObjectId oid);

	/**
	 * Obtain the location of the object.
	 * <p>
	 * The returned object can be reused by the implementations. Callers
	 * must create a #copy() if they want to keep a reference.
	 *
	 * @param objectId
	 *            objectId to read.
	 * @return mutable instance with the location or null if not found.
	 */
	PackOffset find(AnyObjectId objectId);

	/**
	 * Find objects matching the prefix abbreviation.
	 *
	 * @param matches
	 *            set to add any located ObjectIds to. This is an output
	 *            parameter.
	 * @param id
	 *            prefix to search for.
	 * @param matchLimit
	 *            maximum number of results to return. At most this many
	 *            ObjectIds should be added to matches before returning.
	 */
	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id, int matchLimit);

	/**
	 * Memory size of this multipack index
	 *
	 * @return size of this multipack index in memory, in bytes
	 */
	long getMemorySize();

	/**
	 * (packId, offset) coordinates of an object
	 */
	class PackOffset {

		int packId;

		long offset;

		protected PackOffset setValues(int packId, long offset) {
			this.packId = packId;
			this.offset = offset;
			return this;
		}

		public int getPackId() {
			return packId;
		}

		public long getOffset() {
			return offset;
		}

		public PackOffset copy() {
			PackOffset copy = new PackOffset();
			return copy.setValues(this.packId, this.offset);
		}
	}
}
