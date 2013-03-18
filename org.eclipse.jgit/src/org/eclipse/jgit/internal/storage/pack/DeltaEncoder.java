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

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.lib.Constants;

/** Encodes an instruction stream for {@link BinaryDelta}. */
public class DeltaEncoder {
	/**
	 * Maximum number of bytes to be copied in pack v2 format.
	 * <p>
	 * Historical limitations have this at 64k, even though current delta
	 * decoders recognize larger copy instructions.
	 */
	private static final int MAX_V2_COPY = 0x10000;

	/*
	 * Maximum number of bytes to be copied in pack v3 format.
	 *
	 * Current delta decoders can recognize a copy instruction with a count that
	 * is this large, but the historical limitation of {@link MAX_V2_COPY} is
	 * still used.
	 */
	// private static final int MAX_V3_COPY = (0xff << 16) | (0xff << 8) | 0xff;

	/** Maximum number of bytes used by a copy instruction. */
	private static final int MAX_COPY_CMD_SIZE = 8;

	/** Maximum length that an an insert command can encode at once. */
	private static final int MAX_INSERT_DATA_SIZE = 127;

	private final OutputStream out;

	private final byte[] buf = new byte[MAX_COPY_CMD_SIZE * 4];

	private final int limit;

	private int size;

	/**
	 * Create an encoder with no upper bound on the instruction stream size.
	 *
	 * @param out
	 *            buffer to store the instructions written.
	 * @param baseSize
	 *            size of the base object, in bytes.
	 * @param resultSize
	 *            size of the resulting object, after applying this instruction
	 *            stream to the base object, in bytes.
	 * @throws IOException
	 *             the output buffer cannot store the instruction stream's
	 *             header with the size fields.
	 */
	public DeltaEncoder(OutputStream out, long baseSize, long resultSize)
			throws IOException {
		this(out, baseSize, resultSize, 0);
	}

	/**
	 * Create an encoder with an upper limit on the instruction size.
	 *
	 * @param out
	 *            buffer to store the instructions written.
	 * @param baseSize
	 *            size of the base object, in bytes.
	 * @param resultSize
	 *            size of the resulting object, after applying this instruction
	 *            stream to the base object, in bytes.
	 * @param limit
	 *            maximum number of bytes to write to the out buffer declaring
	 *            the stream is over limit and should be discarded. May be 0 to
	 *            specify an infinite limit.
	 * @throws IOException
	 *             the output buffer cannot store the instruction stream's
	 *             header with the size fields.
	 */
	public DeltaEncoder(OutputStream out, long baseSize, long resultSize,
			int limit) throws IOException {
		this.out = out;
		this.limit = limit;
		writeVarint(baseSize);
		writeVarint(resultSize);
	}

	private void writeVarint(long sz) throws IOException {
		int p = 0;
		while (sz >= 0x80) {
			buf[p++] = (byte) (0x80 | (((int) sz) & 0x7f));
			sz >>>= 7;
		}
		buf[p++] = (byte) (((int) sz) & 0x7f);
		size += p;
		if (limit <= 0 || size < limit)
			out.write(buf, 0, p);
	}

	/** @return current size of the delta stream, in bytes. */
	public int getSize() {
		return size;
	}

	/**
	 * Insert a literal string of text, in UTF-8 encoding.
	 *
	 * @param text
	 *            the string to insert.
	 * @return true if the insert fits within the limit; false if the insert
	 *         would cause the instruction stream to exceed the limit.
	 * @throws IOException
	 *             the instruction buffer can't store the instructions.
	 */
	public boolean insert(String text) throws IOException {
		return insert(Constants.encode(text));
	}

	/**
	 * Insert a literal binary sequence.
	 *
	 * @param text
	 *            the binary to insert.
	 * @return true if the insert fits within the limit; false if the insert
	 *         would cause the instruction stream to exceed the limit.
	 * @throws IOException
	 *             the instruction buffer can't store the instructions.
	 */
	public boolean insert(byte[] text) throws IOException {
		return insert(text, 0, text.length);
	}

