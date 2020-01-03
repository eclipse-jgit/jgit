/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * Input/Output utilities
 */
public class IO {

	/**
	 * Read an entire local file into memory as a byte array.
	 *
	 * @param path
	 *            location of the file to read.
	 * @return complete contents of the requested local file.
	 * @throws java.io.FileNotFoundException
	 *             the file does not exist.
	 * @throws java.io.IOException
	 *             the file exists, but its contents cannot be read.
	 */
	public static final byte[] readFully(File path)
			throws FileNotFoundException, IOException {
		return IO.readFully(path, Integer.MAX_VALUE);
	}

	/**
	 * Read at most limit bytes from the local file into memory as a byte array.
	 *
	 * @param path
	 *            location of the file to read.
	 * @param limit
	 *            maximum number of bytes to read, if the file is larger than
	 *            only the first limit number of bytes are returned
	 * @return complete contents of the requested local file. If the contents
	 *         exceeds the limit, then only the limit is returned.
	 * @throws java.io.FileNotFoundException
	 *             the file does not exist.
	 * @throws java.io.IOException
	 *             the file exists, but its contents cannot be read.
	 */
	public static final byte[] readSome(File path, int limit)
			throws FileNotFoundException, IOException {
		try (SilentFileInputStream in = new SilentFileInputStream(path)) {
			byte[] buf = new byte[limit];
			int cnt = 0;
			for (;;) {
				int n = in.read(buf, cnt, buf.length - cnt);
				if (n <= 0)
					break;
				cnt += n;
			}
			if (cnt == buf.length)
				return buf;
			byte[] res = new byte[cnt];
			System.arraycopy(buf, 0, res, 0, cnt);
			return res;
		}
	}

	/**
	 * Read an entire local file into memory as a byte array.
	 *
	 * @param path
	 *            location of the file to read.
	 * @param max
	 *            maximum number of bytes to read, if the file is larger than
	 *            this limit an IOException is thrown.
	 * @return complete contents of the requested local file.
	 * @throws java.io.FileNotFoundException
	 *             the file does not exist.
	 * @throws java.io.IOException
	 *             the file exists, but its contents cannot be read.
	 */
	public static final byte[] readFully(File path, int max)
			throws FileNotFoundException, IOException {
		try (SilentFileInputStream in = new SilentFileInputStream(path)) {
			long sz = Math.max(path.length(), 1);
			if (sz > max)
				throw new IOException(MessageFormat.format(
						JGitText.get().fileIsTooLarge, path));

			byte[] buf = new byte[(int) sz];
			int valid = 0;
			for (;;) {
				if (buf.length == valid) {
					if (buf.length == max) {
						int next = in.read();
						if (next < 0)
							break;

						throw new IOException(MessageFormat.format(
								JGitText.get().fileIsTooLarge, path));
					}

					byte[] nb = new byte[Math.min(buf.length * 2, max)];
					System.arraycopy(buf, 0, nb, 0, valid);
					buf = nb;
				}
				int n = in.read(buf, valid, buf.length - valid);
				if (n < 0)
					break;
				valid += n;
			}
			if (valid < buf.length) {
				byte[] nb = new byte[valid];
				System.arraycopy(buf, 0, nb, 0, valid);
				buf = nb;
			}
			return buf;
		}
	}

	/**
	 * Read an entire input stream into memory as a ByteBuffer.
	 *
	 * Note: The stream is read to its end and is not usable after calling this
	 * method. The caller is responsible for closing the stream.
	 *
	 * @param in
	 *            input stream to be read.
	 * @param sizeHint
	 *            a hint on the approximate number of bytes contained in the
	 *            stream, used to allocate temporary buffers more efficiently
	 * @return complete contents of the input stream. The ByteBuffer always has
	 *         a writable backing array, with {@code position() == 0} and
	 *         {@code limit()} equal to the actual length read. Callers may rely
	 *         on obtaining the underlying array for efficient data access. If
	 *         {@code sizeHint} was too large, the array may be over-allocated,
	 *         resulting in {@code limit() < array().length}.
	 * @throws java.io.IOException
	 *             there was an error reading from the stream.
	 */
	public static ByteBuffer readWholeStream(InputStream in, int sizeHint)
			throws IOException {
		byte[] out = new byte[sizeHint];
		int pos = 0;
		while (pos < out.length) {
			int read = in.read(out, pos, out.length - pos);
			if (read < 0)
				return ByteBuffer.wrap(out, 0, pos);
			pos += read;
		}

		int last = in.read();
		if (last < 0)
			return ByteBuffer.wrap(out, 0, pos);

		try (TemporaryBuffer.Heap tmp = new TemporaryBuffer.Heap(
				Integer.MAX_VALUE)) {
			tmp.write(out);
			tmp.write(last);
			tmp.copy(in);
			return ByteBuffer.wrap(tmp.toByteArray());
		}
	}

	/**
	 * Read the entire byte array into memory, or throw an exception.
	 *
	 * @param fd
	 *            input stream to read the data from.
	 * @param dst
	 *            buffer that must be fully populated, [off, off+len).
	 * @param off
	 *            position within the buffer to start writing to.
	 * @param len
	 *            number of bytes that must be read.
	 * @throws EOFException
	 *             the stream ended before dst was fully populated.
	 * @throws java.io.IOException
	 *             there was an error reading from the stream.
	 */
	public static void readFully(final InputStream fd, final byte[] dst,
			int off, int len) throws IOException {
		while (len > 0) {
			final int r = fd.read(dst, off, len);
			if (r <= 0)
				throw new EOFException(JGitText.get().shortReadOfBlock);
			off += r;
			len -= r;
		}
	}

