/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * Combines messages from an OutputStream (hopefully in UTF-8) and a Writer.
 * <p>
 * This class is primarily meant for {@code BaseConnection} in contexts where a
 * standard error stream from a command execution, as well as messages from a
 * side-band channel, need to be combined together into a buffer to represent
 * the complete set of messages from a remote repository.
 * <p>
 * Writes made to the writer are re-encoded as UTF-8 and interleaved into the
 * buffer that {@link #getRawStream()} also writes to.
 * <p>
 * {@link #toString()} returns all written data, after converting it to a String
 * under the assumption of UTF-8 encoding.
 * <p>
 * Internally {@link org.eclipse.jgit.util.RawParseUtils#decode(byte[])} is used
 * by {@code toString()} tries to work out a reasonably correct character set
 * for the raw data.
 */
public class MessageWriter extends Writer {
	private final ByteArrayOutputStream buf;

	private final OutputStreamWriter enc;

	/**
	 * Create an empty writer.
	 */
	public MessageWriter() {
		buf = new ByteArrayOutputStream();
		enc = new OutputStreamWriter(getRawStream(), UTF_8);
	}

	/** {@inheritDoc} */
	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		synchronized (buf) {
			enc.write(cbuf, off, len);
			enc.flush();
		}
	}

	/**
	 * Get the underlying byte stream that character writes to this writer drop
	 * into.
	 *
	 * @return the underlying byte stream that character writes to this writer
	 *         drop into. Writes to this stream should should be in UTF-8.
	 */
	public OutputStream getRawStream() {
		return buf;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		// Do nothing, we are buffered with no resources.
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		// Do nothing, we are buffered with no resources.
	}

	/** @return string version of all buffered data. */
	/** {@inheritDoc} */
	@Override
	public String toString() {
		return RawParseUtils.decode(buf.toByteArray());
	}
}
