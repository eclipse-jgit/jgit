/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.util.NB;

/**
 * An implementation of {@link DataOutput} that only handles
 * {@link #writeInt(int)} and {@link #writeLong(long)} using the Git conversion
 * utilities for network byte order handling. This is needed to write
 * {@link com.googlecode.javaewah.EWAHCompressedBitmap}s.
 */
class SimpleDataOutput implements DataOutput {
	private final OutputStream fd;

	private final byte[] buf = new byte[8];

	SimpleDataOutput(OutputStream fd) {
		this.fd = fd;
	}

	/** {@inheritDoc} */
	@Override
	public void writeShort(int v) throws IOException {
		NB.encodeInt16(buf, 0, v);
		fd.write(buf, 0, 2);
	}

	/** {@inheritDoc} */
	@Override
	public void writeInt(int v) throws IOException {
		NB.encodeInt32(buf, 0, v);
		fd.write(buf, 0, 4);
	}

	/** {@inheritDoc} */
	@Override
	public void writeLong(long v) throws IOException {
		NB.encodeInt64(buf, 0, v);
		fd.write(buf, 0, 8);
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeBoolean(boolean v) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeByte(int v) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeChar(int v) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeFloat(float v) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeDouble(double v) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeBytes(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeChars(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public void writeUTF(String s) throws IOException {
		throw new UnsupportedOperationException();
	}
}
