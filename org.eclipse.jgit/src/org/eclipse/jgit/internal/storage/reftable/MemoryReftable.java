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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.io.BlockSource;

class MemoryReftable {
	final int blockSize;
	final List<byte[]> blocks;
	long size;
	byte[] buf;
	int bufPtr;

	MemoryReftable(int blockSize) {
		this.blockSize = blockSize;

		buf = new byte[blockSize];
		blocks = new ArrayList<>();
		blocks.add(buf);
	}

	void checkBuffer() {
		if (bufPtr == blockSize) {
			bufPtr = 0;
			buf = new byte[blockSize];
			blocks.add(buf);
		}
	}

	OutputStream getOutput() {
		return new OutputStream() {
			@Override
			public void write(int b) {
				checkBuffer();
				buf[bufPtr++] = (byte) b;
				size++;
			}

			@Override
			public void write(byte[] src, int p, int n) {
				while (n > 0) {
					checkBuffer();
					int c = Math.min(n, blockSize - bufPtr);
					System.arraycopy(src, p, buf, bufPtr, c);
					bufPtr += c;
					size += c;
					n -= c;
					p += c;
				}
			}
		};
	}

	BlockSource getBlockSource() {
		return new BlockSource() {
			@Override
			public ByteBuffer read(long pos, int sz) {
				if (pos >= size) {
					return ByteBuffer.allocate(0);
				}

				int idx = (int) (pos / blockSize);
				int ptr = (int) (pos - (idx * blockSize));
				byte[] src = blocks.get(idx);
				int len = src == buf ? bufPtr : blockSize;
				if (ptr == 0 && sz <= len) {
					ByteBuffer b = ByteBuffer.wrap(src);
					b.position(Math.min(sz, len));
					return b;
				}

				ByteBuffer b = ByteBuffer.allocate(sz);
				while (pos < size && b.hasRemaining()) {
					idx = (int) (pos / blockSize);
					ptr = (int) (pos - (idx * blockSize));
					src = blocks.get(idx);
					len = src == buf ? bufPtr : blockSize;
					int n = Math.min(b.remaining(), len);
					b.put(src, ptr, n);
					pos += n;
				}
				return b;
			}

			@Override
			public long size() throws IOException {
				return size;
			}

			@Override
			public void close() {
				// Do nothing.
			}
		};
	}
}
