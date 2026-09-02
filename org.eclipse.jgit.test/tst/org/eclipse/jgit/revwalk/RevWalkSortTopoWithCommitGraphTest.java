/*
 * Copyright (C) 2026, Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.ThrowingConsumer;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.ChangedPathTreeFilter;
import org.junit.Test;

public class RevWalkSortTopoWithCommitGraphTest
		extends AbstractRevWalkWithCommitGraphTest {

	@Test
	public void testSort_TOPO_WithTreeFilter() throws Exception {
		RevCommit a = commit(tree(file("a", blob("1"))));
		RevCommit b = commit(tree(file("a", blob("2"))), a);
		RevCommit c1 = commit(-5, tree(file("a", blob("3"))), b);
		RevCommit c2 = commit(10,
				tree(file("a", blob("2")), file("b", blob("1"))), b);
		RevCommit d = commit(tree(file("a", blob("3")), file("b", blob("1"))),
				c1, c2);
		RevCommit e = commit(tree(file("a", blob("4")), file("b", blob("2"))),
				d);
		branch(e, "main");

		testWalkBehavior( //
				walk -> {
					walk.sort(RevSort.TOPO);
					walk.setTreeFilter(ChangedPathTreeFilter.create("a"));
					walk.markStart(walk.lookupCommit(e));
				}, //
				actual -> {
					assertEquals(4, actual.size());

					assertRewrittenCommit(actual.get(0), e, c1);
					assertRewrittenCommit(actual.get(1), c1, b);
					assertRewrittenCommit(actual.get(2), b, a);
					assertRewrittenCommit(actual.get(3), a);
				} //
		);
	}

	@Test
	public void testSort_TOPO_WithTreeFilter_Reachable() throws Exception {
		RevCommit a = commit(tree(file("a", blob("1"))));
		RevCommit b = commit(tree(file("a", blob("2"))), a);
		RevCommit c1 = commit(-5, tree(file("a", blob("3"))), b);
		RevCommit c2 = commit(10,
				tree(file("a", blob("2")), file("b", blob("1"))), b);
		RevCommit d = commit(tree(file("a", blob("3")), file("b", blob("1"))),
				c1, c2);
		RevCommit e = commit(tree(file("a", blob("4")), file("b", blob("2"))),
				d);
		branch(e, "main");

		testWalkBehavior( //
				walk -> {
					walk.sort(RevSort.TOPO);
					walk.setTreeFilter(ChangedPathTreeFilter.create("a"));
					walk.markStart(walk.lookupCommit(e));
					walk.markUninteresting(walk.lookupCommit(b));
				}, //
				actual -> {
					assertEquals(2, actual.size());

					assertRewrittenCommit(actual.get(0), e, c1);
					assertRewrittenCommit(actual.get(1), c1, b);
				} //
		);
	}

	@Test
	public void testSort_TOPO_WithTreeFilter_Reachable2() throws Exception {
		RevCommit a = commit(tree(file("a", blob("1"))));
		RevCommit b = commit(tree(file("a", blob("2"))), a);
		RevCommit c1 = commit(-5, tree(file("a", blob("3"))), b);
		RevCommit c2 = commit(10,
				tree(file("a", blob("2")), file("b", blob("1"))), b);
		RevCommit d = commit(tree(file("a", blob("3")), file("b", blob("1"))),
				c1, c2);
		RevCommit e = commit(tree(file("a", blob("4")), file("b", blob("2"))),
				d);
		branch(e, "main");

		testWalkBehavior( //
				walk -> {
					walk.sort(RevSort.TOPO);
					walk.setTreeFilter(ChangedPathTreeFilter.create("a"));
					walk.markStart(walk.lookupCommit(e));
					walk.markUninteresting(walk.lookupCommit(e));
				}, //
				actual -> {
					assertEquals(0, actual.size());
				} //
		);
	}

	@Test
	public void testInDegreePhase_UninterestingRootsNotReturnedFromNextReady()
			throws Exception {
		RevCommit u = commit(10);
		RevCommit c = commit(5);
		branch(u, "uninteresting_branch");
		branch(c, "main");

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		RevCommit uInWalk = rw.lookupCommit(u);
		RevCommit cInWalk = rw.lookupCommit(c);
		rw.parseHeaders(uInWalk);
		rw.parseHeaders(cInWalk);
		uInWalk.add(RevFlag.UNINTERESTING);

		TopoExplorePhase explorePhase = new TopoExplorePhase(rw, RevFilter.ALL,
				true);
		TopoInDegreePhase inDegreePhase = new TopoInDegreePhase(rw,
				explorePhase, false);

		FIFORevQueue q = new FIFORevQueue();
		q.add(uInWalk);
		q.add(cInWalk);
		inDegreePhase.initialize(q);

		// Only the interesting commit c should ever be ready; u is uninteresting and must not be returned
		assertEquals(cInWalk, inDegreePhase.nextReady());
		assertNull(inDegreePhase.nextReady());
	}

	@Test
	public void testSort_TOPO_UninterestingRootDoesNotTriggerEagerExplorationOfInterestingBranch()
			throws Exception {
		RevCommit u = commit();
		for (int i = 0; i < 5; i++) {
			u = commit(u);
		}
		// u has generation 6

		RevCommit c = commit();
		RevCommit c15 = null;
		for (int i = 0; i < 24; i++) {
			c = commit(c);
			if (i == 14) {
				c15 = c; // generation 16
			}
		}
		// c has generation 25

		branch(u, "uninteresting_branch");
		branch(c, "main");

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		rw.sort(RevSort.TOPO);
		RevCommit cInWalk = rw.lookupCommit(c);
		RevCommit c15InWalk = rw.lookupCommit(c15);
		RevCommit uInWalk = rw.lookupCommit(u);
		rw.markStart(cInWalk);
		rw.markUninteresting(uInWalk);

		// Read only the first commit at generation 25
		assertCommit(cInWalk, rw.next());

		// c15 is at generation 16. Without the optimization, u (generation 6)
		// dragged minGeneration down to 6, forcing calculateInDegrees to eagerly
		// explore c down to generation 6. With the optimization, c15 is not explored yet.
		assertNull(c15InWalk.getParents());
	}

	@Test
	public void testSort_TOPO_UninterestingBranchWithLowerGenerationNotExplored()
			throws Exception {
		RevCommit u = commit();
		RevCommit u3 = null;
		for (int i = 0; i < 5; i++) {
			u = commit(u);
			if (i == 2) {
				u3 = u;
			}
		}
		// u has generation 6, u3 has generation 4

		RevCommit base = commit();
		for (int i = 0; i < 9; i++) {
			base = commit(base);
		}
		// base has generation 10

		RevCommit c = base;
		for (int i = 0; i < 5; i++) {
			c = commit(c);
		}
		// c has generation 15

		branch(u, "uninteresting_branch");
		branch(c, "main");

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		rw.sort(RevSort.TOPO);
		RevCommit cInWalk = rw.lookupCommit(c);
		RevCommit baseInWalk = rw.lookupCommit(base);
		RevCommit uInWalk = rw.lookupCommit(u);
		RevCommit u3InWalk = rw.lookupCommit(u3);
		rw.markStart(cInWalk);
		rw.markUninteresting(baseInWalk);
		rw.markUninteresting(uInWalk);

		// Read all 5 commits of c (generations 15 down to 11)
		for (int i = 0; i < 5; i++) {
			rw.next();
		}
		// Walk completes because base (gen 10) is uninteresting
		assertNull(rw.next());

		// u is at generation 6, well below generation 10. Its ancestors should not have been explored.
		assertNull(u3InWalk.getParents());
	}

	@Test
	public void testSort_TOPO_UninterestingBranchHigherGenerationNotExploredBelowCutoff()
			throws Exception {
		RevCommit b = commit();
		RevCommit b10 = null;
		for (int i = 0; i < 30; i++) {
			b = commit(b);
			if (i == 9) {
				b10 = b;
			}
		}
		// b has generation 31, b10 has generation 11

		RevCommit cBase = commit();
		for (int i = 0; i < 14; i++) {
			cBase = commit(cBase);
		}
		// cBase is at generation 15

		RevCommit c = cBase;
		for (int i = 0; i < 4; i++) {
			c = commit(c);
		}
		// c is at generation 19

		branch(b, "uninteresting_branch");
		branch(c, "main");

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		rw.sort(RevSort.TOPO);
		RevCommit cInWalk = rw.lookupCommit(c);
		RevCommit cBaseInWalk = rw.lookupCommit(cBase);
		RevCommit bInWalk = rw.lookupCommit(b);
		RevCommit b10InWalk = rw.lookupCommit(b10);
		rw.markStart(cInWalk);
		rw.markUninteresting(cBaseInWalk);
		rw.markUninteresting(bInWalk);

		// Read all 4 commits of c (generations 19 down to 16)
		for (int i = 0; i < 4; i++) {
			rw.next();
		}
		assertNull(rw.next());

		// b10 is at generation 11, well below generation 15.
		// b was explored down to 16, but NOT down to 11.
		assertNull(b10InWalk.getParents());
	}

	@Test
	public void testSort_TOPO_UninterestingBranchWithMergeBase()
			throws Exception {
		RevCommit root = commit();
		for (int i = 0; i < 4; i++) {
			root = commit(root);
		}
		// root has generation 5

		RevCommit m = root;
		for (int i = 0; i < 5; i++) {
			m = commit(m);
		}
		// m (merge base) has generation 10

		RevCommit b = m;
		for (int i = 0; i < 5; i++) {
			b = commit(b);
		}
		// b has generation 15

		RevCommit c = m;
		for (int i = 0; i < 3; i++) {
			c = commit(c);
		}
		// c has generation 13

		branch(b, "uninteresting_branch");
		branch(c, "main");

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		rw.sort(RevSort.TOPO);
		RevCommit cInWalk = rw.lookupCommit(c);
		RevCommit bInWalk = rw.lookupCommit(b);
		RevCommit rootInWalk = rw.lookupCommit(root);
		rw.markStart(cInWalk);
		rw.markUninteresting(bInWalk);

		// Only the 3 commits unique to c should be output
		List<RevCommit> results = new ArrayList<>();
		RevCommit next;
		while ((next = rw.next()) != null) {
			results.add(next);
		}
		assertEquals(3, results.size());

		// History below merge base m (generation 10) should not have been explored
		assertNull(rootInWalk.getParents());
	}

	// Test the RevWalk behavior with and without enabled commit graph
	private void testWalkBehavior(ThrowingConsumer<RevWalk> configureWalker,
			Consumer<List<RevCommit>> assertResult) throws Exception {
		configureWalker.accept(rw);
		List<RevCommit> actual = new ArrayList<>();
		rw.forEach(actual::add);

		assertEquals(CommitGraph.EMPTY, rw.commitGraph());
		for (RevCommit c : actual) {
			assertEquals(Constants.COMMIT_GENERATION_UNKNOWN,
					c.getGeneration());
		}

		assertResult.accept(actual);

		enableAndWriteCommitGraph();
		reinitializeRevWalk();

		configureWalker.accept(rw);
		actual.clear();
		rw.forEach(actual::add);

		assertNotEquals(0, rw.commitGraph().getCommitCnt());
		for (RevCommit c : actual) {
			assertNotEquals(Constants.COMMIT_GENERATION_UNKNOWN,
					c.getGeneration());
		}

		assertResult.accept(actual);
	}

	private void assertRewrittenCommit(RevCommit actual, AnyObjectId expectId,
			RevCommit... expectParents) {
		assertObjectId(actual, expectId);
		assertEquals(expectParents.length, actual.getParentCount());

		for (int i = 0; i < expectParents.length; i++) {
			assertObjectId(actual.getParent(i), expectParents[i]);
		}
	}

	private void assertObjectId(AnyObjectId actual, AnyObjectId expectId) {
		if (!AnyObjectId.isEqual(expectId, actual)) {
			fail("Expected object id <%s>, got <%s>".formatted(expectId.name(),
					actual.name()));
		}
	}

}
