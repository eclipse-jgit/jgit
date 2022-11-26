/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.api.errors.FileNotFoundException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

@RunWith(Theories.class)
public class RestoreCommandTest extends RepositoryTestCase {
	@DataPoints
	public static boolean[] sleepBeforeAddOptions = { true, false };


	@Override
	public void setUp() throws Exception {
		BuiltinLFS.register();
		super.setUp();
	}

	@Test
	public void testRestoreNothingFromWorkingDir() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.restore().call();
			fail("Expected IllegalArgumentException");
		} catch (NoFilepatternException e) {
			// expected
		}

	}

	@Test
	public void testRestoreNothingFromIndex() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.restore().setCached(true).call();
			fail("Expected IllegalArgumentException");
		} catch (NoFilepatternException e) {
			// expected
		}

	}

	@Test
	public void testRestoreNonExistingSingleFileFromWorkingDir() throws GitAPIException {
		try (Git git = new Git(db)) {
			List<String> restoredFiles = git.restore().addFilepattern("a.txt").call();
			assertEquals(0, restoredFiles.size());
		} catch (FileNotFoundException e) {
			// expected
		}
	}

	@Test
	public void testRestoreNonExistingSingleFileFromIndex() throws GitAPIException {
		try (Git git = new Git(db)) {
			List<String> restoredFiles =  git.restore().setCached(true).addFilepattern("a.txt").call();
			assertEquals(0, restoredFiles.size());
		} catch (FileNotFoundException e) {
			// expected
		}
	}

	@Test
	public void testRestoreExistingSingleFileFromWorkingDir() throws IOException, GitAPIException {
		try (Git git = new Git(db)) {
			Status stat = git.status().call();
			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(0, stat.getUntracked().size());

			File file = new File(db.getWorkTree(), "a.txt");
			FileUtils.createNewFile(file);
			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content");
			}

			stat = git.status().call();
			assertEquals(1, stat.getUntracked().size());

			git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:content]",
					indexState(CONTENT));

			stat = git.status().call();
			assertEquals(1, stat.getAdded().size());

			git.commit().setMessage("commit").call();

			stat = git.status().call();
			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(0, stat.getUntracked().size());

			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("testcontent");
			}

			stat = git.status().call();
			assertEquals(1, stat.getModified().size());

			git.restore().addFilepattern("a.txt").call();

			stat = git.status().call();
			assertEquals(0, stat.getModified().size());

		}
	}

	@Test
	public void testRestoreExistingSingleFileFromIndex() throws IOException, GitAPIException {
		try (Git git = new Git(db)) {

			Status stat = git.status().call();

			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(0, stat.getUntracked().size());

			File file = new File(db.getWorkTree(), "a.txt");
			FileUtils.createNewFile(file);
			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content");
			}

			stat = git.status().call();
			assertEquals(1, stat.getUntracked().size());

			git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:content]",
					indexState(CONTENT));

			stat = git.status().call();
			assertEquals(1, stat.getAdded().size());

			git.restore().setCached(true).addFilepattern("a.txt").call();

			assertEquals(
					"",
					indexState(CONTENT));

			stat = git.status().call();
			assertEquals(1, stat.getUntracked().size());

		}
	}
}
