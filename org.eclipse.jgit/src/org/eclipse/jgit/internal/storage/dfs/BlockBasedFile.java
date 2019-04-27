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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/** Block based file stored in {@link DfsBlockCache}. */
abstract class BlockBasedFile {
	/** Cache that owns this file and its data. */
	final DfsBlockCache cache;

	/** Unique identity of this file while in-memory. */
	final DfsStreamKey key;

	/** Description of the associated pack file's storage. */
	final DfsPackDescription desc;
	final PackExt ext;

	/**
	 * Preferred alignment for loading blocks from the backing file.
	 * <p>
	 * It is initialized to 0 and filled in on the first read made from the
	 * file. Block sizes may be odd, e.g. 4091, caused by the underling DFS
	 * storing 4091 user bytes and 5 bytes block metadata into a lower level
	 * 4096 byte block on disk.
	 */
	volatile int blockSize;

	/**
	 * Total number of bytes in this pack file.
	 * <p>
	 * This field initializes to -1 and gets populated when a block is loaded.
	 */
	volatile long length;

	/** True once corruption has been detected that cannot be worked around. */
	volatile boolean invalid;

	/** Exception that caused the packfile to be flagged as invalid */
	protected volatile Exception invalidatingCause;

	BlockBasedFile(DfsBlockCache cache, DfsPackDescription desc, PackExt ext) {
		this.cache = cache;
		this.key = desc.getStreamKey(ext);
		this.desc = desc;
		this.ext = ext;
	}

	String getFileName() {
		return desc.getFileName(ext);
	}

	boolean invalid() {
		return invalid;
	}

	void setInvalid() {
		invalid = true;
	}

	void setBlockSize(int newSize) {
		blockSize = newSize;
	}

	long alignToBlock(long pos) {
		int size = blockSize;
		if (size == 0)
			size = cache.getBlockSize();
		return (pos / size) * size;
	}

	int blockSize(ReadableChannel rc) {
		// If the block alignment is not yet known, discover it. Prefer the
		// larger size from either the cache or the file itself.
		int size = blockSize;
		if (size == 0) {
			size = rc.blockSize();
			if (size <= 0)
				size = cache.getBlockSize();
			else if (size < cache.getBlockSize())
				size = (cache.getBlockSize() / size) * size;
			blockSize = size;
		}
		return size;
	}

	DfsBlock getOrLoadBlock(long pos, DfsReader ctx) throws IOException {
		try (LazyChannel c = new LazyChannel(ctx, desc, ext)) {
			return cache.getOrLoad(this, pos, ctx, c);
		}
	}

	DfsBlock readOneBlock(long pos, DfsReader ctx, ReadableChannel rc)
			throws IOException {
		if (invalid) {
			throw new PackInvalidException(getFileName(), invalidatingCause);
		}

		ctx.stats.readBlock++;
		long start = System.nanoTime();
		try {
			int size = blockSize(rc);
			pos = (pos / size) * size;

			// If the size of the file is not yet known, try to discover it.
			// Channels may choose to return -1 to indicate they don't
			// know the length yet, in this case read up to the size unit
			// given by the caller, then recheck the length.
			long len = length;
			if (len < 0) {
				len = rc.size();
				if (0 <= len)
					length = len;
			}

			if (0 <= len && len < pos + size)
				size = (int) (len - pos);
			if (size <= 0)
				throw new EOFException(MessageFormat.format(
						DfsText.get().shortReadOfBlock, Long.valueOf(pos),
						getFileName(), Long.valueOf(0), Long.valueOf(0)));

			byte[] buf = new byte[size];
			rc.position(pos);
			int cnt = read(rc, ByteBuffer.wrap(buf, 0, size));
			ctx.stats.readBlockBytes += cnt;
			if (cnt != size) {
				if (0 <= len) {
					throw new EOFException(MessageFormat.format(
							DfsText.get().shortReadOfBlock, Long.valueOf(pos),
							getFileName(), Integer.valueOf(size),
							Integer.valueOf(cnt)));
				}

				// Assume the entire thing was read in a single shot, compact
				// the buffer to only the space required.
				byte[] n = new byte[cnt];
				System.arraycopy(buf, 0, n, 0, n.length);
				buf = n;
			} else if (len < 0) {
				// With no length at the start of the read, the channel should
				// have the length available at the end.
				length = len = rc.size();
			}

			return new DfsBlock(key, pos, buf);
		} finally {
			ctx.stats.readBlockMicros += elapsedMicros(start);
		}
	}

	static int read(ReadableChannel rc, ByteBuffer buf) throws IOException {
		int n;
		do {
			n = rc.read(buf);
		} while (0 < n && buf.hasRemaining());
		return buf.position();
	}

	static long elapsedMicros(long start) {
		return (System.nanoTime() - start) / 1000L;
	}

	/**
	 * A supplier of readable channel that opens the channel lazily.
	 */
	private static class LazyChannel
			implements AutoCloseable, DfsBlockCache.ReadableChannelSupplier {
		private final DfsReader ctx;
		private final DfsPackDescription desc;
		private final PackExt ext;

		private ReadableChannel rc;

		LazyChannel(DfsReader ctx, DfsPackDescription desc, PackExt ext) {
			this.ctx = ctx;
			this.desc = desc;
			this.ext = ext;
		}

		@Override
		public ReadableChannel get() throws IOException {
			if (rc == null) {
				rc = ctx.db.openFile(desc, ext);
			}
			return rc;
		}

		@Override
		public void close() throws IOException {
			if (rc != null) {
				rc.close();
			}
		}
	}
}
