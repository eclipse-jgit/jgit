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

import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;

/**
 * A reftable stored in {@link org.eclipse.jgit.internal.storage.dfs.DfsBlockCache}.
 */
public class DfsReftable extends BlockBasedFile {
	/**
	 * Construct a reader for an existing reftable.
	 *
	 * @param desc
	 *            description of the reftable within the DFS.
	 */
	public DfsReftable(DfsPackDescription desc) {
		this(DfsBlockCache.getInstance(), desc);
	}

	/**
	 * Construct a reader for an existing reftable.
	 *
	 * @param cache
	 *            cache that will store the reftable data.
	 * @param desc
	 *            description of the reftable within the DFS.
	 */
	public DfsReftable(DfsBlockCache cache, DfsPackDescription desc) {
		super(cache, desc, REFTABLE);

		int bs = desc.getBlockSize(REFTABLE);
		if (bs > 0) {
			setBlockSize(bs);
		}

		long sz = desc.getFileSize(REFTABLE);
		length = sz > 0 ? sz : -1;
	}

	/**
	 * Get description that was originally used to configure this file.
	 *
	 * @return description that was originally used to configure this file.
	 */
	public DfsPackDescription getPackDescription() {
		return desc;
	}

	/**
	 * Open reader on the reftable.
	 * <p>
	 * The returned reader is not thread safe.
	 *
	 * @param ctx
	 *            reader to access the DFS storage.
	 * @return cursor to read the table; caller must close.
	 * @throws java.io.IOException
	 *             table cannot be opened.
	 */
	public ReftableReader open(DfsReader ctx) throws IOException {
		return new ReftableReader(new CacheSource(this, cache, ctx));
	}

	private static final class CacheSource extends BlockSource {
		private final DfsReftable file;
		private final DfsBlockCache cache;
		private final DfsReader ctx;
		private ReadableChannel ch;
		private int readAhead;

		CacheSource(DfsReftable file, DfsBlockCache cache, DfsReader ctx) {
			this.file = file;
			this.cache = cache;
			this.ctx = ctx;
		}

		@Override
		public ByteBuffer read(long pos, int cnt) throws IOException {
			if (ch == null && readAhead > 0 && notInCache(pos)) {
				open().setReadAheadBytes(readAhead);
			}

			DfsBlock block = cache.getOrLoad(file, pos, ctx, () -> open());
			if (block.start == pos && block.size() >= cnt) {
				return block.zeroCopyByteBuffer(cnt);
			}

			byte[] dst = new byte[cnt];
			ByteBuffer buf = ByteBuffer.wrap(dst);
			buf.position(ctx.copy(file, pos, dst, 0, cnt));
			return buf;
		}

		private boolean notInCache(long pos) {
			return cache.get(file.key, file.alignToBlock(pos)) == null;
		}

		@Override
		public long size() throws IOException {
			long n = file.length;
			if (n < 0) {
				n = open().size();
				file.length = n;
			}
			return n;
		}

		@Override
		public void adviseSequentialRead(long start, long end) {
			int sz = ctx.getOptions().getStreamPackBufferSize();
			if (sz > 0) {
				readAhead = (int) Math.min(sz, end - start);
			}
		}

		private ReadableChannel open() throws IOException {
			if (ch == null) {
				ch = ctx.db.openFile(file.desc, file.ext);
			}
			return ch;
		}

		@Override
		public void close() {
			if (ch != null) {
				try {
					ch.close();
				} catch (IOException e) {
					// Ignore read close failures.
				} finally {
					ch = null;
				}
			}
		}
	}
}
