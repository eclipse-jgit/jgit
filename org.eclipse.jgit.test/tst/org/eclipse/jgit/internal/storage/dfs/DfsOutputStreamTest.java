/*
 * Copyright (C) 2013, Google Inc.
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class DfsOutputStreamTest {
	@Test
	public void testReadEmpty() throws IOException {
		DfsOutputStream os = newDfsOutputStream(8);
		os.write(Constants.encode(""));
		assertEquals("", readBuffered(os.openInputStream(0)));
		assertEquals("", readByByte(os.openInputStream(0)));
		assertEquals("", readBuffered(os.openInputStream(1)));
		assertEquals("", readByByte(os.openInputStream(1)));
	}

	@Test
	public void testRead() throws IOException {
		DfsOutputStream os = newDfsOutputStream(8);
		os.write(Constants.encode("data"));
		assertEquals("data", readBuffered(os.openInputStream(0)));
		assertEquals("data", readByByte(os.openInputStream(0)));
		assertEquals("ata", readBuffered(os.openInputStream(1)));
		assertEquals("ata", readByByte(os.openInputStream(1)));
		assertEquals("ta", readBuffered(os.openInputStream(2)));
		assertEquals("ta", readByByte(os.openInputStream(2)));
		assertEquals("a", readBuffered(os.openInputStream(3)));
		assertEquals("a", readByByte(os.openInputStream(3)));
		assertEquals("", readBuffered(os.openInputStream(4)));
		assertEquals("", readByByte(os.openInputStream(4)));
		assertEquals("", readBuffered(os.openInputStream(5)));
		assertEquals("", readByByte(os.openInputStream(5)));
	}

	@Test
	public void testReadSmallBuffer() throws IOException {
		DfsOutputStream os = newDfsOutputStream(8);
		os.write(Constants.encode("data"));
		assertEquals("data", readBuffered(os.openInputStream(0), 2));
		assertEquals("ata", readBuffered(os.openInputStream(1), 2));
		assertEquals("ta", readBuffered(os.openInputStream(2), 2));
		assertEquals("a", readBuffered(os.openInputStream(3), 2));
		assertEquals("", readBuffered(os.openInputStream(4), 2));
		assertEquals("", readBuffered(os.openInputStream(5), 2));
	}

	@Test
	public void testReadMultipleBlocks() throws IOException {
		DfsOutputStream os = newDfsOutputStream(2);
		os.write(Constants.encode("data"));
		assertEquals("data", readBuffered(os.openInputStream(0)));
		assertEquals("data", readByByte(os.openInputStream(0)));
		assertEquals("ata", readBuffered(os.openInputStream(1)));
		assertEquals("ata", readByByte(os.openInputStream(1)));
		assertEquals("ta", readBuffered(os.openInputStream(2)));
		assertEquals("ta", readByByte(os.openInputStream(2)));
		assertEquals("a", readBuffered(os.openInputStream(3)));
		assertEquals("a", readByByte(os.openInputStream(3)));
		assertEquals("", readBuffered(os.openInputStream(4)));
		assertEquals("", readByByte(os.openInputStream(4)));
		assertEquals("", readBuffered(os.openInputStream(5)));
		assertEquals("", readByByte(os.openInputStream(5)));
	}

	private static DfsOutputStream newDfsOutputStream(final int blockSize) {
		return new InMemoryOutputStream() {
			@Override
			public int blockSize() {
				return blockSize;
			}
		};
	}

	private static String readBuffered(InputStream is, int bufsize)
			throws IOException {
		StringBuilder out = new StringBuilder();
		byte[] buf = new byte[bufsize];
		while (true) {
			int n = is.read(buf, 0, buf.length);
			if (n < 0)
				break;
			out.append(RawParseUtils.decode(buf, 0, n));
		}
		return out.toString();
	}

	private static String readBuffered(InputStream is) throws IOException {
		return readBuffered(is, 64);
	}

	private static String readByByte(InputStream is) throws IOException {
		StringBuilder out = new StringBuilder();
		while (true) {
			int c = is.read();
			if (c < 0)
				break;
			out.append(RawParseUtils.decode(new byte[] {(byte) c}));
		}
		return out.toString();
	}
}
