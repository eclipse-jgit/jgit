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

package org.eclipse.jgit.storage.dht;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import static org.eclipse.jgit.lib.Constants.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/** Index into a {@link PackChunk}. */
public abstract class ChunkIndex {
	private static final int V1 = 0x01;

	static ChunkIndex fromBytes(ChunkKey key, byte[] index, int ptr, int len)
			throws DhtException {
		int v = index[ptr] & 0xff;
		switch (v) {
		case V1: {
			final int offsetFormat = index[ptr + 1] & 7;
			switch (offsetFormat) {
			case 1:
				return new Offset1(index, ptr, len, key);
			case 2:
				return new Offset2(index, ptr, len, key);
			case 3:
				return new Offset3(index, ptr, len, key);
			case 4:
				return new Offset4(index, ptr, len, key);
			default:
				throw new DhtException(MessageFormat.format(
						DhtText.get().unsupportedChunkIndex,
						Integer.toHexString(NB.decodeUInt16(index, ptr)), key));
			}
		}
		default:
			throw new DhtException(MessageFormat.format(
					DhtText.get().unsupportedChunkIndex,
					Integer.toHexString(v), key));
		}
	}

	/**
	 * Format the chunk index and return its binary representation.
	 *
	 * @param list
	 *            the list of objects that appear in the chunk. This list will
	 *            be sorted in-place if it has more than 1 element.
	 * @return binary representation of the chunk's objects and their starting
	 *         offsets. The format is private to this class.
	 */
	@SuppressWarnings("null")
	static byte[] create(List<? extends PackedObjectInfo> list) {
		int cnt = list.size();
		sortObjectList(list);

		int fanoutFormat = 0;
		int[] buckets = null;
		if (64 < cnt) {
			buckets = new int[256];
			for (PackedObjectInfo oe : list)
				buckets[oe.getFirstByte()]++;
			fanoutFormat = selectFanoutFormat(buckets);
		}

		int offsetFormat = selectOffsetFormat(list);
		byte[] index = new byte[2 // header
				+ 256 * fanoutFormat // (optional) fanout
				+ cnt * OBJECT_ID_LENGTH // ids
				+ cnt * offsetFormat // offsets
		];
		index[0] = V1;
		index[1] = (byte) ((fanoutFormat << 3) | offsetFormat);

		int ptr = 2;

		switch (fanoutFormat) {
		case 0:
			break;
		case 1:
			for (int i = 0; i < 256; i++, ptr++)
				index[ptr] = (byte) buckets[i];
			break;
		case 2:
			for (int i = 0; i < 256; i++, ptr += 2)
				NB.encodeInt16(index, ptr, buckets[i]);
			break;
		case 3:
			for (int i = 0; i < 256; i++, ptr += 3)
				encodeUInt24(index, ptr, buckets[i]);
			break;
		case 4:
			for (int i = 0; i < 256; i++, ptr += 4)
				NB.encodeInt32(index, ptr, buckets[i]);
			break;
		}

		for (PackedObjectInfo oe : list) {
			oe.copyRawTo(index, ptr);
			ptr += OBJECT_ID_LENGTH;
		}

		switch (offsetFormat) {
		case 1:
			for (PackedObjectInfo oe : list)
				index[ptr++] = (byte) oe.getOffset();
			break;

		case 2:
			for (PackedObjectInfo oe : list) {
				NB.encodeInt16(index, ptr, (int) oe.getOffset());
				ptr += 2;
			}
			break;

		case 3:
			for (PackedObjectInfo oe : list) {
				encodeUInt24(index, ptr, (int) oe.getOffset());
				ptr += 3;
			}
			break;

		case 4:
			for (PackedObjectInfo oe : list) {
				NB.encodeInt32(index, ptr, (int) oe.getOffset());
				ptr += 4;
			}
			break;
		}

		return index;
	}

	private static int selectFanoutFormat(int[] buckets) {
		int fmt = 1;
		int max = 1 << (8 * fmt);

		for (int cnt : buckets) {
			while (max <= cnt && fmt < 4) {
				if (++fmt == 4)
					return fmt;
				max = 1 << (8 * fmt);
			}
		}
		return fmt;
	}

	private static int selectOffsetFormat(List<? extends PackedObjectInfo> list) {
		int fmt = 1;
		int max = 1 << (8 * fmt);

		for (PackedObjectInfo oe : list) {
			while (max <= oe.getOffset() && fmt < 4) {
				if (++fmt == 4)
					return fmt;
				max = 1 << (8 * fmt);
			}
		}
		return fmt;
	}

	@SuppressWarnings("unchecked")
	private static void sortObjectList(List<? extends PackedObjectInfo> list) {
		Collections.sort(list);
	}

	private final byte[] indexBuf;

	private final int indexPtr;

	private final int indexLen;

	private final int[] fanout;

	private final int idTable;

	private final int offsetTable;

	private final int count;

