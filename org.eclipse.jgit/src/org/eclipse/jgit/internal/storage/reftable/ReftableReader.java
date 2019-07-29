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
import static org.eclipse.jgit.internal.storage.reftable.BlockReader.decodeBlockLen;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
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
import org.eclipse.jgit.util.LongList;
import org.eclipse.jgit.util.LongMap;
import org.eclipse.jgit.util.NB;

/**
 * Reads a reftable formatted file.
 * <p>
 * {@code ReftableReader} is not thread-safe. Concurrent readers need their own
 * instance to read from the same file.
 */
public class ReftableReader extends Reftable {
	private final BlockSource src;

	private int blockSize = -1;
	private long minUpdateIndex;
	private long maxUpdateIndex;

	private long refEnd;
	private long objPosition;
	private long objEnd;
	private long logPosition;
	private long logEnd;
	private int objIdLen;

	private long refIndexPosition = -1;
	private long objIndexPosition = -1;
	private long logIndexPosition = -1;

	private BlockReader refIndex;
	private BlockReader objIndex;
	private BlockReader logIndex;
	private LongMap<BlockReader> indexCache;

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
	 * Get the block size in bytes chosen for this file by the writer.
	 *
	 * @return the block size in bytes chosen for this file by the writer. Most
	 *         reads from the
	 *         {@link org.eclipse.jgit.internal.storage.io.BlockSource} will be
	 *         aligned to the block size.
	 * @throws java.io.IOException
	 *             file cannot be read.
	 */
	public int blockSize() throws IOException {
		if (blockSize == -1) {
			readFileHeader();
		}
		return blockSize;
	}

	/**
	 * Get the minimum update index for log entries that appear in this
	 * reftable.
	 *
	 * @return the minimum update index for log entries that appear in this
	 *         reftable. This should be 1 higher than the prior reftable's
	 *         {@code maxUpdateIndex} if this table is used in a stack.
	 * @throws java.io.IOException
	 *             file cannot be read.
	 */
	public long minUpdateIndex() throws IOException {
		if (blockSize == -1) {
			readFileHeader();
		}
		return minUpdateIndex;
	}

