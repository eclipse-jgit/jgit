/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for some attribute combinations. Writes files, commit them,
 * examines the index, deletes the files, performs a hard reset and checks file
 * contents again.
 */
public class AttributeFileTests extends RepositoryTestCase {

	@Test
	void testTextAutoCoreEolCoreAutoCrLfInput() throws Exception {
		FileBasedConfig cfg = db.getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
		cfg.save();
		final String content = "Line1\nLine2\n";
		try (Git git = Git.wrap(db)) {
			writeTrashFile(".gitattributes", "* text=auto");
			File dummy = writeTrashFile("dummy.txt", content);
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Commit with LF").call();
			assertEquals("[.gitattributes, mode:100644, content:* text=auto]"
					+ "[dummy.txt, mode:100644, content:" + content
					+ ']',
					indexState(CONTENT),
					"Unexpected index state");
			assertTrue(dummy.delete(), "Should be able to delete " + dummy);
			cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_EOL, "crlf");
			cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");
			cfg.save();
			git.reset().setMode(ResetType.HARD).call();
			assertTrue(dummy.isFile(), "File " + dummy + "should exist");
			String textFile = RawParseUtils.decode(IO.readFully(dummy, 512));
			assertEquals(content, textFile, "Unexpected text content");
		}
	}

	@Test
	void testTextAutoEolLf() throws Exception {
		writeTrashFile(".gitattributes", "* text=auto eol=lf");
		performTest("Test\r\nFile", "Test\nFile", "Test\nFile");
	}

	@Test
	void testTextAutoEolCrLf() throws Exception {
		writeTrashFile(".gitattributes", "* text=auto eol=crlf");
		performTest("Test\r\nFile", "Test\nFile", "Test\r\nFile");
	}

	private void performTest(String initial, String index, String finalText)
			throws Exception {
		File dummy = writeTrashFile("dummy.foo", initial);
		byte[] data = readTestResource("add.png");
		assertTrue(data.length > 100, "Expected some binary data");
		File binary = writeTrashFile("add.png", "");
		Files.write(binary.toPath(), data);
		try (Git git = Git.wrap(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("test commit").call();
			// binary should be unchanged, dummy should match "index"
			verifyIndexContent("dummy.foo",
					index.getBytes(StandardCharsets.UTF_8));
			verifyIndexContent("add.png", data);
			assertTrue(dummy.delete(), "Should be able to delete " + dummy);
			assertTrue(binary.delete(), "Should be able to delete " + binary);
			git.reset().setMode(ResetType.HARD).call();
			assertTrue(dummy.isFile(), "File " + dummy + " should exist");
			assertTrue(binary.isFile(), "File " + binary + " should exist");
			// binary should be unchanged, dummy should match "finalText"
			String textFile = RawParseUtils.decode(IO.readFully(dummy, 512));
			assertEquals(finalText, textFile, "Unexpected text content");
			byte[] binaryFile = IO.readFully(binary, 512);
			assertArrayEquals(data, binaryFile, "Unexpected binary content");
		}
	}

	private byte[] readTestResource(String name) throws Exception {
		try (InputStream in = new BufferedInputStream(
				getClass().getResourceAsStream(name))) {
			byte[] data = new byte[512];
			int read = in.read(data);
			if (read == data.length) {
				return data;
			}
			return Arrays.copyOf(data, read);
		}
	}

	private void verifyIndexContent(String path, byte[] expectedContent)
			throws Exception {
		DirCache dc = db.readDirCache();
		for (int i = 0; i < dc.getEntryCount(); ++i) {
			DirCacheEntry entry = dc.getEntry(i);
			if (path.equals(entry.getPathString())) {
				byte[] data = db.open(entry.getObjectId(), Constants.OBJ_BLOB)
						.getCachedBytes();
				assertArrayEquals(expectedContent, data, "Unexpected index content for " + path);
				return;
			}
		}
		fail("Path not found in index: " + path);
	}
}
