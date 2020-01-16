/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

/**
 * Supports {@link DeltaIndex} by performing a partial scan of the content.
 */
class DeltaIndexScanner {
	final int[] table;

	// To save memory the buckets for hash chains are stored in correlated
	// arrays. This permits us to get 3 values per entry, without paying
	// the penalty for an object header on each entry.

	final long[] entries;

	final int[] next;

	final int tableMask;

	private int entryCnt;

	DeltaIndexScanner(byte[] raw, int len) {
		// Clip the length so it falls on a block boundary. We won't
		// bother to scan the final partial block.
		//
		len -= (len % DeltaIndex.BLKSZ);

		final int worstCaseBlockCnt = len / DeltaIndex.BLKSZ;
		if (worstCaseBlockCnt < 1) {
			table = new int[] {};
			tableMask = 0;

			entries = new long[] {};
			next = new int[] {};

		} else {
			table = new int[tableSize(worstCaseBlockCnt)];
			tableMask = table.length - 1;

			// As we insert blocks we preincrement so that 0 is never a
			// valid entry. Therefore we have to allocate one extra space.
			//
			entries = new long[1 + worstCaseBlockCnt];
			next = new int[entries.length];

			scan(raw, len);
		}
	}

	private void scan(byte[] raw, int end) {
		// We scan the input backwards, and always insert onto the
		// front of the chain. This ensures that chains will have lower
		// offsets at the front of the chain, allowing us to prefer the
		// earlier match rather than the later match.
		//
		int lastHash = 0;
		int ptr = end - DeltaIndex.BLKSZ;
		do {
			final int key = DeltaIndex.hashBlock(raw, ptr);
			final int tIdx = key & tableMask;

			final int head = table[tIdx];
			if (head != 0 && lastHash == key) {
				// Two consecutive blocks have the same content hash,
				// prefer the earlier block because we want to use the
				// longest sequence we can during encoding.
				//
				entries[head] = (((long) key) << 32) | ptr;
			} else {
				final int eIdx = ++entryCnt;
				entries[eIdx] = (((long) key) << 32) | ptr;
				next[eIdx] = head;
				table[tIdx] = eIdx;
			}

			lastHash = key;
			ptr -= DeltaIndex.BLKSZ;
		} while (0 <= ptr);
	}

	private static int tableSize(int worstCaseBlockCnt) {
		int shift = 32 - Integer.numberOfLeadingZeros(worstCaseBlockCnt);
		int sz = 1 << (shift - 1);
		if (sz < worstCaseBlockCnt)
			sz <<= 1;
		return sz;
	}
}
