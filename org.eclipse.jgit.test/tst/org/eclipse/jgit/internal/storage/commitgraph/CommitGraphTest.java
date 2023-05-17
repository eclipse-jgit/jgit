/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Test writing and then reading the commit-graph.
 */
public class CommitGraphTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private CommitGraph commitGraph;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
		mockSystemReader.setJGitConfig(new MockConfig());
	}

	@Test
	public void testGraphWithSingleCommit() throws Exception {
		RevCommit root = commit();
		writeAndReadCommitGraph(Collections.singleton(root));
		verifyCommitGraph();
		assertEquals(1, getGenerationNumber(root));
	}

	@Test
	public void testGraphWithManyParents() throws Exception {
		int parentsNum = 40;
		RevCommit root = commit();

		RevCommit[] parents = new RevCommit[parentsNum];
		for (int i = 0; i < parents.length; i++) {
			parents[i] = commit(root);
		}
		RevCommit tip = commit(parents);

		Set<ObjectId> wants = Collections.singleton(tip);
		writeAndReadCommitGraph(wants);
		assertEquals(parentsNum + 2, commitGraph.getCommitCnt());
		verifyCommitGraph();

		assertEquals(1, getGenerationNumber(root));
		for (RevCommit parent : parents) {
			assertEquals(2, getGenerationNumber(parent));
		}
		assertEquals(3, getGenerationNumber(tip));
	}

	@Test
	public void testGraphLinearHistory() throws Exception {
		int commitNum = 20;
		RevCommit[] commits = new RevCommit[commitNum];
		for (int i = 0; i < commitNum; i++) {
			if (i == 0) {
				commits[i] = commit();
			} else {
				commits[i] = commit(commits[i - 1]);
			}
		}

		Set<ObjectId> wants = Collections.singleton(commits[commitNum - 1]);
		writeAndReadCommitGraph(wants);
		assertEquals(commitNum, commitGraph.getCommitCnt());
		verifyCommitGraph();
		for (int i = 0; i < commitNum; i++) {
			assertEquals(i + 1, getGenerationNumber(commits[i]));
		}
	}

	@Test
	public void testGraphWithMerges() throws Exception {
		RevCommit c1 = commit();
		RevCommit c2 = commit(c1);
		RevCommit c3 = commit(c2);
		RevCommit c4 = commit(c1);
		RevCommit c5 = commit(c4);
		RevCommit c6 = commit(c1);
		RevCommit c7 = commit(c6);

		RevCommit m1 = commit(c2, c4);
		RevCommit m2 = commit(c4, c6);
		RevCommit m3 = commit(c3, c5, c7);

		Set<ObjectId> wants = new HashSet<>();

		/*
		 * <pre>
		 * current graph structure:
		 *    M1
		 *   /  \
		 *  2    4
		 *  |___/
		 *  1
		 * </pre>
		 */
		wants.add(m1);
		writeAndReadCommitGraph(wants);
		assertEquals(4, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *    M1   M2
		 *   /  \ /  \
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		wants.add(m2);
		writeAndReadCommitGraph(wants);
		assertEquals(6, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *
		 *    __M3___
		 *   /   |   \
		 *  3 M1 5 M2 7
		 *  |/  \|/  \|
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		wants.add(m3);
		writeAndReadCommitGraph(wants);
		assertEquals(10, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *       8
		 *       |
		 *    __M3___
		 *   /   |   \
		 *  3 M1 5 M2 7
		 *  |/  \|/  \|
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		RevCommit c8 = commit(m3);
		wants.add(c8);
		writeAndReadCommitGraph(wants);
		assertEquals(11, commitGraph.getCommitCnt());
		verifyCommitGraph();

		assertEquals(getGenerationNumber(c1), 1);
		assertEquals(getGenerationNumber(c2), 2);
		assertEquals(getGenerationNumber(c4), 2);
		assertEquals(getGenerationNumber(c6), 2);
		assertEquals(getGenerationNumber(c3), 3);
		assertEquals(getGenerationNumber(c5), 3);
		assertEquals(getGenerationNumber(c7), 3);
		assertEquals(getGenerationNumber(m1), 3);
		assertEquals(getGenerationNumber(m2), 3);
		assertEquals(getGenerationNumber(m3), 4);
		assertEquals(getGenerationNumber(c8), 5);
	}

	@Test
	public void testGraphComputeChangedPaths() throws Exception {
		RevCommit a = tr.commit(tr.tree(tr.file("d/f", tr.blob("a"))));
		RevCommit b = tr.commit(tr.tree(tr.file("d/f", tr.blob("a"))), a);
		RevCommit c = tr.commit(tr.tree(tr.file("d/f", tr.blob("b"))), b);

		writeAndReadCommitGraph(Collections.singleton(c));
		ChangedPathFilter acpf = commitGraph
				.getChangedPathFilter(commitGraph.findGraphPosition(a));
		assertTrue(acpf.maybeContains("d".getBytes(UTF_8)));
		assertTrue(acpf.maybeContains("d/f".getBytes(UTF_8)));
		ChangedPathFilter bcpf = commitGraph
				.getChangedPathFilter(commitGraph.findGraphPosition(b));
		assertFalse(bcpf.maybeContains("d".getBytes(UTF_8)));
		assertFalse(bcpf.maybeContains("d/f".getBytes(UTF_8)));
		ChangedPathFilter ccpf = commitGraph
				.getChangedPathFilter(commitGraph.findGraphPosition(c));
		assertTrue(ccpf.maybeContains("d".getBytes(UTF_8)));
		assertTrue(ccpf.maybeContains("d/f".getBytes(UTF_8)));
	}

	void writeAndReadCommitGraph(Set<ObjectId> wants) throws Exception {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		try (RevWalk walk = new RevWalk(db)) {
			CommitGraphWriter writer = new CommitGraphWriter(
					GraphCommits.fromWalk(m, wants, walk), true);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			writer.write(m, os);
			InputStream inputStream = new ByteArrayInputStream(
					os.toByteArray());
			commitGraph = CommitGraphLoader.read(inputStream);
		}
	}

	void verifyCommitGraph() throws Exception {
		try (RevWalk walk = new RevWalk(db)) {
			for (int i = 0; i < commitGraph.getCommitCnt(); i++) {
				ObjectId objId = commitGraph.getObjectId(i);

				// check the objectId index of commit-graph
				int pos = commitGraph.findGraphPosition(objId);
				assertEquals(i, pos);

				// check the commit meta of commit-graph
				CommitGraph.CommitData commit = commitGraph.getCommitData(i);
				int[] pList = commit.getParents();

				RevCommit expect = walk.lookupCommit(objId);
				walk.parseBody(expect);

				assertEquals(expect.getCommitTime(), commit.getCommitTime());
				assertEquals(expect.getTree(), commit.getTree());
				assertEquals(expect.getParentCount(), pList.length);

				if (pList.length > 0) {
					ObjectId[] parents = new ObjectId[pList.length];
					for (int j = 0; j < parents.length; j++) {
						parents[j] = commitGraph.getObjectId(pList[j]);
					}
					assertArrayEquals(expect.getParents(), parents);
				}
			}
		}
	}

	int getGenerationNumber(ObjectId id) {
		int graphPos = commitGraph.findGraphPosition(id);
		CommitGraph.CommitData commitData = commitGraph.getCommitData(graphPos);
		if (commitData != null) {
			return commitData.getGeneration();
		}
		return COMMIT_GENERATION_UNKNOWN;
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}

	private static final class MockConfig extends FileBasedConfig {
		private MockConfig() {
			super(null, null);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public void save() throws IOException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}

		@Override
		public String toString() {
			return "MockConfig";
		}

		@Override
		public boolean getBoolean(final String section, final String name,
				final boolean defaultValue) {
			if (section.equals(ConfigConstants.CONFIG_CORE_SECTION) && name
					.equals(ConfigConstants.CONFIG_KEY_READ_BLOOM_FILTER)) {
				return true;
			}
			return defaultValue;
		}
	}
}
