/*
 * Copyright (C) 2011, 2012 Google Inc. and others.
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
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Output stream to create a file on the DFS.
 *
 * @see DfsObjDatabase#writeFile(DfsPackDescription, PackExt)
 */
public abstract class DfsOutputStream extends OutputStream {
	/**
	 * Get the recommended alignment for writing.
	 * <p>
	 * Starting a write at multiples of the blockSize is more efficient than
	 * starting a write at any other position. If 0 or -1 the channel does not
	 * have any specific block size recommendation.
	 * <p>
	 * Channels should not recommend large block sizes. Sizes up to 1-4 MiB may
	 * be reasonable, but sizes above that may be horribly inefficient.
	 *
	 * @return recommended alignment size for randomly positioned reads. Does
	 *         not need to be a power of 2.
	 */
	public int blockSize() {
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		write(new byte[] { (byte) b });
	}

	/** {@inheritDoc} */
	@Override
	public abstract void write(byte[] buf, int off, int len) throws IOException;

	/**
	 * Read back a portion of already written data.
	 * <p>
	 * The writing position of the output stream is not affected by a read.
	 *
	 * @param position
	 *            offset to read from.
	 * @param buf
	 *            buffer to populate. Up to {@code buf.remaining()} bytes will
	 *            be read from {@code position}.
	 * @return number of bytes actually read.
	 * @throws java.io.IOException
	 *             reading is not supported, or the read cannot be performed due
	 *             to DFS errors.
	 */
	public abstract int read(long position, ByteBuffer buf) throws IOException;
}
