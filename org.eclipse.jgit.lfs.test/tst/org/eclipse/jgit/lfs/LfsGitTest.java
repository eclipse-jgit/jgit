/*
 * Copyright (C) 2021, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class LfsGitTest extends RepositoryTestCase {

	private static final String SMUDGE_NAME = org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
			+ Constants.ATTR_FILTER_DRIVER_PREFIX
			+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_SMUDGE;

	private static final String CLEAN_NAME = org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
			+ Constants.ATTR_FILTER_DRIVER_PREFIX
			+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_CLEAN;

	@BeforeClass
	public static void installLfs() {
		FilterCommandRegistry.register(SMUDGE_NAME, SmudgeFilter.FACTORY);
		FilterCommandRegistry.register(CLEAN_NAME, CleanFilter.FACTORY);
	}

	@AfterClass
	public static void removeLfs() {
		FilterCommandRegistry.unregister(SMUDGE_NAME);
		FilterCommandRegistry.unregister(CLEAN_NAME);
	}

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();
		// prepare the config for LFS
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "clean", CLEAN_NAME);
		config.setString("filter", "lfs", "smudge", SMUDGE_NAME);
		config.save();
	}

	@Test
	public void testBranchSwitch() throws Exception {
		git.branchCreate().setName("abranch").call();
		git.checkout().setName("abranch").call();
		File aFile = writeTrashFile("a.bin", "aaa");
		writeTrashFile(".gitattributes", "a.bin filter=lfs");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("acommit").call();
		git.checkout().setName("master").call();
		git.branchCreate().setName("bbranch").call();
		git.checkout().setName("bbranch").call();
		File bFile = writeTrashFile("b.bin", "bbb");
		writeTrashFile(".gitattributes", "b.bin filter=lfs");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("bcommit").call();
		git.checkout().setName("abranch").call();
		checkFile(aFile, "aaa");
		git.checkout().setName("bbranch").call();
		checkFile(bFile, "bbb");
	}

	@Test
	public void checkoutNonLfsPointer() throws Exception {
		String content = "size_t\nsome_function(void* ptr);\n";
		File smallFile = writeTrashFile("Test.txt", content);
		StringBuilder largeContent = new StringBuilder(
				LfsPointer.SIZE_THRESHOLD * 4);
		while (largeContent.length() < LfsPointer.SIZE_THRESHOLD * 4) {
			largeContent.append(content);
		}
		File largeFile = writeTrashFile("large.txt", largeContent.toString());
		fsTick(largeFile);
		git.add().addFilepattern("Test.txt").addFilepattern("large.txt").call();
		git.commit().setMessage("Text files").call();
		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("attributes").call();
		assertTrue(smallFile.delete());
		assertTrue(largeFile.delete());
		// This reset will run the two text files through the smudge filter
		git.reset().setMode(ResetType.HARD).call();
		assertTrue(smallFile.exists());
		assertTrue(largeFile.exists());
		checkFile(smallFile, content);
		checkFile(largeFile, largeContent.toString());
		// Modify the large file
		largeContent.append(content);
		writeTrashFile("large.txt", largeContent.toString());
		// This should convert largeFile to an LFS pointer
		git.add().addFilepattern("large.txt").call();
		git.commit().setMessage("Large modified").call();
		String lfsPtr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:d041ab19bd7edd899b3c0450d0f61819f96672f0b22d26c9753abc62e1261614\n"
				+ "size 858\n";
		assertEquals("[.gitattributes, mode:100644, content:*.txt filter=lfs]"
				+ "[Test.txt, mode:100644, content:" + content + ']'
						+ "[large.txt, mode:100644, content:" + lfsPtr + ']',
				indexState(CONTENT));
		// Verify the file has been saved
		File savedFile = new File(db.getDirectory(), "lfs");
		savedFile = new File(savedFile, "objects");
		savedFile = new File(savedFile, "d0");
		savedFile = new File(savedFile, "41");
		savedFile = new File(savedFile,
				"d041ab19bd7edd899b3c0450d0f61819f96672f0b22d26c9753abc62e1261614");
		String saved = new String(Files.readAllBytes(savedFile.toPath()),
				StandardCharsets.UTF_8);
		assertEquals(saved, largeContent.toString());

		assertTrue(smallFile.delete());
		assertTrue(largeFile.delete());
		git.reset().setMode(ResetType.HARD).call();
		assertTrue(smallFile.exists());
		assertTrue(largeFile.exists());
		checkFile(smallFile, content);
		checkFile(largeFile, largeContent.toString());
		assertEquals("[.gitattributes, mode:100644, content:*.txt filter=lfs]"
				+ "[Test.txt, mode:100644, content:" + content + ']'
						+ "[large.txt, mode:100644, content:" + lfsPtr + ']',
				indexState(CONTENT));
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Small committed again").call();
		String lfsPtrSmall = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:9110463275fb0e2f0e9fdeaf84e598e62915666161145cf08927079119cc7814\n"
				+ "size 33\n";
		assertEquals("[.gitattributes, mode:100644, content:*.txt filter=lfs]"
				+ "[Test.txt, mode:100644, content:" + lfsPtrSmall + ']'
						+ "[large.txt, mode:100644, content:" + lfsPtr + ']',
				indexState(CONTENT));

		assertTrue(git.status().call().isClean());
	}
}
