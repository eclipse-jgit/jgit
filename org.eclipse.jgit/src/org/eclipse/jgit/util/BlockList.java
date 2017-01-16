/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.util;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Random access list that allocates entries in blocks.
 * <p>
 * Unlike {@link java.util.ArrayList}, this type does not need to reallocate the
 * internal array in order to expand the capacity of the list. Access to any
 * element is constant time, but requires two array lookups instead of one.
 * <p>
 * To handle common usages, {@link #add(Object)} and {@link #iterator()} use
 * internal code paths to amortize out the second array lookup, making addition
 * and simple iteration closer to one array operation per element processed.
 * <p>
 * Similar to {@code ArrayList}, adding or removing from any position except the
 * end of the list requires O(N) time to copy all elements between the
 * modification point and the end of the list. Applications are strongly
 * encouraged to not use this access pattern with this list implementation.
 *
 * @param <T>
 *            type of list element.
 */
public class BlockList<T> extends AbstractList<T> {
	private static final int BLOCK_BITS = 10;

	static final int BLOCK_SIZE = 1 << BLOCK_BITS;

	private static final int BLOCK_MASK = BLOCK_SIZE - 1;

	T[][] directory;

	int size;

	private int tailDirIdx;

	private int tailBlkIdx;

	private T[] tailBlock;

	/** Initialize an empty list. */
	public BlockList() {
		directory = BlockList.<T> newDirectory(256);
		directory[0] = BlockList.<T> newBlock();
		tailBlock = directory[0];
	}

	/**
	 * Initialize an empty list with an expected capacity.
	 *
	 * @param capacity
	 *            number of elements expected to be in the list.
	 */
	public BlockList(int capacity) {
		int dirSize = toDirectoryIndex(capacity);
		if ((capacity & BLOCK_MASK) != 0 || dirSize == 0)
			dirSize++;
		directory = BlockList.<T> newDirectory(dirSize);
		directory[0] = BlockList.<T> newBlock();
		tailBlock = directory[0];
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear() {
		for (T[] block : directory) {
			if (block != null)
				Arrays.fill(block, null);
		}
		size = 0;
		tailDirIdx = 0;
		tailBlkIdx = 0;
		tailBlock = directory[0];
	}

	@Override
	public T get(int index) {
		if (index < 0 || size <= index)
			throw new IndexOutOfBoundsException(String.valueOf(index));
		return directory[toDirectoryIndex(index)][toBlockIndex(index)];
	}

	@Override
	public T set(int index, T element) {
		if (index < 0 || size <= index)
			throw new IndexOutOfBoundsException(String.valueOf(index));
		T[] blockRef = directory[toDirectoryIndex(index)];
		int blockIdx = toBlockIndex(index);
		T old = blockRef[blockIdx];
		blockRef[blockIdx] = element;
		return old;
	}

	/**
	 * Quickly append all elements of another BlockList.
	 *
	 * @param src
	 *            the list to copy elements from.
	 */
	public void addAll(BlockList<T> src) {
		if (src.size == 0)
			return;

		int srcDirIdx = 0;
		for (; srcDirIdx < src.tailDirIdx; srcDirIdx++)
			addAll(src.directory[srcDirIdx], 0, BLOCK_SIZE);
		if (src.tailBlkIdx != 0)
			addAll(src.tailBlock, 0, src.tailBlkIdx);
	}

	/**
	 * Quickly append all elements from an array.
	 *
	 * @param src
	 *            the source array.
	 * @param srcIdx
	 *            first index to copy.
	 * @param srcCnt
	 *            number of elements to copy.
	 */
	public void addAll(T[] src, int srcIdx, int srcCnt) {
		while (0 < srcCnt) {
			int i = tailBlkIdx;
			int n = Math.min(srcCnt, BLOCK_SIZE - i);
			if (n == 0) {
				// Our tail is full, expand by one.
				add(src[srcIdx++]);
				srcCnt--;
				continue;
			}

			System.arraycopy(src, srcIdx, tailBlock, i, n);
			tailBlkIdx += n;
			size += n;
			srcIdx += n;
			srcCnt -= n;
		}
	}

	@Override
	public boolean add(T element) {
		int i = tailBlkIdx;
		if (i < BLOCK_SIZE) {
			// Fast-path: Append to current tail block.
			tailBlock[i] = element;
			tailBlkIdx = i + 1;
			size++;
			return true;
		}

		// Slow path: Move to the next block, expanding if necessary.
		if (++tailDirIdx == directory.length) {
			T[][] newDir = BlockList.<T> newDirectory(directory.length << 1);
			System.arraycopy(directory, 0, newDir, 0, directory.length);
			directory = newDir;
		}

		T[] blockRef = directory[tailDirIdx];
		if (blockRef == null) {
			blockRef = BlockList.<T> newBlock();
			directory[tailDirIdx] = blockRef;
		}
		blockRef[0] = element;
		tailBlock = blockRef;
		tailBlkIdx = 1;
		size++;
		return true;
	}

	@Override
	public void add(int index, T element) {
		if (index == size) {
			// Fast-path: append onto the end of the list.
			add(element);

		} else if (index < 0 || size < index) {
			throw new IndexOutOfBoundsException(String.valueOf(index));

		} else {
			// Slow-path: the list needs to expand and insert.
			// Do this the naive way, callers shouldn't abuse
			// this class by entering this code path.
			//
			add(null); // expand the list by one
			for (int oldIdx = size - 2; index <= oldIdx; oldIdx--)
				set(oldIdx + 1, get(oldIdx));
			set(index, element);
		}
	}

	@Override
	public T remove(int index) {
		if (index == size - 1) {
			// Fast-path: remove the last element.
			T[] blockRef = directory[toDirectoryIndex(index)];
			int blockIdx = toBlockIndex(index);
			T old = blockRef[blockIdx];
			blockRef[blockIdx] = null;
			size--;
			if (0 < tailBlkIdx)
				tailBlkIdx--;
			else
				resetTailBlock();
			return old;

		} else if (index < 0 || size <= index) {
			throw new IndexOutOfBoundsException(String.valueOf(index));

		} else {
			// Slow-path: the list needs to contract and remove.
			// Do this the naive way, callers shouldn't abuse
			// this class by entering this code path.
			//
			T old = get(index);
			for (; index < size - 1; index++)
				set(index, get(index + 1));
			set(size - 1, null);
			size--;
			resetTailBlock();
			return old;
		}
	}

	private void resetTailBlock() {
		tailDirIdx = toDirectoryIndex(size);
		tailBlkIdx = toBlockIndex(size);
		tailBlock = directory[tailDirIdx];
	}

	@Override
	public Iterator<T> iterator() {
		return new MyIterator();
	}

	static final int toDirectoryIndex(int index) {
		return index >>> BLOCK_BITS;
	}

	static final int toBlockIndex(int index) {
		return index & BLOCK_MASK;
	}

	@SuppressWarnings("unchecked")
	private static <T> T[][] newDirectory(int size) {
		return (T[][]) new Object[size][];
	}

	@SuppressWarnings("unchecked")
	private static <T> T[] newBlock() {
		return (T[]) new Object[BLOCK_SIZE];
	}

	private class MyIterator implements Iterator<T> {
		private int index;

		private int dirIdx;

		private int blkIdx;

		private T[] block = directory[0];

		@Override
		public boolean hasNext() {
			return index < size;
		}

		@Override
		public T next() {
			if (size <= index)
				throw new NoSuchElementException();

			T res = block[blkIdx];
			if (++blkIdx == BLOCK_SIZE) {
				if (++dirIdx < directory.length)
					block = directory[dirIdx];
				else
					block = null;
				blkIdx = 0;
			}
			index++;
			return res;
		}

		@Override
		public void remove() {
			if (index == 0)
				throw new IllegalStateException();

			BlockList.this.remove(--index);

			dirIdx = toDirectoryIndex(index);
			blkIdx = toBlockIndex(index);
			block = directory[dirIdx];
		}
	}
}
