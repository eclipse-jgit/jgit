/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Multiplexes data and progress messages.
 * <p>
 * This stream is buffered at packet sizes, so the caller doesn't need to wrap
 * it in yet another buffered stream.
 *
 * @since 2.0
 */
public class SideBandOutputStream extends OutputStream {
	/** Channel used for pack data. */
	public static final int CH_DATA = SideBandInputStream.CH_DATA;

	/** Channel used for progress messages. */
	public static final int CH_PROGRESS = SideBandInputStream.CH_PROGRESS;

	/** Channel used for error messages. */
	public static final int CH_ERROR = SideBandInputStream.CH_ERROR;

	/** Default buffer size for a small amount of data. */
	public static final int SMALL_BUF = 1000;

	/** Maximum buffer size for a single packet of sideband data. */
	public static final int MAX_BUF = 65520;

	static final int HDR_SIZE = 5;

	private final OutputStream out;

	private final byte[] buffer;

	/**
	 * Number of bytes in {@link #buffer} that are valid data.
	 * <p>
	 * Initialized to {@link #HDR_SIZE} if there is no application data in the
	 * buffer, as the packet header always appears at the start of the buffer.
	 */
	private int cnt;

	/**
	 * Create a new stream to write side band packets.
	 *
	 * @param chan
	 *            channel number to prefix all packets with, so the remote side
	 *            can demultiplex the stream and get back the original data.
	 *            Must be in the range [1, 255].
	 * @param sz
	 *            maximum size of a data packet within the stream. The remote
	 *            side needs to agree to the packet size to prevent buffer
	 *            overflows. Must be in the range [HDR_SIZE + 1, MAX_BUF).
	 * @param os
	 *            stream that the packets are written onto. This stream should
	 *            be attached to a SideBandInputStream on the remote side.
	 */
	public SideBandOutputStream(int chan, int sz, OutputStream os) {
		if (chan <= 0 || chan > 255)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().channelMustBeInRange1_255,
					Integer.valueOf(chan)));
		if (sz <= HDR_SIZE)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().packetSizeMustBeAtLeast,
					Integer.valueOf(sz), Integer.valueOf(HDR_SIZE)));
		else if (MAX_BUF < sz)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().packetSizeMustBeAtMost, Integer.valueOf(sz),
					Integer.valueOf(MAX_BUF)));

		out = os;
		buffer = new byte[sz];
		buffer[4] = (byte) chan;
		cnt = HDR_SIZE;
	}

	void flushBuffer() throws IOException {
		if (HDR_SIZE < cnt)
			writeBuffer();
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		flushBuffer();
		out.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		while (0 < len) {
			int capacity = buffer.length - cnt;
			if (cnt == HDR_SIZE && capacity < len) {
				// Our block to write is bigger than the packet size,
				// stream it out as-is to avoid unnecessary copies.
				PacketLineOut.formatLength(buffer, buffer.length);
				out.write(buffer, 0, HDR_SIZE);
				out.write(b, off, capacity);
				off += capacity;
				len -= capacity;

			} else {
				if (capacity == 0)
					writeBuffer();

				int n = Math.min(len, capacity);
				System.arraycopy(b, off, buffer, cnt, n);
				cnt += n;
				off += n;
				len -= n;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		if (cnt == buffer.length)
			writeBuffer();
		buffer[cnt++] = (byte) b;
	}

	private void writeBuffer() throws IOException {
		PacketLineOut.formatLength(buffer, cnt);
		out.write(buffer, 0, cnt);
		cnt = HDR_SIZE;
	}
}
