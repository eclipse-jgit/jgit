/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2021, Johannes E. Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A Sequence supporting UNIX formatted text in byte[] format.
 * <p>
 * Elements of the sequence are the lines of the file, as delimited by the UNIX
 * newline character ('\n'). The file content is treated as 8 bit binary text,
 * with no assumptions or requirements on character encoding.
 * <p>
 * Note that the first line of the file is element 0, as defined by the Sequence
 * interface API. Traditionally in a text editor a patch file the first line is
 * line number 1. Callers may need to subtract 1 prior to invoking methods if
 * they are converting from "line number" to "element index".
 */
public class RawText extends Sequence {

	/** A RawText of length 0 */
	public static final RawText EMPTY_TEXT = new RawText(new byte[0]);

	/**
	 * Default and minimum for {@link #BUFFER_SIZE}.
	 */
	private static final int FIRST_FEW_BYTES = 8 * 1024;

	/**
	 * Number of bytes to check for heuristics in {@link #isBinary(byte[])}.
	 */
	private static final AtomicInteger BUFFER_SIZE = new AtomicInteger(
			FIRST_FEW_BYTES);

	/** The file content for this sequence. */
	protected final byte[] content;

	/** Map of line number to starting position within {@link #content}. */
	protected final IntList lines;

	/**
	 * Create a new sequence from an existing content byte array.
	 * <p>
	 * The entire array (indexes 0 through length-1) is used as the content.
	 *
	 * @param input
	 *            the content array. The object retains a reference to this
	 *            array, so it should be immutable.
	 */
	public RawText(byte[] input) {
		this(input, RawParseUtils.lineMap(input, 0, input.length));
	}

	/**
	 * Create a new sequence from the existing content byte array and the line
	 * map indicating line boundaries.
	 *
	 * @param input
	 *            the content array. The object retains a reference to this
	 *            array, so it should be immutable.
	 * @param lineMap
	 *            an array with 1-based offsets for the start of each line.
	 *            The first and last entries should be {@link Integer#MIN_VALUE}
	 *            and an offset one past the end of the last line, respectively.
	 * @since 5.0
	 */
	public RawText(byte[] input, IntList lineMap) {
		content = input;
		lines = lineMap;
	}

	/**
	 * Create a new sequence from a file.
	 * <p>
	 * The entire file contents are used.
	 *
	 * @param file
	 *            the text file.
	 * @throws java.io.IOException
	 *             if Exceptions occur while reading the file
	 */
	public RawText(File file) throws IOException {
		this(IO.readFully(file));
	}

	/**
	 * Get the raw content
	 *
	 * @return the raw, unprocessed content read.
	 * @since 4.11
	 */
	public byte[] getRawContent() {
		return content;
	}

	/** @return total number of items in the sequence. */
	@Override
	public int size() {
		// The line map is always 2 entries larger than the number of lines in
		// the file. Index 0 is padded out/unused. The last index is the total
		// length of the buffer, and acts as a sentinel.
		//
		return lines.size() - 2;
	}

	/**
	 * Write a specific line to the output stream, without its trailing LF.
	 * <p>
	 * The specified line is copied as-is, with no character encoding
	 * translation performed.
	 * <p>
	 * If the specified line ends with an LF ('\n'), the LF is <b>not</b>
	 * copied. It is up to the caller to write the LF, if desired, between
	 * output lines.
	 *
	 * @param out
	 *            stream to copy the line data onto.
	 * @param i
	 *            index of the line to extract. Note this is 0-based, so line
	 *            number 1 is actually index 0.
	 * @throws java.io.IOException
	 *             the stream write operation failed.
	 */
	public void writeLine(OutputStream out, int i)
			throws IOException {
		int start = getStart(i);
		int end = getEnd(i);
		if (content[end - 1] == '\n')
			end--;
		out.write(content, start, end - start);
	}

