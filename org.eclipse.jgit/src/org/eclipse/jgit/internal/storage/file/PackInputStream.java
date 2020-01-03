/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.InputStream;

class PackInputStream extends InputStream {
	private final WindowCursor wc;

	private final PackFile pack;

	private long pos;

	PackInputStream(PackFile pack, long pos, WindowCursor wc)
			throws IOException {
		this.pack = pack;
		this.pos = pos;
		this.wc = wc;

		// Pin the first window, to ensure the pack is open and valid.
		//
		wc.pin(pack, pos);
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int n = wc.copy(pack, pos, b, off, len);
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
		wc.close();
	}
}
