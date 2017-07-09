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
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_RESTARTS;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.OBJ_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_1ID;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_2ID;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_INDEX_RECORD;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_LOG_RECORD;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_NONE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_TEXT;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_TYPE_MASK;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.reverseTime;
import static org.eclipse.jgit.internal.storage.reftable.ReftableOutputStream.computeVarintSize;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.NB;

class BlockWriter {
	private final byte blockType;
	private final List<Entry> entries = new ArrayList<>();
	private final int blockSize;
	private final int restartInterval;

	private int bytesInKeyTable;
	private int restartCnt;

	BlockWriter(byte type, int bs, int ri) {
		blockType = type;
		blockSize = bs;
		restartInterval = ri;
	}

	byte blockType() {
		return blockType;
	}

	boolean padBetweenBlocks() {
		return blockType == REF_BLOCK_TYPE || blockType == OBJ_BLOCK_TYPE;
	}

	byte[] lastKey() {
		return entries.get(entries.size() - 1).key;
	}

	int entryCount() {
		return entries.size();
	}

	int currentSize() {
		return computeBlockSize(0, false);
	}

	int estimateIndexSizeIfAdding(byte[] lastKey, long blockOffset) {
		IndexEntry entry = new IndexEntry(lastKey, blockOffset);
		return computeBlockSize(entry.size(0), true);
	}

	void addIndex(byte[] lastKey, long blockOffset) {
		entries.add(new IndexEntry(lastKey, blockOffset));
	}

	void addFirst(Entry entry) throws BlockSizeTooSmallException {
		if (!tryAdd(entry, true)) {
			// Insanely long names need a larger block size.
			throw blockSizeTooSmall(entry);
		}
	}

	boolean tryAdd(Entry entry) {
		if (tryAdd(entry, true)) {
			return true;
		} else if (nextShouldBeRestart()) {
			// It was time for another restart, but the entry doesn't fit
			// with its complete name, as the block is nearly full. Try to
			// force it to fit with prefix compression rather than waste
			// the tail of the block with padding.
			return tryAdd(entry, false);
		}
		return false;
	}

	private boolean tryAdd(Entry entry, boolean tryRestart) {
		byte[] key = entry.key;
		int prefixLen = 0;
		boolean restart = tryRestart && nextShouldBeRestart();
		if (!restart) {
			byte[] prior = entries.get(entries.size() - 1).key;
			prefixLen = commonPrefix(prior, prior.length, key);
			if (prefixLen == 0) {
				restart = true;
			}
		}

		int entrySize = entry.size(prefixLen);
		if (computeBlockSize(entrySize, restart) > blockSize) {
			return false;
		}

		bytesInKeyTable += entrySize;
		entries.add(entry);
		if (restart) {
			entry.restart = true;
			restartCnt++;
		}
		return true;
	}

	private boolean nextShouldBeRestart() {
		int cnt = entries.size();
		return (cnt == 0 || ((cnt + 1) % restartInterval) == 0)
				&& restartCnt < MAX_RESTARTS;
	}

	private int computeBlockSize(int key, boolean restart) {
		return 4 // 4-byte block header
				+ bytesInKeyTable + key
				+ (restartCnt + (restart ? 1 : 0)) * 4
				+ 2; // 2-byte restart_count
	}

	void writeTo(ReftableOutputStream os) throws IOException {
		if (blockType == INDEX_BLOCK_TYPE) {
			selectIndexRestarts();
		}
		if (restartCnt > MAX_RESTARTS) {
			throw new IllegalStateException();
		}

		IntList restartOffsets = new IntList(restartCnt);
		byte[] prior = {};

		os.beginBlock(blockType);
		for (int entryIdx = 0; entryIdx < entries.size(); entryIdx++) {
			Entry entry = entries.get(entryIdx);
			if (entry.restart) {
				restartOffsets.add(os.bytesWrittenInBlock());
			}
			entry.writeKey(os, prior);
			entry.writeValue(os);
			prior = entry.key;
		}
		for (int i = 0; i < restartOffsets.size(); i++) {
			os.writeInt32(restartOffsets.get(i));
		}
		os.writeInt16(restartOffsets.size());
		os.flushBlock();
	}

