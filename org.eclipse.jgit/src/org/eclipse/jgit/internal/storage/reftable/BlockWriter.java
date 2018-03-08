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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_DATA;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_NONE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_RESTARTS;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.OBJ_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_1ID;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_2ID;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_NONE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_SYMREF;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VALUE_TYPE_MASK;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.reverseUpdateIndex;
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
import org.eclipse.jgit.util.LongList;
import org.eclipse.jgit.util.NB;

/** Formats and writes blocks for {@link ReftableWriter}. */
class BlockWriter {
	private final byte blockType;
	private final byte keyType;
	private final List<Entry> entries;
	private final int blockLimitBytes;
	private final int restartInterval;

	private int entriesSumBytes;
	private int restartCnt;

	BlockWriter(byte type, byte kt, int bs, int ri) {
		blockType = type;
		keyType = kt;
		blockLimitBytes = bs;
		restartInterval = ri;
		entries = new ArrayList<>(estimateEntryCount(type, kt, bs));
	}

	private static int estimateEntryCount(byte blockType, byte keyType,
			int blockLimitBytes) {
		double avgBytesPerEntry;
		switch (blockType) {
		case REF_BLOCK_TYPE:
		default:
			avgBytesPerEntry = 35.31;
			break;

		case OBJ_BLOCK_TYPE:
			avgBytesPerEntry = 4.19;
			break;

		case LOG_BLOCK_TYPE:
			avgBytesPerEntry = 101.14;
			break;

		case INDEX_BLOCK_TYPE:
			switch (keyType) {
			case REF_BLOCK_TYPE:
			case LOG_BLOCK_TYPE:
			default:
				avgBytesPerEntry = 27.44;
				break;

			case OBJ_BLOCK_TYPE:
				avgBytesPerEntry = 11.57;
				break;
			}
		}

		int cnt = (int) (Math.ceil(blockLimitBytes / avgBytesPerEntry));
		return Math.min(cnt, 4096);
	}

	byte blockType() {
		return blockType;
	}

	boolean padBetweenBlocks() {
		return padBetweenBlocks(blockType)
				|| (blockType == INDEX_BLOCK_TYPE && padBetweenBlocks(keyType));
	}

	static boolean padBetweenBlocks(byte type) {
		return type == REF_BLOCK_TYPE || type == OBJ_BLOCK_TYPE;
	}

	byte[] lastKey() {
		return entries.get(entries.size() - 1).key;
	}

	int currentSize() {
		return computeBlockBytes(0, false);
	}

	void mustAdd(Entry entry) throws BlockSizeTooSmallException {
		if (!tryAdd(entry, true)) {
			// Insanely long names need a larger block size.
			throw blockSizeTooSmall(entry);
		}
	}

	boolean tryAdd(Entry entry) {
		if (entry instanceof ObjEntry
				&& computeBlockBytes(entry.sizeBytes(), 1) > blockLimitBytes) {
			// If the ObjEntry has so many ref block pointers that its
			// encoding overflows any block, reconfigure it to tell readers to
			// instead scan all refs for this ObjectId. That significantly
			// shrinks the entry to a very small size, which may now fit into
			// this block.
			((ObjEntry) entry).markScanRequired();
		}

		if (tryAdd(entry, true)) {
			return true;
		} else if (nextShouldBeRestart()) {
			// It was time for another restart, but the entry doesn't fit
			// with its complete key, as the block is nearly full. Try to
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
			Entry priorEntry = entries.get(entries.size() - 1);
			byte[] prior = priorEntry.key;
			prefixLen = commonPrefix(prior, prior.length, key);
			if (prefixLen <= 5 /* "refs/" */ && keyType == REF_BLOCK_TYPE) {
				// Force restart points at transitions between namespaces
				// such as "refs/heads/" to "refs/tags/".
				restart = true;
				prefixLen = 0;
			} else if (prefixLen == 0) {
				restart = true;
			}
		}

		entry.restart = restart;
		entry.prefixLen = prefixLen;
		int entryBytes = entry.sizeBytes();
		if (computeBlockBytes(entryBytes, restart) > blockLimitBytes) {
			return false;
		}

		entriesSumBytes += entryBytes;
		entries.add(entry);
		if (restart) {
			restartCnt++;
		}
		return true;
	}

	private boolean nextShouldBeRestart() {
		int cnt = entries.size();
		return (cnt == 0 || ((cnt + 1) % restartInterval) == 0)
				&& restartCnt < MAX_RESTARTS;
	}

