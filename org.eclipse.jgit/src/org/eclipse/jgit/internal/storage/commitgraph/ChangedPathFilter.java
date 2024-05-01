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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;

import org.apache.commons.codec.digest.MurmurHash3;

/**
 * A changed path filter for a commit.
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

	/**
	 * A filter that matches every path.
	 */
	public static final ChangedPathFilter FULL = new ChangedPathFilter(
			new byte[] { (byte) 0xff }, 0, 1);

	private static final ChangedPathFilter EMPTY = new ChangedPathFilter(
			new byte[] { (byte) 0 }, 0, 1);

	private final byte[] data;

	private final int offset;

	private final int length;

	/**
	 * Constructs a changed path filter.
	 *
	 * @param data
	 *            data (possibly read from a commit graph file)
	 * @param offset
	 *            offset into data
	 * @param length
	 *            length of data
	 */
	private ChangedPathFilter(byte[] data, int offset, int length) {
		this.data = data;
		this.offset = offset;
		this.length = length;
	}

	/**
	 * Returns a filter that matches all given paths.
	 * <p>
	 * Because of the nature of Bloom filters, this filter may also match paths
	 * not in the given set.
	 *
	 * @param paths
	 *            the paths that the filter must match
	 * @return the corresponding filter
	 */
	@SuppressWarnings("ByteBufferBackingArray")
	public static ChangedPathFilter fromPaths(Set<ByteBuffer> paths) {
		if (paths.isEmpty()) {
			return EMPTY;
		}
		byte[] bloom = new byte[-Math
				.floorDiv(-paths.size() * ChangedPathFilter.BITS_PER_ENTRY, 8)];
		for (ByteBuffer path : paths) {
			add(bloom, path.array(), path.position(),
					path.limit() - path.position());
		}
		return new ChangedPathFilter(bloom, 0, bloom.length);
	}

	/**
	 * Returns a filter read from a file.
	 *
	 * @param data
	 *            data (read from a commit graph file)
	 * @param offset
	 *            offset into data
	 * @param length
	 *            length of data
	 *
	 * @return the corresponding filter
	 */
	public static ChangedPathFilter fromFile(byte[] data, int offset,
			int length) {
		return new ChangedPathFilter(data, offset, length);
	}

	private static void add(byte[] changedPathFilterData, byte[] path,
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
	 * Checks if this changed path filter could contain path.
	 *
	 * @param path
	 *            path to check existence of
	 * @return true if the filter could contain path, false if the filter
	 *         definitely does not contain path
	 */
	public boolean maybeContains(byte[] path) {
		int hash0 = MurmurHash3.hash32x86(path, 0, path.length, SEED1);
		int hash1 = MurmurHash3.hash32x86(path, 0, path.length, SEED2);
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

	/**
	 * Writes this filter to the given stream.
	 *
	 * @param s
	 *            stream to write to
	 */
	public void writeTo(ByteArrayOutputStream s) {
		s.write(data, offset, length);
	}
}
