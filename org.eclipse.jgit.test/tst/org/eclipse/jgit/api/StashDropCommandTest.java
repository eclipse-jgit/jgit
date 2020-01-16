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
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
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

	@Test(expected = IllegalArgumentException.class)
	public void dropNegativeRef() {
		git.stashDrop().setStashRef(-1);
	}

	@Test
	public void dropWithNoStashedCommits() throws Exception {
		assertNull(git.stashDrop().call());
	}

	@Test
	public void dropWithInvalidLogIndex() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertEquals(stashed,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());
		try {
			assertNull(git.stashDrop().setStashRef(100).call());
			fail("Exception not thrown");
		} catch (JGitInternalException e) {
			assertNotNull(e.getMessage());
			assertTrue(e.getMessage().length() > 0);
		}
	}

	@Test
	public void dropSingleStashedCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertEquals(stashed,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());
		assertNull(git.stashDrop().call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		assertNull(reader);
	}

	@Test
	public void dropAll() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		RevCommit secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertNull(git.stashDrop().setAll(true).call());
		assertNull(git.getRepository().exactRef(Constants.R_STASH));

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		assertNull(reader);
	}

	@Test
	public void dropFirstStashedCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		RevCommit secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertEquals(firstStash, git.stashDrop().call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash, stashRef.getObjectId());

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		List<ReflogEntry> entries = reader.getReverseEntries();
		assertEquals(1, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(0).getOldId());
		assertEquals(firstStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}

	@Test
	public void dropMiddleStashCommit() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		RevCommit secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content4");
		RevCommit thirdStash = git.stashCreate().call();
		assertNotNull(thirdStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		assertEquals(thirdStash, git.stashDrop().setStashRef(1).call());
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash, stashRef.getObjectId());

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		List<ReflogEntry> entries = reader.getReverseEntries();
		assertEquals(2, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(1).getOldId());
		assertEquals(firstStash, entries.get(1).getNewId());
		assertTrue(entries.get(1).getComment().length() > 0);
		assertEquals(entries.get(0).getOldId(), entries.get(1).getNewId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}

	@Test
	public void dropBoundaryStashedCommits() throws Exception {
		write(committedFile, "content2");
		Ref stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNull(stashRef);
		RevCommit firstStash = git.stashCreate().call();
		assertNotNull(firstStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(firstStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content3");
		RevCommit secondStash = git.stashCreate().call();
		assertNotNull(secondStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(secondStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content4");
		RevCommit thirdStash = git.stashCreate().call();
		assertNotNull(thirdStash);
		stashRef = git.getRepository().exactRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(thirdStash,
				git.getRepository().exactRef(Constants.R_STASH).getObjectId());

		write(committedFile, "content5");
		RevCommit fourthStash = git.stashCreate().call();
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

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		List<ReflogEntry> entries = reader.getReverseEntries();
		assertEquals(2, entries.size());
		assertEquals(ObjectId.zeroId(), entries.get(1).getOldId());
		assertEquals(secondStash, entries.get(1).getNewId());
		assertTrue(entries.get(1).getComment().length() > 0);
		assertEquals(entries.get(0).getOldId(), entries.get(1).getNewId());
		assertEquals(thirdStash, entries.get(0).getNewId());
		assertTrue(entries.get(0).getComment().length() > 0);
	}
}
