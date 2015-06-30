/*
 * Copyright (C) 2015, Google Inc.
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

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IOReadLineTest {
	@Parameter(0)
	public boolean buffered;

	@Parameter(1)
	public int sizeHint;

	@SuppressWarnings("boxing")
	@Parameters(name="buffered={0}, sizeHint={1}")
	public static Collection<Object[]> getParameters() {
		Boolean[] bv = {false, true};
		Integer[] sv = {-1, 0, 1, 2, 3, 4, 64};
		Collection<Object[]> params = new ArrayList<>(bv.length * sv.length);
		for (boolean b : bv) {
			for (Integer s : sv) {
				params.add(new Object[]{b, s});
			}
		}
		return params;
	}

	@Test
	public void testReadLine() throws Exception {
		Reader r = newReader("foo\nbar\nbaz\n");
		assertEquals("foo\n", readLine(r));
		assertEquals("bar\n", readLine(r));
		assertEquals("baz\n", readLine(r));
		assertEquals("", readLine(r));
	}

	@Test
	public void testReadLineNoTrailingNewline() throws Exception {
		Reader r = newReader("foo\nbar\nbaz");
		assertEquals("foo\n", readLine(r));
		assertEquals("bar\n", readLine(r));
		assertEquals("baz", readLine(r));
		assertEquals("", readLine(r));
	}

	private String readLine(Reader r) throws Exception {
		return IO.readLine(r, sizeHint);
	}

	private Reader newReader(String in) {
		Reader r = new InputStreamReader(
				new ByteArrayInputStream(Constants.encode(in)));
		if (buffered) {
			r = new BufferedReader(r);
		}
		assertEquals(Boolean.valueOf(buffered),
				Boolean.valueOf(r.markSupported()));
		return r;
	}
}
