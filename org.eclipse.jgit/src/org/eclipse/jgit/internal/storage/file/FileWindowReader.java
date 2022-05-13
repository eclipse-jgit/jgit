/*
 * Copyright (C) 2022 Matthias Sohn <matthias.sohn@sap.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.EOFException;
import java.io.IOException;

/**
 * Interface for reading a contiguous window of data from a file
 */
public interface FileWindowReader extends AutoCloseable {

	/**
	 * Open the reader
	 *
	 * @return this reader
	 *
	 * @throws IOException
	 */
	FileWindowReader open() throws IOException;

	/**
	 * Length of this file in bytes
	 *
	 * @return length of this file in bytes
	 * @throws IOException
	 */
	long length() throws IOException;

	/**
	 * Read a window of data from a file. Map the file window to memory if
	 * supported by this reader. Mapping a file window to memory incurs some
	 * overhead. Use {@code #readRaw} for reading a small region of the file to
	 * avoid this overhead.
	 *
	 * @param pos
	 *            position in file where window starts
	 * @param size
	 *            size of the window to mmap
	 * @return the mapped page of the file
	 * @throws IOException
	 */
	ByteWindow read(long pos, int size) throws IOException;

	/**
	 * Reads exactly {@code len} bytes from this file into the byte array,
	 * starting at {@code off}. This method reads repeatedly from the file until
	 * the requested number of bytes are read. This method blocks until the
	 * requested number of bytes are read, the end of the stream is detected, or
	 * an exception is thrown.
	 *
	 * @param b
	 *            the buffer into which the data is read.
	 * @param off
	 *            the start offset into the data array {@code b}.
	 * @param len
	 *            the number of bytes to read.
	 * @throws NullPointerException
	 *             if {@code b} is {@code null}.
	 * @throws IndexOutOfBoundsException
	 *             if {@code off} is negative, {@code len} is negative, or
	 *             {@code len} is greater than {@code b.length - off}.
	 * @throws EOFException
	 *             if this file reaches the end before reading all the bytes.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	void readRaw(byte b[], long off, int len) throws IOException;
}
