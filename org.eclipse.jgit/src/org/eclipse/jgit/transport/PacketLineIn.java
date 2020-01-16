/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Iterator;

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

	/**
	 * Magic return from {@link #readString()} when a flush packet is found.
	 *
	 * @deprecated Callers should use {@link #isEnd(String)} to check if a
	 *             string is the end marker, or
	 *             {@link PacketLineIn#readStrings()} to iterate over all
	 *             strings in the input stream until the marker is reached.
	 */
	@Deprecated
	public static final String END = new StringBuilder(0).toString(); 	/* must not string pool */

	/**
	 * Magic return from {@link #readString()} when a delim packet is found.
	 *
	 * @since 5.0
	 * @deprecated Callers should use {@link #isDelimiter(String)} to check if a
	 *             string is the delimiter.
	 */
	@Deprecated
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
			switch (arg) {
			case " continue": //$NON-NLS-1$
				return AckNackResult.ACK_CONTINUE;
			case " common": //$NON-NLS-1$
				return AckNackResult.ACK_COMMON;
			case " ready": //$NON-NLS-1$
				return AckNackResult.ACK_READY;
			default:
				break;
			}
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
	 * Get an iterator to read strings from the input stream.
	 *
	 * @return an iterator that calls {@link #readString()} until {@link #END}
	 *         is encountered.
	 *
	 * @throws IOException
	 *             on failure to read the initial packet line.
	 * @since 5.4
	 */
	public PacketLineInIterator readStrings() throws IOException {
		return new PacketLineInIterator(this);
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
	@SuppressWarnings({ "ReferenceEquality", "StringEquality" })
	public static boolean isDelimiter(String s) {
		return s == DELIM;
	}

	/**
	 * Get the delimiter marker.
	 * <p>
	 * Intended for use only in tests.
	 *
	 * @return The delimiter marker.
	 */
	static String delimiter() {
		return DELIM;
	}

	/**
	 * Get the end marker.
	 * <p>
	 * Intended for use only in tests.
	 *
	 * @return The end marker.
	 */
	static String end() {
		return END;
	}

	/**
	 * Check if a string is the packet end marker.
	 *
	 * @param s
	 *            the string to check
	 * @return true if the given string is {@link #END}, otherwise false.
	 * @since 5.4
	 */
	@SuppressWarnings({ "ReferenceEquality", "StringEquality" })
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

	/**
	 * Iterator over packet lines.
	 * <p>
	 * Calls {@link #readString()} on the {@link PacketLineIn} until
	 * {@link #END} is encountered.
	 *
	 * @since 5.4
	 *
	 */
	public static class PacketLineInIterator implements Iterable<String> {
		private PacketLineIn in;

		private String current;

		PacketLineInIterator(PacketLineIn in) throws IOException {
			this.in = in;
			current = in.readString();
		}

		@Override
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				@Override
				public boolean hasNext() {
					return !PacketLineIn.isEnd(current);
				}

				@Override
				public String next() {
					String next = current;
					try {
						current = in.readString();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
					return next;
				}
			};
		}

	}
}
