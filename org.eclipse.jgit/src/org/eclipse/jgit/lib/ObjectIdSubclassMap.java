/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.util.BlockList;

/**
 * Fast, efficient map specifically for {@link ObjectId} subclasses.
 * <p>
 * This map provides an efficient translation from any ObjectId instance to a
 * cached subclass of ObjectId that has the same value.
 * <p>
 * The current implementation is based on extendible hashing, from Ronald Fagin,
 * Jurg Nievergelt, Nicholas Pippenger, and H. Raymond Strong. 1979. <a
 * href="http://doi.acm.org/10.1145/320083.320092">Extendible hashing—a fast
 * access method for dynamic files</a>. ACM Trans. Database Syst. 4, 3
 * (September 1979), 315-344. DOI=10.1145/320083.320092
 *
 * @param <V>
 *            type of subclass of ObjectId that will be stored in the map.
 */
public class ObjectIdSubclassMap<V extends ObjectId> implements Iterable<V> {
	private final ArrayList<Block<V>> freeList;

	private final BlockList<Block<V>> dirTable;

	private int dirBits;

	private int dirShift;

	private int size;

	/** Create an empty map. */
	public ObjectIdSubclassMap() {
		freeList = new ArrayList<Block<V>>(2);
		dirTable = new BlockList<Block<V>>();
		init();
	}

	/** Remove all entries from this map. */
	public void clear() {
		freeList.add(dirTable.remove(dirTable.size() - 1));
		freeList.add(dirTable.remove(dirTable.size() - 1));

		dirTable.clear();
		init();
		size = 0;
	}

	private void init() {
		dirTable.add(newBlock(1));
		dirTable.add(newBlock(1));
		dirBits = 1;
		dirShift = 32 - dirBits;
	}

	private Block<V> newBlock(int newDepth) {
		if (freeList.isEmpty())
			return new Block<V>(newDepth);

		Block<V> b = freeList.remove(freeList.size() - 1);
		b.reset(newDepth);
		return b;
	}

	private int hash(AnyObjectId toFind) {
		return toFind.w1 >>> dirShift;
	}

	/**
	 * Lookup an existing mapping.
	 *
	 * @param toFind
	 *            the object identifier to find.
	 * @return the instance mapped to toFind, or null if no mapping exists.
	 */
	public V get(final AnyObjectId toFind) {
		return dirTable.get(hash(toFind)).get(toFind);
	}

