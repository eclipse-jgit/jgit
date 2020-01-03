/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.junit.Test;

public class EditListTest {
	@Test
	public void testHunkHeader() throws IOException {
		final Patch p = parseTestPatchFile("testGetText_BothISO88591.patch");
		final FileHeader fh = p.getFiles().get(0);

		final EditList list0 = fh.getHunks().get(0).toEditList();
		assertEquals(1, list0.size());
		assertEquals(new Edit(4 - 1, 5 - 1, 4 - 1, 5 - 1), list0.get(0));

		final EditList list1 = fh.getHunks().get(1).toEditList();
		assertEquals(1, list1.size());
		assertEquals(new Edit(16 - 1, 17 - 1, 16 - 1, 17 - 1), list1.get(0));
	}

	@Test
	public void testFileHeader() throws IOException {
		final Patch p = parseTestPatchFile("testGetText_BothISO88591.patch");
		final FileHeader fh = p.getFiles().get(0);
		final EditList e = fh.toEditList();
		assertEquals(2, e.size());
		assertEquals(new Edit(4 - 1, 5 - 1, 4 - 1, 5 - 1), e.get(0));
		assertEquals(new Edit(16 - 1, 17 - 1, 16 - 1, 17 - 1), e.get(1));
	}

	@Test
	public void testTypes() throws IOException {
		final Patch p = parseTestPatchFile("testEditList_Types.patch");
		final FileHeader fh = p.getFiles().get(0);
		final EditList e = fh.toEditList();
		assertEquals(3, e.size());
		assertEquals(new Edit(3 - 1, 3 - 1, 3 - 1, 4 - 1), e.get(0));
		assertEquals(new Edit(17 - 1, 19 - 1, 18 - 1, 18 - 1), e.get(1));
		assertEquals(new Edit(23 - 1, 25 - 1, 22 - 1, 28 - 1), e.get(2));
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
