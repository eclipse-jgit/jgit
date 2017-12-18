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

package org.eclipse.jgit.internal.storage.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Provides content blocks of file.
 * <p>
 * {@code BlockSource} implementations must decide if they will be thread-safe,
 * or not.
 */
public abstract class BlockSource implements AutoCloseable {
	/**
	 * Wrap a byte array as a {@code BlockSource}.
	 *
	 * @param content
	 *            input file.
	 * @return block source to read from {@code content}.
	 */
	public static BlockSource from(byte[] content) {
		return new BlockSource() {
			@Override
			public ByteBuffer read(long pos, int cnt) {
				ByteBuffer buf = ByteBuffer.allocate(cnt);
				if (pos < content.length) {
					int p = (int) pos;
					int n = Math.min(cnt, content.length - p);
					buf.put(content, p, n);
				}
				return buf;
			}

			@Override
			public long size() {
				return content.length;
			}

			@Override
			public void close() {
				// Do nothing.
			}
		};
	}

	/**
	 * Read from a {@code FileInputStream}.
	 * <p>
	 * The returned {@code BlockSource} is not thread-safe, as it must seek the
	 * file channel to read a block.
	 *
	 * @param in
	 *            the file. The {@code BlockSource} will close {@code in}.
	 * @return wrapper for {@code in}.
	 */
	public static BlockSource from(FileInputStream in) {
		return from(in.getChannel());
	}

	/**
	 * Read from a {@code FileChannel}.
	 * <p>
	 * The returned {@code BlockSource} is not thread-safe, as it must seek the
	 * file channel to read a block.
	 *
	 * @param ch
	 *            the file. The {@code BlockSource} will close {@code ch}.
	 * @return wrapper for {@code ch}.
	 */
	public static BlockSource from(FileChannel ch) {
		return new BlockSource() {
			@Override
			public ByteBuffer read(long pos, int blockSize) throws IOException {
				ByteBuffer b = ByteBuffer.allocate(blockSize);
				ch.position(pos);
				int n;
				do {
					n = ch.read(b);
				} while (n > 0 && b.position() < blockSize);
				return b;
			}

			@Override
			public long size() throws IOException {
				return ch.size();
			}

			@Override
			public void close() {
				try {
					ch.close();
				} catch (IOException e) {
					// Ignore close failures of read-only files.
				}
			}
		};
	}

	/**
	 * Read a block from the file.
	 * <p>
	 * To reduce copying, the returned ByteBuffer should have an accessible
	 * array and {@code arrayOffset() == 0}. The caller will discard the
	 * ByteBuffer and directly use the backing array.
	 *
	 * @param position
	 *            position of the block in the file, specified in bytes from the
	 *            beginning of the file.
	 * @param blockSize
	 *            size to read.
	 * @return buffer containing the block content.
	 * @throws java.io.IOException
	 *             if block cannot be read.
	 */
	public abstract ByteBuffer read(long position, int blockSize)
			throws IOException;

	/**
	 * Determine the size of the file.
	 *
	 * @return total number of bytes in the file.
	 * @throws java.io.IOException
	 *             if size cannot be obtained.
	 */
	public abstract long size() throws IOException;

	/**
	 * Advise the {@code BlockSource} a sequential scan is starting.
	 *
	 * @param startPos
	 *            starting position.
	 * @param endPos
	 *            ending position.
	 */
	public void adviseSequentialRead(long startPos, long endPos) {
		// Do nothing by default.
	}

	/** {@inheritDoc} */
	@Override
	public abstract void close();
}
