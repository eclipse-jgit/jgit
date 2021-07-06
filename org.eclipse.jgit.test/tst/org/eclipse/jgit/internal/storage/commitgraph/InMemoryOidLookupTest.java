/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class InMemoryOidLookupTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
	}

	@Test
	public void testEmptyRepo() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			InMemoryOidLookup lookup = InMemoryOidLookup.load(
					NullProgressMonitor.INSTANCE, Collections.emptySet(), rw);
			assertEquals(0, lookup.size());
			assertEquals(0, lookup.getExtraEdgeCnt());
			assertNull(lookup.getCommit(0));
		}
	}

	@Test
	public void testRepoWithoutExtraEdges() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit root = commit();
			RevCommit a = commit(root);
			RevCommit b = commit(root);
			RevCommit tip = commit(a, b);
			InMemoryOidLookup lookup = InMemoryOidLookup.load(
					NullProgressMonitor.INSTANCE, Collections.singleton(tip),
					rw);
			assertEquals(4, lookup.size());
			assertEquals(0, lookup.getExtraEdgeCnt());
			verifyOrderOfLookup(List.of(root, a, b, tip), lookup);
		}
	}

	@Test
	public void testRepoWithExtraEdges() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit root = commit();
			RevCommit a = commit(root);
			RevCommit b = commit(root);
			RevCommit c = commit(root);
			RevCommit tip = commit(a, b, c);
			InMemoryOidLookup lookup = InMemoryOidLookup.load(
					NullProgressMonitor.INSTANCE, Collections.singleton(tip),
					rw);
			assertEquals(5, lookup.size());
			assertEquals(2, lookup.getExtraEdgeCnt());
			verifyOrderOfLookup(List.of(root, a, b, c, tip), lookup);
		}
	}

	@Test
	public void testCommitNotInLookup() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit a = commit();
			RevCommit b = commit(a);
			InMemoryOidLookup lookup = InMemoryOidLookup.load(
					NullProgressMonitor.INSTANCE, Collections.singleton(a),
					rw);
			assertEquals(1, lookup.size());
			assertEquals(0, lookup.getExtraEdgeCnt());
			assertEquals(a, lookup.getCommit(0));
			assertNull(lookup.getCommit(1));
			assertThrows(MissingObjectException.class, () -> lookup.getOidPosition(b));
		}
	}

	private void verifyOrderOfLookup(List<RevCommit> commits,
			InMemoryOidLookup lookup) throws MissingObjectException {
		commits = new ArrayList<>(commits);
		Collections.sort(commits);
		for (int i = 0; i < commits.size(); i++) {
			RevCommit c = commits.get(i);
			assertEquals(c, lookup.getCommit(i));
			assertEquals(i, lookup.getOidPosition(c));
		}
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}
}
