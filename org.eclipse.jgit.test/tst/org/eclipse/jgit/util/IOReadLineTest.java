/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;
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
				new ByteArrayInputStream(Constants.encode(in)), UTF_8);
		if (buffered) {
			r = new BufferedReader(r);
		}
		assertEquals(Boolean.valueOf(buffered),
				Boolean.valueOf(r.markSupported()));
		return r;
	}
}
