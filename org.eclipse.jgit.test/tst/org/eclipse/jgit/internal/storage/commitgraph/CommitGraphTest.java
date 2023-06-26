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

import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN_V1;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN_V2;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
	}

	@Test
	public void testGraphWithSingleCommit() throws Exception {
		RevCommit root = commit();
		writeAndReadCommitGraph(Collections.singleton(root));
		verifyCommitGraph();
		assertEquals(1, getGenerationNumberV1(root));
		assertNoCommitDateCorrection(root);
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

		assertEquals(1, getGenerationNumberV1(root));
		assertNoCommitDateCorrection(root);

		for (RevCommit parent : parents) {
			assertEquals(2, getGenerationNumberV1(parent));
			assertNoCommitDateCorrection(parent);
		}
		assertEquals(3, getGenerationNumberV1(tip));
		assertNoCommitDateCorrection(tip);
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
			assertEquals(i + 1, getGenerationNumberV1(commits[i]));
			assertNoCommitDateCorrection(commits[i]);
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

		assertEquals(getGenerationNumberV1(c1), 1);
		assertEquals(getGenerationNumberV1(c2), 2);
		assertEquals(getGenerationNumberV1(c4), 2);
		assertEquals(getGenerationNumberV1(c6), 2);
		assertEquals(getGenerationNumberV1(c3), 3);
		assertEquals(getGenerationNumberV1(c5), 3);
		assertEquals(getGenerationNumberV1(c7), 3);
		assertEquals(getGenerationNumberV1(m1), 3);
		assertEquals(getGenerationNumberV1(m2), 3);
		assertEquals(getGenerationNumberV1(m3), 4);
		assertEquals(getGenerationNumberV1(c8), 5);

		assertNoCommitDateCorrection(c1);
		assertNoCommitDateCorrection(c2);
		assertNoCommitDateCorrection(c3);
		assertNoCommitDateCorrection(c4);
		assertNoCommitDateCorrection(c5);
		assertNoCommitDateCorrection(c6);
		assertNoCommitDateCorrection(c7);
		assertNoCommitDateCorrection(c8);
		assertNoCommitDateCorrection(m1);
		assertNoCommitDateCorrection(m2);
		assertNoCommitDateCorrection(m3);
	}

	@Test
	public void testGraphWithClockSkews() throws Exception {
		RevCommit root = commit(new Date(1001 * 1000));
		RevCommit c1 = commit(new Date(1 * 1000), root);
		RevCommit c2 = commit(new Date(2 * 1000), root);
		RevCommit c3 = commit(new Date(3 * 1000), root);
		RevCommit tip = commit(new Date(0), c1, c2, c3);

		Set<ObjectId> wants = new HashSet<>();
		wants.add(tip);

		writeAndReadCommitGraph(wants);
		assertEquals(5, commitGraph.getCommitCnt());

		assertCorrectedCommitDate(root);
		assertCorrectedCommitDate(c1);
		assertCorrectedCommitDate(c2);
		assertCorrectedCommitDate(c3);
		assertCorrectedCommitDate(tip);
	}

	@Test
	public void testGraphWithClockSkewsWithOffsetOverflow() throws Exception {
		RevCommit root = commit(new Date((Integer.MAX_VALUE) * 1000L));
		RevCommit c1 = commit(new Date(0), root);
		RevCommit c2 = commit(new Date(0), c1);
		RevCommit c3 = commit(new Date(0), c2);
		RevCommit tip = commit(new Date(0), c3);

		Set<ObjectId> wants = new HashSet<>();
		wants.add(tip);

		writeAndReadCommitGraph(wants);
		assertEquals(5, commitGraph.getCommitCnt());
		assertCorrectedCommitDate(root);
		assertCorrectedCommitDate(c1);
		assertCorrectedCommitDate(c2);
		assertCorrectedCommitDate(c3);
		assertCorrectedCommitDate(tip);
	}

	void writeAndReadCommitGraph(Set<ObjectId> wants) throws Exception {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		try (RevWalk walk = new RevWalk(db)) {
			CommitGraphWriter writer = new CommitGraphWriter(
					GraphCommits.fromWalk(m, wants, walk), 2);
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

	void assertNoCommitDateCorrection(RevCommit commit) {
		assertCorrectedCommitDate(commit);
	}

	void assertCorrectedCommitDate(RevCommit commit) {
		assertEquals(getExpectedGenerationNumberV2(commit),
				getGenerationNumberV2(commit));
	}

	int getGenerationNumberV1(ObjectId id) {
		int graphPos = commitGraph.findGraphPosition(id);
		CommitGraph.CommitData commitData = commitGraph.getCommitData(graphPos);
		if (commitData != null) {
			return commitData.getGenerationV1();
		}
		return COMMIT_GENERATION_UNKNOWN_V1;
	}

	long getGenerationNumberV2(RevCommit commit) {
		int graphPos = commitGraph.findGraphPosition(commit.toObjectId());
		CommitGraph.GenerationData generationData = commitGraph
				.getGenerationData(graphPos);
		if (generationData != null) {
			return commit.getCommitTime() + generationData.getGenerationV2Offset();
		}
		return COMMIT_GENERATION_UNKNOWN_V2;
	}

	long getExpectedGenerationNumberV2(RevCommit commit) {
		long commitDate = commit.getCommitTime();
		if (commit.getParentCount() == 0) {
			return commitDate;
		}
		long maxParentCorrectedCommitDate = Arrays.stream(commit.getParents())
				.map(this::getGenerationNumberV2).max(Long::compare).get();
		if (commitDate > maxParentCorrectedCommitDate) {
			return commitDate;
		}
		return maxParentCorrectedCommitDate + 1;
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}

	RevCommit commit(Date commitDate, RevCommit... parents) throws Exception {
		return tr.commit(commitDate, parents);
	}
}
