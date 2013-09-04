/*
 * Copyright (C) 2008-2011, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.internal.storage.pack.PackOutputStream;

/** A cached slice of a {@link DfsPackFile}. */
final class DfsBlock {
	final DfsPackKey pack;

	final long start;

	final long end;

	private final byte[] block;

	DfsBlock(DfsPackKey p, long pos, byte[] buf) {
		pack = p;
		start = pos;
		end = pos + buf.length;
		block = buf;
	}

	int size() {
		return block.length;
	}

	int remaining(long pos) {
		int ptr = (int) (pos - start);
		return block.length - ptr;
	}

	boolean contains(DfsPackKey want, long pos) {
		return pack == want && start <= pos && pos < end;
	}

	int copy(long pos, byte[] dstbuf, int dstoff, int cnt) {
		int ptr = (int) (pos - start);
		return copy(ptr, dstbuf, dstoff, cnt);
	}

	int copy(int p, byte[] b, int o, int n) {
		n = Math.min(block.length - p, n);
		System.arraycopy(block, p, b, o, n);
		return n;
	}

	void setInput(Inflater inf, long pos) {
		int ptr = (int) (pos - start);
		inf.setInput(block, ptr, block.length - ptr);
	}

	void crc32(CRC32 out, long pos, int cnt) {
		int ptr = (int) (pos - start);
		out.update(block, ptr, cnt);
	}

	void write(PackOutputStream out, long pos, int cnt, MessageDigest digest)
			throws IOException {
		int ptr = (int) (pos - start);
		out.write(block, ptr, cnt);
		if (digest != null)
			digest.update(block, ptr, cnt);
	}

	void check(Inflater inf, byte[] tmp, long pos, int cnt)
			throws DataFormatException {
		// Unlike inflate() above the exact byte count is known by the caller.
		// Push all of it in a single invocation to avoid unnecessary loops.
		//
		inf.setInput(block, (int) (pos - start), cnt);
		while (inf.inflate(tmp, 0, tmp.length) > 0)
			continue;
	}
}
