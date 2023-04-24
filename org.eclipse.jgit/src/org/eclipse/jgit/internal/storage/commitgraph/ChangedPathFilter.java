/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.apache.commons.codec.digest.MurmurHash3;

/**
 * A changed path filter for a commit, read from a commit graph file.
 *
 * @since 6.7
 */
public class ChangedPathFilter {
	/**
	 * The number of times a path is hashed, as described in man
	 * gitformat-commit-graph(5). The value of this constant is the only value
	 * JGit currently supports.
	 */
	public static final int PATH_HASH_COUNT = 7;

	/**
	 * The minimum bits per entry, as described in man
	 * gitformat-commit-graph(5). The value of this constant is the only value
	 * JGit currently supports.
	 */
	public static final int BITS_PER_ENTRY = 10;

	/**
	 * Seed value as described in man gitformat-commit-graph(5).
	 */
	private static final int SEED1 = 0x293ae76f;

	/**
	 * Seed value as described in man gitformat-commit-graph(5).
	 */
	private static final int SEED2 = 0x7e646e2c;

	private final byte[] data;

	private final int offset;

	private final int length;

	/**
	 * Constructs a changed path filter.
	 *
	 * @param data
	 *            data as read from a commit graph file
	 * @param offset
	 *            offset into data
	 * @param length
	 *            length of data
	 */
	public ChangedPathFilter(byte[] data, int offset, int length) {
		this.data = data;
		this.offset = offset;
		this.length = length;
	}

	/**
	 * Hash the given path multiple times, setting bits corresponding to the
	 * hash outputs.
	 *
	 * @param changedPathFilterData
	 *            bits will be set on this array
	 * @param path
	 *            data to be hashed
	 * @param offset
	 *            offset into path
	 * @param length
	 *            length of path to use
	 */
	public static void add(byte[] changedPathFilterData, byte[] path,
			int offset, int length) {

		int hash0 = MurmurHash3.hash32x86(path, offset, length, SEED1);
		int hash1 = MurmurHash3.hash32x86(path, offset, length, SEED2);
		for (int i = 0; i < PATH_HASH_COUNT; i++) {
			int pos = Integer.remainderUnsigned(hash0 + i * hash1,
					changedPathFilterData.length * 8);
			changedPathFilterData[pos / 8] |= (byte) (1 << (pos % 8));
		}
	}

	/**
	 * Checks if this changed path filter could contain needle.
	 *
	 * @param needle
	 *            what to check existence of
	 * @return true if the filter could contain needle, false if the filter
	 *         definitely does not contain needle
	 */
	public boolean maybeContains(byte[] needle) {
		int hash0 = MurmurHash3.hash32x86(needle, 0, needle.length, SEED1);
		int hash1 = MurmurHash3.hash32x86(needle, 0, needle.length, SEED2);
		int bloomFilterBits = length * 8;
		for (int i = 0; i < PATH_HASH_COUNT; i++) {
			int pos = Integer.remainderUnsigned(hash0 + i * hash1,
					bloomFilterBits);
			if ((data[offset + (pos / 8)] & (byte) (1 << (pos % 8))) == 0) {
				return false;
			}
		}
		return true;
	}
}
