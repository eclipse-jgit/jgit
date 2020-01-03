/*
 * Copyright (C) 2011, 2012 Google Inc. and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
