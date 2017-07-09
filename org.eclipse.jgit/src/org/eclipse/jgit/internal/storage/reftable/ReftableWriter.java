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

import static java.lang.Math.log;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_MAGIC;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.LogEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.RefEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.NB;

/**
 * Writes a reftable formatted file.
 * <p>
 * A reftable can be written in a streaming fashion, provided the caller sorts
 * all references. A {@link ReftableWriter} is single-use, and not thread-safe.
 */
public class ReftableWriter {
	private int refBlockSize = 4 << 10;
	private int logBlockSize;
	private int restartInterval;

	private ReftableOutputStream out;

	private BlockWriter refIndex;
	private BlockWriter logIndex;
	private BlockWriter cur;

	private String logLastRef = ""; //$NON-NLS-1$
	private long logLastTimeUsec;

	private long logOffset;
	private long refIndexOffset;
	private long logIndexOffset;

	private long refBytes;
	private long logBytes;
	private int refBlocks;
	private int refIndexSize;
	private Stats stats;

	/**
	 * @param szBytes
	 *            desired output block size for references, in bytes.
	 * @return {@code this}
	 */
	public ReftableWriter setRefBlockSize(int szBytes) {
		if (out != null) {
			throw new IllegalStateException();
		} else if (szBytes <= 1024 || szBytes >= (1 << 24)) {
			throw new IllegalArgumentException();
		}
		refBlockSize = szBytes;
		return this;
	}

	/**
	 * @param szBytes
	 *            desired output block size for log entries, in bytes.
	 * @return {@code this}
	 */
	public ReftableWriter setLogBlockSize(int szBytes) {
		if (out != null) {
			throw new IllegalStateException();
		} else if (szBytes <= 1024 || szBytes >= (1 << 24)) {
			throw new IllegalArgumentException();
		}
		logBlockSize = szBytes;
		return this;
	}

	/**
	 * @param interval
	 *            number of references between binary search markers. If
	 *            {@code interval} is 0 (default), the writer will select a
	 *            default value based on the block size.
	 * @return {@code this}
	 */
	public ReftableWriter setRestartInterval(int interval) {
		if (out != null) {
			throw new IllegalStateException();
		} else if (interval < 0) {
			throw new IllegalArgumentException();
		}
		restartInterval = interval;
		return this;
	}

	/**
	 * Begin writing the reftable.
	 * <p>
	 * The provided {@code OutputStream} should be buffered by the caller to
	 * amortize system calls.
	 *
	 * @param os
	 *            stream to write the table to. Caller is responsible for
	 *            closing the stream after invoking {@link #finish()}.
	 * @return {@code this}
	 * @throws IOException
	 *             reftable header cannot be written.
	 */
	public ReftableWriter begin(OutputStream os) throws IOException {
		if (restartInterval <= 0) {
			restartInterval = refBlockSize < (60 << 10) ? 16 : 64;
		}

		int ri = restartInterval;
		refIndex = new BlockWriter(INDEX_BLOCK_TYPE, refBlockSize, ri);
		out = new ReftableOutputStream(os, refBlockSize);
		logLastRef = ""; //$NON-NLS-1$
		logLastTimeUsec = 0;
		writeFileHeader();
		return this;
	}

