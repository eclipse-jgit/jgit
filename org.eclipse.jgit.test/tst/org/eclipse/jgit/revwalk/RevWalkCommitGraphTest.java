/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class RevWalkCommitGraphTest extends RevWalkTestCase {

	@Test
	public void testParseHeadersInGraph() throws Exception {
		RevCommit c1 = commitFile("file1", "1", "master");

		enableAndWriteCommitGraph();

		rw.setRetainBody(true);
		rw.dispose();
		RevCommit notParseInGraph = rw.lookupCommit(c1);
		rw.parseHeaders(notParseInGraph);
		assertNotNull(notParseInGraph.getRawBuffer());
		assertEquals(Constants.COMMIT_GENERATION_UNKNOWN,
				notParseInGraph.getGeneration());

		rw.dispose();
		RevCommit parseInGraph = rw.lookupCommit(c1);
		rw.parseHeadersInGraph(parseInGraph);
		assertNull(parseInGraph.getRawBuffer());
		assertEquals(1, parseInGraph.getGeneration());

		assertEquals(notParseInGraph.getId(), parseInGraph.getId());
		assertEquals(notParseInGraph.getTree(), parseInGraph.getTree());
		assertEquals(notParseInGraph.getCommitTime(), parseInGraph.getCommitTime());
		assertArrayEquals(notParseInGraph.getParents(), parseInGraph.getParents());
	}

	void enableAndWriteCommitGraph() throws Exception {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		GC gc = new GC(db);
		gc.gc();
		assertNotNull(rw.getCommitGraph());
	}

	@Test
	public void testTreeFilter() throws Exception {
		RevCommit c1 = commitFile("file1", "1", "master");
		RevCommit c2 = commitFile("file2", "2", "master");
		RevCommit c3 = commitFile("file1", "3", "master");
		RevCommit c4 = commitFile("file2", "4", "master");

		enableAndWriteCommitGraph();
		assertCommitCntInGraph(4);

		rw.markStart(rw.lookupCommit(c4));
		rw.setTreeFilter(AndTreeFilter.create(PathFilter.create("file1"),
				TreeFilter.ANY_DIFF));
		assertEquals(c3, rw.next());
		assertEquals(c1, rw.next());
		assertNull(rw.next());

		rw.dispose();
		rw.markStart(rw.lookupCommit(c4));
		rw.setTreeFilter(AndTreeFilter.create(PathFilter.create("file2"),
				TreeFilter.ANY_DIFF));
		assertEquals(c4, rw.next());
		assertEquals(c2, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testWalkWithCommitMessageFilter() throws Exception {
		RevCommit a = commit();
		RevCommit b = commitBuilder().parent(a)
				.message("The quick brown fox jumps over the lazy dog!")
				.create();
		RevCommit c = commitBuilder().parent(b).message("commit-c").create();
		branch(c, "master");

		enableAndWriteCommitGraph();
		assertCommitCntInGraph(3);

		rw.setRevFilter(MessageRevFilter.create("quick brown fox jumps"));
		rw.markStart(c);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testCommitsWalk() throws Exception {
		RevCommit c1 = commit();
		branch(c1, "commits/1");
		RevCommit c2 = commit(c1);
		branch(c2, "commits/2");
		RevCommit c3 = commit(c2);
		branch(c3, "commits/3");

		enableAndWriteCommitGraph();
		testRevWalkBehavior("commits/1", "commits/3");
		assertCommitCntInGraph(3);

		// add more commits
		RevCommit c4 = commit(c1);
		RevCommit c5 = commit(c4);
		RevCommit c6 = commit(c1);
		RevCommit c7 = commit(c6);

		RevCommit m1 = commit(c2, c4);
		branch(m1, "merge/1");
		RevCommit m2 = commit(c4, c6);
		branch(m2, "merge/2");
		RevCommit m3 = commit(c3, c5, c7);
		branch(m3, "merge/3");

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
		enableAndWriteCommitGraph();
		assertCommitCntInGraph(10);
		testRevWalkBehavior("merge/1", "merge/2");
		testRevWalkBehavior("merge/1", "merge/3");
		testRevWalkBehavior("merge/2", "merge/3");

		// add one more commit
		RevCommit c8 = commit(m3);
		branch(c8, "commits/8");

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
		testRevWalkBehavior("commits/8", "merge/1");
		testRevWalkBehavior("commits/8", "merge/2");

		enableAndWriteCommitGraph();
		assertCommitCntInGraph(11);
		testRevWalkBehavior("commits/8", "merge/1");
		testRevWalkBehavior("commits/8", "merge/2");
	}

	@Test
	public void testMergedInto() throws Exception {
		RevCommit c1 = commit();
		Ref branch1 = branch(c1, "commits/1");
		RevCommit c2 = commit(c1);
		Ref branch2 = branch(c2, "commits/2");
		RevCommit c3 = commit(c2);
		Ref branch3 = branch(c3, "commits/3");
		RevCommit c4 = commit(c1);
		Ref branch4 = branch(c4, "commits/4");
		RevCommit c5 = commit(c4);
		Ref branch5 = branch(c5, "commits/5");
		RevCommit c6 = commit(c1);
		Ref branch6 = branch(c6, "commits/6");
		RevCommit c7 = commit(c2, c4);
		Ref branch7 = branch(c7, "commits/7");
		RevCommit c8 = commit(c5);
		Ref branch8 = branch(c8, "commits/8");
		RevCommit c9 = commit(c4, c6);
		Ref branch9 = branch(c9, "commits/9");
		enableAndWriteCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *       8
		 *       |
		 *  3  7 5  9
		 *  |/  \|/  \
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */

		assertRefsEquals(asList(branch8), allMergedInto(c8));
		assertRefsEquals(asList(branch5, branch8), allMergedInto(c5));
		assertRefsEquals(asList(branch4, branch5, branch7, branch8, branch9),
				allMergedInto(c4));
		assertRefsEquals(asList(branch2, branch3, branch7), allMergedInto(c2));
		assertRefsEquals(asList(branch1, branch2, branch3, branch4, branch5,
				branch6, branch7, branch8, branch9), allMergedInto(c1));
	}

	List<Ref> allMergedInto(RevCommit needle) throws IOException {
		List<Ref> refs = db.getRefDatabase().getRefs();
		return rw.getMergedInto(needle, refs);
	}

	void assertRefsEquals(List<Ref> expecteds, List<Ref> actuals) {
		assertEquals(expecteds.size(), actuals.size());
		for (int i=0; i < expecteds.size(); i ++) {
			Ref expected = expecteds.get(i);
			Ref actual = actuals.get(i);
			assertEquals(expected.getName(), actual.getName());
			assertEquals(expected.getObjectId(), actual.getObjectId());
		}
	}

	void assertCommitCntInGraph(int expect) {
		rw.dispose();
		CommitGraph graph = rw.getCommitGraph();

		if (expect <= 0) {
			assertNull(graph);
			return;
		}
		assertNotNull(graph);
		assertEquals(expect, graph.getCommitCnt());
	}

	void testRevWalkBehavior(String branch, String compare) throws Exception {
		rw.setTreeFilter(TreeFilter.ALL);
		rw.setRevFilter(RevFilter.MERGE_BASE);
		testGraphTwoModes(branch, compare);

		rw.setRevFilter(RevFilter.ALL);
		rw.sort(RevSort.TOPO);
		testGraphTwoModes(branch);
		testGraphTwoModes(compare);

		rw.setRevFilter(RevFilter.ALL);
		rw.sort(RevSort.COMMIT_TIME_DESC);
		testGraphTwoModes(branch);
		testGraphTwoModes(compare);
	}

	Ref branch(RevCommit commit, String name) throws Exception {
		return Git.wrap(db).branchCreate().setName(name)
				.setStartPoint(commit.name()).call();
	}

	void testGraphTwoModes(String branch) throws Exception {
		testGraphTwoModes(Collections.singleton(db.resolve(branch)));
	}

	void testGraphTwoModes(String branch, String compare)
			throws Exception {
		List<ObjectId> commits = new ArrayList<>();
		commits.add(db.resolve(branch));
		commits.add(db.resolve(compare));
		testGraphTwoModes(commits);
	}

	void testGraphTwoModes(Collection<ObjectId> starts)
			throws Exception {
		// without commit-graph
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, false);
		rw.dispose();

		for (ObjectId start : starts) {
			markStart(rw.lookupCommit(start));
		}
		List<RevCommit> withOutGraph = new ArrayList<>();

		assertNull(rw.getCommitGraph());
		for (RevCommit commit : rw) {
			withOutGraph.add(commit);
		}

		// with commit-graph
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, true);
		rw.dispose();

		for (ObjectId start : starts) {
			markStart(rw.lookupCommit(start));
		}
		List<RevCommit> withGraph = new ArrayList<>();

		assertNotNull(rw.getCommitGraph());
		for (RevCommit commit : rw) {
			withGraph.add(commit);
		}
		rw.dispose();

		assertEquals(withOutGraph.size(), withGraph.size());

		for (int i = 0; i < withGraph.size(); i++) {
			RevCommit expect = withOutGraph.get(i);
			RevCommit commit = withGraph.get(i);

			assertEquals(expect.getId(), commit.getId());
			assertEquals(expect.getTree(), commit.getTree());
			assertEquals(expect.getCommitTime(), commit.getCommitTime());
			assertArrayEquals(expect.getParents(), commit.getParents());
			assertArrayEquals(expect.getRawBuffer(), commit.getRawBuffer());
		}
	}
}
