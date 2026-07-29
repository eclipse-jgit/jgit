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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.ThrowingConsumer;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
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