	/**
	 * Write one reference to the reftable.
	 * <p>
	 * References must be passed in sorted order.
	 *
	 * @param ref
	 *            the reference to store.
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter writeRef(Ref ref) throws IOException {
		return writeEntry(refIndex, new RefEntry(ref));
	}

	/**
	 * Write one reflog entry to the reftable.
	 * <p>
	 * Reflog entries must be written in reference name and descending time
	 * (most recent first) order. If duplicate times are detected by this
	 * method, the time of older records will be adjusted backwards by a few
	 * microseconds to maintain uniqueness.
	 *
	 * @param name
	 *            name of the reference.
	 * @param who
	 *            committer of the reflog entry.
	 * @param oldId
	 *            prior id; pass {@link ObjectId#zeroId()} for creations.
	 * @param newId
	 *            new id; pass {@link ObjectId#zeroId()} for deletions.
	 * @param message
	 *            optional message (may be null).
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter writeLog(String name, PersonIdent who, ObjectId oldId,
			ObjectId newId, @Nullable String message) throws IOException {
		long timeUsec = who.getWhen().getTime() * 1000L + 999L;
		if (logLastRef.equals(name) && timeUsec >= logLastTimeUsec) {
			timeUsec = logLastTimeUsec - 1;
		}
		return writeLog(name, timeUsec, who, oldId, newId, message);
	}

	/**
	 * Write one reflog entry to the reftable.
	 * <p>
	 * Reflog entries must be written in reference name and descending time
	 * (most recent first) order.
	 *
	 * @param name
	 *            name of the reference.
	 * @param timeUsec
	 *            time in microseconds since the epoch of the log event. This
	 *            timestamp must be unique within the scope of {@code name}.
	 * @param who
	 *            committer of the reflog entry.
	 * @param oldId
	 *            prior id; pass {@link ObjectId#zeroId()} for creations.
	 * @param newId
	 *            new id; pass {@link ObjectId#zeroId()} for deletions.
	 * @param message
	 *            optional message (may be null).
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter writeLog(String name, long timeUsec, PersonIdent who,
			ObjectId oldId, ObjectId newId, @Nullable String message)
					throws IOException {
		String msg = message != null ? message : ""; //$NON-NLS-1$
		beginLog();
		logLastRef = name;
		logLastTimeUsec = timeUsec;
		return writeEntry(logIndex,
				new LogEntry(name, timeUsec, who, oldId, newId, msg));
	}

	private void beginLog() throws IOException {
		if (logOffset == 0) {
			finishRef(); // close prior ref blocks and their index, if present.

			if (logBlockSize == 0) {
				logBlockSize = refBlockSize * 2;
			}
			logIndex = new BlockWriter(INDEX_BLOCK_TYPE, logBlockSize,
					restartInterval);
			out.setBlockSize(logBlockSize);
			logOffset = out.size();
		}
	}

	private ReftableWriter writeEntry(
			BlockWriter idx,
			BlockWriter.Entry entry)
			throws IOException {
		if (cur == null) {
			beginBlock(entry);
		} else if (!cur.tryAdd(entry)) {
			idx.addIndex(cur.lastKey(), out.size());
			cur.writeTo(out);
			if (entry instanceof RefEntry) {
				out.padBetweenBlocksToNextBlock();
			}
			beginBlock(entry);
		}
		return this;
	}

	private void beginBlock(BlockWriter.Entry entry)
			throws BlockSizeTooSmallException {
		byte type = entry.blockType();
		int bs = out.bytesAvailableInBlock();
		cur = new BlockWriter(type, bs, restartInterval);
		cur.addFirst(entry);
	}

	/**
	 * Finish writing the reftable by writing its trailer.
	 *
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter finish() throws IOException {
		finishRef();
		finishLog();
		writeFileFooter();

		stats = new Stats(this, out, refIndex);
		refIndex = null;
		logIndex = null;
		cur = null;
		out = null;
		return this;
	}

	private void finishRef() throws IOException {
		if (logOffset == 0 && cur != null) {
			refBlocks = out.blockCount() + 1;
			refIndexOffset = finishBlockMaybeWriteIndex(refIndex);
			if (refIndexOffset > 0) {
				refIndexSize = (int) (out.size() - refIndexOffset);
			}
			refBytes = out.size();
		}
	}

	private void finishLog() throws IOException {
		if (logOffset > 0 && cur != null) {
			logIndexOffset = finishBlockMaybeWriteIndex(logIndex);
			logBytes = out.size() - refBytes;
		}
	}

	private long finishBlockMaybeWriteIndex(BlockWriter idx)
			throws IOException {
		idx.addIndex(cur.lastKey(), out.size());
		cur.writeTo(out);
		cur = null;

		int mustHaveIndex = idx == refIndex ? 4 : 1;
		if (idx.entryCount() > mustHaveIndex) {
			if (idx == refIndex) { // only pad before the ref_index
				out.padBetweenBlocksToNextBlock();
			}
			long offset = out.size();
			idx.writeTo(out);
			return offset;
		} else {
			return 0;
		}
	}

	private void writeFileHeader() throws IOException {
		byte[] hdr = new byte[FILE_HEADER_LEN];
		System.arraycopy(FILE_HEADER_MAGIC, 0, hdr, 0, 4);
		NB.encodeInt32(hdr, 4, (VERSION_1 << 24) | refBlockSize);
		out.write(hdr);
	}

	private void writeFileFooter() throws IOException {
		int ftrLen = FILE_FOOTER_LEN;
		byte[] ftr = new byte[ftrLen];
		System.arraycopy(FILE_HEADER_MAGIC, 0, ftr, 0, 4);
		NB.encodeInt32(ftr, 4, (VERSION_1 << 24) | refBlockSize);

		NB.encodeInt64(ftr, 8, refIndexOffset);
		NB.encodeInt64(ftr, 16, logOffset);
		NB.encodeInt64(ftr, 24, logIndexOffset);

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, ftrLen - 4);
		NB.encodeInt32(ftr, ftrLen - 4, (int) crc.getValue());

		out.write(ftr);
		out.finishFile();
	}

	/** @return statistics of the last written reftable. */
	public Stats getStats() {
		return stats;
	}

