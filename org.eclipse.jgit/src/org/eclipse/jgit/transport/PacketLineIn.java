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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read Git style pkt-line formatting from an input stream.
 * <p>
 * This class is not thread safe and may issue multiple reads to the underlying
 * stream for each method call made.
 * <p>
 * This class performs no buffering on its own. This makes it suitable to
 * interleave reads performed by this class with reads performed directly
 * against the underlying InputStream.
 */
public class PacketLineIn {
	private static final Logger log = LoggerFactory.getLogger(PacketLineIn.class);

	/** Magic return from {@link #readString()} when a flush packet is found. */
	public static final String END = new StringBuilder(0).toString(); 	/* must not string pool */

	/**
	 * Magic return from {@link #readString()} when a delim packet is found.
	 *
	 * @since 5.0
	 */
	public static final String DELIM = new StringBuilder(0).toString(); 	/* must not string pool */

	static enum AckNackResult {
		/** NAK */
		NAK,
		/** ACK */
		ACK,
		/** ACK + continue */
		ACK_CONTINUE,
		/** ACK + common */
		ACK_COMMON,
		/** ACK + ready */
		ACK_READY;
	}

	private final byte[] lineBuffer = new byte[SideBandOutputStream.SMALL_BUF];
	private final InputStream in;
	private long limit;

	/**
	 * Create a new packet line reader.
	 *
	 * @param in
	 *            the input stream to consume.
	 */
	public PacketLineIn(InputStream in) {
		this(in, 0);
	}

	/**
	 * Create a new packet line reader.
	 *
	 * @param in
	 *            the input stream to consume.
	 * @param limit
	 *            bytes to read from the input; unlimited if set to 0.
	 * @since 4.7
	 */
	public PacketLineIn(InputStream in, long limit) {
		this.in = in;
		this.limit = limit;
	}

	AckNackResult readACK(MutableObjectId returnedId) throws IOException {
		final String line = readString();
		if (line.length() == 0)
			throw new PackProtocolException(JGitText.get().expectedACKNAKFoundEOF);
		if ("NAK".equals(line)) //$NON-NLS-1$
			return AckNackResult.NAK;
		if (line.startsWith("ACK ")) { //$NON-NLS-1$
			returnedId.fromString(line.substring(4, 44));
			if (line.length() == 44)
				return AckNackResult.ACK;

			final String arg = line.substring(44);
			if (arg.equals(" continue")) //$NON-NLS-1$
				return AckNackResult.ACK_CONTINUE;
			else if (arg.equals(" common")) //$NON-NLS-1$
				return AckNackResult.ACK_COMMON;
			else if (arg.equals(" ready")) //$NON-NLS-1$
				return AckNackResult.ACK_READY;
		}
		if (line.startsWith("ERR ")) //$NON-NLS-1$
			throw new PackProtocolException(line.substring(4));
		throw new PackProtocolException(MessageFormat.format(JGitText.get().expectedACKNAKGot, line));
	}

	/**
	 * Read a single UTF-8 encoded string packet from the input stream.
	 * <p>
	 * If the string ends with an LF, it will be removed before returning the
	 * value to the caller. If this automatic trimming behavior is not desired,
	 * use {@link #readStringRaw()} instead.
	 *
	 * @return the string. {@link #END} if the string was the magic flush
	 *         packet, {@link #DELIM} if the string was the magic DELIM
	 *         packet.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public String readString() throws IOException {
		int len = readLength();
		if (len == 0) {
			log.debug("git< 0000"); //$NON-NLS-1$
			return END;
		}
		if (len == 1) {
			log.debug("git< 0001"); //$NON-NLS-1$
			return DELIM;
		}

		len -= 4; // length header (4 bytes)
		if (len == 0) {
			log.debug("git< "); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}

		byte[] raw;
		if (len <= lineBuffer.length)
			raw = lineBuffer;
		else
			raw = new byte[len];

		IO.readFully(in, raw, 0, len);
		if (raw[len - 1] == '\n')
			len--;

		String s = RawParseUtils.decode(UTF_8, raw, 0, len);
		log.debug("git< " + s); //$NON-NLS-1$
		return s;
	}

	/**
	 * Read a single UTF-8 encoded string packet from the input stream.
	 * <p>
	 * Unlike {@link #readString()} a trailing LF will be retained.
	 *
	 * @return the string. {@link #END} if the string was the magic flush
	 *         packet.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public String readStringRaw() throws IOException {
		int len = readLength();
		if (len == 0) {
			log.debug("git< 0000"); //$NON-NLS-1$
			return END;
		}

		len -= 4; // length header (4 bytes)

		byte[] raw;
		if (len <= lineBuffer.length)
			raw = lineBuffer;
		else
			raw = new byte[len];

		IO.readFully(in, raw, 0, len);

		String s = RawParseUtils.decode(UTF_8, raw, 0, len);
		log.debug("git< " + s); //$NON-NLS-1$
		return s;
	}

	/**
	 * Check if a string is the delimiter marker.
	 *
	 * @param s
	 *            the string to check
	 * @return true if the given string is {@link #DELIM}, otherwise false.
	 * @since 5.4
	 */
	public static boolean isDelimiter(String s) {
		return s == DELIM;
	}

	/**
	 * Check if a string is the packet end marker.
	 *
	 * @param s
	 *            the string to check
	 * @return true if the given string is {@link #END}, otherwise false.
	 * @since 5.4
	 */
	public static boolean isEnd(String s) {
		return s == END;
	}

	void discardUntilEnd() throws IOException {
		for (;;) {
			int n = readLength();
			if (n == 0) {
				break;
			}
			IO.skipFully(in, n - 4);
		}
	}

	int readLength() throws IOException {
		IO.readFully(in, lineBuffer, 0, 4);
		int len;
		try {
			len = RawParseUtils.parseHexInt16(lineBuffer, 0);
		} catch (ArrayIndexOutOfBoundsException err) {
			throw invalidHeader();
		}

		if (len == 0) {
			return 0;
		} else if (len == 1) {
			return 1;
		} else if (len < 4) {
			throw invalidHeader();
		}

		if (limit != 0) {
			int n = len - 4;
			if (limit < n) {
				limit = -1;
				try {
					IO.skipFully(in, n);
				} catch (IOException e) {
					// Ignore failure discarding packet over limit.
				}
				throw new InputOverLimitIOException();
			}
			// if set limit must not be 0 (means unlimited).
			limit = n < limit ? limit - n : -1;
		}
		return len;
	}

	private IOException invalidHeader() {
		return new IOException(MessageFormat.format(JGitText.get().invalidPacketLineHeader,
				"" + (char) lineBuffer[0] + (char) lineBuffer[1] //$NON-NLS-1$
				+ (char) lineBuffer[2] + (char) lineBuffer[3]));
	}

	/**
	 * IOException thrown by read when the configured input limit is exceeded.
	 *
	 * @since 4.7
	 */
	public static class InputOverLimitIOException extends IOException {
		private static final long serialVersionUID = 1L;
	}
}
