/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link StashListCommand}
 */
public class StashListCommandTest extends RepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
	}

	private void convertToRefTable() throws Exception {
		// for some reason, after converting an empty Repository to reftable
		// Repo.exactRef("HEAD") return null instead an Ref to zeroid.
		// This leads to NoHeadException in CommitCommand, so we have to add a
		// commit, before converting to reftable
		RevCommit head = git.commit().setMessage("rootcommit")
				.setAllowEmpty(true).call();
		assertNotNull(head);

		((FileRepository) git.getRepository()).convertRefStorage("reftable",
				true, false);
	}

	@Test
	public void noStashRef() throws Exception {
		StashListCommand command = git.stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertTrue(stashed.isEmpty());
	}

	@Test
	public void noStashRefRefTable() throws Exception {
		convertToRefTable();
		noStashRef();
	}

	@Test
	public void emptyStashReflog() throws Exception {
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		RefUpdate update = db.updateRef(Constants.R_STASH);
		update.setNewObjectId(commit);
		update.disableRefLog();
		assertEquals(Result.NEW, update.update());

		StashListCommand command = Git.wrap(db).stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertTrue(stashed.isEmpty());
	}

	@Test
	public void emptyStashReflogRefTable() throws Exception {
		convertToRefTable();

		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		RefUpdate update = db.updateRef(Constants.R_STASH);
		update.setNewObjectId(commit);
		update.disableRefLog();
		assertEquals(Result.NEW, update.update());

		StashListCommand command = Git.wrap(db).stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		// In reftable the empty Stash-Reflog didn't leads to an empty stash
		// stack
		// This has to analyze
		assertEquals(1, stashed.size());
		assertTrue(stashed.contains(commit));
	}

	@Test
	public void singleStashedCommit() throws Exception {
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		assertEquals(Result.NEW, newStashUpdate(commit).update());

		StashListCommand command = git.stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertEquals(1, stashed.size());
		assertEquals(commit, stashed.iterator().next());
	}

	@Test
	public void singleStashedCommitRefTable() throws Exception {
		convertToRefTable();
		singleStashedCommit();
	}

	@Test
	public void multipleStashedCommits() throws Exception {
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("edit file").call();

		assertEquals(Result.NEW, newStashUpdate(commit1).update());
		assertEquals(Result.FAST_FORWARD, newStashUpdate(commit2).update());

		StashListCommand command = git.stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertEquals(2, stashed.size());
		Iterator<RevCommit> iter = stashed.iterator();
		assertEquals(commit2, iter.next());
		assertEquals(commit1, iter.next());
	}

	@Test
	public void multipleStashedCommitsRefTable() throws Exception {
		convertToRefTable();
		multipleStashedCommits();
	}

	private RefUpdate newStashUpdate(ObjectId newId) throws Exception {
		RefUpdate ru = db.updateRef(Constants.R_STASH);
		ru.setNewObjectId(newId);
		ru.setForceRefLog(true);
		return ru;
	}
}