	/**
	 * Determine if the file ends with a LF ('\n').
	 *
	 * @return true if the last line has an LF; false otherwise.
	 */
	public boolean isMissingNewlineAtEnd() {
		final int end = lines.get(lines.size() - 1);
		if (end == 0)
			return true;
		return content[end - 1] != '\n';
	}

	/**
	 * Get the text for a single line.
	 *
	 * @param i
	 *            index of the line to extract. Note this is 0-based, so line
	 *            number 1 is actually index 0.
	 * @return the text for the line, without a trailing LF.
	 */
	public String getString(int i) {
		return getString(i, i + 1, true);
	}

	/**
	 * Get the raw text for a single line.
	 *
	 * @param i
	 *            index of the line to extract. Note this is 0-based, so line
	 *            number 1 is actually index 0.
	 * @return the text for the line, without a trailing LF, as a
	 *         {@link ByteBuffer} that is backed by a slice of the
	 *         {@link #getRawContent() raw content}, with the buffer's position
	 *         on the start of the line and the limit at the end.
	 * @since 5.12
	 */
	public ByteBuffer getRawString(int i) {
		int s = getStart(i);
		int e = getEnd(i);
		if (e > 0 && content[e - 1] == '\n') {
			e--;
		}
		return ByteBuffer.wrap(content, s, e - s);
	}

	/**
	 * Get the text for a region of lines.
	 *
	 * @param begin
	 *            index of the first line to extract. Note this is 0-based, so
	 *            line number 1 is actually index 0.
	 * @param end
	 *            index of one past the last line to extract.
	 * @param dropLF
	 *            if true the trailing LF ('\n') of the last returned line is
	 *            dropped, if present.
	 * @return the text for lines {@code [begin, end)}.
	 */
	public String getString(int begin, int end, boolean dropLF) {
		if (begin == end)
			return ""; //$NON-NLS-1$

		int s = getStart(begin);
		int e = getEnd(end - 1);
		if (dropLF && content[e - 1] == '\n')
			e--;
		return decode(s, e);
	}

	/**
	 * Decode a region of the text into a String.
	 *
	 * The default implementation of this method tries to guess the character
	 * set by considering UTF-8, the platform default, and falling back on
	 * ISO-8859-1 if neither of those can correctly decode the region given.
	 *
	 * @param start
	 *            first byte of the content to decode.
	 * @param end
	 *            one past the last byte of the content to decode.
	 * @return the region {@code [start, end)} decoded as a String.
	 */
	protected String decode(int start, int end) {
		return RawParseUtils.decode(content, start, end);
	}

	private int getStart(int i) {
		return lines.get(i + 1);
	}

	private int getEnd(int i) {
		return lines.get(i + 2);
	}

	/**
	 * Obtains the buffer size to use for analyzing whether certain content is
	 * text or binary, or what line endings are used if it's text.
	 *
	 * @return the buffer size, by default {@link #FIRST_FEW_BYTES} bytes
	 * @since 6.0
	 */
	public static int getBufferSize() {
		return BUFFER_SIZE.get();
	}

	/**
	 * Sets the buffer size to use for analyzing whether certain content is text
	 * or binary, or what line endings are used if it's text. If the given
	 * {@code bufferSize} is smaller than {@link #FIRST_FEW_BYTES} set the
	 * buffer size to {@link #FIRST_FEW_BYTES}.
	 *
	 * @param bufferSize
	 *            Size to set
	 * @return the size actually set
	 * @since 6.0
	 */
	public static int setBufferSize(int bufferSize) {
		int newSize = Math.max(FIRST_FEW_BYTES, bufferSize);
		return BUFFER_SIZE.updateAndGet(curr -> newSize);
	}

