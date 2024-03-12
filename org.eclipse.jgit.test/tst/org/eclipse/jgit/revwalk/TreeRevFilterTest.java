/*
 * Copyright (C) 2014, Google Inc. and others
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

import org.eclipse.jgit.revwalk.filter.OrRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.revwalk.filter.SkipRevFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class TreeRevFilterTest extends RevWalkTestCase {
	private RevFilter treeRevFilter(boolean rewriteParents) {
		rw.setRewriteParents(rewriteParents);
		return new TreeRevFilter(rw, TreeFilter.ANY_DIFF);
	}

	@Test
	public void testStringOfPearls_FilePath1()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);

		// Revwalk No. 1, Parents are not rewritten because no TreeFilter is set
		rw.setRevFilter(treeRevFilter(true));
		markStart(c);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		assertCommit(a, rw.next()); // b was skipped
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());

		// Revwalk No. 2, Parents are not rewritten since rewriteParents is false
		rw.reset();
		rw.setRevFilter(treeRevFilter(false));
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(c);

		// NOTE: Since there was no TreeFilter set, the parents don't actually get rewritten
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		assertCommit(a, rw.next()); // b was skipped
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());

		// Revwalk No. 3, Parents are rewritten since a TreeFilter different from TreeFilter.ALL is set
		// note that this RevWalk makes changes to the RevCommits a,b,c
		rw.reset();
		rw.setRevFilter(treeRevFilter(true));
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(c);

		// NOTE: Since there was no TreeFilter set, the parents don't actually get rewritten
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		// check if parent was rewritten
		assertCommit(a, c.getParent(0));

		assertCommit(a, rw.next()); // b was skipped
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter(true));
		markStart(d);

		// d was skipped
		// NOTE: Since there was no TreeFilter set, the parents don't actually get rewritten
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2_RewriteParents() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter(true));
		// parents only get rewritten if a TreeFilter != TreeFilter.ALL is set as TreeFilter
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2_RewriteParents_False() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter(false));
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_DirPath2() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter(true));
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath3() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit i = commit(tree(file("d/f", blob("c"))), h);

		// Revwalk No 1, doesn't rewrite parents since no TreeFilter is set
		rw.setRevFilter(treeRevFilter(true));
		markStart(i);

		assertCommit(i, rw.next());
		assertEquals(1, i.getParentCount());
		assertCommit(h, i.getParent(0));

		// h..d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());

		// RevWalk No 2, rewrite parents
		// note that this revwalk changes RevCommits a,...
		rw.reset();
		rw.setRevFilter(treeRevFilter(true));
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(i);

		assertCommit(i, rw.next());
		assertEquals(1, i.getParentCount());
		assertCommit(c, i.getParent(0));

		// h..d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testPathFilterOrOtherFilter() throws Exception {
		RevFilter pathFilter = treeRevFilter(true);
		RevFilter skipFilter = SkipRevFilter.create(1);
		RevFilter orFilter = OrRevFilter.create(skipFilter, pathFilter);

		RevCommit a = parseBody(commit(5, tree(file("d/f", blob("a")))));
		RevCommit b = parseBody(commit(5, tree(file("d/f", blob("a"))), a));
		RevCommit c = parseBody(commit(5, tree(file("d/f", blob("b"))), b));

		// Path filter matches c, a.
		rw.setRevFilter(pathFilter);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(a, rw.next());

		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0));

		// Skip filter matches b, a.
		rw.reset();
		rw.setRevFilter(skipFilter);
		markStart(c);
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());

		// (Path OR Skip) matches c, b, a.
		rw.reset();
		rw.setRevFilter(orFilter);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
	}
}
