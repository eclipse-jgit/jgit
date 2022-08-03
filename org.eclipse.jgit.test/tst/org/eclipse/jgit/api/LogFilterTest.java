/*
 * Copyright (C) 2019, John Tipper <John_Tipper@hotmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Testing the log command with include and exclude filters
 */
public class LogFilterTest extends RepositoryTestCase {
	private Git git;

	@Before
	public void setup() throws Exception {
		super.setUp();
		git = new Git(db);

		// create first file
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.print("content1");
		}

		// First commit - a.txt file
		git.add().addFilepattern("a.txt").call();
		git.commit().setMessage("commit1").setCommitter(committer).call();

		// create second file
		file = new File(db.getWorkTree(), "b.txt");
		FileUtils.createNewFile(file);
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.print("content2");
		}

		// Second commit - b.txt file
		git.add().addFilepattern("b.txt").call();
		git.commit().setMessage("commit2").setCommitter(committer).call();

		// create third file
		Path includeSubdir = Paths.get(db.getWorkTree().toString(),
				"subdir-include");
		includeSubdir.toFile().mkdirs();
		file = Paths.get(includeSubdir.toString(), "c.txt").toFile();
		FileUtils.createNewFile(file);
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.print("content3");
		}

		// Third commit - c.txt file
		git.add().addFilepattern("subdir-include").call();
		git.commit().setMessage("commit3").setCommitter(committer).call();

		// create fourth file
		Path excludeSubdir = Paths.get(db.getWorkTree().toString(),
				"subdir-exclude");
		excludeSubdir.toFile().mkdirs();
		file = Paths.get(excludeSubdir.toString(), "d.txt").toFile();
		FileUtils.createNewFile(file);
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.print("content4");
		}

		// Fourth commit - d.txt file
		git.add().addFilepattern("subdir-exclude").call();
		git.commit().setMessage("commit4").setCommitter(committer).call();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		git.close();
		super.tearDown();
	}

	@Test
	public void testLogWithFilterCanDistinguishFilesByPath() throws Exception {
		int count = 0;
		for (RevCommit c : git.log().addPath("a.txt").call()) {
			assertEquals("commit1", c.getFullMessage());
			count++;
		}
		assertEquals(1, count);

		count = 0;
		for (RevCommit c : git.log().addPath("b.txt").call()) {
			assertEquals("commit2", c.getFullMessage());
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	public void testLogWithFilterCanIncludeFilesInDirectory() throws Exception {
		int count = 0;
		for (RevCommit c : git.log().addPath("subdir-include").call()) {
			assertEquals("commit3", c.getFullMessage());
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	public void testLogWithFilterCanExcludeFilesInDirectory() throws Exception {
		int count = 0;
		Iterator it = git.log().excludePath("subdir-exclude").call().iterator();
		while (it.hasNext()) {
			it.next();
			count++;
		}
		// of all the commits, we expect to filter out only d.txt
		assertEquals(3, count);
	}

	@Test
	public void testLogWithoutFilter() throws Exception {
		int count = 0;
		for (RevCommit c : git.log().call()) {
			assertEquals(committer, c.getCommitterIdent());
			count++;
		}
		assertEquals(4, count);
	}

	@Test
	public void testLogWithFilterCanExcludeAndIncludeFilesInDifferentDirectories()
			throws Exception {
		int count = 0;
		Iterator it = git.log().addPath("subdir-include")
				.excludePath("subdir-exclude").call().iterator();
		while (it.hasNext()) {
			it.next();
			count++;
		}
		// we expect to include c.txt
		assertEquals(1, count);
	}

	@Test
	public void testLogWithFilterExcludeAndIncludeSameFileIncludesNothing()
			throws Exception {
		int count = 0;
		Iterator it = git.log().addPath("subdir-exclude")
				.excludePath("subdir-exclude").call().iterator();

		while (it.hasNext()) {
			it.next();
			count++;
		}
		// we expect the exclude to trump everything
		assertEquals(0, count);
	}

	@Test
	public void testLogWithFilterCanExcludeFileAndDirectory() throws Exception {
		int count = 0;
		Iterator it = git.log().excludePath("b.txt")
				.excludePath("subdir-exclude").call().iterator();

		while (it.hasNext()) {
			it.next();
			count++;
		}
		// we expect a.txt and c.txt
		assertEquals(2, count);
	}
}