	private void selectIndexRestarts() {
		// Indexes grow without bound, but the restart table has a limit.
		// Select restarts in the index as far apart as possible to stay
		// within the MAX_RESTARTS limit defined by the file format.
		int ir = Math.max(restartInterval, entries.size() / MAX_RESTARTS);
		for (int k = 0; k < entries.size(); k++) {
			if ((k % ir) == 0) {
				entries.get(k).restart = true;
			}
		}
	}

	private BlockSizeTooSmallException blockSizeTooSmall(Entry entry) {
		// Compute size required to fit this entry by itself.
		int min = computeBlockSize(entry.size(0), true);
		return new BlockSizeTooSmallException(min);
	}

	static int commonPrefix(byte[] a, int n, byte[] b) {
		int len = Math.min(n, Math.min(a.length, b.length));
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return len;
	}

	static int encodeSuffixAndType(int sfx, int valueType) {
		return (sfx << 3) | valueType;
	}

	static int compare(
			byte[] a, int ai, int aLen,
			byte[] b, int bi, int bLen) {
		int aEnd = ai + aLen;
		int bEnd = bi + bLen;
		while (ai < aEnd && bi < bEnd) {
			int c = (a[ai++] & 0xff) - (b[bi++] & 0xff);
			if (c != 0) {
				return c;
			}
		}
		return aLen - bLen;
	}

	static abstract class Entry {
		static int compare(Entry ea, Entry eb) {
			byte[] a = ea.key;
			byte[] b = eb.key;
			return BlockWriter.compare(a, 0, a.length, b, 0, b.length);
		}

		final byte[] key;
		boolean restart;

		Entry(byte[] key) {
			this.key = key;
		}

		void writeKey(ReftableOutputStream os, byte[] prior) {
			int pfx;
			int sfx;
			if (restart) {
				pfx = 0;
				sfx = key.length;
			} else {
				pfx = commonPrefix(prior, prior.length, key);
				sfx = key.length - pfx;
			}
			os.writeVarint(pfx);
			os.writeVarint(encodeSuffixAndType(sfx, valueType()));
			os.write(key, pfx, sfx);
		}

		int size(int prefixLen) {
			int sfx = key.length - prefixLen;
			return computeVarintSize(prefixLen)
					+ computeVarintSize(encodeSuffixAndType(sfx, valueType()))
					+ sfx
					+ valueSize();
		}

		abstract byte blockType();
		abstract int valueType();
		abstract int valueSize();
		abstract void writeValue(ReftableOutputStream os) throws IOException;
	}

	static class IndexEntry extends Entry {
		private final long blockOffset;

		IndexEntry(byte[] key, long blockOffset) {
			super(key);
			this.blockOffset = blockOffset;
		}

		@Override
		byte blockType() {
			return INDEX_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return VALUE_INDEX_RECORD;
		}

		@Override
		int valueSize() {
			return computeVarintSize(blockOffset);
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeVarint(blockOffset);
		}
	}

	static class RefEntry extends Entry {
		final Ref ref;

		RefEntry(Ref ref) {
			super(nameUtf8(ref));
			this.ref = ref;
		}

		@Override
		byte blockType() {
			return REF_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			if (ref.isSymbolic()) {
				return VALUE_TEXT;
			} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
				return VALUE_NONE;
			} else if (ref.getPeeledObjectId() != null) {
				return VALUE_2ID;
			} else {
				return VALUE_1ID;
			}
		}

