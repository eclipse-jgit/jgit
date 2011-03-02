/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.storage.pack.PackOutputStream;

/**
 * A window for accessing git packs using a {@link ByteBuffer} for storage.
 *
 * @see ByteWindow
 */
final class ByteBufferWindow extends ByteWindow {
	private final ByteBuffer buffer;

	ByteBufferWindow(final PackFile pack, final long o, final ByteBuffer b) {
		super(pack, o, b.capacity());
		buffer = b;
	}

	@Override
	protected int copy(final int p, final byte[] b, final int o, int n) {
		final ByteBuffer s = buffer.slice();
		s.position(p);
		n = Math.min(s.remaining(), n);
		s.get(b, o, n);
		return n;
	}

	@Override
	void write(PackOutputStream out, long pos, int cnt, MessageDigest digest)
			throws IOException {
		final ByteBuffer s = buffer.slice();
		s.position((int) (pos - start));

		while (0 < cnt) {
			byte[] buf = out.getCopyBuffer();
			int n = Math.min(cnt, buf.length);
			s.get(buf, 0, n);
			out.write(buf, 0, n);
			if (digest != null)
				digest.update(buf, 0, n);
			cnt -= n;
		}
	}

	@Override
	protected int setInput(final int pos, final Inflater inf)
			throws DataFormatException {
		final ByteBuffer s = buffer.slice();
		s.position(pos);
		final byte[] tmp = new byte[Math.min(s.remaining(), 512)];
		s.get(tmp, 0, tmp.length);
		inf.setInput(tmp, 0, tmp.length);
		return tmp.length;
	}
}
