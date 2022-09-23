/*
 * Copyright (C) 2022, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public class FilteredRevWalkTest extends RevWalkTestCase {
	private TestRepository<FileRepository> repository;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		repository = new TestRepository<>(db);
	}

	@Test
	public void testWalk() throws Exception {
		writeTrashFile("a.txt", "content");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c1 = repository.git().commit().setMessage("first commit")
				.call();

		writeTrashFile("b.txt", "new file added");
		repository.git().add().addFilepattern("b.txt").call();
		repository.git().commit().setMessage("second commit").call();

		writeTrashFile("a.txt", "content added");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c3 = repository.git().commit().setMessage("third commit")
				.call();

		RevWalk revWalk = repository.getRevWalk();
		FilteredRevCommit filteredRevCommit = new FilteredRevCommit(c3, c1);

		revWalk.markStart(filteredRevCommit);
		assertEquals(c3, revWalk.next());
		assertEquals(c1, revWalk.next());
	}

	@Test
	public void testParseBody() throws Exception {
		writeTrashFile("a.txt", "content");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c1 = repository.git().commit().setMessage("first commit")
				.call();

		writeTrashFile("b.txt", "new file added");
		repository.git().add().addFilepattern("b.txt").call();
		repository.git().commit().setMessage("second commit").call();

		writeTrashFile("a.txt", "content added");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c3 = repository.git().commit().setMessage("third commit")
				.call();

		FilteredRevCommit filteredRevCommit = new FilteredRevCommit(c3, c1);
		filteredRevCommit.disposeBody();

		RevWalk revWalk = repository.getRevWalk();

		revWalk.parseBody(filteredRevCommit);
		assertEquals(filteredRevCommit.getFullMessage(), c3.getFullMessage());
		assertEquals(filteredRevCommit.getShortMessage(), c3.getShortMessage());
		assertEquals(filteredRevCommit.commitTime, c3.commitTime);
		assertSame(filteredRevCommit.getTree(), c3.getTree());
		assertSame(filteredRevCommit.parents, RevCommit.NO_PARENTS);

	}

	/**
	 * Test that the uninteresting flag is carried over correctly. Every commit
	 * should have the uninteresting flag resulting in a RevWalk returning no
	 * commit.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRevWalkCarryUninteresting() throws Exception {
		writeTrashFile("a.txt", "content");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c1 = repository.git().commit().setMessage("first commit")
				.call();

		writeTrashFile("b.txt", "new file added");
		repository.git().add().addFilepattern("b.txt").call();
		RevCommit c2 = repository.git().commit().setMessage("second commit")
				.call();

		writeTrashFile("a.txt", "content added");
		repository.git().add().addFilepattern("a.txt").call();
		RevCommit c3 = repository.git().commit().setMessage("third commit")
				.call();

		RevWalk revWalk = repository.getRevWalk();
		FilteredRevCommit filteredCommit1 = new FilteredRevCommit(c1);
		FilteredRevCommit filteredCommit2 = new FilteredRevCommit(c2,
				filteredCommit1);
		FilteredRevCommit filteredCommit3 = new FilteredRevCommit(c3,
				filteredCommit2);

		revWalk.markStart(filteredCommit2);
		markUninteresting(filteredCommit3);
		assertNull("Found an unexpected commit", rw.next());
	}
}