		@Override
		int valueSize() {
			if (ref.isSymbolic()) {
				int nameLen = 5 + nameUtf8(ref.getTarget()).length;
				return computeVarintSize(nameLen) + nameLen;
			} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
				return 0;
			} else if (ref.getPeeledObjectId() != null) {
				return 2 * OBJECT_ID_LENGTH;
			} else {
				return OBJECT_ID_LENGTH;
			}
		}

		@Override
		void writeValue(ReftableOutputStream os) throws IOException {
			if (ref.isSymbolic()) {
				String target = ref.getTarget().getName();
				os.writeVarintString("ref: " + target); //$NON-NLS-1$
				return;
			}

			ObjectId id1 = ref.getObjectId();
			if (id1 == null) {
				if (ref.getStorage() == NEW) {
					return;
				}
				throw new IOException(JGitText.get().invalidId0);
			} else if (!ref.isPeeled()) {
				throw new IOException(JGitText.get().peeledRefIsRequired);
			}
			os.writeId(id1);

			ObjectId id2 = ref.getPeeledObjectId();
			if (id2 != null) {
				os.writeId(id2);
			}
		}

		private static byte[] nameUtf8(Ref ref) {
			return ref.getName().getBytes(UTF_8);
		}
	}

	static class ObjEntry extends Entry {
		final IntList blocks;

		ObjEntry(int idLen, ObjectId id, IntList blocks) {
			super(key(idLen, id));
			this.blocks = blocks;
		}

		private static byte[] key(int idLen, ObjectId id) {
			byte[] key = new byte[OBJECT_ID_LENGTH];
			id.copyRawTo(key, 0);
			if (idLen < OBJECT_ID_LENGTH) {
				return Arrays.copyOf(key, idLen);
			}
			return key;
		}

		@Override
		byte blockType() {
			return OBJ_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return Math.min(blocks.size(), VALUE_TYPE_MASK);
		}

		@Override
		int valueSize() {
			int n = 0;
			int cnt = blocks.size();
			if (cnt >= VALUE_TYPE_MASK) {
				n += computeVarintSize(cnt - VALUE_TYPE_MASK);
			}
			n += computeVarintSize(blocks.get(0));
			for (int j = 1; j < cnt; j++) {
				int prior = blocks.get(j - 1);
				int b = blocks.get(j);
				n += computeVarintSize(b - prior);
			}
			return n;
		}

		@Override
		void writeValue(ReftableOutputStream os) throws IOException {
			int cnt = blocks.size();
			if (cnt >= VALUE_TYPE_MASK) {
				os.writeVarint(cnt - VALUE_TYPE_MASK);
			}
			os.writeVarint(blocks.get(0));
			for (int j = 1; j < cnt; j++) {
				int prior = blocks.get(j - 1);
				int b = blocks.get(j);
				os.writeVarint(b - prior);
			}
		}
	}

	static class LogEntry extends Entry {
		final ObjectId oldId;
		final ObjectId newId;
		final short tz;
		final byte[] name;
		final byte[] email;
		final byte[] msg;

		LogEntry(String refName, long timeUsec, PersonIdent who,
				ObjectId oldId, ObjectId newId,
				String message) {
			super(key(refName, timeUsec));

			this.oldId = oldId;
			this.newId = newId;
			this.tz = (short) who.getTimeZoneOffset();
			this.name = who.getName().getBytes(UTF_8);
			this.email = who.getEmailAddress().getBytes(UTF_8);
			this.msg = message.getBytes(UTF_8);
		}

		static byte[] key(String refName, long time) {
			byte[] name = refName.getBytes(UTF_8);
			byte[] key = Arrays.copyOf(name, name.length + 1 + 8);
			NB.encodeInt64(key, key.length - 8, reverseTime(time));
			return key;
		}

		@Override
		byte blockType() {
			return LOG_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return VALUE_LOG_RECORD;
		}

		@Override
		int valueSize() {
			return 2 * OBJECT_ID_LENGTH
					+ 2 // tz
					+ computeVarintSize(name.length) + name.length
					+ computeVarintSize(email.length) + email.length
					+ computeVarintSize(msg.length) + msg.length;
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeId(oldId);
			os.writeId(newId);
			os.writeInt16(tz);
			os.writeVarintString(name);
			os.writeVarintString(email);
			os.writeVarintString(msg);
		}
	}
}
