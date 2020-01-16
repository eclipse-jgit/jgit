/*
 * Copyright (C) 2017, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class LsFilesTest extends CLIRepositoryTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			JGitTestUtil.writeTrashFile(db, "hello", "hello");
			JGitTestUtil.writeTrashFile(db, "dir", "world", "world");
			git.add().addFilepattern("dir").call();
			git.commit().setMessage("Initial commit").call();

			JGitTestUtil.writeTrashFile(db, "hello2", "hello");
			git.add().addFilepattern("hello2").call();
			FileUtils.createSymLink(new File(db.getWorkTree(), "link"),
					"target");
			FileUtils.mkdir(new File(db.getWorkTree(), "target"));
			writeTrashFile("target/file", "someData");
			git.add().addFilepattern("target").addFilepattern("link").call();
			git.commit().setMessage("hello2").call();

			JGitTestUtil.writeTrashFile(db, "staged", "x");
			JGitTestUtil.deleteTrashFile(db, "hello2");
			git.add().addFilepattern("staged").call();
			JGitTestUtil.writeTrashFile(db, "untracked", "untracked");
		}
	}

	@Test
	public void testHelp() throws Exception {
		String[] result = execute("git ls-files -h");
		assertTrue(result[1].startsWith("jgit ls-files"));
	}

	@Test
	public void testLsFiles() throws Exception {
		assertArrayEquals(new String[] { "dir/world", "hello2", "link",
				"staged", "target/file", "" }, execute("git ls-files"));
	}
}
