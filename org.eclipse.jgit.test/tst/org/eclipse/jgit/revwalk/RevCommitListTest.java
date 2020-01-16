/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class RevCommitListTest extends RepositoryTestCase {

	private RevCommitList<RevCommit> list;

	public void setup(int count) throws Exception {
		try (Git git = new Git(db);
				RevWalk w = new RevWalk(db);) {
			for (int i = 0; i < count; i++)
				git.commit().setCommitter(committer).setAuthor(author)
						.setMessage("commit " + i).call();
			list = new RevCommitList<>();
			w.markStart(w.lookupCommit(db.resolve(Constants.HEAD)));
			list.source(w);
		}
	}

	@Test
	public void testFillToHighMark2() throws Exception {
		setup(3);
		list.fillTo(1);
		assertEquals(2, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 1", list.get(1).getFullMessage());
		assertNull("commit 0 shouldn't be loaded", list.get(2));
	}

	@Test
	public void testFillToHighMarkAll() throws Exception {
		setup(3);
		list.fillTo(2);
		assertEquals(3, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 0", list.get(2).getFullMessage());
	}

	@Test
	public void testFillToHighMark4() throws Exception {
		setup(3);
		list.fillTo(3);
		assertEquals(3, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 0", list.get(2).getFullMessage());
		assertNull("commit 3 can't be loaded", list.get(3));
	}

	@Test
	public void testFillToHighMarkMulitpleBlocks() throws Exception {
		setup(258);
		list.fillTo(257);
		assertEquals(258, list.size());
	}

	@Test
	public void testFillToCommit() throws Exception {
		setup(3);

		try (RevWalk w = new RevWalk(db)) {
			w.markStart(w.lookupCommit(db.resolve(Constants.HEAD)));

			w.next();
			RevCommit c = w.next();
			assertNotNull("should have found 2. commit", c);

			list.fillTo(c, 5);
			assertEquals(2, list.size());
			assertEquals("commit 1", list.get(1).getFullMessage());
			assertNull(list.get(3));
		}
	}

	@Test
	public void testFillToUnknownCommit() throws Exception {
		setup(258);
		RevCommit c = new RevCommit(
				ObjectId.fromString("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));

		list.fillTo(c, 300);
		assertEquals("loading to unknown commit should load all commits", 258,
				list.size());
	}

	@Test
	public void testFillToNullCommit() throws Exception {
		setup(3);
		list.fillTo(null, 1);
		assertNull(list.get(0));
	}
}
