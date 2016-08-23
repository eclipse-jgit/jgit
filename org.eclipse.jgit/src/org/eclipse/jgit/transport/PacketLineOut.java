/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write Git style pkt-line formatting to an output stream.
 * <p>
 * This class is not thread safe and may issue multiple writes to the underlying
 * stream for each method call made.
 * <p>
 * This class performs no buffering on its own. This makes it suitable to
 * interleave writes performed by this class with writes performed directly
 * against the underlying OutputStream.
 */
public class PacketLineOut {
	private static final Logger log = LoggerFactory.getLogger(PacketLineOut.class);

	private final OutputStream out;

	private final byte[] lenbuffer;

	private boolean flushOnEnd;

	/**
	 * Create a new packet line writer.
	 *
	 * @param outputStream
	 *            stream.
	 */
	public PacketLineOut(final OutputStream outputStream) {
		out = outputStream;
		lenbuffer = new byte[5];
		flushOnEnd = true;
	}

	/**
	 * Set the flush behavior during {@link #end()}.
	 *
	 * @param flushOnEnd
	 *            if true, a flush-pkt written during {@link #end()} also
	 *            flushes the underlying stream.
	 */
	public void setFlushOnEnd(boolean flushOnEnd) {
		this.flushOnEnd = flushOnEnd;
	}

	/**
	 * Write a UTF-8 encoded string as a single length-delimited packet.
	 *
	 * @param s
	 *            string to write.
	 * @throws IOException
	 *             the packet could not be written, the stream is corrupted as
	 *             the packet may have been only partially written.
	 */
	public void writeString(final String s) throws IOException {
		writePacket(Constants.encode(s));
	}

	/**
	 * Write a binary packet to the stream.
	 *
	 * @param packet
	 *            the packet to write; the length of the packet is equal to the
	 *            size of the byte array.
	 * @throws IOException
	 *             the packet could not be written, the stream is corrupted as
	 *             the packet may have been only partially written.
	 */
	public void writePacket(byte[] packet) throws IOException {
		writePacket(packet, 0, packet.length);
	}

	/**
	 * Write a binary packet to the stream.
	 *
	 * @param buf
	 *            the packet to write
	 * @param pos
	 *            first index within {@code buf}.
	 * @param len
	 *            number of bytes to write.
	 * @throws IOException
	 *             the packet could not be written, the stream is corrupted as
	 *             the packet may have been only partially written.
	 * @since 4.5
	 */
	public void writePacket(byte[] buf, int pos, int len) throws IOException {
		formatLength(len + 4);
		out.write(lenbuffer, 0, 4);
		out.write(buf, pos, len);
		if (log.isDebugEnabled()) {
			String s = RawParseUtils.decode(Constants.CHARSET, buf, pos, len);
			log.debug("git> " + s); //$NON-NLS-1$
		}
	}

	/**
	 * Write a packet end marker, sometimes referred to as a flush command.
	 * <p>
	 * Technically this is a magical packet type which can be detected
	 * separately from an empty string or an empty packet.
	 * <p>
	 * Implicitly performs a flush on the underlying OutputStream to ensure the
	 * peer will receive all data written thus far.
	 *
	 * @throws IOException
	 *             the end marker could not be written, the stream is corrupted
	 *             as the end marker may have been only partially written.
	 */
	public void end() throws IOException {
		formatLength(0);
		out.write(lenbuffer, 0, 4);
		log.debug("git> 0000"); //$NON-NLS-1$
		if (flushOnEnd)
			flush();
	}

	/**
	 * Flush the underlying OutputStream.
	 * <p>
	 * Performs a flush on the underlying OutputStream to ensure the peer will
	 * receive all data written thus far.
	 *
	 * @throws IOException
	 *             the underlying stream failed to flush.
	 */
	public void flush() throws IOException {
		out.flush();
	}

	private static final byte[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private void formatLength(int w) {
		formatLength(lenbuffer, w);
	}

	static void formatLength(byte[] lenbuffer, int w) {
		int o = 3;
		while (o >= 0 && w != 0) {
			lenbuffer[o--] = hexchar[w & 0xf];
			w >>>= 4;
		}
		while (o >= 0)
			lenbuffer[o--] = '0';
	}
}
