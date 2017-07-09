/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Wrapper to assist formatting a reftable to an {@link OutputStream}.
 * <p>
 * Assumes the caller will supply a buffered {@code OutputStream}.
 */
class ReftableOutputStream extends FilterOutputStream {
	private final byte[] tmp = new byte[OBJECT_ID_LENGTH];
	private final int blockSize;

	private int cur;
	private long size;
	private long paddingUsed;
	private int blockCount;

	ReftableOutputStream(OutputStream os, int blockSize) {
		super(os);
		this.blockSize = blockSize;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		cur++;
		size++;
	}

	@Override
	public void write(byte[] b, int off, int cnt) throws IOException {
		out.write(b, off, cnt);
		cur += cnt;
		size += cnt;
	}

	int bytesWrittenInBlock() {
		return cur;
	}

	int bytesAvailableInBlock() {
		return blockSize - cur;
	}

	long paddingUsed() {
		return paddingUsed;
	}

	int blockCount() {
		return blockCount;
	}

	long size() {
		return size;
	}

	static int computeVarintSize(int val) {
		int n = 1;
		for (; (val >>>= 7) != 0; n++) {
			val--;
		}
		return n;
	}

	void writeVarint(int val) throws IOException {
		int n = tmp.length;
		tmp[--n] = (byte) (val & 0x7f);
		while ((val >>>= 7) != 0) {
			tmp[--n] = (byte) (0x80 | (--val & 0x7F));
		}
		write(tmp, n, tmp.length - n);
	}

	void writeInt32(int val) throws IOException {
		NB.encodeInt32(tmp, 0, val);
		write(tmp, 0, 4);
	}

	void write(ObjectId id) throws IOException {
		id.copyRawTo(tmp, 0);
		write(tmp, 0, OBJECT_ID_LENGTH);
	}

	void pad(int len) throws IOException {
		paddingUsed += len;
		Arrays.fill(tmp, (byte) 0);
		while (len > 0) {
			int n = Math.min(len, tmp.length);
			write(tmp, 0, n);
			len -= n;
		}
	}

	void finishBlock(boolean mustFillBlock) throws IOException {
		if (cur < blockSize && mustFillBlock) {
			throw new IOException(JGitText.get().underflowedReftableBlock);
		} else if (cur > blockSize) {
			throw new IOException(JGitText.get().overflowedReftableBlock);
		}
		cur = 0;
		blockCount++;
	}
}
