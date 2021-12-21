/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

/**
 * Implementation of the MurmurHash3 32-bit functions.
 *
 * <p>
 * MurmurHash is a non-cryptographic hash function suitable for general
 * hash-based lookup. The name comes from two basic operations, multiply (MU)
 * and rotate (R), used in its inner loop. Unlike cryptographic hash functions,
 * it is not specifically designed to be difficult to reverse by an adversary,
 * making it unsuitable for cryptographic purposes.
 * </p>
 *
 * <p>
 * This contains a Java port of the 32-bit hash function
 * {@code MurmurHash3_x86_32} from Austin Applyby's original {@code c++} code in
 * SMHasher.
 * </p>
 *
 * <p>
 * This is public domain code with no copyrights. From home page of
 * <a href="https://github.com/aappleby/smhasher">SMHasher</a>:
 * </p>
 *
 * <blockquote> "All MurmurHash versions are public domain software, and the
 * author disclaims all copyright to their code." </blockquote>
 *
 * <p>
 * Original adaption from Apache Hive.
 * <p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/MurmurHash">MurmurHash</a>
 * @see <a href=
 *      "https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp">
 *      Original MurmurHash3 c++ code</a>
 * @see <a href=
 *      "https://github.com/apache/hive/blob/master/storage-api/src/java/org/apache/hive/common/util/Murmur3.java">
 *      Apache Hive Murmer3</a>
 */
public final class MurmurHash3 {

	/**
	 * A random number to use for a hash code.
	 *
	 * @deprecated This is not used internally and will be removed in a future
	 *             release.
	 */
	@Deprecated
	public static final long NULL_HASHCODE = 2862933555777941757L;

	// Constants for 32-bit variant
	private static final int C1_32 = 0xcc9e2d51;

	private static final int C2_32 = 0x1b873593;

	private static final int R1_32 = 15;

	private static final int R2_32 = 13;

	private static final int M_32 = 5;

	private static final int N_32 = 0xe6546b64;

	/** No instance methods. */
	private MurmurHash3() {
	}

	/**
	 * Generates 32-bit hash from the byte array with the given offset, length
	 * and seed.
	 *
	 * <p>
	 * This is an implementation of the 32-bit hash function
	 * {@code MurmurHash3_x86_32} from from Austin Applyby's original
	 * MurmurHash3 {@code c++} code in SMHasher.
	 * </p>
	 *
	 * @param data
	 *            The input byte array
	 * @param offset
	 *            The offset of data
	 * @param length
	 *            The length of array
	 * @param seed
	 *            The initial seed value
	 * @return The 32-bit hash
	 */
	public static int hash32x86(final byte[] data, final int offset,
			final int length, final int seed) {
		int hash = seed;
		final int nblocks = length >> 2;

		// body
		for (int i = 0; i < nblocks; i++) {
			final int index = offset + (i << 2);
			final int k = getLittleEndianInt(data, index);
			hash = mix32(k, hash);
		}

		// tail
		final int index = offset + (nblocks << 2);
		int k1 = 0;
		switch (offset + length - index) {
		case 3:
			k1 ^= (data[index + 2] & 0xff) << 16;
			//$FALL-THROUGH$
		case 2:
			k1 ^= (data[index + 1] & 0xff) << 8;
			//$FALL-THROUGH$
		case 1:
			k1 ^= (data[index] & 0xff);

			// mix functions
			k1 *= C1_32;
			k1 = Integer.rotateLeft(k1, R1_32);
			k1 *= C2_32;
			hash ^= k1;
		}

		hash ^= length;
		return fmix32(hash);
	}

	/**
	 * Gets the little-endian int from 4 bytes starting at the specified index.
	 *
	 * @param data
	 *            The data
	 * @param index
	 *            The index
	 * @return The little-endian int
	 */
	private static int getLittleEndianInt(final byte[] data, final int index) {
		return ((data[index] & 0xff)) | ((data[index + 1] & 0xff) << 8)
				| ((data[index + 2] & 0xff) << 16)
				| ((data[index + 3] & 0xff) << 24);
	}

	/**
	 * Performs the intermediate mix step of the 32-bit hash function
	 * {@code MurmurHash3_x86_32}.
	 *
	 * @param k
	 *            The data to add to the hash
	 * @param hash
	 *            The current hash
	 * @return The new hash
	 */
	private static int mix32(int k, int hash) {
		k *= C1_32;
		k = Integer.rotateLeft(k, R1_32);
		k *= C2_32;
		hash ^= k;
		return Integer.rotateLeft(hash, R2_32) * M_32 + N_32;
	}

	/**
	 * Performs the final avalanche mix step of the 32-bit hash function
	 * {@code MurmurHash3_x86_32}.
	 *
	 * @param hash
	 *            The current hash
	 * @return The final hash
	 */
	private static int fmix32(int hash) {
		hash ^= (hash >>> 16);
		hash *= 0x85ebca6b;
		hash ^= (hash >>> 13);
		hash *= 0xc2b2ae35;
		hash ^= (hash >>> 16);
		return hash;
	}
}
