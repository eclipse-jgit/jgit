/*
 * Copyright (C) 2008-2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.Test;

public class PatchErrorTest {
	@Test
	public void testError_DisconnectedHunk() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		{
			final FileHeader fh = p.getFiles().get(0);
			assertEquals(
					"org.eclipse.jgit/src/org/spearce/jgit/lib/RepositoryConfig.java",
					fh.getNewPath());
			assertEquals(1, fh.getHunks().size());
		}

		assertEquals(1, p.getErrors().size());
		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.ERROR, e.getSeverity());
		assertEquals("Hunk disconnected from file", e.getMessage());
		assertEquals(18, e.getOffset());
		assertTrue(e.getLineText().startsWith("@@ -109,4 +109,11 @@ assert"));
	}

	@Test
	public void testError_TruncatedOld() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertEquals(1, p.getErrors().size());

		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.ERROR, e.getSeverity());
		assertEquals("Truncated hunk, at least 1 old lines is missing", e
				.getMessage());
		assertEquals(313, e.getOffset());
		assertTrue(e.getLineText().startsWith("@@ -236,9 +236,9 @@ protected "));
	}

	@Test
	public void testError_TruncatedNew() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertEquals(1, p.getErrors().size());

		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.ERROR, e.getSeverity());
		assertEquals("Truncated hunk, at least 1 new lines is missing", e
				.getMessage());
		assertEquals(313, e.getOffset());
		assertTrue(e.getLineText().startsWith("@@ -236,9 +236,9 @@ protected "));
	}

	@Test
	public void testError_BodyTooLong() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertEquals(1, p.getErrors().size());

		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.WARNING, e.getSeverity());
		assertEquals("Hunk header 4:11 does not match body line count of 4:12",
				e.getMessage());
		assertEquals(349, e.getOffset());
		assertTrue(e.getLineText().startsWith("@@ -109,4 +109,11 @@ assert"));
	}

	@Test
	public void testError_GarbageBetweenFiles() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(2, p.getFiles().size());
		{
			final FileHeader fh = p.getFiles().get(0);
			assertEquals(
					"org.eclipse.jgit.test/tst/org/spearce/jgit/lib/RepositoryConfigTest.java",
					fh.getNewPath());
			assertEquals(1, fh.getHunks().size());
		}
		{
			final FileHeader fh = p.getFiles().get(1);
			assertEquals(
					"org.eclipse.jgit/src/org/spearce/jgit/lib/RepositoryConfig.java",
					fh.getNewPath());
			assertEquals(1, fh.getHunks().size());
		}

		assertEquals(1, p.getErrors().size());
		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.WARNING, e.getSeverity());
		assertEquals("Unexpected hunk trailer", e.getMessage());
		assertEquals(926, e.getOffset());
		assertEquals("I AM NOT HERE\n", e.getLineText());
	}

	@Test
	public void testError_GitBinaryNoForwardHunk() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(2, p.getFiles().size());
		{
			final FileHeader fh = p.getFiles().get(0);
			assertEquals("org.spearce.egit.ui/icons/toolbar/fetchd.png", fh
					.getNewPath());
			assertSame(FileHeader.PatchType.GIT_BINARY, fh.getPatchType());
			assertTrue(fh.getHunks().isEmpty());
			assertNull(fh.getForwardBinaryHunk());
		}
		{
			final FileHeader fh = p.getFiles().get(1);
			assertEquals("org.spearce.egit.ui/icons/toolbar/fetche.png", fh
					.getNewPath());
			assertSame(FileHeader.PatchType.UNIFIED, fh.getPatchType());
			assertTrue(fh.getHunks().isEmpty());
			assertNull(fh.getForwardBinaryHunk());
		}

		assertEquals(1, p.getErrors().size());
		final FormatError e = p.getErrors().get(0);
		assertSame(FormatError.Severity.ERROR, e.getSeverity());
		assertEquals("Missing forward-image in GIT binary patch", e
				.getMessage());
		assertEquals(297, e.getOffset());
		assertEquals("\n", e.getLineText());
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
}
