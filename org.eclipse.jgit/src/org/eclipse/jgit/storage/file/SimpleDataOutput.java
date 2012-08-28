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

package org.eclipse.jgit.storage.file;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.util.NB;

/**
 * An implementation of {@link DataOutput} that only handles
 * {@link #writeInt(int)} and {@link #writeLong(long)} using the Git conversion
 * utilities for network byte order handling. This is needed to write
 * {@link javaewah.EWAHCompressedBitmap}s.
 */
class SimpleDataOutput implements DataOutput {
	private final OutputStream fd;

	private final byte[] buf = new byte[8];

	SimpleDataOutput(OutputStream fd) {
		this.fd = fd;
	}

	public void writeInt(int v) throws IOException {
		NB.encodeInt32(buf, 0, v);
		fd.write(buf, 0, 4);
	}

	public void writeLong(long v) throws IOException {
		NB.encodeInt64(buf, 0, v);
		fd.write(buf, 0, 8);
	}

	public void write(int b) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void write(byte[] b) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void write(byte[] b, int off, int len) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeBoolean(boolean v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeByte(int v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeShort(int v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeChar(int v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeFloat(float v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeDouble(double v) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeBytes(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeChars(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	public void writeUTF(String s) throws IOException {
		throw new UnsupportedOperationException();
	}
}