/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.internal.storage.pack.PackOutputStream;

/**
 * A {@link ByteWindow} with an underlying byte array for storage.
 */
final class ByteArrayWindow extends ByteWindow {
	private final byte[] array;

	ByteArrayWindow(PackFile pack, long o, byte[] b) {
		super(pack, o, b.length);
		array = b;
	}

	/** {@inheritDoc} */
	@Override
	protected int copy(int p, byte[] b, int o, int n) {
		n = Math.min(array.length - p, n);
		System.arraycopy(array, p, b, o, n);
		return n;
	}

	/** {@inheritDoc} */
	@Override
	protected int setInput(int pos, Inflater inf)
			throws DataFormatException {
		int n = array.length - pos;
		inf.setInput(array, pos, n);
		return n;
	}

	void crc32(CRC32 out, long pos, int cnt) {
		out.update(array, (int) (pos - start), cnt);
	}

	@Override
	void write(PackOutputStream out, long pos, int cnt)
			throws IOException {
		int ptr = (int) (pos - start);
		out.write(array, ptr, cnt);
	}

	void check(Inflater inf, byte[] tmp, long pos, int cnt)
			throws DataFormatException {
		inf.setInput(array, (int) (pos - start), cnt);
		while (inf.inflate(tmp, 0, tmp.length) > 0)
			continue;
	}
}
