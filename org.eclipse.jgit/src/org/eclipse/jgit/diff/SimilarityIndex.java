/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

/**
 * Index structure of lines/blocks in one file.
 * <p>
 * This structure can be used to compute an approximation of the similarity
 * between two files. The index is used by
 * {@link org.eclipse.jgit.diff.SimilarityRenameDetector} to compute scores
 * between files.
 * <p>
 * To save space in memory, this index uses a space efficient encoding which
 * will not exceed 1 MiB per instance. The index starts out at a smaller size
 * (closer to 2 KiB), but may grow as more distinct blocks within the scanned
 * file are discovered.
 *
 * @since 4.0
 */
public class SimilarityIndex {
	/** A special {@link TableFullException} used in place of OutOfMemoryError. */
	public static final TableFullException
			TABLE_FULL_OUT_OF_MEMORY = new TableFullException();

	/**
	 * Shift to apply before storing a key.
	 * <p>
	 * Within the 64 bit table record space, we leave the highest bit unset so
	 * all values are positive. The lower 32 bits to count bytes.
	 */
	private static final int KEY_SHIFT = 32;

	/** Maximum value of the count field, also mask to extract the count. */
	private static final long MAX_COUNT = (1L << KEY_SHIFT) - 1;

	/**
	 * Total amount of bytes hashed into the structure, including \n. This is
	 * usually the size of the file minus number of CRLF encounters.
	 */
	private long hashedCnt;

	/** Number of non-zero entries in {@link #idHash}. */
	private int idSize;

	/** {@link #idSize} that triggers {@link #idHash} to double in size. */
	private int idGrowAt;

	/**
	 * Pairings of content keys and counters.
	 * <p>
	 * Slots in the table are actually two ints wedged into a single long. The
	 * upper 32 bits stores the content key, and the remaining lower bits stores
	 * the number of bytes associated with that key. Empty slots are denoted by
	 * 0, which cannot occur because the count cannot be 0. Values can only be
	 * positive, which we enforce during key addition.
	 */
	private long[] idHash;

	/** {@code idHash.length == 1 << idHashBits}. */
	private int idHashBits;

	/**
	 * Create a new similarity index for the given object
	 *
	 * @param obj
	 *            the object to hash
	 * @return similarity index for this object
	 * @throws java.io.IOException
	 *             file contents cannot be read from the repository.
	 * @throws org.eclipse.jgit.diff.SimilarityIndex.TableFullException
	 *             object hashing overflowed the storage capacity of the
	 *             SimilarityIndex.
	 */
	public static SimilarityIndex create(ObjectLoader obj) throws IOException,
			TableFullException {
		SimilarityIndex idx = new SimilarityIndex();
		idx.hash(obj);
		idx.sort();
		return idx;
	}

	SimilarityIndex() {
		idHashBits = 8;
		idHash = new long[1 << idHashBits];
		idGrowAt = growAt(idHashBits);
	}

	void hash(ObjectLoader obj) throws MissingObjectException, IOException,
			TableFullException {
		if (obj.isLarge()) {
			hashLargeObject(obj);
		} else {
			byte[] raw = obj.getCachedBytes();
			hash(raw, 0, raw.length);
		}
	}

	private void hashLargeObject(ObjectLoader obj) throws IOException,
			TableFullException {
		boolean text;
		try (ObjectStream in1 = obj.openStream()) {
			text = !RawText.isBinary(in1);
		}

		try (ObjectStream in2 = obj.openStream()) {
			hash(in2, in2.getSize(), text);
		}
	}

	void hash(byte[] raw, int ptr, int end) throws TableFullException {
		final boolean text = !RawText.isBinary(raw);
		hashedCnt = 0;
		while (ptr < end) {
			int hash = 5381;
			int blockHashedCnt = 0;
			int start = ptr;

			// Hash one line, or one block, whichever occurs first.
			do {
				int c = raw[ptr++] & 0xff;
				// Ignore CR in CRLF sequence if text
				if (text && c == '\r' && ptr < end && raw[ptr] == '\n')
					continue;
				blockHashedCnt++;
				if (c == '\n')
					break;
				hash = (hash << 5) + hash + c;
			} while (ptr < end && ptr - start < 64);
			hashedCnt += blockHashedCnt;
			add(hash, blockHashedCnt);
		}
	}

	void hash(InputStream in, long remaining, boolean text) throws IOException,
			TableFullException {
		byte[] buf = new byte[4096];
		int ptr = 0;
		int cnt = 0;

		while (0 < remaining) {
			int hash = 5381;
			int blockHashedCnt = 0;

			// Hash one line, or one block, whichever occurs first.
			int n = 0;
			do {
				if (ptr == cnt) {
					ptr = 0;
					cnt = in.read(buf, 0, buf.length);
					if (cnt <= 0)
						throw new EOFException();
				}

				n++;
				int c = buf[ptr++] & 0xff;
				// Ignore CR in CRLF sequence if text
				if (text && c == '\r' && ptr < cnt && buf[ptr] == '\n')
					continue;
				blockHashedCnt++;
				if (c == '\n')
					break;
				hash = (hash << 5) + hash + c;
			} while (n < 64 && n < remaining);
			hashedCnt += blockHashedCnt;
			add(hash, blockHashedCnt);
			remaining -= n;
		}
	}

	/**
	 * Sort the internal table so it can be used for efficient scoring.
	 * <p>
	 * Once sorted, additional lines/blocks cannot be added to the index.
	 */
	void sort() {
		// Sort the array. All of the empty space will wind up at the front,
		// because we forced all of the keys to always be positive. Later
		// we only work with the back half of the array.
		//
		Arrays.sort(idHash);
	}

