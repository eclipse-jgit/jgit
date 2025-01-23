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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;

import org.eclipse.jgit.lib.internal.WorkQueue;

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
	 * Read from a {@code File}.
	 * <p>
	 * The returned {@code BlockSource} is thread-safe. It's read the whole file
	 * when necessary and safe the content as SoftReference. So the content is
	 * cleanup, when the heap memory gets low.
	 *
	 * @since 7.2
	 * @param file
	 *            the file. (@code BlockSource) read the content in first call
	 *            of read
	 * @return wrapper for {@code file}.
	 */
	public static BlockSource from(File file) {
		return new BlockSource() {

			final long BLOCK_SIZE = 4096;

			private FileInputStream fis = null;
			private FileChannel ch = null;
			private List<SoftReference<ByteBuffer>> blocks;

			private Future<?> backgroundReader;
			private ConcurrentLinkedDeque<Integer> stack = new ConcurrentLinkedDeque<>();

			private Object blocker = new Object();

			{
				int blockcount = blockIndexOfByte(file.length());
				blocks = new ArrayList<>();
				while (blockcount >= 0) {
					blocks.add(new SoftReference<>(null));
					blockcount--;
				}

			}

			@Override
			public long size() throws IOException {
				return file.length();
			}

			@Override
			public ByteBuffer read(long position, int blockSize)
					throws IOException {
				int beginBlock = blockIndexOfByte(position);
				int endBlock = blockIndexOfByte(position + blockSize);

				ensureBlocksReaded(beginBlock, endBlock);

				ByteBuffer b = ByteBuffer.allocate(blockSize);

				int currentblock = beginBlock;
				while (blockSize > 0) {
					long beginOfBlock = currentblock * BLOCK_SIZE;
					long readbegin = position - beginOfBlock;
					long readlength = Math.min(blockSize, BLOCK_SIZE - readbegin);
					byte[] blockbuffer = blocks.get(currentblock).get().array();

					b.put(blockbuffer, (int) readbegin, (int) readlength);
					blockSize -= readlength;
					currentblock++;
				}

				return b;
			}

			private void ensureBlocksReaded(int beginBlock, int endBlock)
					throws IOException {
				for (int i = beginBlock; i <= endBlock; i++) {
					if (blocks.get(i).get() == null) {
						readBlockImmedatly(i);
					}
				}

				if (backgroundReader != null) {
					return;
				}
				boolean needread = false;
				for (SoftReference<ByteBuffer> ref : blocks) {
					if (ref.get() == null) {
						needread = true;
						break;
					}
				}
				if (!needread) {
					closeFileInputStream();
					return;
				}
				// The backgroundReader reads all Blocks sequentiell, looks
				// in the stack, if blocks are needed
				backgroundReader = WorkQueue.getExecutor().submit(() -> {
					int i = 0;

					while (i < blocks.size()) {
						int readblock = i;
						if (!stack.isEmpty()) {
							readblock = stack.pollFirst();
						} else {
							i++;
						}
						if (blocks.get(readblock).get() == null) {
							try {
								readBlock(readblock);
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						}
						synchronized (blocker) {
							blocker.notify();
						}
					}
					closeFileInputStream();
					backgroundReader = null;
				});
			}

			private void readBlockImmedatly(int block) throws IOException {
				if (ch == null) {
					fis = new FileInputStream(file);
					ch = fis.getChannel();
				}

				if (backgroundReader == null) {
					readBlock(block);
				} else {
					stack.push(block);
					try {
						while (blocks.get(block).get() == null) {
							synchronized (blocker) {
								blocker.wait();
							}
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new RuntimeException(e);
					}
				}
			}

			private void readBlock(int block) throws IOException {
				ch.position(block * BLOCK_SIZE);

				ByteBuffer b = ByteBuffer.allocate((int) BLOCK_SIZE);
				int n;
				do {
					n = ch.read(b);
				} while (n > 0 && b.position() < BLOCK_SIZE);
				blocks.set(block, new SoftReference<>(b));
			}

			public int blockIndexOfByte(final long position) {
				return (int) (position / BLOCK_SIZE);
			}

			private void closeFileInputStream() {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						// Ignore Error on Closing FileInputStream
					}
					fis = null;
					ch = null;
				}
			}

			@Override
			public void close() {
				blocks.clear();
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

	@Override
	public abstract void close();
}