	/**
	 * Returns true if this map contains the specified object.
	 *
	 * @param toFind
	 *            object to find.
	 * @return true if the mapping exists for this object; false otherwise.
	 */
	public boolean contains(final AnyObjectId toFind) {
		return get(toFind) != null;
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * An existing mapping for <b>must not</b> be in this map. Callers must
	 * first call {@link #get(AnyObjectId)} to verify there is no current
	 * mapping prior to adding a new mapping, or use
	 * {@link #addIfAbsent(ObjectId)}.
	 *
	 * @param newValue
	 *            the object to store.
	 * @param <Q>
	 *            type of instance to store.
	 */
	public <Q extends V> void add(final Q newValue) {
		addIfAbsent(newValue);
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * Stores {@code newValue}, but only if there is not already an object for
	 * the same object name. Callers can tell if the value is new by checking
	 * the return value with reference equality:
	 *
	 * <pre>
	 * V obj = ...;
	 * boolean wasNew = map.addIfAbsent(obj) == obj;
	 * </pre>
	 *
	 * @param newValue
	 *            the object to store.
	 * @return {@code newValue} if stored, or the prior value already stored and
	 *         that would have been returned had the caller used
	 *         {@code get(newValue)} first.
	 * @param <Q>
	 *            type of instance to store.
	 */
	public <Q extends V> V addIfAbsent(final Q newValue) {
		for (;;) {
			final Block<V> b = dirTable.get(hash(newValue));
			final V old = b.addIfAbsent(newValue);
			if (old == newValue) {
				size++;
				return newValue;
			}

			if (old != null)
				return old;

			// At this point the block has returned null to say its full.
			// The block needs to be split in order to make this addition.

			final int bDepth = b.depth;
			if (bDepth == dirBits) {
				// If the block depth is the same as the directory bits,
				// the directory should be doubled in size. A rewrite is
				// necessary to keep blocks in consecutive slots.
				//
				final int cnt = dirTable.size();
				dirTable.setSize(cnt << 1);
				for (int i = cnt - 1; 0 <= i; i--) {
					Block<V> o = dirTable.get(i);
					dirTable.set(i << 1, o);
					dirTable.set((i << 1) | 1, o);
				}
				dirShift = 32 - ++dirBits;
			}

			Block<V> b0 = newBlock(bDepth + 1);
			Block<V> b1 = newBlock(bDepth + 1);

			final int nMask = 1 << (32 - b0.depth);
			for (int i = 0; i < Block.SIZE; i++) {
				V obj = b.members[i];
				if (obj == null)
					continue;
				if ((obj.w1 & nMask) == 0)
					b0.appendExisting(obj);
				else
					b1.appendExisting(obj);
			}

			// All uses of b are consecutive. Replace the
			// first half with b0, second half with b1.
			//
			int useCnt = (1 << (dirBits - bDepth)) >> 1;
			int i = hash(newValue);
			while (0 < i && dirTable.get(i - 1) == b)
				i--;
			for (int n = 0; n < useCnt; n++) {
				if (dirTable.set(i++, b0) != b)
					throw new IllegalStateException();
			}
			for (int n = 0; n < useCnt; n++) {
				if (dirTable.set(i++, b1) != b)
					throw new IllegalStateException();
			}

			freeList.add(b);
		}
	}

	/**
	 * @return number of objects in map
	 */
	public int size() {
		return size;
	}

	/** @return true if {@link #size()} is 0. */
	public boolean isEmpty() {
		return size == 0;
	}

	public Iterator<V> iterator() {
		return new Iterator<V>() {
			private int found;

			private int dirIdx;

			private int blkIdx;

			private Block<V> block = dirTable.get(dirIdx);

			public boolean hasNext() {
				return found < size;
			}

			public V next() {
				if (found == size)
					throw new NoSuchElementException();

				for (;;) {
					if (blkIdx == Block.SIZE) {
						// Blocks can be repeated in consecutive slots
						// when the directory doubled and the block has
						// not been split yet.
						//
						int useCnt = (1 << (dirBits - block.depth));
						if (dirTable.size() <= dirIdx + useCnt)
							throw new NoSuchElementException();
						dirIdx += useCnt;
						block = dirTable.get(dirIdx);
						blkIdx = 0;
					}

					V obj = block.members[blkIdx++];
					if (obj != null) {
						found++;
						return obj;
					}
				}
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static class Block<V extends ObjectId> {
		private static final int BITS = 6;

		static final int SIZE = 1 << BITS;

		static final int CHAIN_LENGTH = 8;

		final V[] members;

		int depth;

		Block(int blockDepth) {
			members = Block.<V> createArray(SIZE);
			depth = blockDepth;
		}

		void reset(int blockDepth) {
			depth = blockDepth;
			Arrays.fill(members, null);
		}

		V addIfAbsent(V newValue) {
			int h = hash(newValue);
			int n = CHAIN_LENGTH;
			V obj;
			while ((obj = members[h]) != null) {
				if (AnyObjectId.equals(obj, newValue))
					return obj;
				if (++h == SIZE)
					h = 0;
				if (--n == 0)
					return null;
			}
			members[h] = newValue;
			return newValue;
		}

		void appendExisting(V newValue) {
			int h = hash(newValue);
			int n = CHAIN_LENGTH;
			while (members[h] != null) {
				if (++h == SIZE)
					h = 0;
				if (--n == 0)
					throw new IllegalStateException("chain length exceeded");
			}
			members[h] = newValue;
		}

		V get(AnyObjectId toFind) {
			int h = hash(toFind);
			int n = CHAIN_LENGTH;
			V obj;
			while ((obj = members[h]) != null) {
				if (AnyObjectId.equals(obj, toFind))
					return obj;
				if (++h == SIZE)
					h = 0;
				if (--n == 0)
					return null;
			}
			return null;
		}

		private static int hash(AnyObjectId objId) {
			return objId.w3 >>> (32 - BITS);
		}

		@SuppressWarnings("unchecked")
		private static final <V> V[] createArray(int sz) {
			return (V[]) new ObjectId[sz];
		}
	}
}