	ChunkIndex(byte[] indexBuf, int ptr, int len, ChunkKey key)
			throws DhtException {
		final int ctl = indexBuf[ptr + 1];
		final int fanoutFormat = (ctl >>> 3) & 7;
		final int offsetFormat = ctl & 7;

		switch (fanoutFormat) {
		case 0:
			fanout = null; // no fanout, too small
			break;

		case 1: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += indexBuf[ptr + 2 + i] & 0xff;
				fanout[i] = last;
			}
			break;
		}
		case 2: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += NB.decodeUInt16(indexBuf, ptr + 2 + i * 2);
				fanout[i] = last;
			}
			break;
		}
		case 3: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += decodeUInt24(indexBuf, ptr + 2 + i * 3);
				fanout[i] = last;
			}
			break;
		}
		case 4: {
			int last = 0;
			fanout = new int[256];
			for (int i = 0; i < 256; i++) {
				last += NB.decodeInt32(indexBuf, ptr + 2 + i * 4);
				fanout[i] = last;
			}
			break;
		}
		default:
			throw new DhtException(MessageFormat.format(
					DhtText.get().unsupportedChunkIndex,
					Integer.toHexString(NB.decodeUInt16(indexBuf, ptr)), key));
		}

		this.indexBuf = indexBuf;
		this.indexPtr = ptr;
		this.indexLen = len;
		this.idTable = indexPtr + 2 + 256 * fanoutFormat;

		int recsz = OBJECT_ID_LENGTH + offsetFormat;
		this.count = (indexLen - (idTable - indexPtr)) / recsz;
		this.offsetTable = idTable + count * OBJECT_ID_LENGTH;
	}

	/**
	 * Get the total number of objects described by this index.
	 *
	 * @return number of objects in this index and its associated chunk.
	 */
	public final int getObjectCount() {
		return count;
	}

	/**
	 * Get an ObjectId from this index.
	 *
	 * @param nth
	 *            the object to return. Must be in range [0, getObjectCount).
	 * @return the object id.
	 */
	public final ObjectId getObjectId(int nth) {
		return ObjectId.fromRaw(indexBuf, idPosition(nth));
	}

	/**
	 * Get the offset of an object in the chunk.
	 *
	 * @param nth
	 *            offset to return. Must be in range [0, getObjectCount).
	 * @return the offset.
	 */
	public final int getOffset(int nth) {
		return getOffset(indexBuf, offsetTable, nth);
	}

	/** @return the size of this index, in bytes. */
	int getIndexSize() {
		int sz = indexBuf.length;
		if (fanout != null)
			sz += 12 + 256 * 4;
		return sz;
	}

	/**
	 * Search for an object in the index.
	 *
	 * @param objId
	 *            the object to locate.
	 * @return offset of the object in the corresponding chunk; -1 if not found.
	 */
	final int findOffset(AnyObjectId objId) {
		int hi, lo;

		if (fanout != null) {
			int fb = objId.getFirstByte();
			lo = fb == 0 ? 0 : fanout[fb - 1];
			hi = fanout[fb];
		} else {
			lo = 0;
			hi = count;
		}

		while (lo < hi) {
			final int mid = (lo + hi) >>> 1;
			final int cmp = objId.compareTo(indexBuf, idPosition(mid));
			if (cmp < 0)
				hi = mid;
			else if (cmp == 0)
				return getOffset(mid);
			else
				lo = mid + 1;
		}
		return -1;
	}

	abstract int getOffset(byte[] indexArray, int offsetTableStart, int nth);

	private int idPosition(int nth) {
		return idTable + (nth * OBJECT_ID_LENGTH);
	}

	private static class Offset1 extends ChunkIndex {
		Offset1(byte[] index, int ptr, int len, ChunkKey key)
				throws DhtException {
			super(index, ptr, len, key);
		}

		int getOffset(byte[] index, int offsetTable, int nth) {
			return index[offsetTable + nth] & 0xff;
		}
	}

	private static class Offset2 extends ChunkIndex {
		Offset2(byte[] index, int ptr, int len, ChunkKey key)
				throws DhtException {
			super(index, ptr, len, key);
		}

		int getOffset(byte[] index, int offsetTable, int nth) {
			return NB.decodeUInt16(index, offsetTable + (nth * 2));
		}
	}

	private static class Offset3 extends ChunkIndex {
		Offset3(byte[] index, int ptr, int len, ChunkKey key)
				throws DhtException {
			super(index, ptr, len, key);
		}

		int getOffset(byte[] index, int offsetTable, int nth) {
			return decodeUInt24(index, offsetTable + (nth * 3));
		}
	}

	private static class Offset4 extends ChunkIndex {
		Offset4(byte[] index, int ptr, int len, ChunkKey key)
				throws DhtException {
			super(index, ptr, len, key);
		}

		int getOffset(byte[] index, int offsetTable, int nth) {
			return NB.decodeInt32(index, offsetTable + (nth * 4));
		}
	}

	private static void encodeUInt24(byte[] intbuf, int offset, int v) {
		intbuf[offset + 2] = (byte) v;
		v >>>= 8;

		intbuf[offset + 1] = (byte) v;
		v >>>= 8;

		intbuf[offset] = (byte) v;
	}

	private static int decodeUInt24(byte[] intbuf, int offset) {
		int r = (intbuf[offset] & 0xff) << 8;

		r |= intbuf[offset + 1] & 0xff;
		r <<= 8;

		r |= intbuf[offset + 2] & 0xff;
		return r;
	}
}