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
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.NB;

/**
 * Memory representation of the object-size index
 *
 * The object size index is a map from position in the primary idx (i.e.
 * position of the object-id in lexicographical order) to size.
 *
 * Most of the positions fit in unsigned 3 bytes (up to 16 million)
 */
class PackObjectSizeIndexV1 implements PackObjectSizeIndex {

	private static final byte BITS_24 = 0x18;

	private static final byte BITS_32 = 0x20;

	private final int threshold;

	private final UInt24Array positions24;

	private final IntArray positions32;

	/**
	 * Parallel array to concat(positions24, positions32) with the size of the
	 * objects.
	 *
	 * A value >= 0 is the size of the object. A negative value means the size
	 * doesn't fit in an int and |value|-1 is the position for the size in the
	 * size64 array e.g. a value of -1 is sizes64[0], -2 = sizes64[1], ...
	 */
	private final IntArray sizes32;

	private final LongArray sizes64;

	static PackObjectSizeIndex parse(InputStream in) throws IOException {
		/** Header and version already out of the input */
		byte[] buffer = new byte[8];
		in.readNBytes(buffer, 0, 8);
		int threshold = NB.decodeInt32(buffer, 0); // minSize
		int objCount = NB.decodeInt32(buffer, 4);
		if (objCount == 0) {
			return new EmptyPackObjectSizeIndex(threshold);
		}
		return new PackObjectSizeIndexV1(in, threshold, objCount);
	}

	private PackObjectSizeIndexV1(InputStream stream, int threshold,
			int objCount) throws IOException {
		this.threshold = threshold;
		UInt24Array pos24 = null;
		IntArray pos32 = null;

		StreamHelper helper = new StreamHelper();
		byte positionEncoding;
		while ((positionEncoding = helper.readByte(stream)) != 0) {
			if (Byte.compareUnsigned(positionEncoding, BITS_24) == 0) {
				int sz = helper.readInt(stream);
				pos24 = new UInt24Array(stream.readNBytes(sz * 3));
			} else if (Byte.compareUnsigned(positionEncoding, BITS_32) == 0) {
				int sz = helper.readInt(stream);
				pos32 = IntArray.from(stream, sz);
			} else {
				throw new UnsupportedEncodingException(
						String.format(JGitText.get().unknownPositionEncoding,
								Integer.toHexString(positionEncoding)));
			}
		}
		positions24 = pos24 != null ? pos24 : UInt24Array.EMPTY;
		positions32 = pos32 != null ? pos32 : IntArray.EMPTY;

		sizes32 = IntArray.from(stream, objCount);
		int c64sizes = helper.readInt(stream);
		if (c64sizes == 0) {
			sizes64 = LongArray.EMPTY;
			return;
		}
		sizes64 = LongArray.from(stream, c64sizes);
		int c128sizes = helper.readInt(stream);
		if (c128sizes != 0) {
			// this MUST be 0 (we don't support 128 bits sizes yet)
			throw new IOException(JGitText.get().unsupportedSizesObjSizeIndex);
		}
	}

	@Override
	public long getSize(int idxOffset) {
		int pos = -1;
		if (!positions24.isEmpty() && idxOffset <= positions24.getLastValue()) {
			pos = positions24.binarySearch(idxOffset);
		} else if (!positions32.empty() && idxOffset >= positions32.get(0)) {
			int pos32 = positions32.binarySearch(idxOffset);
			if (pos32 >= 0) {
				pos = pos32 + positions24.size();
			}
		}
		if (pos < 0) {
			return -1;
		}

		int objSize = sizes32.get(pos);
		if (objSize < 0) {
			int secondPos = Math.abs(objSize) - 1;
			return sizes64.get(secondPos);
		}
		return objSize;
	}

	@Override
	public long getObjectCount() {
		return (long) positions24.size() + positions32.size();
	}

	@Override
	public int getThreshold() {
		return threshold;
	}

	/**
	 * A byte[] that should be interpreted as an int[]
	 */
	private static class IntArray {
		private static final IntArray EMPTY = new IntArray(new byte[0]);

		private static final int INT_SIZE = 4;

		private final byte[] data;

		private final int size;

		static IntArray from(InputStream in, int ints) throws IOException {
			int expectedBytes = ints * INT_SIZE;
			byte[] data = in.readNBytes(expectedBytes);
			if (data.length < expectedBytes) {
				throw new IOException(MessageFormat
						.format(JGitText.get().unableToReadFullArray,
								Integer.valueOf(ints)));
			}
			return new IntArray(data);
		}

		private IntArray(byte[] data) {
			this.data = data;
			size = data.length / INT_SIZE;
		}

		/**
		 * Returns position of element in array, -1 if not there
		 *
		 * @param needle
		 *            element to look for
		 * @return position of the element in the array or -1 if not found
		 */
		int binarySearch(int needle) {
			if (size == 0) {
				return -1;
			}
			int high = size;
			int low = 0;
			do {
				int mid = (low + high) >>> 1;
				int cmp = Integer.compare(needle, get(mid));
				if (cmp < 0)
					high = mid;
				else if (cmp == 0) {
					return mid;
				} else
					low = mid + 1;
			} while (low < high);
			return -1;
		}

		int get(int position) {
			if (position < 0 || position >= size) {
				throw new IndexOutOfBoundsException(position);
			}
			return NB.decodeInt32(data, position * INT_SIZE);
		}

		boolean empty() {
			return size == 0;
		}

		int size() {
			return size;
		}
	}

	/**
	 * A byte[] that should be interpreted as an long[]
	 */
	private static class LongArray {
		private static final LongArray EMPTY = new LongArray(new byte[0]);

		private static final int LONG_SIZE = 8; // bytes

		private final byte[] data;

		private final int size;

		static LongArray from(InputStream in, int longs) throws IOException {
			byte[] data = in.readNBytes(longs * LONG_SIZE);
			if (data.length < longs * LONG_SIZE) {
				throw new IOException(MessageFormat
						.format(JGitText.get().unableToReadFullArray,
								Integer.valueOf(longs)));
			}
			return new LongArray(data);
		}

		private LongArray(byte[] data) {
			this.data = data;
			size = data.length / LONG_SIZE;
		}

		long get(int position) {
			if (position < 0 || position >= size) {
				throw new IndexOutOfBoundsException(position);
			}
			return NB.decodeInt64(data, position * LONG_SIZE);
		}
	}

	private static class StreamHelper {
		private final byte[] buffer = new byte[8];

		int readInt(InputStream in) throws IOException {
			int n = in.readNBytes(buffer, 0, 4);
			if (n < 4) {
				throw new IOException(JGitText.get().unableToReadFullInt);
			}
			return NB.decodeInt32(buffer, 0);
		}

		byte readByte(InputStream in) throws IOException {
			int n = in.readNBytes(buffer, 0, 1);
			if (n != 1) {
				throw new IOException(JGitText.get().cannotReadByte);
			}
			return buffer[0];
		}
	}

	private static class EmptyPackObjectSizeIndex
			implements PackObjectSizeIndex {

		private final int threshold;

		EmptyPackObjectSizeIndex(int threshold) {
			this.threshold = threshold;
		}

		@Override
		public long getSize(int idxOffset) {
			return -1;
		}

		@Override
		public long getObjectCount() {
			return 0;
		}

		@Override
		public int getThreshold() {
			return threshold;
		}
	}
}