	/**
	 * Get the maximum update index for log entries that appear in this
	 * reftable.
	 *
	 * @return the maximum update index for log entries that appear in this
	 *         reftable. This should be 1 higher than the prior reftable's
	 *         {@code maxUpdateIndex} if this table is used in a stack.
	 * @throws java.io.IOException
	 *             file cannot be read.
	 */
	public long maxUpdateIndex() throws IOException {
		if (blockSize == -1) {
			readFileHeader();
		}
		return maxUpdateIndex;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor allRefs() throws IOException {
		if (blockSize == -1) {
			readFileHeader();
		}

		long end = refEnd > 0 ? refEnd : (src.size() - FILE_FOOTER_LEN);
		src.adviseSequentialRead(0, end);

		RefCursorImpl i = new RefCursorImpl(end, null, false);
		i.block = readBlock(0, end);
		return i;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor seekRef(String refName) throws IOException {
		initRefIndex();

		byte[] key = refName.getBytes(UTF_8);
		RefCursorImpl i = new RefCursorImpl(refEnd, key, false);
		i.block = seek(REF_BLOCK_TYPE, key, refIndex, 0, refEnd);
		return i;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor seekRefsWithPrefix(String prefix) throws IOException {
		initRefIndex();

		byte[] key = prefix.getBytes(UTF_8);
		RefCursorImpl i = new RefCursorImpl(refEnd, key, true);
		i.block = seek(REF_BLOCK_TYPE, key, refIndex, 0, refEnd);
		return i;
	}

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
	@Override
	public LogCursor allLogs() throws IOException {
		initLogIndex();
		if (logPosition > 0) {
			src.adviseSequentialRead(logPosition, logEnd);
			LogCursorImpl i = new LogCursorImpl(logEnd, null);
			i.block = readBlock(logPosition, logEnd);
			return i;
		}
		return new EmptyLogCursor();
	}

	/** {@inheritDoc} */
	@Override
	public LogCursor seekLog(String refName, long updateIndex)
			throws IOException {
		initLogIndex();
		if (logPosition > 0) {
			byte[] key = LogEntry.key(refName, updateIndex);
			byte[] match = refName.getBytes(UTF_8);
			LogCursorImpl i = new LogCursorImpl(logEnd, match);
			i.block = seek(LOG_BLOCK_TYPE, key, logIndex, logPosition, logEnd);
			return i;
		}
		return new EmptyLogCursor();
	}

	private BlockReader seek(byte blockType, byte[] key, BlockReader idx,
			long startPos, long endPos) throws IOException {
		if (idx != null) {
			// Walk through a possibly multi-level index to a leaf block.
			BlockReader block = idx;
			do {
				if (block.seekKey(key) > 0) {
					return null;
				}
				long pos = block.readPositionFromIndex();
				block = readBlock(pos, endPos);
			} while (block.type() == INDEX_BLOCK_TYPE);
			block.seekKey(key);
			return block;
		}
		if (blockType == LOG_BLOCK_TYPE) {
			// No index. Log blocks are irregularly sized, so we can't do binary search
			// between blocks. Scan over blocks instead.
			BlockReader block = readBlock(startPos, endPos);

			for (;;) {
				if (block == null || block.type() != LOG_BLOCK_TYPE) {
					return null;
				}

				int result = block.seekKey(key);
				if (result <= 0) {
					// == 0 : we found the key.
					// < 0 : the key is before this block. Either the ref name is there
					// but only at a newer updateIndex, or it is absent. We leave it to
					// logcursor to distinguish between both cases.
					return block;
				}

				long pos = block.endPosition();
				if (pos >= endPos) {
					return null;
				}
				block = readBlock(pos, endPos);
			}
		}
		return binarySearch(blockType, key, startPos, endPos);
	}

	private BlockReader binarySearch(byte blockType, byte[] key,
			long startPos, long endPos) throws IOException {
		if (blockSize == 0) {
			BlockReader b = readBlock(startPos, endPos);
			if (blockType != b.type()) {
				return null;
			}
			b.seekKey(key);
			return b;
		}

		int low = (int) (startPos / blockSize);
		int end = blocksIn(startPos, endPos);
		BlockReader block = null;
		do {
			int mid = (low + end) >>> 1;
			block = readBlock(((long) mid) * blockSize, endPos);
			if (blockType != block.type()) {
				return null;
			}
			int cmp = block.seekKey(key);
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

		refIndexPosition = NB.decodeInt64(ftr, 24);
		long p = NB.decodeInt64(ftr, 32);
		objPosition = p >>> 5;
		objIdLen = (int) (p & 0x1f);
		objIndexPosition = NB.decodeInt64(ftr, 40);
		logPosition = NB.decodeInt64(ftr, 48);
		logIndexPosition = NB.decodeInt64(ftr, 56);

		if (refIndexPosition > 0) {
			refEnd = refIndexPosition;
		} else if (objPosition > 0) {
			refEnd = objPosition;
		} else if (logPosition > 0) {
			refEnd = logPosition;
		} else {
			refEnd = src.size() - ftrLen;
		}

		if (objPosition > 0) {
			if (objIndexPosition > 0) {
				objEnd = objIndexPosition;
			} else if (logPosition > 0) {
				objEnd = logPosition;
			} else {
				objEnd = src.size() - ftrLen;
			}
		}

		if (logPosition > 0) {
			if (logIndexPosition > 0) {
				logEnd = logIndexPosition;
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
		if (blockSize == -1) {
			blockSize = v & 0xffffff;
		}
		minUpdateIndex = NB.decodeInt64(tmp, 8);
		maxUpdateIndex = NB.decodeInt64(tmp, 16);
		return tmp;
	}

	private void initRefIndex() throws IOException {
		if (refIndexPosition < 0) {
			readFileFooter();
		}
		if (refIndex == null && refIndexPosition > 0) {
			refIndex = readIndex(refIndexPosition);
		}
	}

	private void initObjIndex() throws IOException {
		if (objIndexPosition < 0) {
			readFileFooter();
		}
		if (objIndex == null && objIndexPosition > 0) {
			objIndex = readIndex(objIndexPosition);
		}
	}

	private void initLogIndex() throws IOException {
		if (logIndexPosition < 0) {
			readFileFooter();
		}
		if (logIndex == null && logIndexPosition > 0) {
			logIndex = readIndex(logIndexPosition);
		}
	}

	private BlockReader readIndex(long pos) throws IOException {
		int sz = readBlockLen(pos);
		BlockReader i = new BlockReader();
		i.readBlock(src, pos, sz);
		i.verifyIndex();
		return i;
	}

	private int readBlockLen(long pos) throws IOException {
		int sz = pos == 0 ? FILE_HEADER_LEN + 4 : 4;
		ByteBuffer tmp = src.read(pos, sz);
		if (tmp.position() < sz) {
			throw new IOException(JGitText.get().invalidReftableFile);
		}
		byte[] buf;
		if (tmp.hasArray() && tmp.arrayOffset() == 0) {
			buf = tmp.array();
		} else {
			buf = new byte[sz];
			tmp.flip();
			tmp.get(buf);
		}
		if (pos == 0 && buf[FILE_HEADER_LEN] == FILE_BLOCK_TYPE) {
			return FILE_HEADER_LEN;
		}
		int p = pos == 0 ? FILE_HEADER_LEN : 0;
		return decodeBlockLen(NB.decodeInt32(buf, p));
	}

	private BlockReader readBlock(long pos, long end) throws IOException {
		if (indexCache != null) {
			BlockReader b = indexCache.get(pos);
			if (b != null) {
				return b;
			}
		}

		int sz = blockSize;
		if (sz == 0) {
			sz = readBlockLen(pos);
		} else if (pos + sz > end) {
			sz = (int) (end - pos); // last block may omit padding.
		}

		BlockReader b = new BlockReader();
		b.readBlock(src, pos, sz);
		if (b.type() == INDEX_BLOCK_TYPE && !b.truncated()) {
			if (indexCache == null) {
				indexCache = new LongMap<>();
			}
			indexCache.put(pos, b);
		}
		return b;
	}

	private int blocksIn(long pos, long end) {
		int blocks = (int) ((end - pos) / blockSize);
		return end % blockSize == 0 ? blocks : (blocks + 1);
	}

	/**
	 * Get size of the reftable, in bytes.
	 *
	 * @return size of the reftable, in bytes.
	 * @throws java.io.IOException
	 *             size cannot be obtained.
	 */
	public long size() throws IOException {
		return src.size();
	}

	/** {@inheritDoc} */
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
					long pos = block.endPosition();
					if (pos >= scanEnd) {
						return false;
					}
					block = readBlock(pos, scanEnd);
					continue;
				}

				block.parseKey();
				if (match != null && !block.match(match, prefix)) {
					block.skipValue();
					return false;
				}

				ref = block.readRef(minUpdateIndex);
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
					long pos = block.endPosition();
					if (pos >= scanEnd) {
						return false;
					}
					block = readBlock(pos, scanEnd);
					continue;
				}

				block.parseKey();
				if (match != null && !block.match(match, false)) {
					block.skipValue();
					return false;
				}

				refName = block.name();
				updateIndex = block.readLogUpdateIndex();
				entry = block.readLogEntry();
				if (entry == null && !includeDeletes) {
					continue;
				}
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

	static final LongList EMPTY_LONG_LIST = new LongList(0);

	private class ObjCursorImpl extends RefCursor {
		private final long scanEnd;
		private final ObjectId match;

		private Ref ref;
		private int listIdx;

		private LongList blockPos;
		private BlockReader block;

		ObjCursorImpl(long scanEnd, AnyObjectId id) {
			this.scanEnd = scanEnd;
			this.match = id.copy();
		}

		void initSeek() throws IOException {
			byte[] rawId = new byte[OBJECT_ID_LENGTH];
			match.copyRawTo(rawId, 0);
			byte[] key = Arrays.copyOf(rawId, objIdLen);

			BlockReader b = objIndex;
			do {
				if (b.seekKey(key) > 0) {
					blockPos = EMPTY_LONG_LIST;
					return;
				}
				long pos = b.readPositionFromIndex();
				b = readBlock(pos, objEnd);
			} while (b.type() == INDEX_BLOCK_TYPE);
			b.seekKey(key);
			while (b.next()) {
				b.parseKey();
				if (b.match(key, false)) {
					blockPos = b.readBlockPositionList();
					if (blockPos == null) {
						initScan();
						return;
					}
					break;
				}
				b.skipValue();
			}
			if (blockPos == null) {
				blockPos = EMPTY_LONG_LIST;
			}
			if (blockPos.size() > 0) {
				long pos = blockPos.get(listIdx++);
				block = readBlock(pos, scanEnd);
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
					long pos;
					if (blockPos != null) {
						if (listIdx >= blockPos.size()) {
							return false;
						}
						pos = blockPos.get(listIdx++);
					} else {
						pos = block.endPosition();
					}
					if (pos >= scanEnd) {
						return false;
					}
					block = readBlock(pos, scanEnd);
					continue;
				}

				block.parseKey();
				ref = block.readRef(minUpdateIndex);
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
