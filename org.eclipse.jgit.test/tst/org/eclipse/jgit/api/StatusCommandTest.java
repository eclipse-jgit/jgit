/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class StatusCommandTest extends RepositoryTestCase {

	@Test
	public void testEmptyStatus() throws NoWorkTreeException,
			GitAPIException {
		try (Git git = new Git(db)) {
			Status stat = git.status().call();
			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(0, stat.getUntracked().size());
		}
	}

	@Test
	public void testDifferentStates() throws IOException,
			NoFilepatternException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "content of a");
			writeTrashFile("b", "content of b");
			writeTrashFile("c", "content of c");
			git.add().addFilepattern("a").addFilepattern("b").call();
			Status stat = git.status().call();
			assertEquals(Sets.of("a", "b"), stat.getAdded());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(Sets.of("c"), stat.getUntracked());
			git.commit().setMessage("initial").call();

			writeTrashFile("a", "modified content of a");
			writeTrashFile("b", "modified content of b");
			writeTrashFile("d", "content of d");
			git.add().addFilepattern("a").addFilepattern("d").call();
			writeTrashFile("a", "again modified content of a");
			stat = git.status().call();
			assertEquals(Sets.of("d"), stat.getAdded());
			assertEquals(Sets.of("a"), stat.getChanged());
			assertEquals(0, stat.getMissing().size());
			assertEquals(Sets.of("b", "a"), stat.getModified());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(Sets.of("c"), stat.getUntracked());
			git.add().addFilepattern(".").call();
			git.commit().setMessage("second").call();

			stat = git.status().call();
			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(0, stat.getRemoved().size());
			assertEquals(0, stat.getUntracked().size());

			deleteTrashFile("a");
			assertFalse(new File(git.getRepository().getWorkTree(), "a").exists());
			git.add().addFilepattern("a").setUpdate(true).call();
			writeTrashFile("a", "recreated content of a");
			stat = git.status().call();
			assertEquals(0, stat.getAdded().size());
			assertEquals(0, stat.getChanged().size());
			assertEquals(0, stat.getMissing().size());
			assertEquals(0, stat.getModified().size());
			assertEquals(Sets.of("a"), stat.getRemoved());
			assertEquals(Sets.of("a"), stat.getUntracked());
			git.commit().setMessage("t").call();

			writeTrashFile("sub/a", "sub-file");
			stat = git.status().call();
			assertEquals(1, stat.getUntrackedFolders().size());
			assertTrue(stat.getUntrackedFolders().contains("sub"));
		}
	}

	@Test
	public void testDifferentStatesWithPaths() throws IOException,
			NoFilepatternException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "content of a");
			writeTrashFile("D/b", "content of b");
			writeTrashFile("D/c", "content of c");
			writeTrashFile("D/D/d", "content of d");
			git.add().addFilepattern(".").call();

			writeTrashFile("a", "new content of a");
			writeTrashFile("D/b", "new content of b");
			writeTrashFile("D/D/d", "new content of d");


			// filter on an not existing path
			Status stat = git.status().addPath("x").call();
			assertEquals(0, stat.getModified().size());

			// filter on an existing file
			stat = git.status().addPath("a").call();
			assertEquals(Sets.of("a"), stat.getModified());

			// filter on an existing folder
			stat = git.status().addPath("D").call();
			assertEquals(Sets.of("D/b", "D/D/d"), stat.getModified());

			// filter on an existing folder and file
			stat = git.status().addPath("D/D").addPath("a").call();
			assertEquals(Sets.of("a", "D/D/d"), stat.getModified());

			// do not filter at all
			stat = git.status().call();
			assertEquals(Sets.of("a", "D/b", "D/D/d"), stat.getModified());
		}
	}

	@Test
	public void testExecutableWithNonNormalizedIndex() throws Exception {
		assumeTrue(FS.DETECTED.supportsExecute());
		try (Git git = new Git(db)) {
			// Commit a file with CR/LF into the index
			FileBasedConfig config = db.getConfig();
			config.setString("core", null, "autocrlf", "false");
			config.save();
			File testFile = writeTrashFile("file.txt", "line 1\r\nline 2\r\n");
			FS.DETECTED.setExecute(testFile, true);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Initial").call();
			assertEquals(
					"[file.txt, mode:100755, content:line 1\r\nline 2\r\n]",
					indexState(CONTENT));
			config.setString("core", null, "autocrlf", "true");
			config.save();
			Status status = git.status().call();
			assertTrue("Expected no differences", status.isClean());
		}
	}

	@Test
	public void testFolderPrefix() throws Exception {
		// "audio" is a prefix of "audio-new" and "audio.new".
		try (Git git = new Git(db)) {
			// Order here is the git order, but that doesn't really matter.
			// They are processed by StatusCommand in this order even if written
			// in a different order. Bug 566799 would, when having processed
			// audio/foo, remove previously recorded untracked folders that have
			// "audio" as a prefix: audio-new and audio.new.
			writeTrashFile("audi", "foo", "foo");
			writeTrashFile("audio-new", "foo", "foo");
			writeTrashFile("audio.new", "foo", "foo");
			writeTrashFile("audio", "foo", "foo");
			writeTrashFile("audio_new", "foo", "foo");
			Status stat = git.status().call();
			assertEquals(Sets.of("audi", "audio-new", "audio.new", "audio",
					"audio_new"), stat.getUntrackedFolders());
		}
	}

	@Test
	public void testNestedCommittedGitRepoAndPathFilter() throws Exception {
		commitFile("file.txt", "file", "master");
		try (Repository inner = new FileRepositoryBuilder()
				.setWorkTree(new File(db.getWorkTree(), "subgit")).build()) {
			inner.create();
			writeTrashFile("subgit/sub.txt", "sub");
			try (Git outerGit = new Git(db); Git innerGit = new Git(inner)) {
				innerGit.add().addFilepattern("sub.txt").call();
				innerGit.commit().setMessage("Inner commit").call();
				outerGit.add().addFilepattern("subgit").call();
				outerGit.commit().setMessage("Outer commit").call();
				assertTrue(innerGit.status().call().isClean());
				assertTrue(outerGit.status().call().isClean());
				writeTrashFile("subgit/sub.txt", "sub2");
				assertFalse(innerGit.status().call().isClean());
				assertFalse(outerGit.status().call().isClean());
				assertTrue(
						outerGit.status().addPath("file.txt").call().isClean());
				assertTrue(outerGit.status().addPath("doesntexist").call()
						.isClean());
				assertFalse(
						outerGit.status().addPath("subgit").call().isClean());
			}
		}
	}

}
