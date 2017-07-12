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
import static org.eclipse.jgit.internal.storage.reftable.BlockReader.decodeIndexSize;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.isFileHeaderMagic;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.LogEntry;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.NB;

/**
 * Reads a reftable formatted file.
 * <p>
 * {@code ReftableReader} is not thread-safe. Concurrent readers need their own
 * instance to read from the same file.
 */
public class ReftableReader extends Reftable {
	private final BlockSource src;

	private int blockSize;
	private long minUpdateIndex;
	private long maxUpdateIndex;

	private long refEnd;
	private long objOffset;
	private long objEnd;
	private long logOffset;
	private long logEnd;

	private long refIndexOffset = -1;
	private long objIndexOffset = -1;
	private long logIndexOffset = -1;

	private BlockReader refIndex;
	private BlockReader objIndex;
	private BlockReader logIndex;

	/**
	 * Initialize a new reftable reader.
	 *
	 * @param src
	 *            the file content to read.
	 */
	public ReftableReader(BlockSource src) {
		this.src = src;
	}

	/**
	 * @return the block size in bytes chosen for this file by the writer. Most
	 *         reads from the {@link BlockSource} will be aligned to the block
	 *         size.
	 * @throws IOException
	 *             file cannot be read.
	 */
	public int blockSize() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}
		return blockSize;
	}

	/**
	 * @return the minimum update index for log entries that appear in this
	 *         reftable. This should be 1 higher than the prior reftable's
	 *         {@code maxUpdateIndex} if this table is used in a stack.
	 * @throws IOException
	 *             file cannot be read.
	 */
	public long minUpdateIndex() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}
		return minUpdateIndex;
	}

	/**
	 * @return the maximum update index for log entries that appear in this
	 *         reftable. This should be 1 higher than the prior reftable's
	 *         {@code maxUpdateIndex} if this table is used in a stack.
	 * @throws IOException
	 *             file cannot be read.
	 */
	public long maxUpdateIndex() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}
		return maxUpdateIndex;
	}

	@Override
	public RefCursor allRefs() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}

		long end = refEnd > 0 ? refEnd : (src.size() - FILE_FOOTER_LEN);
		src.adviseSequentialRead(0, end);

		RefCursorImpl i = new RefCursorImpl(end, null, false);
		i.block = readBlock(0, end);
		return i;
	}

	@Override
	public RefCursor seek(String refName) throws IOException {
		initRefIndex();

		byte[] match = refName.getBytes(UTF_8);
		boolean prefix = match[match.length - 1] == '/';
		byte[] key = match;
		if (prefix) {
			key = Arrays.copyOf(key, key.length + 1);
			key[key.length - 1] = '\1';
		}

		RefCursorImpl i = new RefCursorImpl(refEnd, match, prefix);
		i.block = seek(REF_BLOCK_TYPE, key, refIndex, 0, refEnd);
		return i;
	}

	@Override
	public RefCursor byObjectId(AnyObjectId id) throws IOException {
		initObjIndex();
		ObjCursorImpl i = new ObjCursorImpl(refEnd, id);
		if (objIndex != null) {
			i.initSeek();
		} else {
			i.initScan();
		}
		return i;
	}

	@Override
	public LogCursor allLogs() throws IOException {
		initLogIndex();
		if (logOffset > 0) {
			src.adviseSequentialRead(logOffset, logEnd);
			LogCursorImpl i = new LogCursorImpl(logEnd, null);
			i.block = readBlock(logOffset, logEnd);
			return i;
		}
		return new EmptyLogCursor();
	}

	@Override
	public LogCursor seekLog(String refName, long updateIndex)
			throws IOException {
		initLogIndex();
		if (logOffset > 0) {
			byte[] key = LogEntry.key(refName, updateIndex);
			byte[] match = refName.getBytes(UTF_8);
			LogCursorImpl i = new LogCursorImpl(logEnd, match);
			i.block = seek(LOG_BLOCK_TYPE, key, logIndex, logOffset, logEnd);
			return i;
		}
		return new EmptyLogCursor();
	}

	private BlockReader seek(byte blockType, byte[] key, BlockReader idx,
			long start, long end) throws IOException {
		if (idx != null) {
			long blockOffset = idx.seekKey(key) <= 0
					? idx.readIndex()
					: ((blocksIn(start, end) - 1) * blockSize);
			BlockReader block = readBlock(blockOffset, end);
			block.seekKey(key);
			return block;
		}
		return binarySearch(blockType, key, start, end);
	}

	private BlockReader binarySearch(byte blockType, byte[] name,
			long startPos, long endPos) throws IOException {
		int low = (int) (startPos / blockSize);
		int end = blocksIn(startPos, endPos);
		BlockReader block = null;
		do {
			int mid = (low + end) >>> 1;
			block = readBlock(((long) mid) * blockSize, endPos);
			if (blockType != block.type()) {
				return null;
			}
			int cmp = block.seekKey(name);
			if (cmp < 0) {
				end = mid;
			} else if (cmp == 0) {
				break;
			} else /* if (cmp > 0) */ {
				low = mid + 1;
			}
		} while (low < end);
		return block;
	}

	private void readFileHeader() throws IOException {
		readHeaderOrFooter(0, FILE_HEADER_LEN);
	}

	private void readFileFooter() throws IOException {
		int ftrLen = FILE_FOOTER_LEN;
		byte[] ftr = readHeaderOrFooter(src.size() - ftrLen, ftrLen);

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, ftrLen - 4);
		if (crc.getValue() != NB.decodeUInt32(ftr, ftrLen - 4)) {
			throw new IOException(JGitText.get().invalidReftableCRC);
		}

		refIndexOffset = NB.decodeInt64(ftr, 24);
		objOffset = NB.decodeInt64(ftr, 32);
		objIndexOffset = NB.decodeInt64(ftr, 40);
		logOffset = NB.decodeInt64(ftr, 48);
		logIndexOffset = NB.decodeInt64(ftr, 56);

		if (refIndexOffset > 0) {
			refEnd = refIndexOffset;
		} else if (objOffset > 0) {
			refEnd = objOffset;
		} else if (logOffset > 0) {
			refEnd = logOffset;
		} else {
			refEnd = src.size() - ftrLen;
		}

		if (objOffset > 0) {
			if (objIndexOffset > 0) {
				objEnd = objIndexOffset;
			} else if (logOffset > 0) {
				objEnd = logOffset;
			} else {
				objEnd = src.size() - ftrLen;
			}
		}

		if (logOffset > 0) {
			if (logIndexOffset > 0) {
				logEnd = logIndexOffset;
			} else {
				logEnd = src.size() - ftrLen;
			}
		}
	}

	private byte[] readHeaderOrFooter(long pos, int len) throws IOException {
		ByteBuffer buf = src.read(pos, len);
		if (buf.position() != len) {
			throw new IOException(JGitText.get().shortReadOfBlock);
		}

		byte[] tmp = new byte[len];
		buf.flip();
		buf.get(tmp);
		if (!isFileHeaderMagic(tmp, 0, len)) {
			throw new IOException(JGitText.get().invalidReftableFile);
		}

		int v = NB.decodeInt32(tmp, 4);
		int version = v >>> 24;
		if (VERSION_1 != version) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedReftableVersion,
					Integer.valueOf(version)));
		}
		if (blockSize == 0) {
			blockSize = v & 0xffffff;
		}
		minUpdateIndex = NB.decodeInt64(tmp, 8);
		maxUpdateIndex = NB.decodeInt64(tmp, 16);
		return tmp;
	}

	private void initRefIndex() throws IOException {
		if (blockSize == 0 || refIndexOffset < 0) {
			readFileFooter();
		}
		if (refIndex == null && refIndexOffset > 0) {
			refIndex = readIndex(refIndexOffset);
		}
	}

	private void initObjIndex() throws IOException {
		if (blockSize == 0 || objIndexOffset < 0) {
			readFileFooter();
		}
		if (objIndex == null && objIndexOffset > 0) {
			objIndex = readIndex(objIndexOffset);
		}
	}

	private void initLogIndex() throws IOException {
		if (blockSize == 0 || logIndexOffset < 0) {
			readFileFooter();
		}
		if (logIndex == null && logIndexOffset > 0) {
			logIndex = readIndex(logIndexOffset);
		}
	}

	private BlockReader readIndex(long pos) throws IOException {
		int sz = readIndexSize(pos);
		BlockReader i = new BlockReader();
		i.readBlock(src, pos, sz);
		i.verifyIndex();
		return i;
	}

	private int readIndexSize(long pos) throws IOException {
		ByteBuffer tmp = src.read(pos, 4);
		if (tmp.position() < 4) {
			throw new IOException(JGitText.get().invalidReftableFile);
		}
		byte[] buf;
		if (tmp.hasArray() && tmp.arrayOffset() == 0) {
			buf = tmp.array();
		} else {
			buf = new byte[4];
			tmp.flip();
			tmp.get(buf, 0, 4);
		}
		return decodeIndexSize(NB.decodeInt32(buf, 0));
	}

	private BlockReader readBlock(long position, long end) throws IOException {
		int sz = blockSize;
		if (position + sz > end) {
			sz = (int) (end - position); // last block may omit padding.
		}

		BlockReader b = new BlockReader();
		b.readBlock(src, position, sz);
		return b;
	}

	private int blocksIn(long pos, long end) {
		int blocks = (int) ((end - pos) / blockSize);
		return end % blockSize == 0 ? blocks : (blocks + 1);
	}

	@Override
	public void close() throws IOException {
		src.close();
	}

	private class RefCursorImpl extends RefCursor {
		private final long scanEnd;
		private final byte[] match;
		private final boolean prefix;

		private Ref ref;
		BlockReader block;

		RefCursorImpl(long scanEnd, byte[] match, boolean prefix) {
			this.scanEnd = scanEnd;
			this.match = match;
			this.prefix = prefix;
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				if (block == null || block.type() != REF_BLOCK_TYPE) {
					return false;
				} else if (!block.next()) {
					long p = block.endPosition();
					if (p >= scanEnd) {
						return false;
					}
					block = readBlock(p, scanEnd);
					continue;
				}

				block.parseKey();
				if (match != null && !block.match(match, prefix)) {
					block.skipValue();
					return false;
				}

				ref = block.readRef();
				if (!includeDeletes && wasDeleted()) {
					continue;
				}
				return true;
			}
		}

		@Override
		public Ref getRef() {
			return ref;
		}

		@Override
		public void close() {
			// Do nothing.
		}
	}

	private class LogCursorImpl extends LogCursor {
		private final long scanEnd;
		private final byte[] match;

		private String refName;
		private long updateIndex;
		private ReflogEntry entry;
		BlockReader block;

		LogCursorImpl(long scanEnd, byte[] match) {
			this.scanEnd = scanEnd;
			this.match = match;
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				if (block == null || block.type() != LOG_BLOCK_TYPE) {
					return false;
				} else if (!block.next()) {
					long p = block.endPosition();
					if (p >= scanEnd) {
						return false;
					}
					block = readBlock(p, scanEnd);
					continue;
				}

				block.parseKey();
				if (match != null && !block.match(match, false)) {
					block.skipValue();
					return false;
				}

				refName = block.name();
				updateIndex = block.readLogUpdateIndex();
				entry = block.readLog();
				return true;
			}
		}

		@Override
		public String getRefName() {
			return refName;
		}

		@Override
		public long getUpdateIndex() {
			return updateIndex;
		}

		@Override
		public ReflogEntry getReflogEntry() {
			return entry;
		}

		@Override
		public void close() {
			// Do nothing.
		}
	}

	private class ObjCursorImpl extends RefCursor {
		private final long scanEnd;
		private final ObjectId match;

		private Ref ref;
		private int listIdx;
		private IntList blockList;
		private BlockReader block;

		ObjCursorImpl(long scanEnd, AnyObjectId id) {
			this.scanEnd = scanEnd;
			this.match = id.copy();
		}

		void initSeek() throws IOException {
			byte[] key = new byte[OBJECT_ID_LENGTH];
			match.copyRawTo(key, 0);

			long blockOffset = objIndex.seekAbbrevId(key) <= 0
					? objIndex.readIndex()
					: ((blocksIn(objOffset, objEnd) - 1) * blockSize);
			BlockReader b = readBlock(blockOffset, objEnd);
			b.seekAbbrevId(key);
			while (b.next()) {
				b.parseKey();
				if (b.matchAbbrevId(key)) {
					blockList = b.readBlockList();
					break;
				}
				b.skipValue();
			}
			if (blockList == null) {
				blockList = new IntList(0);
			}
			if (blockList.size() > 0) {
				int blockIdx = blockList.get(listIdx++);
				block = readBlock(blockIdx * blockSize, scanEnd);
			}
		}

		void initScan() throws IOException {
			block = readBlock(0, scanEnd);
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				if (block == null || block.type() != REF_BLOCK_TYPE) {
					return false;
				} else if (!block.next()) {
					long p;
					if (blockList != null) {
						if (listIdx >= blockList.size()) {
							return false;
						}
						int blockIdx = blockList.get(listIdx++);
						p = blockIdx * blockSize;
					} else {
						p = block.endPosition();
					}
					if (p >= scanEnd) {
						return false;
					}
					block = readBlock(p, scanEnd);
					continue;
				}

				block.parseKey();
				ref = block.readRef();
				ObjectId id = ref.getObjectId();
				if (id != null && match.equals(id)
						&& (includeDeletes || !wasDeleted())) {
					return true;
				}
			}
		}

		@Override
		public Ref getRef() {
			return ref;
		}

		@Override
		public void close() {
			// Do nothing.
		}
	}
}