	/**
	 * Read as much of the array as possible from a channel.
	 *
	 * @param channel
	 *            channel to read data from.
	 * @param dst
	 *            buffer that must be fully populated, [off, off+len).
	 * @param off
	 *            position within the buffer to start writing to.
	 * @param len
	 *            number of bytes that should be read.
	 * @return number of bytes actually read.
	 * @throws java.io.IOException
	 *             there was an error reading from the channel.
	 */
	public static int read(ReadableByteChannel channel, byte[] dst, int off,
			int len) throws IOException {
		if (len == 0)
			return 0;
		int cnt = 0;
		while (0 < len) {
			int r = channel.read(ByteBuffer.wrap(dst, off, len));
			if (r <= 0)
				break;
			off += r;
			len -= r;
			cnt += r;
		}
		return cnt != 0 ? cnt : -1;
	}

	/**
	 * Read the entire byte array into memory, unless input is shorter
	 *
	 * @param fd
	 *            input stream to read the data from.
	 * @param dst
	 *            buffer that must be fully populated, [off, off+len).
	 * @param off
	 *            position within the buffer to start writing to.
	 * @return number of bytes in buffer or stream, whichever is shortest
	 * @throws java.io.IOException
	 *             there was an error reading from the stream.
	 */
	public static int readFully(InputStream fd, byte[] dst, int off)
			throws IOException {
		int r;
		int len = 0;
		while ((r = fd.read(dst, off, dst.length - off)) >= 0
				&& len < dst.length) {
			off += r;
			len += r;
		}
		return len;
	}

	/**
	 * Skip an entire region of an input stream.
	 * <p>
	 * The input stream's position is moved forward by the number of requested
	 * bytes, discarding them from the input. This method does not return until
	 * the exact number of bytes requested has been skipped.
	 *
	 * @param fd
	 *            the stream to skip bytes from.
	 * @param toSkip
	 *            total number of bytes to be discarded. Must be &gt;= 0.
	 * @throws EOFException
	 *             the stream ended before the requested number of bytes were
	 *             skipped.
	 * @throws java.io.IOException
	 *             there was an error reading from the stream.
	 */
	public static void skipFully(InputStream fd, long toSkip)
			throws IOException {
		while (toSkip > 0) {
			final long r = fd.skip(toSkip);
			if (r <= 0)
				throw new EOFException(JGitText.get().shortSkipOfBlock);
			toSkip -= r;
		}
	}

	/**
	 * Divides the given string into lines.
	 *
	 * @param s
	 *            the string to read
	 * @return the string divided into lines
	 * @since 2.0
	 */
	public static List<String> readLines(String s) {
		List<String> l = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n') {
				l.add(sb.toString());
				sb.setLength(0);
				continue;
			}
			if (c == '\r') {
				if (i + 1 < s.length()) {
					c = s.charAt(++i);
					l.add(sb.toString());
					sb.setLength(0);
					if (c != '\n') {
						sb.append(c);
					}
					continue;
				}
				// EOF
				l.add(sb.toString());
				break;
			}
			sb.append(c);
		}
		l.add(sb.toString());
		return l;
	}

	/**
	 * Read the next line from a reader.
	 * <p>
	 * Like {@link java.io.BufferedReader#readLine()}, but only treats
	 * {@code \n} as end-of-line, and includes the trailing newline.
	 *
	 * @param in
	 *            the reader to read from.
	 * @param sizeHint
	 *            hint for buffer sizing; 0 or negative for default.
	 * @return the next line from the input, always ending in {@code \n} unless
	 *         EOF was reached.
	 * @throws java.io.IOException
	 *             there was an error reading from the stream.
	 * @since 4.1
	 */
	public static String readLine(Reader in, int sizeHint) throws IOException {
		if (in.markSupported()) {
			if (sizeHint <= 0) {
				sizeHint = 1024;
			}
			StringBuilder sb = new StringBuilder(sizeHint);
			char[] buf = new char[sizeHint];
			while (true) {
				in.mark(sizeHint);
				int n = in.read(buf);
				if (n < 0) {
					in.reset();
					return sb.toString();
				}
				for (int i = 0; i < n; i++) {
					if (buf[i] == '\n') {
						resetAndSkipFully(in, ++i);
						sb.append(buf, 0, i);
						return sb.toString();
					}
				}
				if (n > 0) {
					sb.append(buf, 0, n);
				}
				resetAndSkipFully(in, n);
			}
		}
		StringBuilder buf = sizeHint > 0 ? new StringBuilder(sizeHint)
				: new StringBuilder();
		int i;
		while ((i = in.read()) != -1) {
			char c = (char) i;
			buf.append(c);
			if (c == '\n') {
				break;
			}
		}
		return buf.toString();
	}

	private static void resetAndSkipFully(Reader fd, long toSkip) throws IOException {
		fd.reset();
		while (toSkip > 0) {
			long r = fd.skip(toSkip);
			if (r <= 0) {
				throw new EOFException(JGitText.get().shortSkipOfBlock);
			}
			toSkip -= r;
		}
	}

	private IO() {
		// Don't create instances of a static only utility.
	}
}
