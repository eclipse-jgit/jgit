/*
 * Copyright (C) 2010, 2013 Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2015, 2020 Ivan Motsch <ivan.motsch@bsiag.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.jgit.diff.RawText;

/**
 * An InputStream that normalizes CRLF to LF.
 * <p>
 * Existing single CR are not changed to LF but are retained as is.
 * </p>
 * <p>
 * Optionally, a binary check on the first 8kB is performed and in case of
 * binary files, canonicalization is turned off (for the complete file). If
 * binary checking determines that the input is CR/LF-delimited text and the
 * stream has been created for checkout, canonicalization is also turned off.
 * </p>
 *
 * @since 4.3
 */
public class AutoLFInputStream extends InputStream {

	// This is the former EolCanonicalizingInputStream with a new name in order
	// to have same naming for all LF / CRLF streams.

	/**
	 * Flags for controlling auto-detection of binary vs. text content (for
	 * text=auto).
	 *
	 * @since 5.9
	 */
	public enum StreamFlag {
		/**
		 * Check the first 8kB for binary content and switch off
		 * canonicalization off for the whole file if so.
		 */
		DETECT_BINARY,
		/**
		 * If {@link #DETECT_BINARY} is set, throw an {@link IsBinaryException}
		 * if binary content is detected.
		 */
		ABORT_IF_BINARY,
		/**
		 * If {@link #DETECT_BINARY} is set and content is found to be CR-LF
		 * delimited text, switch off canonicalization.
		 */
		FOR_CHECKOUT
	}

	private final byte[] single = new byte[1];

	private final byte[] buf = new byte[8 * 1024];

	private final InputStream in;

	private int cnt;

	private int ptr;

	/**
	 * Set to {@code true} if no CR/LF processing is to be done: if the input is
	 * binary data, or CR/LF-delimited text and {@link StreamFlag#FOR_CHECKOUT}
	 * was given.
	 */
	private boolean passAsIs;

	/**
	 * Set to {@code true} if the input was detected to be binary data.
	 */
	private boolean isBinary;

	private boolean detectBinary;

	private final boolean abortIfBinary;

	private final boolean forCheckout;

	/**
	 * A special exception thrown when {@link AutoLFInputStream} is told to
	 * throw an exception when attempting to read a binary file. The exception
	 * may be thrown at any stage during reading.
	 *
	 * @since 3.3
	 */
	public static class IsBinaryException extends IOException {
		private static final long serialVersionUID = 1L;

		IsBinaryException() {
			super();
		}
	}

	/**
	 * Factory method for creating an {@link AutoLFInputStream} with the
	 * specified {@link StreamFlag flags}.
	 *
	 * @param in
	 *            raw input stream
	 * @param flags
	 *            {@link StreamFlag}s controlling the stream behavior
	 * @return a new {@link AutoLFInputStream}
	 * @since 5.9
	 */
	public static AutoLFInputStream create(InputStream in,
			StreamFlag... flags) {
		if (flags == null) {
			return new AutoLFInputStream(in, null);
		}
		EnumSet<StreamFlag> set = EnumSet.noneOf(StreamFlag.class);
		set.addAll(Arrays.asList(flags));
		return new AutoLFInputStream(in, set);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream.
	 *
	 * @param in
	 *            raw input stream
	 * @param flags
	 *            {@link StreamFlag}s controlling the stream behavior;
	 *            {@code null} is treated as an empty set
	 * @since 5.9
	 */
	public AutoLFInputStream(InputStream in, Set<StreamFlag> flags) {
		this.in = in;
		this.detectBinary = flags != null
				&& flags.contains(StreamFlag.DETECT_BINARY);
		this.abortIfBinary = flags != null
				&& flags.contains(StreamFlag.ABORT_IF_BINARY);
		this.forCheckout = flags != null
				&& flags.contains(StreamFlag.FOR_CHECKOUT);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream.
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @since 2.0
	 * @deprecated since 5.9, use {@link #create(InputStream, StreamFlag...)}
	 *             instead
	 */
	@Deprecated
	public AutoLFInputStream(InputStream in, boolean detectBinary) {
		this(in, detectBinary, false);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream.
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @param abortIfBinary
	 *            throw an IOException if the file is binary
	 * @since 3.3
	 * @deprecated since 5.9, use {@link #create(InputStream, StreamFlag...)}
	 *             instead
	 */
	@Deprecated
	public AutoLFInputStream(InputStream in, boolean detectBinary,
			boolean abortIfBinary) {
		this.in = in;
		this.detectBinary = detectBinary;
		this.abortIfBinary = abortIfBinary;
		this.forCheckout = false;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		final int read = read(single, 0, 1);
		return read == 1 ? single[0] & 0xff : -1;
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] bs, int off, int len)
			throws IOException {
		if (len == 0)
			return 0;

		if (cnt == -1)
			return -1;

		int i = off;
		final int end = off + len;

		while (i < end) {
			if (ptr == cnt && !fillBuffer()) {
				break;
			}

			byte b = buf[ptr++];
			if (passAsIs || b != '\r') {
				// Logic for binary files ends here
				bs[i++] = b;
				continue;
			}

			if (ptr == cnt && !fillBuffer()) {
				bs[i++] = '\r';
				break;
			}

			if (buf[ptr] == '\n') {
				bs[i++] = '\n';
				ptr++;
			} else
				bs[i++] = '\r';
		}

		return i == off ? -1 : i - off;
	}

	/**
	 * Whether the stream has detected as a binary so far.
	 *
	 * @return true if the stream has detected as a binary so far.
	 * @since 3.3
	 */
	public boolean isBinary() {
		return isBinary;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		in.close();
	}

	private boolean fillBuffer() throws IOException {
		cnt = 0;
		while (cnt < buf.length) {
			int n = in.read(buf, cnt, buf.length - cnt);
			if (n < 0) {
				break;
			}
			cnt += n;
		}
		if (cnt < 1) {
			cnt = -1;
			return false;
		}
		if (detectBinary) {
			isBinary = RawText.isBinary(buf, cnt);
			passAsIs = isBinary;
			detectBinary = false;
			if (isBinary && abortIfBinary) {
				throw new IsBinaryException();
			}
			if (!passAsIs && forCheckout) {
				passAsIs = RawText.isCrLfText(buf, cnt);
			}
		}
		ptr = 0;
		return true;
	}
}
