/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.Test;

public class GetTextTest {
	@Test
	public void testGetText_BothISO88591() throws IOException {
		final Charset cs = ISO_8859_1;
		final Patch p = parseTestPatchFile();
		assertTrue(p.getErrors().isEmpty());
		assertEquals(1, p.getFiles().size());
		final FileHeader fh = p.getFiles().get(0);
		assertEquals(2, fh.getHunks().size());
		assertEquals(readTestPatchFile(cs), fh.getScriptText(cs, cs));
	}

	@Test
	public void testGetText_NoBinary() throws IOException {
		final Charset cs = ISO_8859_1;
		final Patch p = parseTestPatchFile();
		assertTrue(p.getErrors().isEmpty());
		assertEquals(1, p.getFiles().size());
		final FileHeader fh = p.getFiles().get(0);
		assertEquals(0, fh.getHunks().size());
		assertEquals(readTestPatchFile(cs), fh.getScriptText(cs, cs));
	}

	@Test
	public void testGetText_Convert() throws IOException {
		final Charset csOld = ISO_8859_1;
		final Charset csNew = UTF_8;
		final Patch p = parseTestPatchFile();
		assertTrue(p.getErrors().isEmpty());
		assertEquals(1, p.getFiles().size());
		final FileHeader fh = p.getFiles().get(0);
		assertEquals(2, fh.getHunks().size());

		// Read the original file as ISO-8859-1 and fix up the one place
		// where we changed the character encoding. That makes the exp
		// string match what we really expect to get back.
		//
		String exp = readTestPatchFile(csOld);
		exp = exp.replace("\303\205ngstr\303\266m", "\u00c5ngstr\u00f6m");

		assertEquals(exp, fh.getScriptText(csOld, csNew));
	}

	@Test
	public void testGetText_DiffCc() throws IOException {
		final Charset csOld = ISO_8859_1;
		final Charset csNew = UTF_8;
		final Patch p = parseTestPatchFile();
		assertTrue(p.getErrors().isEmpty());
		assertEquals(1, p.getFiles().size());
		final CombinedFileHeader fh = (CombinedFileHeader) p.getFiles().get(0);
		assertEquals(1, fh.getHunks().size());

		// Read the original file as ISO-8859-1 and fix up the one place
		// where we changed the character encoding. That makes the exp
		// string match what we really expect to get back.
		//
		String exp = readTestPatchFile(csOld);
		exp = exp.replace("\303\205ngstr\303\266m", "\u00c5ngstr\u00f6m");

		assertEquals(exp, fh
				.getScriptText(new Charset[] { csNew, csOld, csNew }));
	}

	private Patch parseTestPatchFile() throws IOException {
		final String patchFile = JGitTestUtil.getName() + ".patch";
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

	private String readTestPatchFile(Charset cs) throws IOException {
		final String patchFile = JGitTestUtil.getName() + ".patch";
		try (InputStream in = getClass().getResourceAsStream(patchFile)) {
			if (in == null) {
				fail("No " + patchFile + " test vector");
				return null; // Never happens
			}
			final InputStreamReader r = new InputStreamReader(in, cs);
			char[] tmp = new char[2048];
			final StringBuilder s = new StringBuilder();
			int n;
			while ((n = r.read(tmp)) > 0)
				s.append(tmp, 0, n);
			return s.toString();
		}
	}
}
