/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * An input stream which canonicalizes EOLs bytes on the fly
 * to '\n'.
 *
 * Note: Make sure to apply this InputStream only to text files!
 */
public class EolCanonicalizingInputStream extends InputStream {

	private final byte[] BUFFER = new byte[8092];
	private final PushbackInputStream is;

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in raw input stream
	 */
	public EolCanonicalizingInputStream(InputStream in) {
		this.is = new PushbackInputStream(in);
	}

	@Override
	public int read() throws IOException {
		final int b = is.read();
		if (b != '\r') {
			return b;
		}

		final int future = is.read();
		if (future == '\n') {
			return '\n';
		}

		is.unread(future);
		return b;
	}

	@Override
	public int read(byte[] bs, int off, int len) throws IOException {
		// Having this method implemented compared to having only read() implemented, gives
		// a speed-up of about factor 30 for my local jgit repository (files are most likely
		// all in disk cache).

		if (len == 0) {
			return 0;
		}

		int bsIndex = 0;
		for (; bsIndex < len;) {
			final int readNow = is.read(BUFFER, 0, Math.min(len - bsIndex, BUFFER.length));
			if (readNow == -1) {
				break;
			}

			for (int bufIndex = 0; bufIndex < readNow; bufIndex++) {
				byte b = BUFFER[bufIndex];
				if (b == '\r') {
					if (bufIndex < readNow - 1) {
						final byte future = BUFFER[bufIndex + 1];
						if (future == '\n') {
							continue;
						}
					}
					else {
						final int future = is.read();
						is.unread(future);
						if (future == '\n') {
							continue;
						}
					}
				}

				bs[off + bsIndex++] = b;
			}
		}

		return bsIndex == 0 ? -1 : bsIndex;
	}

	@Override
	public void close() throws IOException {
		try {
			super.close();
		} finally {
			is.close();
		}
	}
}
