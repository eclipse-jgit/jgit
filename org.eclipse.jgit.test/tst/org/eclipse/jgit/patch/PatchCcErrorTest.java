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

import static java.lang.Integer.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.Test;

public class PatchCcErrorTest {
	@Test
	public void testError_CcTruncatedOld() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertEquals(3, p.getErrors().size());
		{
			final FormatError e = p.getErrors().get(0);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals(MessageFormat.format(
					JGitText.get().truncatedHunkLinesMissingForAncestor,
					valueOf(1), valueOf(1)), e.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
		{
			final FormatError e = p.getErrors().get(1);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals(MessageFormat.format(
					JGitText.get().truncatedHunkLinesMissingForAncestor,
					valueOf(2), valueOf(2)), e.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
		{
			final FormatError e = p.getErrors().get(2);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals("Truncated hunk, at least 3 new lines is missing", e
					.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
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
