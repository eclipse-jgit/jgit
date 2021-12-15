/*
 * Copyright (C) 2021, Google Inc. and others
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

	private int[] offsets32;

	private long[] offsets64;

	private int[] sizes32;

	private long[] sizes64;

	PackObjectSizeIndexV1(InputStream in) throws IOException {
		/** Header and version already out of the input */
		readInt(in); // minSize
		int c32 = readInt(in);
		int c64 = readInt(in);
		offsets32 = readIntArray(in, c32);
		offsets64 = readLongArray(in, c64);
		sizes32 = readIntArray(in, c32 + c64);
		int c64sizes = readInt(in);
		if (c64sizes > 0) {
			sizes64 = readLongArray(in, c64sizes);
			// this MUST be 0 (we don't support 128 bits sizes yet)
			readInt(in);
		}
	}

	private int readInt(InputStream in) throws IOException {
		return NB.decodeInt32(in.readNBytes(4), 0);
	}

	private int[] readIntArray(InputStream in, int intsCount)
			throws IOException {
		byte[] data = in.readNBytes(intsCount*4);
		int[] dest = new int[intsCount];
		for (int i = 0; i < intsCount; i++) {
			dest[i] = NB.decodeInt32(data, i*4);
		}
		return dest;
	}

	private long[] readLongArray(InputStream in, int longsCount)
			throws IOException {
		byte[] data = in.readNBytes(longsCount*8);
		long[] dest = new long[longsCount];
		for (int i = 0; i < longsCount; i++) {
			dest[i] = NB.decodeInt64(data, i * 8);
		}
		return dest;
	}

	@Override
	public long getSize(long offset) {
		int pos;
		if (offset < Integer.MAX_VALUE) {
			pos = Arrays.binarySearch(offsets32, (int)offset);
		} else {
			pos = Arrays.binarySearch(offsets64, offset);
		}

		if (pos < 0) {
			return -1;
		}

		int sizePos = offset < Integer.MAX_VALUE ? pos : pos + offsets32.length;
		int objSize = sizes32[sizePos];
		if (objSize < 0) {
			int secondPos = Math.abs(objSize) - 1;
			return sizes64[secondPos];
		}
		return objSize;
	}

	@Override
	public long getObjectCount() {
		return offsets32.length + offsets64.length;
	}

}
