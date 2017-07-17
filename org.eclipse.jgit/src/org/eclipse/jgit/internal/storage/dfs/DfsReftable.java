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
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.util.NB;

/** A reftable stored in {@link DfsBlockCache}. */
public class DfsReftable extends BlockBasedFile {
	DfsReftable(DfsBlockCache cache, DfsPackDescription desc) {
		super(cache, desc, REFTABLE);
		length = desc.getFileSize(REFTABLE);
		if (length <= 0) {
			length = -1;
		}
	}

	/**
	 * Open a cursor to read from the reftable.
	 * <p>
	 * The returned cursor is not thread safe.
	 *
	 * @param ctx
	 *            reader to access the DFS storage.
	 * @return cursor to read the table; caller must close.
	 * @throws IOException
	 *             table cannot be opened.
	 */
	public RefCursor open(DfsReader ctx) throws IOException {
		return new ReftableReader(new CacheSource(this, key, cache, ctx));
	}

	private static final class CacheSource extends BlockSource {
		private final DfsReftable file;
		private final DfsStreamKey key;
		private final DfsBlockCache cache;
		private final DfsReader ctx;
		private ReadableChannel ch;

		CacheSource(DfsReftable file, DfsStreamKey key,
				DfsBlockCache cache, DfsReader ctx) {
			this.file = file;
			this.key = key;
			this.cache = cache;
			this.ctx = ctx;
		}

		@Override
		public ByteBuffer read(long pos, int want) throws IOException {
			if (want == 0) {
				return ByteBuffer.allocate(0);
			}

			int bs = file.blockSize;
			DfsBlock block = cache.get(key, pos);
			if (block != null && isAlignedToRequest(block, pos, want)) {
				if (bs == 0 && pos == 0) {
					file.setBlockSize(readBlockSize(block));
				}
				return block.zeroCopyByteBuffer(want);
			}

			if (bs == 0) {
				bs = readBlockSize();
				file.setBlockSize(bs);
			}

			block = cache.getOrLoad(file, pos, ctx, ch);
			if (isAlignedToRequest(block, pos, want)) {
				return block.zeroCopyByteBuffer(want);
			}

			byte[] dst = new byte[want];
			int off = 0;
			for (;;) {
				int r = block.copy(pos, dst, off, want);
				pos += r;
				off += r;
				want -= r;
				if (want == 0) {
					return ByteBuffer.wrap(dst);
				}
				block = cache.getOrLoad(file, pos, ctx, ch);
			}
		}

		private static boolean isAlignedToRequest(DfsBlock b, long p, int n) {
			return b.start == p && b.end >= p + n;
		}

		private int readBlockSize() throws IOException {
			int bs = open().blockSize();
			if (bs <= 0) {
				byte[] tmp = new byte[FILE_HEADER_LEN];
				BlockBasedFile.read(ch, ByteBuffer.wrap(tmp));
				bs = NB.decodeInt32(tmp, 4) & 0xffffff;
			}
			return bs;
		}

		private int readBlockSize(DfsBlock block) {
			byte[] tmp = new byte[4];
			block.copy(4, tmp, 0, 4);
			return NB.decodeInt32(tmp, 0) & 0xffffff;
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
				try {
					open().setReadAheadBytes((int) Math.min(end - start, sz));
				} catch (IOException e) {
					// Ignore failed read-ahead advice.
				}
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
