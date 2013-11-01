/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.util.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.eclipse.jgit.lib.Constants;
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
 * Internally {@link RawParseUtils#decode(byte[])} is used by {@code toString()}
 * tries to work out a reasonably correct character set for the raw data.
 */
public class MessageWriter extends Writer {
	private final ByteArrayOutputStream buf;

	private final OutputStreamWriter enc;

	/** Create an empty writer. */
	public MessageWriter() {
		buf = new ByteArrayOutputStream();
		enc = new OutputStreamWriter(getRawStream(), Constants.CHARSET);
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		synchronized (buf) {
			enc.write(cbuf, off, len);
			enc.flush();
		}
	}

	/**
	 * @return the underlying byte stream that character writes to this writer
	 *         drop into. Writes to this stream should should be in UTF-8.
	 */
	public OutputStream getRawStream() {
		return buf;
	}

	@Override
	public void close() throws IOException {
		// Do nothing, we are buffered with no resources.
	}

	@Override
	public void flush() throws IOException {
		// Do nothing, we are buffered with no resources.
	}

	/** @return string version of all buffered data. */
	@Override
	public String toString() {
		return RawParseUtils.decode(buf.toByteArray());
	}
}
