/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

/** Loader for a large non-delta object. */
class LargeNonDeltaObject extends ObjectLoader {
	private final int type;

	private final long sz;

	private final int pos;

	private final DhtReader ctx;

	private final ChunkMeta meta;

	private PackChunk firstChunk;

	LargeNonDeltaObject(int type, long sz, PackChunk pc, int pos, DhtReader ctx) {
		this.type = type;
		this.sz = sz;
		this.pos = pos;
		this.ctx = ctx;
		this.meta = pc.getMeta();
		firstChunk = pc;
	}

	@Override
	public boolean isLarge() {
		return true;
	}

	@Override
	public byte[] getCachedBytes() throws LargeObjectException {
		throw new LargeObjectException.ExceedsByteArrayLimit();
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public long getSize() {
		return sz;
	}

	@Override
	public ObjectStream openStream() throws MissingObjectException, IOException {
		PackChunk pc = firstChunk;
		if (pc != null)
			firstChunk = null;
		else
			pc = ctx.getChunk(ChunkKey.fromString(meta.getFragment(0)));

		InputStream in = new ChunkInputStream(meta, ctx, pos, pc);
		in = new BufferedInputStream(new InflaterInputStream(in), 8192);
		return new ObjectStream.Filter(type, sz, in);
	}

	private static class ChunkInputStream extends InputStream {
		private final ChunkMeta meta;

		private final DhtReader ctx;

		private int ptr;

		private PackChunk pc;

		private int fragment;

		ChunkInputStream(ChunkMeta meta, DhtReader ctx, int pos, PackChunk pc) {
			this.ctx = ctx;
			this.meta = meta;
			this.ptr = pos;
			this.pc = pc;
		}

		@Override
		public int read(byte[] dstbuf, int dstptr, int dstlen)
				throws IOException {
			if (0 == dstlen)
				return 0;

			int n = pc.read(ptr, dstbuf, dstptr, dstlen);
			if (n == 0) {
				if (fragment == meta.getFragmentCount())
					return -1;

				pc = ctx.getChunk(ChunkKey.fromString(
						meta.getFragment(++fragment)));
				ptr = 0;
				n = pc.read(ptr, dstbuf, dstptr, dstlen);
				if (n == 0)
					return -1;
			}
			ptr += n;
			return n;
		}

		@Override
		public int read() throws IOException {
			byte[] tmp = new byte[1];
			int n = read(tmp, 0, 1);
			return n == 1 ? tmp[0] & 0xff : -1;
		}
	}
}
