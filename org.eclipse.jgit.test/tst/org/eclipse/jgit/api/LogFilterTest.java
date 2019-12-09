/*
 * Copyright (C) 2019, John Tipper <John_Tipper@hotmail.com>
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
