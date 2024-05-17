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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class LinkedWorktreeTest extends RepositoryTestCase {

	private ObjectId oldCommitId;

	@Override
	public void setUp() throws Exception {
		super.setUp();

		// create a repo and clone it as bare so that we're able to manually
		// fake worktree creation as there's no write support
		try (Git git = new Git(db)) {
			oldCommitId = git.commit().setMessage("Initial commit").call().getId();
			git.branchCreate().setName("wt").call();
			Git.cloneRepository()
				.setURI(db.getDirectory().toString())
				.setDirectory(new File(trash.getParentFile(), ".bare"))
				.setBare(true)
				.call();
		}

	}

	@Test
	public void testWeCanReadFromLinkedWorktree() throws IOException, NoWorkTreeException, GitAPIException {
		addWorktreeToWorkRepository("wt");

		File worktreesDir = new File(db.getDirectory(), "worktrees");
		File wtDir = new File(worktreesDir, "wt");

		FileRepository repository = new FileRepository(wtDir);
		try (Git git = new Git(repository);) {
			ObjectId objectId = repository.resolve("HEAD");
			assertNotNull(objectId);

			Iterator<RevCommit> log = git.log().all().call().iterator();
			assertTrue(log.hasNext());
			assertTrue("Initial commit".equals(log.next().getShortMessage()));
		}
	}

	private void addWorktreeToWorkRepository(String name) throws IOException {
		File worktreesDir = new File(db.getDirectory(), "worktrees");
		File worktreeDir = new File(worktreesDir, name);
		File orig_head = new File(worktreeDir, "ORIG_HEAD");
		File head = new File(worktreeDir, "HEAD");
		File commondir = new File(worktreeDir, "commondir");
		File gitdir = new File(worktreeDir, "gitdir");

		FileUtils.mkdir(worktreesDir);
		FileUtils.mkdir(worktreeDir);
		FileUtils.createNewFile(orig_head);
		FileUtils.createNewFile(head);
		FileUtils.createNewFile(commondir);
		FileUtils.createNewFile(gitdir);

		File wtDir = new File(trash.getParentFile(), name);
		File wtDotgit = new File(wtDir, ".git");
		FileUtils.mkdir(wtDir);
		FileUtils.createNewFile(wtDotgit);

		write(orig_head, ObjectId.toString(oldCommitId));
		write(head, "ref: refs/heads/" + name);
		write(commondir, "../..");
		write(gitdir, wtDir.getAbsolutePath() + "/.git");

		write(wtDotgit, "gitdir: " + worktreeDir.getAbsolutePath());
	}
}