	/**
	 * Compute the similarity score between this index and another.
	 * <p>
	 * A region of a file is defined as a line in a text file or a fixed-size
	 * block in a binary file. To prepare an index, each region in the file is
	 * hashed; the values and counts of hashes are retained in a sorted table.
	 * Define the similarity fraction F as the the count of matching regions
	 * between the two files divided between the maximum count of regions in
	 * either file. The similarity score is F multiplied by the maxScore
	 * constant, yielding a range [0, maxScore]. It is defined as maxScore for
	 * the degenerate case of two empty files.
	 * <p>
	 * The similarity score is symmetrical; i.e. a.score(b) == b.score(a).
	 *
	 * @param dst
	 *            the other index
	 * @param maxScore
	 *            the score representing a 100% match
	 * @return the similarity score
	 */
	public int score(SimilarityIndex dst, int maxScore) {
		long max = Math.max(hashedCnt, dst.hashedCnt);
		if (max == 0)
			return maxScore;
		return (int) ((common(dst) * maxScore) / max);
	}

	long common(SimilarityIndex dst) {
		return common(this, dst);
	}

	private static long common(SimilarityIndex src, SimilarityIndex dst) {
		int srcIdx = src.packedIndex(0);
		int dstIdx = dst.packedIndex(0);
		long[] srcHash = src.idHash;
		long[] dstHash = dst.idHash;
		return common(srcHash, srcIdx, dstHash, dstIdx);
	}

	private static long common(long[] srcHash, int srcIdx, //
			long[] dstHash, int dstIdx) {
		if (srcIdx == srcHash.length || dstIdx == dstHash.length)
			return 0;

		long common = 0;
		int srcKey = keyOf(srcHash[srcIdx]);
		int dstKey = keyOf(dstHash[dstIdx]);

		for (;;) {
			if (srcKey == dstKey) {
				common += Math.min(countOf(srcHash[srcIdx]),
						countOf(dstHash[dstIdx]));

				if (++srcIdx == srcHash.length)
					break;
				srcKey = keyOf(srcHash[srcIdx]);

				if (++dstIdx == dstHash.length)
					break;
				dstKey = keyOf(dstHash[dstIdx]);

			} else if (srcKey < dstKey) {
				// Regions of src which do not appear in dst.
				if (++srcIdx == srcHash.length)
					break;
				srcKey = keyOf(srcHash[srcIdx]);

			} else /* if (dstKey < srcKey) */{
				// Regions of dst which do not appear in src.
				if (++dstIdx == dstHash.length)
					break;
				dstKey = keyOf(dstHash[dstIdx]);
			}
		}

		return common;
	}

	// Testing only
	int size() {
		return idSize;
	}

	// Testing only
	int key(int idx) {
		return keyOf(idHash[packedIndex(idx)]);
	}

	// Testing only
	long count(int idx) {
		return countOf(idHash[packedIndex(idx)]);
	}

	// Brute force approach only for testing.
	int findIndex(int key) {
		for (int i = 0; i < idSize; i++)
			if (key(i) == key)
				return i;
		return -1;
	}

	private int packedIndex(int idx) {
		return (idHash.length - idSize) + idx;
	}

	void add(int key, int cnt) throws TableFullException {
		key = (key * 0x9e370001) >>> 1; // Mix bits and ensure not negative.

		int j = slot(key);
		for (;;) {
			long v = idHash[j];
			if (v == 0) {
				// Empty slot in the table, store here.
				if (idGrowAt <= idSize) {
					grow();
					j = slot(key);
					continue;
				}
				idHash[j] = pair(key, cnt);
				idSize++;
				return;

			} else if (keyOf(v) == key) {
				// Same key, increment the counter. If it overflows, fail
				// indexing to prevent the key from being impacted.
				//
				idHash[j] = pair(key, countOf(v) + cnt);
				return;

			} else if (++j >= idHash.length) {
				j = 0;
			}
		}
	}

	private static long pair(int key, long cnt) throws TableFullException {
		if (MAX_COUNT < cnt)
			throw new TableFullException();
		return (((long) key) << KEY_SHIFT) | cnt;
	}

	private int slot(int key) {
		// We use 31 - idHashBits because the upper bit was already forced
		// to be 0 and we want the remaining high bits to be used as the
		// table slot.
		//
		return key >>> (31 - idHashBits);
	}

	private static int growAt(int idHashBits) {
		return (1 << idHashBits) * (idHashBits - 3) / idHashBits;
	}

	private void grow() throws TableFullException {
		if (idHashBits == 30)
			throw new TableFullException();

		long[] oldHash = idHash;
		int oldSize = idHash.length;

		idHashBits++;
		idGrowAt = growAt(idHashBits);

		try {
			idHash = new long[1 << idHashBits];
		} catch (OutOfMemoryError noMemory) {
			throw TABLE_FULL_OUT_OF_MEMORY;
		}

		for (int i = 0; i < oldSize; i++) {
			long v = oldHash[i];
			if (v != 0) {
				int j = slot(keyOf(v));
				while (idHash[j] != 0)
					if (++j >= idHash.length)
						j = 0;
				idHash[j] = v;
			}
		}
	}

	private static int keyOf(long v) {
		return (int) (v >>> KEY_SHIFT);
	}

	private static long countOf(long v) {
		return v & MAX_COUNT;
	}

	/** Thrown by {@code create()} when file is too large. */
	public static class TableFullException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
