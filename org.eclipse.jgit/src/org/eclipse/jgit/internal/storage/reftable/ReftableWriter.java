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
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.NB;

/**
 * Writes a reftable formatted file.
 * <p>
 * A reftable can be written in a streaming fashion, provided the caller sorts
 * all references. A {@link ReftableWriter} is single-use, and not thread-safe.
 */
public class ReftableWriter {
	private int restartInterval;
	private int blockSize = 4 << 10;

	private ReftableOutputStream out;
	private IndexBlockWriter idx;
	private RefBlockWriter cur;
	private Stats stats;

	/**
	 * @param szBytes
	 *            desired output block size in bytes.
	 * @return {@code this}
	 */
	public ReftableWriter setBlockSize(int szBytes) {
		if (out != null) {
			throw new IllegalStateException();
		} else if (szBytes <= 1024 || szBytes >= (1 << 24)) {
			throw new IllegalArgumentException();
		}
		blockSize = szBytes;
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

	/** @return statistics of the last written reftable. */
	public Stats getStats() {
		return stats;
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
			restartInterval = blockSize < (60 << 10) ? 16 : 64;
		}

		idx = new IndexBlockWriter(restartInterval);
		out = new ReftableOutputStream(os, blockSize);
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
	public ReftableWriter write(Ref ref) throws IOException {
		if (cur == null) {
			int bs = out.bytesAvailableInBlock();
			cur = new RefBlockWriter(bs, restartInterval, ref);
		} else if (!cur.tryAdd(ref)) {
			idx.addBlock(cur);
			cur.writeTo(out, true);

			int bs = out.bytesAvailableInBlock();
			cur = new RefBlockWriter(bs, restartInterval, ref);
		}
		return this;
	}

	/**
	 * Finish writing the reftable by writing its trailer.
	 *
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter finish() throws IOException {
		if (cur != null) {
			idx.addBlock(cur);
			boolean useIdx = idx.shouldWriteIndex();
			cur.writeTo(out, useIdx);
			if (useIdx) {
				idx.writeTo(out);
			}
		}
		writeFileFooter();

		stats = new Stats(this, out, idx);
		idx = null;
		cur = null;
		out = null;
		return this;
	}

	private void writeFileHeader() throws IOException {
		byte[] hdr = new byte[FILE_HEADER_LEN];
		System.arraycopy(FILE_HEADER_MAGIC, 0, hdr, 0, 4);
		NB.encodeInt32(hdr, 4, (VERSION_1 << 24) | blockSize);
		out.write(hdr);
	}

	private void writeFileFooter() throws IOException {
		byte[] ftr = new byte[FILE_FOOTER_LEN];
		System.arraycopy(FILE_HEADER_MAGIC, 0, ftr, 0, 4);
		NB.encodeInt32(ftr, 4, (VERSION_1 << 24) | blockSize);
		NB.encodeInt32(ftr, 8, idx.bytesInIndex());

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, 12);
		NB.encodeInt32(ftr, 12, (int) crc.getValue());

		out.write(ftr);
	}

	/** Statistics about a written reftable. */
	public static class Stats {
		private final int blockSize;
		private final int restartInterval;
		private final long totalBytes;
		private final int blockCount;
		private final long paddingUsed;
		private final int indexKeys;
		private final int indexSize;

		Stats(ReftableWriter w, ReftableOutputStream o, IndexBlockWriter idx) {
			blockSize = w.blockSize;
			restartInterval = w.restartInterval;
			totalBytes = o.size();
			blockCount = o.blockCount();
			paddingUsed = o.paddingUsed();
			indexKeys = idx.bytesInIndex() > 0 ? idx.keysInIndex() : 0;
			indexSize = idx.bytesInIndex();
		}

		/** @return number of bytes in a single block. */
		public int blockSize() {
			return blockSize;
		}

		/** @return number of references between binary search markers. */
		public int restartInterval() {
			return restartInterval;
		}

		/** @return total number of bytes in the reftable. */
		public long totalBytes() {
			return totalBytes;
		}

		/** @return bytes of padding used to maintain block alignment. */
		public long paddingBytes() {
			return paddingUsed;
		}

		/** @return number of blocks in the output stream, excluding index. */
		public int blockCount() {
			return blockCount;
		}

		/** @return number of keys in the index; 0 if no index was used. */
		public int indexKeys() {
			return indexKeys;
		}

		/** @return number of bytes in the index; 0 if no index was used. */
		public int indexSize() {
			return indexSize;
		}

		/** @return estimated number of disk seeks per read. */
		public double diskSeeksPerRead() {
			if (indexKeys > 0) {
				return 1;
			}
			return log(blockCount) / log(2);
		}
	}
}
