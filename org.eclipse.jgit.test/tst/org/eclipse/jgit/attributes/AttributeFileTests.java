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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

/**
 * End-to-end tests for some attribute combinations. Writes files, commit them,
 * examines the index, deletes the files, performs a hard reset and checks file
 * contents again.
 */
public class AttributeFileTests extends RepositoryTestCase {

	@Test
	public void testTextAutoEolLf() throws Exception {
		writeTrashFile(".gitattributes", "* text=auto eol=lf");
		performTest("Test\r\nFile", "Test\nFile", "Test\nFile");
	}

	@Test
	public void testTextAutoEolCrLf() throws Exception {
		writeTrashFile(".gitattributes", "* text=auto eol=crlf");
		performTest("Test\r\nFile", "Test\nFile", "Test\r\nFile");
	}

	private void performTest(String initial, String index, String finalText)
			throws Exception {
		File dummy = writeTrashFile("dummy.foo", initial);
		byte[] data = readTestResource("add.png");
		assertTrue("Expected some binary data", data.length > 100);
		File binary = writeTrashFile("add.png", "");
		Files.write(binary.toPath(), data);
		try (Git git = Git.wrap(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("test commit").call();
			// binary should be unchanged, dummy should match "index"
			verifyIndexContent("dummy.foo",
					index.getBytes(StandardCharsets.UTF_8));
			verifyIndexContent("add.png", data);
			assertTrue("Should be able to delete " + dummy, dummy.delete());
			assertTrue("Should be able to delete " + binary, binary.delete());
			git.reset().setMode(ResetType.HARD).call();
			assertTrue("File " + dummy + " should exist", dummy.isFile());
			assertTrue("File " + binary + " should exist", binary.isFile());
			// binary should be unchanged, dummy should match "finalText"
			String textFile = RawParseUtils.decode(IO.readFully(dummy, 512));
			assertEquals("Unexpected text content", finalText, textFile);
			byte[] binaryFile = IO.readFully(binary, 512);
			assertArrayEquals("Unexpected binary content", data, binaryFile);
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
				assertArrayEquals("Unexpected index content for " + path,
						expectedContent, data);
				return;
			}
		}
		fail("Path not found in index: " + path);
	}
}
