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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.isFileHeaderMagic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.NB;

/**
 * Reads a reftable formatted file.
 * <p>
 * {@code ReftableReader} is not thread-safe. Concurrent readers need their own
 * instance to read from the same file.
 */
public class ReftableReader implements AutoCloseable {
	private final BlockSource src;

	/** Size of the reftable, minus its footer block. */
	private long size;
	private int blockSize;
	private int indexSize;
	private boolean shouldReadFooter = true;

	private BlockReader index;
	private BlockReader block;
	private String seeking;
	private Ref ref;

	/**
	 * Initialize a new reftable reader.
	 *
	 * @param src
	 *            the file content to read.
	 */
	public ReftableReader(BlockSource src) {
		this.src = src;
	}

	/**
	 * Manually specify the block size used by {@link ReftableWriter}.
	 * <p>
	 * If not known, the block size will be automatically discovered from the
	 * reftable by reading and verifying the file trailer. Manually setting the
	 * block size may allow the reader to avoid reading the file footer.
	 *
	 * @param bs
	 *            writer's block size in bytes.
	 * @return {@code this}
	 */
	public ReftableReader setBlockSize(int bs) {
		this.blockSize = bs;
		return this;
	}

	/**
	 * Manually specify the index size created by {@link ReftableWriter}.
	 * <p>
	 * If not known, the index size will be automatically discovered from the
	 * reftable by reading and verifying the file trailer. Manually setting the
	 * index size may allow the reader to avoid reading the file footer.
	 *
	 * @param sz
	 *            index size in bytes.
	 * @return {@code this}
	 */
	public ReftableReader setIndexSize(int sz) {
		this.indexSize = sz;
		this.shouldReadFooter = false;
		return this;
	}

	/**
	 * Seek to the first reference in the file, to iterate in order.
	 *
	 * @return {@code this}
	 * @throws IOException
	 *             cannot read the reftree file.
	 */
	public ReftableReader seekToFirstRef() throws IOException {
		if (blockSize == 0) {
			readFileHeader();
		}
		initFileSize();

		src.adviseSequentialRead(0, size - indexSize);
		block = readBlock(0);
		seeking = null;
		ref = null;
		return this;
	}

	/**
	 * Seek either to a reference, or a reference subtree.
	 * <p>
	 * If {@code name} ends with {@code "/"} the method will seek to the subtree
	 * of all references starting with {@code name} as a prefix.
	 * <p>
	 * Otherwise, only {@code name} will be found, if present.
	 *
	 * @param name
	 *            reference name or subtree to find.
	 * @return {@code this}
	 * @throws IOException
	 *             cannot read the reftree file.
	 */
	public ReftableReader seek(String name) throws IOException {
		byte[] nameBin = name.getBytes(UTF_8);
		if (nameBin[nameBin.length - 1] == '/') {
			nameBin = Arrays.copyOf(nameBin, nameBin.length + 1);
			nameBin[nameBin.length - 1] = '\1';
		}

		initFileSize();
		if (blockSize == 0) {
			readFileFooter();
		}
		initIndex();

		seeking = name;
		ref = null;

		if (index == null) {
			return search(nameBin, 0, blocks());
		}

		int blockIdx = index.seek(nameBin) <= 0
				? index.readIndexBlock()
				: blocks() - 1;
		block = readBlock(((long) blockIdx) * blockSize);
		block.seek(nameBin);
		return this;
	}

	private ReftableReader search(byte[] name, int low, int end)
			throws IOException {
		do {
			int mid = (low + end) >>> 1;
			block = readBlock(((long) mid) * blockSize);
			if (!block.isRefBlock()) {
				break;
			}
			int cmp = block.seek(name);
			if (cmp < 0) {
				end = mid;
			} else if (cmp == 0) {
				break;
			} else /* if (cmp > 0) */ {
				low = mid + 1;
			}
		} while (low < end);
		return this;
	}

	/**
	 * Check if another reference is available.
	 *
	 * @return {@code true} if there is another reference.
	 * @throws IOException
	 *             reftable file cannot be read.
	 */
	public boolean next() throws IOException {
		if (!block.isRefBlock()) {
			return false;
		} else if (block.next()) {
			return tryNext();
		}

		long p = block.position() + blockSize;
		if (p < (size - indexSize)) {
			block = readBlock(p);
			if (block.isRefBlock() && block.next()) {
				return tryNext();
			}
		}
		ref = null;
		return false;
	}

	private boolean tryNext() throws IOException {
		ref = block.readRef();
		if (seeking != null) {
			if (seeking.endsWith("/")) { //$NON-NLS-1$
				return ref.getName().startsWith(seeking);
			} else {
				return ref.getName().equals(seeking);
			}
		}
		return true;
	}

	/** @return reference at the current position. */
	public Ref getRef() {
		return ref;
	}

	private void readFileHeader() throws IOException {
		byte[] hdr = readHeaderOrFooter(0, FILE_HEADER_LEN);
		blockSize = NB.decodeInt32(hdr, 4) & 0xffffff;
	}

	private void readFileFooter() throws IOException {
		int ftrLen = FILE_FOOTER_LEN;
		byte[] ftr = readHeaderOrFooter(size, ftrLen);

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, ftrLen - 4);
		if (crc.getValue() != NB.decodeUInt32(ftr, ftrLen - 4)) {
			throw new IOException(JGitText.get().invalidReftableCRC);
		}

		blockSize = NB.decodeInt32(ftr, 4) & 0xffffff;
		indexSize = NB.decodeInt32(ftr, 8);
		shouldReadFooter = false;
	}

	private byte[] readHeaderOrFooter(long pos, int len) throws IOException {
		ByteBuffer buf = src.read(pos, len);
		if (buf.position() != len) {
			throw new IOException(JGitText.get().shortReadOfBlock);
		}

		byte[] tmp = new byte[len];
		buf.flip();
		buf.get(tmp);
		if (!isFileHeaderMagic(tmp, 0, len)) {
			throw new IOException(JGitText.get().invalidReftableFile);
		}

		int v = NB.decodeInt32(tmp, 4);
		int version = v >>> 24;
		if (VERSION_1 != version) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedReftableVersion,
					Integer.valueOf(version)));
		}
		return tmp;
	}

	private void initFileSize() throws IOException {
		if (size == 0) {
			size = src.size() - FILE_FOOTER_LEN;
		}
	}

	private void initIndex() throws IOException {
		if (index == null) {
			if (shouldReadFooter && indexSize == 0) {
				readFileFooter();
			}
			if (indexSize > 0) {
				index = new BlockReader();
				index.readFrom(src, size - indexSize, indexSize);
				index.verifyIndex();
			}
		}
	}

	private BlockReader readBlock(long position) throws IOException {
		long end = size - indexSize;
		int sz = blockSize;
		if (position + sz > end) {
			// Last block of the file can omit padding.
			sz = (int) (end - position);
		}

		BlockReader b = new BlockReader();
		b.readFrom(src, position, sz);
		return b;
	}

	private int blocks() {
		long end = size - indexSize;
		int blocks = (int) (end / blockSize);
		if (end % blockSize != 0) {
			blocks++;
		}
		return blocks;
	}

	@Override
	public void close() throws IOException {
		src.close();
	}
}