	/**
	 * Insert a literal binary sequence.
	 *
	 * @param text
	 *            the binary to insert.
	 * @param off
	 *            offset within {@code text} to start copying from.
	 * @param cnt
	 *            number of bytes to insert.
	 * @return true if the insert fits within the limit; false if the insert
	 *         would cause the instruction stream to exceed the limit.
	 * @throws IOException
	 *             the instruction buffer can't store the instructions.
	 */
	public boolean insert(byte[] text, int off, int cnt)
			throws IOException {
		if (cnt <= 0)
			return true;
		if (0 < limit) {
			int hdrs = cnt / MAX_INSERT_DATA_SIZE;
			if (cnt % MAX_INSERT_DATA_SIZE != 0)
				hdrs++;
			if (limit < size + hdrs + cnt)
				return false;
		}
		do {
			int n = Math.min(MAX_INSERT_DATA_SIZE, cnt);
			out.write((byte) n);
			out.write(text, off, n);
			off += n;
			cnt -= n;
			size += 1 + n;
		} while (0 < cnt);
		return true;
	}

	/**
	 * Create a copy instruction to copy from the base object.
	 *
	 * @param offset
	 *            position in the base object to copy from. This is absolute,
	 *            from the beginning of the base.
	 * @param cnt
	 *            number of bytes to copy.
	 * @return true if the copy fits within the limit; false if the copy
	 *         would cause the instruction stream to exceed the limit.
	 * @throws IOException
	 *             the instruction buffer cannot store the instructions.
	 */
	public boolean copy(long offset, int cnt) throws IOException {
		if (cnt == 0)
			return true;

		int p = 0;

		// We cannot encode more than MAX_V2_COPY bytes in a single
		// command, so encode that much and start a new command.
		// This limit is imposed by the pack file format rules.
		//
		while (MAX_V2_COPY < cnt) {
			p = encodeCopy(p, offset, MAX_V2_COPY);
			offset += MAX_V2_COPY;
			cnt -= MAX_V2_COPY;

			if (buf.length < p + MAX_COPY_CMD_SIZE) {
				if (0 < limit && limit < size + p)
					return false;
				out.write(buf, 0, p);
				size += p;
				p = 0;
			}
		}

		p = encodeCopy(p, offset, cnt);
		if (0 < limit && limit < size + p)
			return false;
		out.write(buf, 0, p);
		size += p;
		return true;
	}

	private int encodeCopy(int p, long offset, int cnt) {
		int cmd = 0x80;
		final int cmdPtr = p++; // save room for the command

		if ((offset & 0xff) != 0) {
			cmd |= 0x01;
			buf[p++] = (byte) (offset & 0xff);
		}
		if ((offset & (0xff << 8)) != 0) {
			cmd |= 0x02;
			buf[p++] = (byte) ((offset >>> 8) & 0xff);
		}
		if ((offset & (0xff << 16)) != 0) {
			cmd |= 0x04;
			buf[p++] = (byte) ((offset >>> 16) & 0xff);
		}
		if ((offset & (0xff << 24)) != 0) {
			cmd |= 0x08;
			buf[p++] = (byte) ((offset >>> 24) & 0xff);
		}

		if (cnt != MAX_V2_COPY) {
			if ((cnt & 0xff) != 0) {
				cmd |= 0x10;
				buf[p++] = (byte) (cnt & 0xff);
			}
			if ((cnt & (0xff << 8)) != 0) {
				cmd |= 0x20;
				buf[p++] = (byte) ((cnt >>> 8) & 0xff);
			}
			if ((cnt & (0xff << 16)) != 0) {
				cmd |= 0x40;
				buf[p++] = (byte) ((cnt >>> 16) & 0xff);
			}
		}

		buf[cmdPtr] = (byte) cmd;
		return p;
	}
}
