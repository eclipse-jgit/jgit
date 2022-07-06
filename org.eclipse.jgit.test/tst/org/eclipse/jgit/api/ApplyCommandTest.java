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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
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

	@Test
	public void testCrLf() throws Exception {
		try {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
			ApplyResult result = init("crlf", true, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "crlf"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "crlf"),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	@Test
	public void testCrLfOff() throws Exception {
		try {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
			ApplyResult result = init("crlf", true, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "crlf"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "crlf"),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	@Test
	public void testCrLfEmptyCommitted() throws Exception {
		try {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
			ApplyResult result = init("crlf3", true, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "crlf3"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "crlf3"),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	@Test
	public void testCrLfNewFile() throws Exception {
		try {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
			ApplyResult result = init("crlf4", false, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "crlf4"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "crlf4"),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	@Test
	public void testPatchWithCrLf() throws Exception {
		try {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
			ApplyResult result = init("crlf2", true, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "crlf2"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "crlf2"),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	@Test
	public void testPatchWithCrLf2() throws Exception {
		String name = "crlf2";
		try (Git git = new Git(db)) {
			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
			a = new RawText(readFile(name + "_PreImage"));
			write(new File(db.getWorkTree(), name),
					a.getString(0, a.size(), false));

			git.add().addFilepattern(name).call();
			git.commit().setMessage("PreImage").call();

			b = new RawText(readFile(name + "_PostImage"));

			db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
			ApplyResult result = git.apply()
					.setPatch(getTestResource(name + ".patch")).call();
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), name),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), name),
					b.getString(0, b.size(), false));
		} finally {
			db.getConfig().unset(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF);
		}
	}

	// Clean/smudge filter for testFiltering. The smudgetest test resources were
	// created with C git using a clean filter sed -e "s/A/E/g" and the smudge
	// filter sed -e "s/E/A/g". To keep the test independent of the presence of
	// sed, implement this with a built-in filter.
	private static class ReplaceFilter extends FilterCommand {

		private final char toReplace;

		private final char replacement;

		ReplaceFilter(InputStream in, OutputStream out, char toReplace,
				char replacement) {
			super(in, out);
			this.toReplace = toReplace;
			this.replacement = replacement;
		}

		@Override
		public int run() throws IOException {
			int b = in.read();
			if (b < 0) {
				in.close();
				out.close();
				return -1;
			}
			if ((b & 0xFF) == toReplace) {
				b = replacement;
			}
			out.write(b);
			return 1;
		}
	}

	@Test
	public void testFiltering() throws Exception {
		// Set up filter
		FilterCommandFactory clean = (repo, in, out) -> {
			return new ReplaceFilter(in, out, 'A', 'E');
		};
		FilterCommandFactory smudge = (repo, in, out) -> {
			return new ReplaceFilter(in, out, 'E', 'A');
		};
		FilterCommandRegistry.register("jgit://builtin/a2e/clean", clean);
		FilterCommandRegistry.register("jgit://builtin/a2e/smudge", smudge);
		try (Git git = new Git(db)) {
			Config config = db.getConfig();
			config.setString(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
					"clean", "jgit://builtin/a2e/clean");
			config.setString(ConfigConstants.CONFIG_FILTER_SECTION, "a2e",
					"smudge", "jgit://builtin/a2e/smudge");
			write(new File(db.getWorkTree(), ".gitattributes"),
					"smudgetest filter=a2e");
			git.add().addFilepattern(".gitattributes").call();
			git.commit().setMessage("Attributes").call();
			ApplyResult result = init("smudgetest", true, true);
			assertEquals(1, result.getUpdatedFiles().size());
			assertEquals(new File(db.getWorkTree(), "smudgetest"),
					result.getUpdatedFiles().get(0));
			checkFile(new File(db.getWorkTree(), "smudgetest"),
					b.getString(0, b.size(), false));

		} finally {
			// Tear down filter
			FilterCommandRegistry.unregister("jgit://builtin/a2e/clean");
			FilterCommandRegistry.unregister("jgit://builtin/a2e/smudge");
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
	public void testBinaryDelta() throws Exception {
		checkBinary("delta", true);
	}

	@Test
	public void testBinaryLiteral() throws Exception {
		checkBinary("literal", true);
	}

	@Test
	public void testBinaryLiteralAdd() throws Exception {
		checkBinary("literal_add", false);
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

	@Test
	public void testModifyM2() throws Exception {
		ApplyResult result = init("M2", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		if (FS.DETECTED.supportsExecute()) {
			assertTrue(FS.DETECTED.canExecute(result.getUpdatedFiles().get(0)));
		}
		checkFile(new File(db.getWorkTree(), "M2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyM3() throws Exception {
		ApplyResult result = init("M3", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		if (FS.DETECTED.supportsExecute()) {
			assertFalse(
					FS.DETECTED.canExecute(result.getUpdatedFiles().get(0)));
		}
		checkFile(new File(db.getWorkTree(), "M3"),
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

	@Test
	public void testModifyNL1() throws Exception {
		ApplyResult result = init("NL1");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NL1"), result
				.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NL1"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCII() throws Exception {
		ApplyResult result = init("NonASCII");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCII"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCII"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCII2() throws Exception {
		ApplyResult result = init("NonASCII2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCII2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCII2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIAdd() throws Exception {
		ApplyResult result = init("NonASCIIAdd");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIAdd"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCIIAdd"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIAdd2() throws Exception {
		ApplyResult result = init("NonASCIIAdd2", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIAdd2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCIIAdd2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIDel() throws Exception {
		ApplyResult result = init("NonASCIIDel", true, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIDel"),
				result.getUpdatedFiles().get(0));
		assertFalse(new File(db.getWorkTree(), "NonASCIIDel").exists());
	}

	@Test
	public void testRenameNoHunks() throws Exception {
		ApplyResult result = init("RenameNoHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "RenameNoHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testRenameWithHunks() throws Exception {
		ApplyResult result = init("RenameWithHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "RenameWithHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testCopyWithHunks() throws Exception {
		ApplyResult result = init("CopyWithHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "CopyWithHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "CopyResult"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftUp() throws Exception {
		ApplyResult result = init("ShiftUp");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftUp"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftUp"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftUp2() throws Exception {
		ApplyResult result = init("ShiftUp2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftUp2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftUp2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftDown() throws Exception {
		ApplyResult result = init("ShiftDown");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftDown"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftDown"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftDown2() throws Exception {
		ApplyResult result = init("ShiftDown2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftDown2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftDown2"),
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
