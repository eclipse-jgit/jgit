/*
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.util.NB;

class XInputStream extends BufferedInputStream {
	private final byte[] intbuf = new byte[8];

	XInputStream(InputStream s) {
		super(s);
	}

	synchronized byte[] readFully(final int len) throws IOException {
		final byte[] b = new byte[len];
		readFully(b, 0, len);
		return b;
	}

	synchronized void readFully(byte[] b, int o, int len)
			throws IOException {
		int r;
		while (len > 0 && (r = read(b, o, len)) > 0) {
			o += r;
			len -= r;
		}
		if (len > 0)
			throw new EOFException();
	}

	int readUInt8() throws IOException {
		final int r = read();
		if (r < 0)
			throw new EOFException();
		return r;
	}

	long readUInt32() throws IOException {
		readFully(intbuf, 0, 4);
		return NB.decodeUInt32(intbuf, 0);
	}
}
