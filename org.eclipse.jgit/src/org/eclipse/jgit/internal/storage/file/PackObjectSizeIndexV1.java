/*
 * Copyright (C) 2022, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.util.NB;

class PackObjectSizeIndexV1 implements PackObjectSizeIndex {

	private final int[] positions;

	private final int[] sizes32;

	private final long[] sizes64;

	PackObjectSizeIndexV1(InputStream in) throws IOException {
		/** Header and version already out of the input */
		readInt(in); // minSize
		int c = readInt(in);
		positions = readIntArray(in, c);
		sizes32 = readIntArray(in, c);
		int c64sizes = readInt(in);
		sizes64 = readLongArray(in, c64sizes);
		if (c64sizes > 0) {
			// this MUST be 0 (we don't support 128 bits sizes yet)
			readInt(in);
		}
	}

	private int readInt(InputStream in) throws IOException {
		return NB.decodeInt32(in.readNBytes(4), 0);
	}

	private int[] readIntArray(InputStream in, int intsCount)
			throws IOException {
		if (intsCount == 0) {
			return new int[0];
		}
		byte[] data = in.readNBytes(intsCount*4);
		int[] dest = new int[intsCount];
		for (int i = 0; i < intsCount; i++) {
			dest[i] = NB.decodeInt32(data, i*4);
		}
		return dest;
	}

	private long[] readLongArray(InputStream in, int longsCount)
			throws IOException {
		if (longsCount == 0) {
			return new long[0];
		}
		byte[] data = in.readNBytes(longsCount*8);
		long[] dest = new long[longsCount];
		for (int i = 0; i < longsCount; i++) {
			dest[i] = NB.decodeInt64(data, i * 8);
		}
		return dest;
	}

	@Override
	public long getSize(long position) {
		int pos = Arrays.binarySearch(positions, (int)position);
		if (pos < 0) {
			return -1;
		}

		int objSize = sizes32[pos];
		if (objSize < 0) {
			int secondPos = Math.abs(objSize) - 1;
			return sizes64[secondPos];
		}
		return objSize;
	}

	@Override
	public long getObjectCount() {
		return positions.length;
	}

}
