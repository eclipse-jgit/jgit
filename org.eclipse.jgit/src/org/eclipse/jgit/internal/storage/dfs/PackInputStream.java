/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.io.InputStream;

final class PackInputStream extends InputStream {
	final DfsReader ctx;

	private final DfsPackFile pack;

	private long pos;

	PackInputStream(DfsPackFile pack, long pos, DfsReader ctx)
			throws IOException {
		this.pack = pack;
		this.pos = pos;
		this.ctx = ctx;

		// Pin the first window, to ensure the pack is open and valid.
		//
		ctx.pin(pack, pos);
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int n = ctx.copy(pack, pos, b, off, len);
		pos += n;
		return n;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		byte[] buf = new byte[1];
		int n = read(buf, 0, 1);
		return n == 1 ? buf[0] & 0xff : -1;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		ctx.close();
	}
}
