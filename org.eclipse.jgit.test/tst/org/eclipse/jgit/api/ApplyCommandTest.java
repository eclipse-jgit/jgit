/*
 * Copyright (C) 2011, 2012, IBM Corporation and others.
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.diff.DiffFormatterReflowTest;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Test;

public class ApplyCommandTest extends RepositoryTestCase {

	private RawText a;

	private RawText b;

	private ApplyResult init(final String name) throws Exception {
		return init(name, true, true);
	}

	private ApplyResult init(final String name, final boolean preExists,
			final boolean postExists) throws Exception {
		Git git = new Git(db);

		if (preExists) {
			a = new RawText(readFile(name + "_PreImage"));
			write(new File(db.getDirectory().getParent(), name),
					a.getString(0, a.size(), false));

			git.add().addFilepattern(name).call();
			git.commit().setMessage("PreImage").call();
		}

		if (postExists)
			b = new RawText(readFile(name + "_PostImage"));

		return git
				.apply()
				.setPatch(
						DiffFormatterReflowTest.class.getResourceAsStream(name
								+ ".patch")).call();
	}

	@Test
	public void testAddA1() throws Exception {
		ApplyResult result = init("A1", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A1"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "A1"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddA2() throws Exception {
		ApplyResult result = init("A2", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A2"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "A2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddA1Sub() throws Exception {
		ApplyResult result = init("A1_sub", false, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "sub/A1"), result
				.getUpdatedFiles().get(0));
	}

	@Test
	public void testDeleteD() throws Exception {
		ApplyResult result = init("D", true, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "D"), result.getUpdatedFiles()
				.get(0));
		assertFalse(new File(db.getWorkTree(), "D").exists());
	}

	@Test(expected = PatchFormatException.class)
	public void testFailureF1() throws Exception {
		init("F1", true, false);
	}

	@Test(expected = PatchApplyException.class)
	public void testFailureF2() throws Exception {
		init("F2", true, false);
	}

	@Test
	public void testModifyE() throws Exception {
		ApplyResult result = init("E");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "E"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "E"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyX() throws Exception {
		ApplyResult result = init("X");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "X"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "X"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyY() throws Exception {
		ApplyResult result = init("Y");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Y"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "Y"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyZ() throws Exception {
		ApplyResult result = init("Z");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Z"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "Z"),
				b.getString(0, b.size(), false));
	}

	private byte[] readFile(final String patchFile) throws IOException {
		final InputStream in = DiffFormatterReflowTest.class
				.getResourceAsStream(patchFile);
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
}
