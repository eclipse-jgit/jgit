/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Johannes E. Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.junit.TestInfoRetriever;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DiffFormatterReflowTest extends TestInfoRetriever {
	private RawText a;

	private RawText b;

	private FileHeader file;

	private ByteArrayOutputStream out;

	private DiffFormatter fmt;

	@BeforeEach
	public void setUp() throws Exception {
		out = new ByteArrayOutputStream();
		fmt = new DiffFormatter(out);
	}

	@Test
	void testNegativeContextFails() throws IOException {
		init("X");
		try {
			fmt.setContext(-1);
			fail("accepted negative context");
		} catch (IllegalArgumentException e) {
			// pass
		}
	}

	@Test
	void testContext0() throws IOException {
		init("X");
		fmt.setContext(0);
		assertFormatted();
	}

	@Test
	void testContext1() throws IOException {
		init("X");
		fmt.setContext(1);
		assertFormatted();
	}

	@Test
	void testContext3() throws IOException {
		init("X");
		fmt.setContext(3);
		assertFormatted();
	}

	@Test
	void testContext5() throws IOException {
		init("X");
		fmt.setContext(5);
		assertFormatted();
	}

	@Test
	void testContext10() throws IOException {
		init("X");
		fmt.setContext(10);
		assertFormatted();
	}

	@Test
	void testContext100() throws IOException {
		init("X");
		fmt.setContext(100);
		assertFormatted();
	}

	@Test
	void testEmpty1() throws IOException {
		init("E");
		assertFormatted("E.patch");
	}

	@Test
	void testNoNewLine1() throws IOException {
		init("Y");
		assertFormatted("Y.patch");
	}

	@Test
	void testNoNewLine2() throws IOException {
		init("Z");
		assertFormatted("Z.patch");
	}

	private void init(String name) throws IOException {
		a = new RawText(readFile(name + "_PreImage"));
		b = new RawText(readFile(name + "_PostImage"));
		file = parseTestPatchFile(name + ".patch").getFiles().get(0);
	}

	private void assertFormatted() throws IOException {
		assertFormatted(getTestMethodName() + ".out");
	}

	private void assertFormatted(String name) throws IOException {
		fmt.format(file, a, b);
		final String exp = RawParseUtils.decode(readFile(name));
		assertEquals(exp, RawParseUtils.decode(out.toByteArray()));
	}

	private byte[] readFile(String patchFile) throws IOException {
		final InputStream in = getClass().getResourceAsStream(patchFile);
		if (in == null) {
			fail("No " + patchFile + " test vector");
			return null; // Never happens
		}
		try {
			final byte[] buf = new byte[1024];
			final ByteArrayOutputStream temp = new ByteArrayOutputStream();
			int n;
			while ((n = in.read(buf)) > 0)
				temp.write(buf, 0, n);
			return temp.toByteArray();
		} finally {
			in.close();
		}
	}

	private Patch parseTestPatchFile(String patchFile) throws IOException {
		try (InputStream in = getClass().getResourceAsStream(patchFile)) {
			if (in == null) {
				fail("No " + patchFile + " test vector");
				return null; // Never happens
			}
			final Patch p = new Patch();
			p.parse(in);
			return p;
		}
	}
}
