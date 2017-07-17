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
	DfsReftable(DfsBlockCache cache, DfsStreamKey key,
			DfsPackDescription desc) {
		super(cache, key, desc, REFTABLE);
		length = desc.getFileSize(REFTABLE);
		if (length <= 0) {
			length = 1;
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
		return new ReftableReader(new CacheSource(ctx));
	}

	private final class CacheSource extends BlockSource {
		private final DfsReader ctx;
		private ReadableChannel ch;

		CacheSource(DfsReader ctx) {
			this.ctx = ctx;
		}

		@Override
		public ByteBuffer read(long pos, int bs) throws IOException {
			DfsBlock block = cache.get(key, pos);
			if (block != null && isAlignedToRead(block, pos, bs)) {
				return zeroCopyBlock(block, bs);
			}

			if (pos == 0 && bs == FILE_HEADER_LEN) {
				bs = readBlockSize(pos, bs);
				blockSize = bs;
			}

			block = cache.getOrLoad(DfsReftable.this, pos, bs, ctx, ch);
			if (isAlignedToRead(block, pos, bs)) {
				return zeroCopyBlock(block, bs);
			}

			byte[] dst = new byte[bs];
			int n = 0;
			while (n < bs) {
				n += block.copy(pos, dst, n, bs - n);
				block = cache.getOrLoad(DfsReftable.this, pos + n, bs, ctx, ch);
			}
			return ByteBuffer.wrap(dst);
		}

		private boolean isAlignedToRead(DfsBlock block, long pos, int bs) {
			return block.start == pos && block.end >= pos + bs;
		}

		private ByteBuffer zeroCopyBlock(DfsBlock block, int bs) {
			ByteBuffer b = block.asByteBuffer();
			b.limit(bs);
			return b;
		}

		private int readBlockSize(long pos, int bs) throws IOException {
			byte[] tmp = new byte[bs];
			ByteBuffer b = ByteBuffer.wrap(tmp);
			open();
			ch.position(pos);
			int n;
			do {
				n = ch.read(b);
			} while (n > 0 && b.position() < bs);
			return NB.decodeInt32(tmp, 8) & 0xffffff;
		}

		@Override
		public long size() throws IOException {
			if (length < 0) {
				length = open().size();
			}
			return length;
		}

		private ReadableChannel open() throws IOException {
			if (ch == null) {
				ch = ctx.db.openFile(packDesc, ext);
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