	/**
	 * Determine heuristically whether the bytes contained in a stream
	 * represents binary (as opposed to text) content.
	 *
	 * Note: Do not further use this stream after having called this method! The
	 * stream may not be fully read and will be left at an unknown position
	 * after consuming an unknown number of bytes. The caller is responsible for
	 * closing the stream.
	 *
	 * @param raw
	 *            input stream containing the raw file content.
	 * @return true if raw is likely to be a binary file, false otherwise
	 * @throws java.io.IOException
	 *             if input stream could not be read
	 */
	public static boolean isBinary(InputStream raw) throws IOException {
		final byte[] buffer = new byte[getBufferSize() + 1];
		int cnt = 0;
		while (cnt < buffer.length) {
			final int n = raw.read(buffer, cnt, buffer.length - cnt);
			if (n == -1) {
				break;
			}
			cnt += n;
		}
		return isBinary(buffer, cnt, cnt < buffer.length);
	}

	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *            the raw file content.
	 * @return true if raw is likely to be a binary file, false otherwise
	 */
	public static boolean isBinary(byte[] raw) {
		return isBinary(raw, raw.length);
	}

	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param length
	 *            number of bytes in {@code raw} to evaluate. This should be
	 *            {@code raw.length} unless {@code raw} was over-allocated by
	 *            the caller.
	 * @return true if raw is likely to be a binary file, false otherwise
	 */
	public static boolean isBinary(byte[] raw, int length) {
		return isBinary(raw, length, false);
	}

	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param length
	 *            number of bytes in {@code raw} to evaluate. This should be
	 *            {@code raw.length} unless {@code raw} was over-allocated by
	 *            the caller.
	 * @param complete
	 *            whether {@code raw} contains the whole data
	 * @return true if raw is likely to be a binary file, false otherwise
	 * @since 6.0
	 */
	public static boolean isBinary(byte[] raw, int length, boolean complete) {
		// Similar heuristic as C Git. Differences:
		// - limited buffer size; may be only the beginning of a large blob
		// - no counting of printable vs. non-printable bytes < 0x20 and 0x7F
		int maxLength = getBufferSize();
		boolean isComplete = complete;
		if (length > maxLength) {
			// We restrict the length in all cases to getBufferSize() to get
			// predictable behavior. Sometimes we load streams, and sometimes we
			// have the full data in memory. With streams, we never look at more
			// than the first getBufferSize() bytes. If we looked at more when
			// we have the full data, different code paths in JGit might come to
			// different conclusions.
			length = maxLength;
			isComplete = false;
		}
		byte last = 'x'; // Just something inconspicuous.
		for (int ptr = 0; ptr < length; ptr++) {
			byte curr = raw[ptr];
			if (isBinary(curr, last)) {
				return true;
			}
			last = curr;
		}
		if (isComplete) {
			// Buffer contains everything...
			return last == '\r'; // ... so this must be a lone CR
		}
		return false;
	}

