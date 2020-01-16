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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class PatchCcTest {
	@Test
	public void testParse_OneFileCc() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertTrue(p.getErrors().isEmpty());

		final CombinedFileHeader cfh = (CombinedFileHeader) p.getFiles().get(0);

		assertEquals("org.spearce.egit.ui/src/org/spearce/egit/ui/UIText.java",
				cfh.getNewPath());
		assertEquals(cfh.getNewPath(), cfh.getOldPath());

		assertEquals(98, cfh.startOffset);

		assertEquals(2, cfh.getParentCount());
		assertSame(cfh.getOldId(0), cfh.getOldId());
		assertEquals("169356b", cfh.getOldId(0).name());
		assertEquals("dd8c317", cfh.getOldId(1).name());
		assertEquals("fd85931", cfh.getNewId().name());

		assertSame(cfh.getOldMode(0), cfh.getOldMode());
		assertSame(FileMode.REGULAR_FILE, cfh.getOldMode(0));
		assertSame(FileMode.REGULAR_FILE, cfh.getOldMode(1));
		assertSame(FileMode.EXECUTABLE_FILE, cfh.getNewMode());
		assertSame(FileHeader.ChangeType.MODIFY, cfh.getChangeType());
		assertSame(FileHeader.PatchType.UNIFIED, cfh.getPatchType());

		assertEquals(1, cfh.getHunks().size());
		{
			final CombinedHunkHeader h = cfh.getHunks().get(0);

			assertSame(cfh, h.getFileHeader());
			assertEquals(346, h.startOffset);
			assertEquals(764, h.endOffset);

			assertSame(h.getOldImage(0), h.getOldImage());
			assertSame(cfh.getOldId(0), h.getOldImage(0).getId());
			assertSame(cfh.getOldId(1), h.getOldImage(1).getId());

			assertEquals(55, h.getOldImage(0).getStartLine());
			assertEquals(12, h.getOldImage(0).getLineCount());
			assertEquals(3, h.getOldImage(0).getLinesAdded());
			assertEquals(0, h.getOldImage(0).getLinesDeleted());

			assertEquals(163, h.getOldImage(1).getStartLine());
			assertEquals(13, h.getOldImage(1).getLineCount());
			assertEquals(2, h.getOldImage(1).getLinesAdded());
			assertEquals(0, h.getOldImage(1).getLinesDeleted());

			assertEquals(163, h.getNewStartLine());
			assertEquals(15, h.getNewLineCount());

			assertEquals(10, h.getLinesContext());
		}
	}

	@Test
	public void testParse_CcNewFile() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertTrue(p.getErrors().isEmpty());

		final CombinedFileHeader cfh = (CombinedFileHeader) p.getFiles().get(0);

		assertSame(DiffEntry.DEV_NULL, cfh.getOldPath());
		assertEquals("d", cfh.getNewPath());

		assertEquals(187, cfh.startOffset);

		assertEquals(2, cfh.getParentCount());
		assertSame(cfh.getOldId(0), cfh.getOldId());
		assertEquals("0000000", cfh.getOldId(0).name());
		assertEquals("0000000", cfh.getOldId(1).name());
		assertEquals("4bcfe98", cfh.getNewId().name());

		assertSame(cfh.getOldMode(0), cfh.getOldMode());
		assertSame(FileMode.MISSING, cfh.getOldMode(0));
		assertSame(FileMode.MISSING, cfh.getOldMode(1));
		assertSame(FileMode.REGULAR_FILE, cfh.getNewMode());
		assertSame(FileHeader.ChangeType.ADD, cfh.getChangeType());
		assertSame(FileHeader.PatchType.UNIFIED, cfh.getPatchType());

		assertEquals(1, cfh.getHunks().size());
		{
			final CombinedHunkHeader h = cfh.getHunks().get(0);

			assertSame(cfh, h.getFileHeader());
			assertEquals(273, h.startOffset);
			assertEquals(300, h.endOffset);

			assertSame(h.getOldImage(0), h.getOldImage());
			assertSame(cfh.getOldId(0), h.getOldImage(0).getId());
			assertSame(cfh.getOldId(1), h.getOldImage(1).getId());

			assertEquals(1, h.getOldImage(0).getStartLine());
			assertEquals(0, h.getOldImage(0).getLineCount());
			assertEquals(1, h.getOldImage(0).getLinesAdded());
			assertEquals(0, h.getOldImage(0).getLinesDeleted());

			assertEquals(1, h.getOldImage(1).getStartLine());
			assertEquals(0, h.getOldImage(1).getLineCount());
			assertEquals(1, h.getOldImage(1).getLinesAdded());
			assertEquals(0, h.getOldImage(1).getLinesDeleted());

			assertEquals(1, h.getNewStartLine());
			assertEquals(1, h.getNewLineCount());

			assertEquals(0, h.getLinesContext());
		}
	}

	@Test
	public void testParse_CcDeleteFile() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertTrue(p.getErrors().isEmpty());

		final CombinedFileHeader cfh = (CombinedFileHeader) p.getFiles().get(0);

		assertEquals("a", cfh.getOldPath());
		assertSame(DiffEntry.DEV_NULL, cfh.getNewPath());

		assertEquals(187, cfh.startOffset);

		assertEquals(2, cfh.getParentCount());
		assertSame(cfh.getOldId(0), cfh.getOldId());
		assertEquals("7898192", cfh.getOldId(0).name());
		assertEquals("2e65efe", cfh.getOldId(1).name());
		assertEquals("0000000", cfh.getNewId().name());

		assertSame(cfh.getOldMode(0), cfh.getOldMode());
		assertSame(FileMode.REGULAR_FILE, cfh.getOldMode(0));
		assertSame(FileMode.REGULAR_FILE, cfh.getOldMode(1));
		assertSame(FileMode.MISSING, cfh.getNewMode());
		assertSame(FileHeader.ChangeType.DELETE, cfh.getChangeType());
		assertSame(FileHeader.PatchType.UNIFIED, cfh.getPatchType());

		assertTrue(cfh.getHunks().isEmpty());
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
