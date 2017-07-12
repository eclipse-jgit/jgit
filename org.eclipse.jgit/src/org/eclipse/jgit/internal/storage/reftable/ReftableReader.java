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
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.isFileHeaderMagic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.LogEntry;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.util.NB;

/**
 * Reads a reftable formatted file.
 * <p>
 * {@code ReftableReader} is not thread-safe. Concurrent readers need their own
 * instance to read from the same file.
 */
public class ReftableReader implements AutoCloseable {
	private final BlockSource src;

	private int blockSize;
	private long refEnd;

	private long logOffset;
	private long logEnd;

	private long refIndexOffset = -1;
	private long logIndexOffset = -1;

	private BlockReader refIndex;
	private BlockReader logIndex;
	private BlockReader block;

	private byte blockType;
	private long scanEnd;
	private boolean includeDeletes;

	private byte[] match;
	private Ref ref;
	private ReflogEntry log;

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
	 * @param deletes
	 *            if {@code true} deleted references will be returned. If
	 *            {@code false} (default behavior), deleted references will be
	 *            skipped, and not returned.
	 */
	public void setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
	}

	/**
	 * Seek to the first reference in the file, to iterate in order.
	 *
	 * @throws IOException
	 *             reftable cannot be read.
	 */
	public void seekToFirstRef() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}
		long end = refEnd > 0 ? refEnd : (src.size() - FILE_FOOTER_LEN);
		initScan(REF_BLOCK_TYPE, end);
		src.adviseSequentialRead(0, end);
		block = readBlock(0);
	}

	/**
	 * Seek either to a reference, or a reference subtree.
	 * <p>
	 * If {@code refName} ends with {@code "/"} the method will seek to the
	 * subtree of all references starting with {@code refName} as a prefix.
	 * <p>
	 * Otherwise, only {@code refName} will be found, if present.
	 *
	 * @param refName
	 *            reference name or subtree to find.
	 * @throws IOException
	 *             reftable cannot be read.
	 */
	public void seek(String refName) throws IOException {
		byte[] rn = refName.getBytes(UTF_8);
		byte[] key = rn;
		if (key[key.length - 1] == '/') {
			key = Arrays.copyOf(key, key.length + 1);
			key[key.length - 1] = '\1';
		}

		initRefIndex();
		initScan(REF_BLOCK_TYPE, refEnd);
		match = rn;
		seek(key, refIndex, 0, refEnd);
	}

	/**
	 * Seek reader to read log records.
	 *
	 * @throws IOException
	 *             reftable cannot be read.
	 */
	public void seekToFirstLog() throws IOException {
		initLogIndex();
		initScan(LOG_BLOCK_TYPE, logEnd);
		if (logOffset > 0) {
			src.adviseSequentialRead(logOffset, logEnd);
			block = readBlock(logOffset);
		}
	}

	/**
	 * Seek to a timestamp in a reference's log.
	 *
	 * @param refName
	 *            exact name of the reference whose log to read.
	 * @param time
	 *            time in seconds since the epoch to scan from. Records at this
	 *            time and older will be returned.
	 * @throws IOException
	 *             reftable cannot be read.
	 */
	public void seekLog(String refName, int time) throws IOException {
		byte[] key = LogEntry.key(refName, time);

		initLogIndex();
		initScan(LOG_BLOCK_TYPE, logEnd);
		match = refName.getBytes(UTF_8);
		if (logOffset > 0) {
			seek(key, logIndex, logOffset, logEnd);
		}
	}

	private void initScan(byte bt, long end) {
		blockType = bt;
		scanEnd = end;
		block = null;
		match = null;
		ref = null;
		log = null;
	}

	private void seek(byte[] key, BlockReader idx, long start, long end)
			throws IOException {
		if (idx != null) {
			long blockOffset = idx.seek(key) <= 0
					? idx.readIndex()
					: ((blocksIn(start, end) - 1) * blockSize);
			block = readBlock(blockOffset);
			block.seek(key);
		}
		binarySearch(key, start, end);
	}

	private void binarySearch(byte[] name, long startPos, long endPos)
			throws IOException {
		int low = (int) (startPos / blockSize);
		int end = blocksIn(startPos, endPos);
		do {
			int mid = (low + end) >>> 1;
			block = readBlock(((long) mid) * blockSize);
			if (blockType != block.type()) {
				break;
			}
			int cmp = block.seek(name);
			if (cmp < 0) {
				end = mid;
			} else if (cmp == 0) {
				break;
			} else /* if (cmp > 0) */ {
				low = mid + 1;
			}
		} while (low < end);
	}

	/**
	 * Check if another reference is available.
	 *
	 * @return {@code true} if there is another reference.
	 * @throws IOException
	 *             reftable cannot be read.
	 */
	public boolean next() throws IOException {
		for (;;) {
			if (block == null || blockType != block.type()) {
				return done();
			} else if (!block.next()) {
				long p = block.blockEndPosition();
				if (p >= scanEnd) {
					return done();
				}
				block = readBlock(p);
				continue;
			}

			block.parseEntryName();
			if (match != null && !block.checkNameMatches(match)) {
				block.skipValue();
				return done();
			} else if (blockType == REF_BLOCK_TYPE) {
				ref = block.readRef();
				if (!includeDeletes && wasDeleted()) {
					continue;
				}
			} else if (blockType == LOG_BLOCK_TYPE) {
				log = block.readLog();
			}
			return true;
		}
	}

	private boolean done() {
		block = null;
		ref = null;
		log = null;
		return false;
	}

	/** @return {@code true} if the current reference was deleted. */
	public boolean wasDeleted() {
		Ref r = getRef();
		return r.getStorage() == Ref.Storage.NEW && r.getObjectId() == null;
	}

	/** @return reference at the current position. */
	public Ref getRef() {
		return ref;
	}

	/** @return name of the current reference. */
	public String getRefName() {
		return ref != null ? ref.getName() : block.name();
	}

	/** @return current log entry. */
	public ReflogEntry getReflogEntry() {
		return log;
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

		refIndexOffset = NB.decodeInt64(ftr, 8);
		logOffset = NB.decodeInt64(ftr, 16);
		logIndexOffset = NB.decodeInt64(ftr, 24);

		if (refIndexOffset > 0) {
			refEnd = refIndexOffset;
		} else if (logOffset > 0) {
			refEnd = logOffset;
		} else {
			refEnd = src.size() - ftrLen;
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
		return tmp;
	}

	private void initRefIndex() throws IOException {
		if (blockSize == 0 || refIndexOffset < 0) {
			readFileFooter();
		}
		if (refIndex == null && refIndexOffset > 0) {
			long guessedEnd = logOffset > 0
					? logOffset
					: (src.size() - FILE_FOOTER_LEN);
			int guessedSize = (int) (guessedEnd - refIndexOffset);

			refIndex = new BlockReader();
			refIndex.readFrom(src, refIndexOffset, guessedSize);
			refIndex.verifyIndex();
		}
	}

	private void initLogIndex() throws IOException {
		if (blockSize == 0 || logIndexOffset < 0) {
			readFileFooter();
		}
		if (logIndex == null && logIndexOffset > 0) {
			long guessedEnd = (src.size() - FILE_FOOTER_LEN);
			int guessedSize = (int) (guessedEnd - logIndexOffset);

			logIndex = new BlockReader();
			logIndex.readFrom(src, logIndexOffset, guessedSize);
			logIndex.verifyIndex();
		}
	}

	private BlockReader readBlock(long position) throws IOException {
		long end = scanEnd;
		int sz = blockSize;
		if (position + sz > end) {
			sz = (int) (end - position); // last block may omit padding.
		}

		BlockReader b = new BlockReader();
		b.readFrom(src, position, sz);
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
}
