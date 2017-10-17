/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;

import org.eclipse.jgit.errors.BinaryBlobException;
import org.junit.Test;

public class RawParseUtils_LineMapTest {
	@Test
	public void testEmpty() throws Exception {
		final IntList map = RawParseUtils.lineMap(new byte[] {}, 0, 0);
		assertNotNull(map);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0}, asInts(map));
	}

	@Test
	public void testOneBlankLine() throws Exception  {
		final IntList map = RawParseUtils.lineMap(new byte[] { '\n' }, 0, 1);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 1}, asInts(map));
	}

	@Test
	public void testTwoLineFooBar() throws Exception {
		final byte[] buf = "foo\nbar\n".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 4, buf.length}, asInts(map));
	}

	@Test
	public void testTwoLineNoLF() throws Exception {
		final byte[] buf = "foo\nbar".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 4, buf.length}, asInts(map));
	}

	@Test(expected = BinaryBlobException.class)
	public void testBinary() throws Exception {
		final byte[] buf = "xxxfoo\nb\0ar".getBytes("ISO-8859-1");
		RawParseUtils.lineMap(buf, 3, buf.length);
	}

	@Test
	public void testFourLineBlanks() throws Exception {
		final byte[] buf = "foo\n\n\nbar\n".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);

		assertArrayEquals(new int[]{
				Integer.MIN_VALUE, 0, 4, 5, 6, buf.length
		}, asInts(map));
	}

	private int[] asInts(IntList l) {
		int[] result = new int[l.size()];
		for (int i = 0; i < l.size(); i++) {
			result[i] = l.get(i);
		}
		return result;
	}
}
