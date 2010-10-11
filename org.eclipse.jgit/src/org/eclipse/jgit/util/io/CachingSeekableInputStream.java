/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.util.io;

import java.io.IOException;

import org.eclipse.jgit.storage.pack.DeltaStream;
import org.eclipse.jgit.util.IO;

/**
 * Caches frequently accessed blocks of a {@link SeekableInputStream}.
 *
 * This stream is useful as input to a {@link DeltaStream}, where it can be
 * common for certain sections of a base to be used multiple times.
 */
public class CachingSeekableInputStream extends SeekableInputStream {
	private final SeekableInputStream in;

	private final int blockSize;

	private final LruCache<byte[]> cache;

	private long sz = -1;

	private long ptr;

	/**
	 * Wrap a stream to cache frequently accessed blocks.
	 *
	 * @param in
	 *            the input stream.
	 * @param blockSize
	 *            size of blocks to read from {@code in}.
	 * @param cacheSize
	 *            total size of the cache. The total number of blocks stored
	 *            will be {@code cacheSize / blockSize}.
	 */
	public CachingSeekableInputStream(SeekableInputStream in, int blockSize,
			int cacheSize) {
		this.in = in;
		this.blockSize = blockSize;
		this.cache = new LruCache<byte[]>(cacheSize / blockSize);
	}

	@Override
	public void seek(long offset) throws IOException {
		ptr = offset;
	}

	@Override
	public long size() throws IOException {
		if (sz < 0)
			sz = in.size();
		return sz;
	}

	@Override
	public long position() {
		return ptr;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0)
			return 0;
		if (ptr == size())
			return -1;

		int r = 0;
		while (0 < len) {
			final long blockId = ptr / blockSize;
			final long blockStart = blockId * blockSize;
			final int p = (int) (ptr - blockStart);

			byte[] block = cache.get(blockId);
			if (block == null) {
				in.seek(blockStart);

				if (p == 0 && blockSize <= len) {
					// If the caller needs a whole block, bypass the cache
					// and load the block. They probably won't need that
					// particular large unit again.
					//
					final int n = blockSize;
					IO.readFully(in, b, off, n);
					off += n;
					len -= n;
					r += n;
					ptr += n;
					continue;
				}

				block = new byte[(int) Math.min(blockSize, size() - blockStart)];
				IO.readFully(in, block, 0, block.length);
				cache.put(blockId, block);
			}

			final int n = Math.min(len, block.length - p);
			System.arraycopy(block, p, b, off, n);
			off += n;
			len -= n;
			r += n;
			ptr += n;
		}
		return r;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
