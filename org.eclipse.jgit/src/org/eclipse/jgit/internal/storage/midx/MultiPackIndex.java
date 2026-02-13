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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An index over multiple packs
 */
public interface MultiPackIndex extends Iterable<MultiPackIndex.MutableEntry> {

	/**
	 * Obtain the array of packfiles in the MultiPackIndex.
	 * <p>
	 * The pack ids correspond to positions in this list.
	 *
	 * @return array of packnames refered in this multipack index
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
	 * the (packId, offset) pair.
	 *
	 * @param po
	 *            a location in the midx (packId, offset)
	 * @return the position in the midx, in offset order
	 */
	int findBitmapPosition(PackOffset po);

	/**
	 * Object id at the specified position in offset order (i.e. position in the
	 * ridx or bitmap)
	 *
	 * @param bitmapPosition
	 *            position in the bitmap
	 * @return object id at that position.
	 */
	ObjectId getObjectAtBitmapPosition(int bitmapPosition);

	/**
	 * ObjectId at this position in the midx
	 *
	 * @param position
	 *            position inside this midx in sha1 order
	 * @return the object id at that position
	 */
	ObjectId getObjectAt(int position);

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
	 * Index checksum of the contents of this midx file
	 *
	 * @return checksum of the contents of this midx file
	 */
	byte[] getChecksum();

	/**
	 * Memory size of this multipack index
	 *
	 * @return size of this multipack index in memory, in bytes
	 */
	long getMemorySize();

	/**
	 * Return an iterator over the <em>local</em> objects in this midx.
	 * <p>
	 * In chained midxs, this iterator does not include the base midx.
	 *
	 * @return iterator in sha1 order of the objects in this midx
	 */
	@Override
	MidxIterator iterator();

	/**
	 * An peekable iterator for the midx
	 */
	interface MidxIterator extends Iterator<MutableEntry> {
		/**
		 * Like next() but without advancing the iterator.
		 *
		 * @return next() element in the iterator without advancing
		 */
		MutableEntry peek();

		/**
		 * Pack names in the order of new packIds emitted by the iterator
		 *
		 * @return pack names
		 */
		List<String> getPackNames();

		/**
		 * Restart the iteration from the beginning
		 */
		void reset();
	}

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

		@Override
		public String toString() {
			return String.format("PackOffset(packId=%d|offset=%d)", packId,
					offset);

		}
	}

	/**
	 * Entry from the midx with object id, pack id, offset (in pack)
	 * <p>
	 * Mutable so the iterator can reuse the instance for performance.
	 */
	class MutableEntry {
		protected final MutableObjectId oid = new MutableObjectId();

		protected final PackOffset packOffset = new PackOffset();

		/**
		 * Copy data from other into this instance, adding the shift to the
		 * packId
		 *
		 * @param other
		 *            another entry
		 * @param shift
		 *            amount to add to the packid
		 * @return this instance
		 */
		public MutableEntry fill(MutableEntry other, int shift) {
			oid.fromObjectId(other.oid);
			packOffset.setValues(other.getPackId() + shift, other.getOffset());
			return this;
		}

		public MutableObjectId getObjectId() {
			return oid;
		}

		public int getPackId() {
			return packOffset.getPackId();
		}

		public long getOffset() {
			return packOffset.getOffset();
		}

		public void clear() {
			oid.clear();
			packOffset.setValues(0, 0);
		}

		@Override
		public String toString() {
			return String.format("%s,%s", oid.name(), packOffset);
		}
	}
}
