/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_MAGIC;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.RAW_IDLEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.isFileHeaderMagic;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.isIndexMagic;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/** Reads a single block for {@link ReftableReader}. */
class BlockReader {
	enum BlockType {
		REF, INDEX, FILE_FOOTER;
	}

	private long position;
	private BlockType blockType;
	private byte[] buf;
	private int bufLen;

	private int keysStart;
	private int keysEnd;
	private int restartIdx;
	private int restartCount;

	private byte[] nameBuf = new byte[256];
	private int ptr;

	long position() {
		return position;
	}

	boolean isRefBlock() {
		return blockType == BlockType.REF;
	}

	boolean next() {
		return ptr < keysEnd;
	}

	int readIndexBlock() {
		readVarint32(); // skip prefix length
		int nameLen = readVarint32() >>> 2;
		ptr += nameLen; // skip name
		return readVarint32();
	}

	Ref readRef() throws IOException {
		int pfx = readVarint32();
		int sfxType = readVarint32();
		int nameLen = readRefName(pfx, sfxType >>> 2);
		String name = RawParseUtils.decode(UTF_8, nameBuf, 0, nameLen);

		switch (sfxType & 0x03) {
		case 0x00: // delete
			return newRef(name);

		case 0x01: // single ObjectId
			return new ObjectIdRef.PeeledNonTag(PACKED, name, readIdValue());

		case 0x02: { // annotated tag, two ObjectIds
			ObjectId id1 = readIdValue();
			ObjectId id2 = readIdValue();
			return new ObjectIdRef.PeeledTag(PACKED, name, id1, id2);
		}

		case 0x03: // symbolic ref
			return new SymbolicRef(name, newRef(readValueString()));

		default:
			throw invalidBlock();
		}
	}

	private int readRefName(int pfx, int len) {
		if (pfx + len > nameBuf.length) {
			int newsz = Math.max(pfx + len, nameBuf.length * 2);
			nameBuf = Arrays.copyOf(nameBuf, newsz);
		}
		System.arraycopy(buf, ptr, nameBuf, pfx, len);
		ptr += len;
		return pfx + len;
	}

	private ObjectId readIdValue() {
		ObjectId id = ObjectId.fromRaw(buf, ptr);
		ptr += RAW_IDLEN;
		return id;
	}

	private String readValueString() {
		int len = readVarint32();
		int end = ptr + len;
		String s = RawParseUtils.decode(UTF_8, buf, ptr, end);
		ptr = end;
		return s;
	}

	private void skipValueString() {
		ptr += readVarint32();
	}

	void readFrom(BlockSource src, long pos, int blockSize)
			throws IOException {
		ByteBuffer b = src.read(pos, blockSize);
		bufLen = b.position();
		if (b.hasArray() && b.arrayOffset() == 0) {
			buf = b.array();
		} else {
			buf = new byte[bufLen];
			b.flip();
			b.get(buf);
		}
		if (bufLen <= 0) {
			throw invalidBlock();
		}
		position = pos;
		parseBlockStart();
	}

	private void parseBlockStart() throws IOException {
		ptr = position == 0 ? FILE_HEADER_LEN : 0;

		keysStart = ptr;
		if (isIndexMagic(buf, ptr, bufLen)) {
			blockType = BlockType.INDEX;
			keysStart += INDEX_MAGIC.length;
		} else if (ptr == bufLen || isFileHeaderMagic(buf, ptr, bufLen)) {
			blockType = BlockType.FILE_FOOTER;
		} else if (buf[ptr] == 0) {
			blockType = BlockType.REF;
		} else {
			throw invalidBlock();
		}

		if (blockType == BlockType.REF || blockType == BlockType.INDEX) {
			keysEnd = NB.decodeInt32(buf, bufLen - 8);
			restartCount = NB.decodeInt32(buf, bufLen - 4);
			restartIdx = bufLen - (restartCount * 4 + 8);
		}
	}

	void verifyIndex() throws IOException {
		if (blockType != BlockType.INDEX) {
			throw invalidBlock();
		}
	}

	int seek(byte[] name) {
		int low = 0;
		int end = restartCount;
		for (;;) {
			int mid = (low + end) >>> 1;
			int p = NB.decodeInt32(buf, restartIdx + mid * 4);
			ptr = p + 1; // skip 0 prefix length
			int len = readVarint32() >>> 2;
			int cmp = compare(name, buf, ptr, len);
			if (cmp < 0) {
				end = mid;
			} else if (cmp == 0) {
				ptr = p;
				return 0;
			} else /* if (cmp > 0) */ {
				low = mid + 1;
			}
			if (low >= end) {
				return seekToKey(name, p, low, cmp);
			}
		}
	}

	private int seekToKey(byte[] name, int p, int rIdx, int rCmp) {
		if (rCmp < 0) {
			if (rIdx == 0) {
				ptr = keysStart;
				return -1;
			}
			ptr = NB.decodeInt32(buf, restartIdx + (rIdx - 1) * 4);
		} else {
			ptr = p;
		}

		for (;;) {
			int savePtr = ptr;
			int pfx = readVarint32();
			int sfxType = readVarint32();
			int nameLen = readRefName(pfx, sfxType >>> 2);
			int cmp = compare(name, nameBuf, 0, nameLen);
			if (cmp <= 0) {
				// cmp < 0, name should be in this block, but is not.
				// cmp = 0, block is positioned at name.
				ptr = savePtr;
				return cmp < 0 && savePtr == keysStart ? -1 : 0;
			}
			skipValue(sfxType);
			if (ptr == keysEnd) {
				return cmp;
			}
		}
	}

	private void skipValue(int sfxType) {
		if (blockType == BlockType.REF) {
			switch (sfxType & 0x03) {
			case 0x00: // deletion
				break;
			case 0x01:
				ptr += RAW_IDLEN;
				break;
			case 0x02:
				ptr += RAW_IDLEN * 2;
				break;
			case 0x03:
				skipValueString();
				break;
			default:
				throw new IllegalStateException();
			}
		} else if (blockType == BlockType.INDEX) {
			readVarint32();
		}
	}

	private int readVarint32() {
		byte c = buf[ptr++];
		int val = c & 0x7f;
		while ((c & 0x80) != 0) {
			c = buf[ptr++];
			val++;
			val <<= 7;
			val |= (c & 0x7f);
		}
		return val;
	}

	private static Ref newRef(String name) {
		return new ObjectIdRef.Unpeeled(NEW, name, null);
	}

	private static int compare(byte[] a, byte[] b, int bi, int bLen) {
		int bEnd = bi + bLen;
		for (int ai = 0; ai < a.length && bi < bEnd; ai++, bi++) {
			int c = (a[ai] & 0xff) - (b[bi] & 0xff);
			if (c != 0) {
				return c;
			}
		}
		return a.length - bLen;
	}

	private static IOException invalidBlock() {
		return new IOException(JGitText.get().invalidReftableBlock);
	}
}
