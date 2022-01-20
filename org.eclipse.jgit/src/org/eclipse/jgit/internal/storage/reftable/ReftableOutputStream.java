/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.stream.CountingOutputStream;

/**
 * Wrapper to assist formatting a reftable to an {@link OutputStream}.
 * <p>
 * Internally buffers at block size boundaries, flushing only complete blocks to
 * the {@code OutputStream}.
 */
class ReftableOutputStream extends OutputStream {
	private final byte[] tmp = new byte[10];
	private final CountingOutputStream out;
	private final boolean alignBlocks;

	private Deflater deflater;
	private DeflaterOutputStream compressor;

	private int blockType;
	private int blockSize;
	private int blockStart;
	private byte[] blockBuf;
	private int cur;
	private long paddingUsed;

	ReftableOutputStream(OutputStream os, int bs, boolean align) {
		blockSize = bs;
		blockBuf = new byte[bs];
		alignBlocks = align;
		out = new CountingOutputStream(os);
	}

	void setBlockSize(int bs) {
		blockSize = bs;
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) {
		ensureBytesAvailableInBlockBuf(1);
		blockBuf[cur++] = (byte) b;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b, int off, int cnt) {
		ensureBytesAvailableInBlockBuf(cnt);
		System.arraycopy(b, off, blockBuf, cur, cnt);
		cur += cnt;
	}

	int bytesWrittenInBlock() {
		return cur;
	}

	int bytesAvailableInBlock() {
		return blockSize - cur;
	}

	long paddingUsed() {
		return paddingUsed;
	}

	/** @return bytes flushed; excludes {@link #bytesWrittenInBlock()}. */
	long size() {
		return out.getCount();
	}

	static int computeVarintSize(long val) {
		int n = 1;
		for (; (val >>>= 7) != 0; n++) {
			val--;
		}
		return n;
	}

	void writeVarint(long val) {
		int n = tmp.length;
		tmp[--n] = (byte) (val & 0x7f);
		while ((val >>>= 7) != 0) {
			tmp[--n] = (byte) (0x80 | (--val & 0x7F));
		}
		write(tmp, n, tmp.length - n);
	}

	void writeInt16(int val) {
		ensureBytesAvailableInBlockBuf(2);
		NB.encodeInt16(blockBuf, cur, val);
		cur += 2;
	}

	void writeInt24(int val) {
		ensureBytesAvailableInBlockBuf(3);
		NB.encodeInt24(blockBuf, cur, val);
		cur += 3;
	}

	void writeId(ObjectId id) {
		ensureBytesAvailableInBlockBuf(OBJECT_ID_LENGTH);
		id.copyRawTo(blockBuf, cur);
		cur += OBJECT_ID_LENGTH;
	}

	void writeVarintString(String s) {
		writeVarintString(s.getBytes(UTF_8));
	}

	void writeVarintString(byte[] msg) {
		writeVarint(msg.length);
		write(msg, 0, msg.length);
	}

	private void ensureBytesAvailableInBlockBuf(int cnt) {
		if (cur + cnt > blockBuf.length) {
			int n = Math.max(cur + cnt, blockBuf.length * 2);
			blockBuf = Arrays.copyOf(blockBuf, n);
		}
	}

	void flushFileHeader() throws IOException {
		if (cur == FILE_HEADER_LEN && out.getCount() == 0) {
			out.write(blockBuf, 0, cur);
			cur = 0;
		}
	}

	void beginBlock(byte type) {
		blockType = type;
		blockStart = cur;
		cur += 4; // reserve space for 4-byte block header.
	}

	void flushBlock() throws IOException {
		if (cur > blockSize && blockType != INDEX_BLOCK_TYPE) {
			throw new IOException(JGitText.get().overflowedReftableBlock);
		}
		NB.encodeInt32(blockBuf, blockStart, (blockType << 24) | cur);

		if (blockType == LOG_BLOCK_TYPE) {
			// Log blocks are deflated after the block header.
			out.write(blockBuf, 0, 4);
			if (deflater != null) {
				deflater.reset();
			} else {
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
				compressor = new DeflaterOutputStream(out, deflater);
			}
			compressor.write(blockBuf, 4, cur - 4);
			compressor.finish();
		} else {
			// Other blocks are uncompressed.
			out.write(blockBuf, 0, cur);
		}

		cur = 0;
		blockType = 0;
		blockStart = 0;
	}

	void padBetweenBlocksToNextBlock() throws IOException {
		if (alignBlocks) {
			long m = size() % blockSize;
			if (m > 0) {
				int pad = blockSize - (int) m;
				ensureBytesAvailableInBlockBuf(pad);
				Arrays.fill(blockBuf, 0, pad, (byte) 0);
				out.write(blockBuf, 0, pad);
				paddingUsed += pad;
			}
		}
	}

	int estimatePadBetweenBlocks(int currentBlockSize) {
		if (alignBlocks) {
			long m = (size() + currentBlockSize) % blockSize;
			return m > 0 ? blockSize - (int) m : 0;
		}
		return 0;
	}

	void finishFile() throws IOException {
		// File footer doesn't need patching for the block start.
		// Just flush what has been buffered.
		out.write(blockBuf, 0, cur);
		cur = 0;

		if (deflater != null) {
			deflater.end();
		}
	}
}
