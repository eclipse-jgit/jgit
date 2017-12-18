/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;

/**
 * An implementation of DataInput that only handles readInt() and readLong()
 * using the Git conversion utilities for network byte order handling. This is
 * needed to read {@link com.googlecode.javaewah.EWAHCompressedBitmap}s.
 */
class SimpleDataInput implements DataInput {
	private final InputStream fd;

	private final byte[] buf = new byte[8];

	SimpleDataInput(InputStream fd) {
		this.fd = fd;
	}

	/** {@inheritDoc} */
	@Override
	public int readInt() throws IOException {
		readFully(buf, 0, 4);
		return NB.decodeInt32(buf, 0);
	}

	/** {@inheritDoc} */
	@Override
	public long readLong() throws IOException {
		readFully(buf, 0, 8);
		return NB.decodeInt64(buf, 0);
	}

	/**
	 * Read unsigned int
	 *
	 * @return a long.
	 * @throws java.io.IOException
	 *             if any.
	 */
	public long readUnsignedInt() throws IOException {
		readFully(buf, 0, 4);
		return NB.decodeUInt32(buf, 0);
	}

	/** {@inheritDoc} */
	@Override
	public void readFully(byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	/** {@inheritDoc} */
	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		IO.readFully(fd, b, off, len);
	}

	/** {@inheritDoc} */
	@Override
	public int skipBytes(int n) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public boolean readBoolean() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public byte readByte() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public int readUnsignedByte() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public short readShort() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public int readUnsignedShort() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public char readChar() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public float readFloat() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public double readDouble() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public String readLine() throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public String readUTF() throws IOException {
		throw new UnsupportedOperationException();
	}
}
