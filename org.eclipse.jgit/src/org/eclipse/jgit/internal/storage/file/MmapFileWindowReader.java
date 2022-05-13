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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

/**
 * Map window of data to virtual memory using NIO and access it via ByteBuffer
 */
class MmapFileWindowReader implements FileWindowReader {
	private final Pack pack;
	private RandomAccessFile fd;
	private long length;

	MmapFileWindowReader(Pack pack) {
		this.pack = pack;
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
	public FileWindowReader open() throws IOException {
		fd = new RandomAccessFile(pack.getPackFile(), "r"); //$NON-NLS-1$
		length = fd.length();
		return this;
	}

	@Override
	public long length() throws IOException {
		return length;
	}

	@Override
	public ByteWindow read(long pos, int size) throws IOException {
		if (length < pos + size) {
			size = (int) (length - pos);
		}

		MappedByteBuffer map;
		try {
			map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
		} catch (IOException ioe1) {
			// The most likely reason this failed is the JVM has run out
			// of virtual memory. We need to discard quickly, and try to
			// force the GC to finalize and release any existing mappings.
			//
			System.gc();
			System.runFinalization();
			map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
		}

		if (map.hasArray()) {
			return new ByteArrayWindow(pack, pos, map.array());
		}
		return new ByteBufferWindow(pack, pos, map);
	}

	@Override
	public void readRaw(byte[] b, long off, int len) throws IOException {
		fd.seek(off);
		fd.readFully(b, 0, len);
	}

}
