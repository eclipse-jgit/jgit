/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
