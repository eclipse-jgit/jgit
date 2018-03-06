/*
 * Copyright (C) 2009, Google Inc.
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

	private Patch parseTestPatchFile(final String patchFile) throws IOException {
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