	/** Statistics about a written reftable. */
	public static class Stats {
		private final int refBlockSize;
		private final int logBlockSize;
		private final int restartInterval;

		private final long refBytes;
		private final long logBytes;
		private final long paddingUsed;
		private final long totalBytes;
		private final int refBlocks;

		private final int refIndexKeys;
		private final int refIndexSize;

		Stats(ReftableWriter w, ReftableOutputStream o, BlockWriter refIdx) {
			refBlockSize = w.refBlockSize;
			logBlockSize = w.logBlockSize;
			restartInterval = w.restartInterval;

			refBytes = w.refBytes;
			logBytes = w.logBytes;
			paddingUsed = o.paddingUsed();
			totalBytes = o.size();
			refBlocks = w.refBlocks;

			refIndexKeys = w.refIndexOffset > 0 ? refIdx.entryCount() : 0;
			refIndexSize = w.refIndexSize;
		}

		/** @return number of bytes in a ref block. */
		public int refBlockSize() {
			return refBlockSize;
		}

		/** @return number of bytes in a ref block. */
		public int logBlockSize() {
			return logBlockSize;
		}

		/** @return number of references between binary search markers. */
		public int restartInterval() {
			return restartInterval;
		}

		/** @return number of ref blocks in the output, excluding index. */
		public int refBlockCount() {
			return refBlocks;
		}

		/** @return number of bytes for references, including ref index. */
		public long refBytes() {
			return refBytes;
		}

		/** @return number of bytes for log, including log index. */
		public long logBytes() {
			return logBytes;
		}

		/** @return total number of bytes in the reftable. */
		public long totalBytes() {
			return totalBytes;
		}

		/** @return bytes of padding used to maintain block alignment. */
		public long paddingBytes() {
			return paddingUsed;
		}

		/** @return number of keys in the ref index; 0 if no index was used. */
		public int refIndexKeys() {
			return refIndexKeys;
		}

		/** @return number of bytes in the ref index; 0 if no index was used. */
		public int refIndexSize() {
			return refIndexSize;
		}

		/** @return estimated number of disk seeks per ref read. */
		public double diskSeeksPerRead() {
			if (refIndexKeys() > 0) {
				return 1;
			}
			return log(refBlockCount()) / log(2);
		}
	}
}
