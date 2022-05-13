/*
 * Copyright (C) 2008, 2009 Google Inc.
 * Copyright (C) 2008, 2022 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Read from file and copy window of data to ByteArrayWindow allocated on the
 * heap
 */
public class HeapFileWindowReader implements FileWindowReader {
	private final Pack pack;
	private RandomAccessFile fd;

	private long length;

	HeapFileWindowReader(Pack pack) {
		this.pack = pack;
	}

	@Override
	public FileWindowReader open() throws IOException {
		fd = new RandomAccessFile(pack.getPackFile(), "r"); //$NON-NLS-1$
		length = fd.length();
		return this;
	}

	@Override
	public void close() throws Exception {
		if (fd != null) {
			RandomAccessFile toClose = fd;
			fd = null;
			toClose.close();
		}
	}

	@Override
	public long length() {
		return length;
	}

	@Override
	public ByteWindow read(long pos, int size) throws IOException {
		if (length < pos + size) {
			size = (int) (length - pos);
		}
		final byte[] buf = new byte[size];
		fd.seek(pos);
		fd.readFully(buf, 0, size);
		return new ByteArrayWindow(pack, pos, buf);
	}

	@Override
	public void readRaw(byte[] b, long off, int len) throws IOException {
		fd.seek(off);
		fd.readFully(b, 0, len);
	}

}
