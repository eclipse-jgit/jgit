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
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.internal.storage.pack.PackOutputStream;

/**
 * A window for accessing git packs using a {@link ByteBuffer} for storage.
 *
 * @see ByteWindow
 */
final class ByteBufferWindow extends ByteWindow {
	private final ByteBuffer buffer;

	ByteBufferWindow(PackFile pack, long o, ByteBuffer b) {
		super(pack, o, b.capacity());
		buffer = b;
	}

	/** {@inheritDoc} */
	@Override
	protected int copy(int p, byte[] b, int o, int n) {
		final ByteBuffer s = buffer.slice();
		s.position(p);
		n = Math.min(s.remaining(), n);
		s.get(b, o, n);
		return n;
	}

	@Override
	void write(PackOutputStream out, long pos, int cnt)
			throws IOException {
		final ByteBuffer s = buffer.slice();
		s.position((int) (pos - start));

		while (0 < cnt) {
			byte[] buf = out.getCopyBuffer();
			int n = Math.min(cnt, buf.length);
			s.get(buf, 0, n);
			out.write(buf, 0, n);
			cnt -= n;
		}
	}

	/** {@inheritDoc} */
	@Override
	protected int setInput(int pos, Inflater inf)
			throws DataFormatException {
		final ByteBuffer s = buffer.slice();
		s.position(pos);
		final byte[] tmp = new byte[Math.min(s.remaining(), 512)];
		s.get(tmp, 0, tmp.length);
		inf.setInput(tmp, 0, tmp.length);
		return tmp.length;
	}
}
