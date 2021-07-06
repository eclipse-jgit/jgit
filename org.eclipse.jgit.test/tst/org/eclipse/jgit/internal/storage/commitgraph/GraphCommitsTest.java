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

import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN;
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

public class GraphCommitsTest extends RepositoryTestCase {

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
			GraphCommits commits = GraphCommits.fromWalk(
					NullProgressMonitor.INSTANCE, Collections.emptySet(), rw);
			assertEquals(0, commits.size());
			assertEquals(0, commits.getExtraEdgeCnt());
			assertNull(commits.getCommit(0));
		}
	}

	@Test
	public void testRepoWithoutExtraEdges() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit root = commit();
			RevCommit a = commit(root);
			RevCommit b = commit(root);
			RevCommit tip = commit(a, b);
			GraphCommits commits = GraphCommits.fromWalk(
					NullProgressMonitor.INSTANCE, Collections.singleton(tip),
					rw);
			assertEquals(4, commits.size());
			assertEquals(0, commits.getExtraEdgeCnt());
			verifyOrderOfLookup(List.of(root, a, b, tip), commits);
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
			GraphCommits commits = GraphCommits.fromWalk(
					NullProgressMonitor.INSTANCE, Collections.singleton(tip),
					rw);
			assertEquals(5, commits.size());
			assertEquals(2, commits.getExtraEdgeCnt());
			verifyOrderOfLookup(List.of(root, a, b, c, tip), commits);
		}
	}

	@Test
	public void testCommitNotInLookup() throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit a = commit();
			RevCommit b = commit(a);
			GraphCommits commits = GraphCommits.fromWalk(
					NullProgressMonitor.INSTANCE, Collections.singleton(a), rw);
			assertEquals(1, commits.size());
			assertEquals(0, commits.getExtraEdgeCnt());
			assertEquals(a, commits.getCommit(0));
			assertNull(commits.getCommit(1));
			assertThrows(MissingObjectException.class,
					() -> commits.getOidPosition(b));
		}
	}

	private void verifyOrderOfLookup(List<RevCommit> commits,
			GraphCommits graphCommits) throws MissingObjectException {
		commits = new ArrayList<>(commits);
		Collections.sort(commits);
		for (int i = 0; i < commits.size(); i++) {
			RevCommit c = commits.get(i);
			assertEquals(c, graphCommits.getCommit(i));
			assertEquals(i, graphCommits.getOidPosition(c));
		}
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}
}