	private int computeBlockBytes(int entryBytes, boolean restart) {
		return computeBlockBytes(
				entriesSumBytes + entryBytes,
				restartCnt + (restart ? 1 : 0));
	}

	private static int computeBlockBytes(int entryBytes, int restartCnt) {
		return 4 // 4-byte block header
				+ entryBytes
				+ restartCnt * 3 // restart_offset
				+ 2; // 2-byte restart_count
	}

	void writeTo(ReftableOutputStream os) throws IOException {
		os.beginBlock(blockType);
		IntList restarts = new IntList(restartCnt);
		for (Entry entry : entries) {
			if (entry.restart) {
				restarts.add(os.bytesWrittenInBlock());
			}
			entry.writeKey(os);
			entry.writeValue(os);
		}
		if (restarts.size() == 0 || restarts.size() > MAX_RESTARTS) {
			throw new IllegalStateException();
		}
		for (int i = 0; i < restarts.size(); i++) {
			os.writeInt24(restarts.get(i));
		}
		os.writeInt16(restarts.size());
		os.flushBlock();
	}

	private BlockSizeTooSmallException blockSizeTooSmall(Entry entry) {
		// Compute size required to fit this entry by itself.
		int min = FILE_HEADER_LEN + computeBlockBytes(entry.sizeBytes(), 1);
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
		int prefixLen;
		boolean restart;

		Entry(byte[] key) {
			this.key = key;
		}

		void writeKey(ReftableOutputStream os) {
			int sfxLen = key.length - prefixLen;
			os.writeVarint(prefixLen);
			os.writeVarint(encodeSuffixAndType(sfxLen, valueType()));
			os.write(key, prefixLen, sfxLen);
		}

		int sizeBytes() {
			int sfxLen = key.length - prefixLen;
			int sfx = encodeSuffixAndType(sfxLen, valueType());
			return computeVarintSize(prefixLen)
					+ computeVarintSize(sfx)
					+ sfxLen
					+ valueSize();
		}

		abstract byte blockType();
		abstract int valueType();
		abstract int valueSize();
		abstract void writeValue(ReftableOutputStream os) throws IOException;
	}

	static class IndexEntry extends Entry {
		private final long blockPosition;

		IndexEntry(byte[] key, long blockPosition) {
			super(key);
			this.blockPosition = blockPosition;
		}

		@Override
		byte blockType() {
			return INDEX_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return 0;
		}

		@Override
		int valueSize() {
			return computeVarintSize(blockPosition);
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeVarint(blockPosition);
		}
	}

	static class RefEntry extends Entry {
		final Ref ref;
		final long updateIndexDelta;

		RefEntry(Ref ref, long updateIndexDelta) {
			super(nameUtf8(ref));
			this.ref = ref;
			this.updateIndexDelta = updateIndexDelta;
		}

		@Override
		byte blockType() {
			return REF_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			if (ref.isSymbolic()) {
				return VALUE_SYMREF;
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
			int n = computeVarintSize(updateIndexDelta);
			switch (valueType()) {
			case VALUE_NONE:
				return n;
			case VALUE_1ID:
				return n + OBJECT_ID_LENGTH;
			case VALUE_2ID:
				return n + 2 * OBJECT_ID_LENGTH;
			case VALUE_SYMREF:
				if (ref.isSymbolic()) {
					int nameLen = nameUtf8(ref.getTarget()).length;
					return n + computeVarintSize(nameLen) + nameLen;
				}
			}
			throw new IllegalStateException();
		}

		@Override
		void writeValue(ReftableOutputStream os) throws IOException {
			os.writeVarint(updateIndexDelta);
			switch (valueType()) {
			case VALUE_NONE:
				return;

			case VALUE_1ID: {
				ObjectId id1 = ref.getObjectId();
				if (!ref.isPeeled()) {
					throw new IOException(JGitText.get().peeledRefIsRequired);
				} else if (id1 == null) {
					throw new IOException(JGitText.get().invalidId0);
				}
				os.writeId(id1);
				return;
			}

			case VALUE_2ID: {
				ObjectId id1 = ref.getObjectId();
				ObjectId id2 = ref.getPeeledObjectId();
				if (!ref.isPeeled()) {
					throw new IOException(JGitText.get().peeledRefIsRequired);
				} else if (id1 == null || id2 == null) {
					throw new IOException(JGitText.get().invalidId0);
				}
				os.writeId(id1);
				os.writeId(id2);
				return;
			}

			case VALUE_SYMREF:
				if (ref.isSymbolic()) {
					os.writeVarintString(ref.getTarget().getName());
					return;
				}
			}
			throw new IllegalStateException();
		}

		private static byte[] nameUtf8(Ref ref) {
			return ref.getName().getBytes(CHARSET);
		}
	}

