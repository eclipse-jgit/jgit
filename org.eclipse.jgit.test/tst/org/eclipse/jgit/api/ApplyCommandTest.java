/*
 * Copyright (C) 2011, 2021 IBM Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

public class ApplyCommandTest extends RepositoryTestCase {

	private RawText a;

	private RawText b;

	private ApplyResult init(String name) throws Exception {
		return init(name, true, true);
	}

	private ApplyResult init(final String name, final boolean preExists,
			final boolean postExists) throws Exception {
		try (Git git = new Git(db)) {
			if (preExists) {
				a = new RawText(readFile(name + "_PreImage"));
				write(new File(db.getDirectory().getParent(), name),
						a.getString(0, a.size(), false));

				git.add().addFilepattern(name).call();
				git.commit().setMessage("PreImage").call();
			}

			if (postExists) {
				b = new RawText(readFile(name + "_PostImage"));
			}

			return git
					.apply()
					.setPatch(getTestResource(name + ".patch")).call();
		}
	}

	private void checkBinary(String name, boolean hasPreImage)
			throws Exception {
		checkBinary(name, hasPreImage, 1);
	}

	private void checkBinary(String name, boolean hasPreImage,
			int numberOfFiles) throws Exception {
		try (Git git = new Git(db)) {
			byte[] post = IO
					.readWholeStream(getTestResource(name + "_PostImage"), 0)
					.array();
			File f = new File(db.getWorkTree(), name);
			if (hasPreImage) {
				byte[] pre = IO
						.readWholeStream(getTestResource(name + "_PreImage"), 0)
						.array();
				Files.write(f.toPath(), pre);
				git.add().addFilepattern(name).call();
				git.commit().setMessage("PreImage").call();
			}
			ApplyResult result = git.apply()
					.setPatch(getTestResource(name + ".patch")).call();
			assertEquals(numberOfFiles, result.getUpdatedFiles().size());
			assertEquals(f, result.getUpdatedFiles().get(0));
			assertArrayEquals(post, Files.readAllBytes(f.toPath()));
		}
	}

	@Test
	public void testEncodingChange() throws Exception {
		// This is a text patch that changes a file containing ÄÖÜ in UTF-8 to
		// the same characters in ISO-8859-1. The patch file itself uses mixed
		// encoding. Since checkFile() works with strings use the binary check.
		checkBinary("umlaut", true);
	}

	@Test
	public void testEmptyLine() throws Exception {
		// C git accepts completely empty lines as empty context lines.
		// According to comments in the C git sources (apply.c), newer GNU diff
		// may produce such diffs.
		checkBinary("emptyLine", true);
	}

	@Test
	public void testMultiFileNoNewline() throws Exception {
		// This test needs two files. One is in the test resources.
		try (Git git = new Git(db)) {
			Files.write(db.getWorkTree().toPath().resolve("yello"),
					"yello".getBytes(StandardCharsets.US_ASCII));
			git.add().addFilepattern("yello").call();
			git.commit().setMessage("yello").call();
		}
		checkBinary("hello", true, 2);
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
	public void testAddA3() throws Exception {
		ApplyResult result = init("A3", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A3"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A3"),
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
	public void testModifyW() throws Exception {
		ApplyResult result = init("W");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "W"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "W"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddM1() throws Exception {
		ApplyResult result = init("M1", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		if (FS.DETECTED.supportsExecute()) {
			assertTrue(FS.DETECTED.canExecute(result.getUpdatedFiles().get(0)));
		}
		checkFile(new File(db.getWorkTree(), "M1"),
				b.getString(0, b.size(), false));
	}

	private static byte[] readFile(String patchFile) throws IOException {
		final InputStream in = getTestResource(patchFile);
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

	private static InputStream getTestResource(String patchFile) {
		return ApplyCommandTest.class.getClassLoader()
				.getResourceAsStream("org/eclipse/jgit/diff/" + patchFile);
	}
}
