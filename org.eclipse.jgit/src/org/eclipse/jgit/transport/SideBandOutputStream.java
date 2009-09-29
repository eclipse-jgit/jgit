/*
 * Copyright (C) 2008, Google Inc.
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

/**
 * Multiplexes data and progress messages
 * <p>
 * To correctly use this class you must wrap it in a BufferedOutputStream with a
 * buffer size no larger than either {@link #SMALL_BUF} or {@link #MAX_BUF},
 * minus {@link #HDR_SIZE}.
 */
class SideBandOutputStream extends OutputStream {
	static final int CH_DATA = SideBandInputStream.CH_DATA;

	static final int CH_PROGRESS = SideBandInputStream.CH_PROGRESS;

	static final int CH_ERROR = SideBandInputStream.CH_ERROR;

	static final int SMALL_BUF = 1000;

	static final int MAX_BUF = 65520;

	static final int HDR_SIZE = 5;

	private final int channel;

	private final PacketLineOut pckOut;

	private byte[] singleByteBuffer;

	SideBandOutputStream(final int chan, final PacketLineOut out) {
		channel = chan;
		pckOut = out;
	}

	@Override
	public void flush() throws IOException {
		if (channel != CH_DATA)
			pckOut.flush();
	}

	@Override
	public void write(final byte[] b, final int off, final int len)
			throws IOException {
		pckOut.writeChannelPacket(channel, b, off, len);
	}

	@Override
	public void write(final int b) throws IOException {
		if (singleByteBuffer == null)
			singleByteBuffer = new byte[1];
		singleByteBuffer[0] = (byte) b;
		write(singleByteBuffer);
	}
}
