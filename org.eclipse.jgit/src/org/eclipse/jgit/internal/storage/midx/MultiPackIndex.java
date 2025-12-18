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
	 * The returned object can be reused by the implementations. Callers must
	 * create a #copy() if they want to keep a reference.
	 *
	 * @param objectId
	 *            objectId to read.
	 * @return mutable instance with the location or null if not found.
	 */
	PackOffset find(AnyObjectId objectId);

	/**
	 * Position of the object in this midx, when all covered objects are ordered
	 * by SHA1.
	 * <p>
	 * As the midx removes duplicates, this position is NOT equivalent to
	 * "position in pack + total count of objects in previous packs in the
	 * stack".
	 *
	 * @param objectId
	 *            an object id
	 * @return position of the object in this multipack index
	 */
	int findPosition(AnyObjectId objectId);

	/**
	 * Return the position in offset order (i.e. ridx or bitmap position) for
	 * the (packId, offset pair).
	 *
	 * @param po
	 *            a location in the midx (packId, offset)
	 * @return the position in the midx, in offset order
	 */
	int findBitmapPosition(PackOffset po);

	/**
	 * Object id at the specified position in offset order (i.e position in the
	 * ridx or bitmap)
	 * 
	 * @param bitmapPosition
	 *            position in the bitmap
	 * @return object id at that position.
	 */
	ObjectId getObjectAtBitmapPosition(int bitmapPosition);

	/**
	 * Number of objects in this midx
	 * <p>
	 * This number doesn't match with the sum of objects in each covered pack
	 * because midx removes duplicates.
	 *
	 * @return number of objects in this midx
	 */
	int getObjectCount();

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
	 * <p>
	 * Mutable object to avoid creating many instances while looking for objects
	 * in the pack. Use #copy() to get a new instance with the data.
	 */
	class PackOffset implements Comparable<PackOffset> {

		private int packId;

		private long offset;

		/**
		 * Return a new PackOffset with the defined data.
		 * <p>
		 * This is for tests, as regular code reuses the instance
		 *
		 * @param packId
		 *            a pack id
		 * @param offset
		 *            an offset
		 * @return a new PackOffset instance with this data
		 */
		public static PackOffset create(int packId, long offset) {
			return new PackOffset().setValues(packId, offset);
		}

		public PackOffset() {
		}

		public PackOffset setValues(int packId, long offset) {
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

		@Override
		public int compareTo(PackOffset packOffset) {
			int cmp = this.packId - packOffset.packId;
			if (cmp != 0) {
				return cmp;
			}

			return Long.compare(this.offset, packOffset.offset);
		}
	}
}