	static class ObjEntry extends Entry {
		final LongList blockPos;

		ObjEntry(int idLen, ObjectId id, LongList blockPos) {
			super(key(idLen, id));
			this.blockPos = blockPos;
		}

		private static byte[] key(int idLen, ObjectId id) {
			byte[] key = new byte[OBJECT_ID_LENGTH];
			id.copyRawTo(key, 0);
			if (idLen < OBJECT_ID_LENGTH) {
				return Arrays.copyOf(key, idLen);
			}
			return key;
		}

		void markScanRequired() {
			blockPos.clear();
		}

		@Override
		byte blockType() {
			return OBJ_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			int cnt = blockPos.size();
			return cnt != 0 && cnt <= VALUE_TYPE_MASK ? cnt : 0;
		}

		@Override
		int valueSize() {
			int cnt = blockPos.size();
			if (cnt == 0) {
				return computeVarintSize(0);
			}

			int n = 0;
			if (cnt > VALUE_TYPE_MASK) {
				n += computeVarintSize(cnt);
			}
			n += computeVarintSize(blockPos.get(0));
			for (int j = 1; j < cnt; j++) {
				long prior = blockPos.get(j - 1);
				long b = blockPos.get(j);
				n += computeVarintSize(b - prior);
			}
			return n;
		}

		@Override
		void writeValue(ReftableOutputStream os) throws IOException {
			int cnt = blockPos.size();
			if (cnt == 0) {
				os.writeVarint(0);
				return;
			}

			if (cnt > VALUE_TYPE_MASK) {
				os.writeVarint(cnt);
			}
			os.writeVarint(blockPos.get(0));
			for (int j = 1; j < cnt; j++) {
				long prior = blockPos.get(j - 1);
				long b = blockPos.get(j);
				os.writeVarint(b - prior);
			}
		}
	}

	static class DeleteLogEntry extends Entry {
		DeleteLogEntry(String refName, long updateIndex) {
			super(LogEntry.key(refName, updateIndex));
		}

		@Override
		byte blockType() {
			return LOG_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return LOG_NONE;
		}

		@Override
		int valueSize() {
			return 0;
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			// Nothing in a delete log record.
		}
	}

	static class LogEntry extends Entry {
		final ObjectId oldId;
		final ObjectId newId;
		final long timeSecs;
		final short tz;
		final byte[] name;
		final byte[] email;
		final byte[] msg;

		LogEntry(String refName, long updateIndex, PersonIdent who,
				ObjectId oldId, ObjectId newId, String message) {
			super(key(refName, updateIndex));

			this.oldId = oldId;
			this.newId = newId;
			this.timeSecs = who.getWhen().getTime() / 1000L;
			this.tz = (short) who.getTimeZoneOffset();
			this.name = who.getName().getBytes(CHARSET);
			this.email = who.getEmailAddress().getBytes(CHARSET);
			this.msg = message.getBytes(CHARSET);
		}

		static byte[] key(String ref, long index) {
			byte[] name = ref.getBytes(CHARSET);
			byte[] key = Arrays.copyOf(name, name.length + 1 + 8);
			NB.encodeInt64(key, key.length - 8, reverseUpdateIndex(index));
			return key;
		}

		@Override
		byte blockType() {
			return LOG_BLOCK_TYPE;
		}

		@Override
		int valueType() {
			return LOG_DATA;
		}

		@Override
		int valueSize() {
			return 2 * OBJECT_ID_LENGTH
					+ computeVarintSize(name.length) + name.length
					+ computeVarintSize(email.length) + email.length
					+ computeVarintSize(timeSecs)
					+ 2 // tz
					+ computeVarintSize(msg.length) + msg.length;
		}

		@Override
		void writeValue(ReftableOutputStream os) {
			os.writeId(oldId);
			os.writeId(newId);
			os.writeVarintString(name);
			os.writeVarintString(email);
			os.writeVarint(timeSecs);
			os.writeInt16(tz);
			os.writeVarintString(msg);
		}
	}
}