	/**
	 * Determines from the last two bytes read from a source if it looks like
	 * binary content.
	 *
	 * @param curr
	 *            the last byte, read after {@code prev}
	 * @param prev
	 *            the previous byte, read before {@code last}
	 * @return {@code true} if either byte is NUL, or if prev is CR and curr is
	 *         not LF, {@code false} otherwise
	 * @since 6.0
	 */
	public static boolean isBinary(byte curr, byte prev) {
		return curr == '\0' || (curr != '\n' && prev == '\r') || prev == '\0';
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *            the raw file content.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *         {@code false} otherwise
	 * @since 5.3
	 */
	public static boolean isCrLfText(byte[] raw) {
		return isCrLfText(raw, raw.length);
	}

	/**
	 * Determine heuristically whether the bytes contained in a stream represent
	 * text content using CR-LF as line separator.
	 *
	 * Note: Do not further use this stream after having called this method! The
	 * stream may not be fully read and will be left at an unknown position
	 * after consuming an unknown number of bytes. The caller is responsible for
	 * closing the stream.
	 *
	 * @param raw
	 *            input stream containing the raw file content.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *         {@code false} otherwise
	 * @throws java.io.IOException
	 *             if input stream could not be read
	 * @since 5.3
	 */
	public static boolean isCrLfText(InputStream raw) throws IOException {
		byte[] buffer = new byte[getBufferSize()];
		int cnt = 0;
		while (cnt < buffer.length) {
			int n = raw.read(buffer, cnt, buffer.length - cnt);
			if (n == -1) {
				break;
			}
			cnt += n;
		}
		return isCrLfText(buffer, cnt);
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param length
	 *            number of bytes in {@code raw} to evaluate.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *         {@code false} otherwise
	 * @since 5.3
	 */
	public static boolean isCrLfText(byte[] raw, int length) {
		return isCrLfText(raw, length, false);
	}

	/**
	 * Determine heuristically whether a byte array represents text content
	 * using CR-LF as line separator.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param length
	 *            number of bytes in {@code raw} to evaluate.
	 * @return {@code true} if raw is likely to be CR-LF delimited text,
	 *         {@code false} otherwise
	 * @param complete
	 *            whether {@code raw} contains the whole data
	 * @since 6.0
	 */
	public static boolean isCrLfText(byte[] raw, int length, boolean complete) {
		boolean has_crlf = false;
		byte last = 'x'; // Just something inconspicuous
		for (int ptr = 0; ptr < length; ptr++) {
			byte curr = raw[ptr];
			if (isBinary(curr, last)) {
				return false;
			}
			if (curr == '\n' && last == '\r') {
				has_crlf = true;
			}
			last = curr;
		}
		if (last == '\r') {
			if (complete) {
				// Lone CR: it's binary after all.
				return false;
			}
			// Tough call. If the next byte, which we don't have, would be a
			// '\n', it'd be a CR-LF text, otherwise it'd be binary. Just decide
			// based on what we already scanned; it wasn't binary until now.
		}
		return has_crlf;
	}

	/**
	 * Get the line delimiter for the first line.
	 *
	 * @since 2.0
	 * @return the line delimiter or <code>null</code>
	 */
	public String getLineDelimiter() {
		if (size() == 0) {
			return null;
		}
		int e = getEnd(0);
		if (content[e - 1] != '\n') {
			return null;
		}
		if (content.length > 1 && e > 1 && content[e - 2] == '\r') {
			return "\r\n"; //$NON-NLS-1$
		}
		return "\n"; //$NON-NLS-1$
	}

	/**
	 * Read a blob object into RawText, or throw BinaryBlobException if the blob
	 * is binary.
	 *
	 * @param ldr
	 *            the ObjectLoader for the blob
	 * @param threshold
	 *            if the blob is larger than this size, it is always assumed to
	 *            be binary.
	 * @since 4.10
	 * @return the RawText representing the blob.
	 * @throws org.eclipse.jgit.errors.BinaryBlobException
	 *             if the blob contains binary data.
	 * @throws java.io.IOException
	 *             if the input could not be read.
	 */
	public static RawText load(ObjectLoader ldr, int threshold)
			throws IOException, BinaryBlobException {
		long sz = ldr.getSize();

		if (sz > threshold) {
			throw new BinaryBlobException();
		}

		int bufferSize = getBufferSize();
		if (sz <= bufferSize) {
			byte[] data = ldr.getCachedBytes(bufferSize);
			if (isBinary(data, data.length, true)) {
				throw new BinaryBlobException();
			}
			return new RawText(data);
		}

		byte[] head = new byte[bufferSize];
		try (InputStream stream = ldr.openStream()) {
			int off = 0;
			int left = head.length;
			byte last = 'x'; // Just something inconspicuous
			while (left > 0) {
				int n = stream.read(head, off, left);
				if (n < 0) {
					throw new EOFException();
				}
				left -= n;

				while (n > 0) {
					byte curr = head[off];
					if (isBinary(curr, last)) {
						throw new BinaryBlobException();
					}
					last = curr;
					off++;
					n--;
				}
			}

			byte[] data;
			try {
				data = new byte[(int)sz];
			} catch (OutOfMemoryError e) {
				throw new LargeObjectException.OutOfMemory(e);
			}

			System.arraycopy(head, 0, data, 0, head.length);
			IO.readFully(stream, data, off, (int) (sz-off));
			return new RawText(data, RawParseUtils.lineMapOrBinary(data, 0, (int) sz));
		}
	}
}
