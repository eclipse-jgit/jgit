/*
 * Copyright (C) 2012, GitHub Inc. and others
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link StashCreateCommand}
 */
public class StashDropCommandTest extends RepositoryTestCase {

	private RevCommit head;

	private Git git;

	private File committedFile;

	private List<ReflogEntry> entries;

	private RevCommit firstStash;

	private RevCommit secondStash;

	private RevCommit thirdStash;

	private RevCommit fourthStash;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
		committedFile = writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		head = git.commit().setMessage("add file").call();
		assertNotNull(head);
	}

	private void convertToRefTable() throws IOException {
		((FileRepository) git.getRepository()).convertRefStorage("reftable",
				true, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void dropNegativeRef() {
		git.stashDrop().setStashRef(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void dropNegativeRefRefTable() throws Exception {
		convertToRefTable();
		dropNegativeRef();
	}

	@Test
	public void dropWithNoStashedCommits() throws Exception {
		assertNull(git.stashDrop().call());
	}

	@Test
	public void dropWithNoStashedCommitsRefTable() throws Exception {
		convertToRefTable();
		dropWithNoStashedCommits();
	}

	@Test
	public void dropWithInvalidLogIndex() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertEquals(stashed, stashRef.getObjectId());
		try {
			assertNull(git.stashDrop().setStashRef(100).call());
			fail("Exception not thrown");
		} catch (JGitInternalException e) {
			assertNotNull(e.getMessage());
			assertTrue(e.getMessage().length() > 0);
		}
	}

	@Test
	public void dropWithInvalidLogIndexRefTable() throws Exception {
		convertToRefTable();
		dropWithInvalidLogIndex();
	}

	@Test
	public void dropSingleStashedCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertEquals(stashed, stashRef.getObjectId());
		assertNull(git.stashDrop().call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);

		assertTrue(git.stashList().call().isEmpty());
	}

	@Test
	public void dropSingleStashedCommitRefTable() throws Exception {
		convertToRefTable();

		dropSingleStashedCommit();
	}

	@Test
	public void dropAll() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertNull(git.stashDrop().setAll(true).call());

		assertTrue(git.stashList().call().isEmpty());
	}

	@Test
	public void dropAllRefTable() throws Exception {
		convertToRefTable();
		dropAll();
	}

	@Test
	public void dropFirstStashedCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertEquals(firstStash, git.stashDrop().call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash, stashRef.getObjectId());

		ReflogReader reader = git.getRepository().getRefDatabase()
				.getReflogReader(Constants.R_STASH);
		entries = reader.getReverseEntries();
		assertEquals(1, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(0).getOldId());
		assertEquals(firstStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}

	@Test
	public void dropFirstStashedCommitRefTable() throws Exception {
		convertToRefTable();
		dropFirstStashedCommit();
	}

	private void doDropMiddleStashCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content4");
		thirdStash = git.stashCreate().call();
		assertNotNull(thirdStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertEquals(thirdStash, git.stashDrop().setStashRef(1).call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash, stashRef.getObjectId());

		ReflogReader reader = git.getRepository().getRefDatabase()
				.getReflogReader(Constants.R_STASH);
		entries = reader.getReverseEntries();
	}

	@Test
	public void dropMiddleStashCommit() throws Exception {
		doDropMiddleStashCommit();

		assertEquals(2, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(1).getOldId());
		assertEquals(firstStash, entries.get(1).getNewId());
		assertTrue(entries.get(1).getComment().length() > 0);
		assertEquals(entries.get(0).getOldId(), entries.get(1).getNewId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}

	@Test
	public void dropMiddleStashCommitRefTable() throws Exception {
		convertToRefTable();

		doDropMiddleStashCommit();

		assertEquals(2, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(1).getOldId());
		assertEquals(firstStash, entries.get(1).getNewId());
		assertEquals(secondStash, entries.get(0).getOldId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
		assertTrue(entries.get(1).getComment().length() > 0);
	}

	private void doDropBoundaryStashedCommits() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content4");
		thirdStash = git.stashCreate().call();
		assertNotNull(thirdStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content5");
		fourthStash = git.stashCreate().call();
		assertNotNull(fourthStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(fourthStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertEquals(thirdStash, git.stashDrop().call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash, stashRef.getObjectId());

		assertEquals(thirdStash, git.stashDrop().setStashRef(2).call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash, stashRef.getObjectId());

		ReflogReader reader = git.getRepository().getRefDatabase()
				.getReflogReader(Constants.R_STASH);
		entries = reader.getReverseEntries();
	}

	@Test
	public void dropBoundaryStashedCommits() throws Exception {
		doDropBoundaryStashedCommits();

		assertEquals(2, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(1).getOldId());
		assertEquals(secondStash, entries.get(1).getNewId());
		assertTrue(entries.get(1).getComment().length() > 0);
		assertEquals(entries.get(0).getOldId(), entries.get(1).getNewId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}

	@Test
	public void dropBoundaryStashedCommitsRefTable() throws Exception {
		convertToRefTable();

		// Drop a stash in reftable didn't change the oldid, because a
		// stash entry can be hided in reftable. No need to rewrite the
		// whole stash. So we have here other results than in
		// dropBoundaryStashedCommits (refdirectory test)
		doDropBoundaryStashedCommits();

		assertEquals(2, entries.size());
		assertEquals(secondStash, entries.get(0).getOldId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertEquals(firstStash, entries.get(1).getOldId());
		assertEquals(secondStash, entries.get(1).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
		assertTrue(entries.get(1).getComment().length() > 0);
	}
}
