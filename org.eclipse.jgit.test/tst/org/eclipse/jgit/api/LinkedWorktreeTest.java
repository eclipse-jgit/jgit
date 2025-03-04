/*
 * Copyright (C) 2024, Broadcom and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Test;

public class LinkedWorktreeTest extends RepositoryTestCase {

	@Override
	public void setUp() throws Exception {
		super.setUp();

		try (Git git = new Git(db)) {
			git.commit().setMessage("Initial commit").call();
		}
	}

	@Test
	public void testWeCanReadFromLinkedWorktreeFromBare() throws Exception {
		FS fs = db.getFS();
		File directory = trash.getParentFile();
		String dbDirName = db.getWorkTree().getName();
		cloneBare(fs, directory, dbDirName, "bare");
		File bareDirectory = new File(directory, "bare");
		worktreeAddExisting(fs, bareDirectory, "master");

		File worktreesDir = new File(bareDirectory, "worktrees");
		File masterWorktreesDir = new File(worktreesDir, "master");

		FileRepository repository = new FileRepository(masterWorktreesDir);
		try (Git git = new Git(repository)) {
			ObjectId objectId = repository.resolve(HEAD);
			assertNotNull(objectId);

			Iterator<RevCommit> log = git.log().all().call().iterator();
			assertTrue(log.hasNext());
			assertTrue("Initial commit".equals(log.next().getShortMessage()));

			// we have reflog entry
			// depending on git version we either have one or
			// two entries where extra is zeroid entry with
			// same message or no message
			Collection<ReflogEntry> reflog = git.reflog().call();
			assertNotNull(reflog);
			assertTrue(reflog.size() > 0);
			ReflogEntry[] reflogs = reflog.toArray(new ReflogEntry[0]);
			assertEquals(reflogs[reflogs.length - 1].getComment(),
					"reset: moving to HEAD");

			// index works with file changes
			File masterDir = new File(directory, "master");
			File testFile = new File(masterDir, "test");

			Status status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 0);
			assertTrue(status.getUntracked().size() == 0);

			JGitTestUtil.write(testFile, "test");
			status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 0);
			assertTrue(status.getUntracked().size() == 1);

			git.add().addFilepattern("test").call();
			status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 1);
			assertTrue(status.getUntracked().size() == 0);
		}
	}

	@Test
	public void testWeCanReadFromLinkedWorktreeFromNonBare() throws Exception {
		FS fs = db.getFS();
		worktreeAddNew(fs, db.getWorkTree(), "wt");

		File worktreesDir = new File(db.getDirectory(), "worktrees");
		File masterWorktreesDir = new File(worktreesDir, "wt");

		FileRepository repository = new FileRepository(masterWorktreesDir);
		try (Git git = new Git(repository)) {
			ObjectId objectId = repository.resolve(HEAD);
			assertNotNull(objectId);

			Iterator<RevCommit> log = git.log().all().call().iterator();
			assertTrue(log.hasNext());
			assertTrue("Initial commit".equals(log.next().getShortMessage()));

			// we have reflog entry
			Collection<ReflogEntry> reflog = git.reflog().call();
			assertNotNull(reflog);
			assertTrue(reflog.size() > 0);
			ReflogEntry[] reflogs = reflog.toArray(new ReflogEntry[0]);
			assertEquals(reflogs[reflogs.length - 1].getComment(),
					"reset: moving to HEAD");

			// index works with file changes
			File directory = trash.getParentFile();
			File wtDir = new File(directory, "wt");
			File testFile = new File(wtDir, "test");

			Status status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 0);
			assertTrue(status.getUntracked().size() == 0);

			JGitTestUtil.write(testFile, "test");
			status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 0);
			assertTrue(status.getUntracked().size() == 1);

			git.add().addFilepattern("test").call();
			status = git.status().call();
			assertTrue(status.getUncommittedChanges().size() == 1);
			assertTrue(status.getUntracked().size() == 0);
		}

	}

	private static void cloneBare(FS fs, File directory, String from, String to) throws IOException, InterruptedException {
		ProcessBuilder builder = fs.runInShell("git",
				new String[] { "clone", "--bare", from, to });
		builder.directory(directory);
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		StringBuilder input = new StringBuilder();
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				input.toString().getBytes(StandardCharsets.UTF_8)));
		String stdOut = toString(result.getStdout());
		String errorOut = toString(result.getStderr());
		assertNotNull(stdOut);
		assertNotNull(errorOut);
	}

	private static void worktreeAddExisting(FS fs, File directory, String name) throws IOException, InterruptedException {
		ProcessBuilder builder = fs.runInShell("git",
				new String[] { "worktree", "add", "../" + name, name });
		builder.directory(directory);
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		StringBuilder input = new StringBuilder();
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				input.toString().getBytes(StandardCharsets.UTF_8)));
		String stdOut = toString(result.getStdout());
		String errorOut = toString(result.getStderr());
		assertNotNull(stdOut);
		assertNotNull(errorOut);
	}

	private static void worktreeAddNew(FS fs, File directory, String name) throws IOException, InterruptedException {
		ProcessBuilder builder = fs.runInShell("git",
				new String[] { "worktree", "add", "-b", name, "../" + name, "master"});
		builder.directory(directory);
		builder.environment().put("HOME", fs.userHome().getAbsolutePath());
		StringBuilder input = new StringBuilder();
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				input.toString().getBytes(StandardCharsets.UTF_8)));
		String stdOut = toString(result.getStdout());
		String errorOut = toString(result.getStderr());
		assertNotNull(stdOut);
		assertNotNull(errorOut);
	}

	private static String toString(TemporaryBuffer b) throws IOException {
		return RawParseUtils.decode(b.toByteArray());
	}

}
