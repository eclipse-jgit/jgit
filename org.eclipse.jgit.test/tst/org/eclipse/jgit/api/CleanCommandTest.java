/*
 * Copyright (C) 2011, Abhishek Bhatnagar <abhatnag@redhat.com>
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
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for CleanCommand
 */
public class CleanCommandTest extends RepositoryTestCase {
	private Git git;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		// create test files
		writeTrashFile("File1.txt", "Hello world");
		writeTrashFile("File2.txt", "Delete Me");
		writeTrashFile("File3.txt", "Delete Me");

		// create files in sub-directories.
		writeTrashFile("sub-noclean/File1.txt", "Hello world");
		writeTrashFile("sub-noclean/File2.txt", "Delete Me");
		writeTrashFile("sub-clean/File4.txt", "Delete Me");
		writeTrashFile("sub-noclean/Ignored.txt", "Ignored");
		writeTrashFile(".gitignore", "/ignored-dir\n/sub-noclean/Ignored.txt");
		writeTrashFile("ignored-dir/Ignored2.txt", "Ignored");

		// add and commit first file
		git.add().addFilepattern("File1.txt").call();
		git.add().addFilepattern("sub-noclean/File1.txt").call();
		git.add().addFilepattern(".gitignore").call();
		git.commit().setMessage("Initial commit").call();
	}

	@Test
	public void testClean() throws NoWorkTreeException, GitAPIException {
		// create status
		StatusCommand command = git.status();
		Status status = command.call();
		Set<String> files = status.getUntracked();
		assertTrue(files.size() > 0);

		// run clean
		Set<String> cleanedFiles = git.clean().call();

		status = git.status().call();
		files = status.getUntracked();

		assertTrue(files.size() == 1); // one remains (directories not cleaned)
		assertTrue(cleanedFiles.contains("File2.txt"));
		assertTrue(cleanedFiles.contains("File3.txt"));
		assertTrue(!cleanedFiles.contains("sub-noclean/File1.txt"));
		assertTrue(cleanedFiles.contains("sub-noclean/File2.txt"));
		assertTrue(!cleanedFiles.contains("sub-clean/File4.txt"));
	}

	@Test
	public void testCleanDirs() throws NoWorkTreeException, GitAPIException {
		// create status
		StatusCommand command = git.status();
		Status status = command.call();
		Set<String> files = status.getUntracked();
		assertTrue(files.size() > 0);

		// run clean
		Set<String> cleanedFiles = git.clean().setCleanDirectories(true).call();

		status = git.status().call();
		files = status.getUntracked();

		assertTrue(files.size() == 0);
		assertTrue(cleanedFiles.contains("File2.txt"));
		assertTrue(cleanedFiles.contains("File3.txt"));
		assertTrue(!cleanedFiles.contains("sub-noclean/File1.txt"));
		assertTrue(cleanedFiles.contains("sub-noclean/File2.txt"));
		assertTrue(cleanedFiles.contains("sub-clean/"));
	}

	@Test
	public void testCleanWithPaths() throws NoWorkTreeException,
			GitAPIException {
		// create status
		StatusCommand command = git.status();
		Status status = command.call();
		Set<String> files = status.getUntracked();
		assertTrue(files.size() > 0);

		// run clean with setPaths
		Set<String> paths = new TreeSet<String>();
		paths.add("File3.txt");
		Set<String> cleanedFiles = git.clean().setPaths(paths).call();

		status = git.status().call();
		files = status.getUntracked();
		assertTrue(files.size() == 3);
		assertTrue(cleanedFiles.contains("File3.txt"));
		assertFalse(cleanedFiles.contains("File2.txt"));
	}

	@Test
	public void testCleanWithDryRun() throws NoWorkTreeException,
			GitAPIException {
		// create status
		StatusCommand command = git.status();
		Status status = command.call();
		Set<String> files = status.getUntracked();
		assertTrue(files.size() > 0);

		// run clean
		Set<String> cleanedFiles = git.clean().setDryRun(true).call();

		status = git.status().call();
		files = status.getUntracked();

		assertEquals(4, files.size());
		assertTrue(cleanedFiles.contains("File2.txt"));
		assertTrue(cleanedFiles.contains("File3.txt"));
		assertTrue(!cleanedFiles.contains("sub-noclean/File1.txt"));
		assertTrue(cleanedFiles.contains("sub-noclean/File2.txt"));
	}

	@Test
	public void testCleanDirsWithDryRun() throws NoWorkTreeException,
			GitAPIException {
		// create status
		StatusCommand command = git.status();
		Status status = command.call();
		Set<String> files = status.getUntracked();
		assertTrue(files.size() > 0);

		// run clean
		Set<String> cleanedFiles = git.clean().setDryRun(true)
				.setCleanDirectories(true).call();

		status = git.status().call();
		files = status.getUntracked();

		assertTrue(files.size() == 4);
		assertTrue(cleanedFiles.contains("File2.txt"));
		assertTrue(cleanedFiles.contains("File3.txt"));
		assertTrue(!cleanedFiles.contains("sub-noclean/File1.txt"));
		assertTrue(cleanedFiles.contains("sub-noclean/File2.txt"));
		assertTrue(cleanedFiles.contains("sub-clean/"));
	}

	@Test
	public void testCleanWithDryRunAndNoIgnore() throws NoWorkTreeException,
			GitAPIException {
		// run clean
		Set<String> cleanedFiles = git.clean().setDryRun(true).setIgnore(false)
				.call();

		Status status = git.status().call();
		Set<String> files = status.getIgnoredNotInIndex();

		assertTrue(files.size() == 2);
		assertTrue(cleanedFiles.contains("sub-noclean/Ignored.txt"));
		assertTrue(!cleanedFiles.contains("ignored-dir/"));
	}

	@Test
	public void testCleanDirsWithDryRunAndNoIgnore()
			throws NoWorkTreeException, GitAPIException {
		// run clean
		Set<String> cleanedFiles = git.clean().setDryRun(true).setIgnore(false)
				.setCleanDirectories(true).call();

		Status status = git.status().call();
		Set<String> files = status.getIgnoredNotInIndex();

		assertTrue(files.size() == 2);
		assertTrue(cleanedFiles.contains("sub-noclean/Ignored.txt"));
		assertTrue(cleanedFiles.contains("ignored-dir/"));
	}

}
