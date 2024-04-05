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
	private RevFilter treeRevFilter() {
		return new TreeRevFilter(rw, TreeFilter.ANY_DIFF);
	}

	@Test
	public void testStringOfPearls_FilePath1()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		rw.setRevFilter(treeRevFilter());
		rw.setRewriteParents(false);
		markStart(c);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

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
		rw.setRevFilter(treeRevFilter());
		rw.setRewriteParents(false);
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
		rw.setRevFilter(treeRevFilter());
		rw.setRewriteParents(false);
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
	public void testTreeRevFilter_WithoutRewrite_parentsUnchanged()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit i = commit(tree(file("d/f", blob("c"))), h);
		rw.setRevFilter(treeRevFilter());
		rw.setRewriteParents(false);
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
	}

	@Test
	public void testTreeRevFilter_WithRewrite_parentsChanged()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit i = commit(tree(file("d/f", blob("c"))), h);

		rw.setRevFilter(treeRevFilter());
		rw.setRewriteParents(true);
		markStart(i);

		assertCommit(i, rw.next());
		assertEquals(1, i.getParentCount());
		// parent rewritten from h -> c
		assertCommit(c, i.getParent(0));

		// h..d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		// parent rewritten from b -> a
		assertCommit(a, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testNonTreeRevFilter_NoRewrite_parentsUnchanged()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit a_2 = commit(tree(file("d/f", blob("b"))));
		RevCommit b = commit(tree(file("d/f", blob("ab"))), a, a_2);

		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);

		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit h_2 = commit(tree(file("d/f", blob("c"))), g);
		RevCommit i = commit(tree(file("d/f", blob("bc"))), h, h_2);

		rw.setRevFilter(RevFilter.ONLY_MERGES);
		rw.setRewriteParents(false);
		markStart(i);

		RevCommit firstOutput = rw.next();
		assertCommit(i, firstOutput);
		assertEquals(2, firstOutput.getParentCount());
		assertCommit(h, firstOutput.getParent(0));
		assertCommit(h_2, firstOutput.getParent(1));

		// h..c was skipped
		RevCommit secondOutput = rw.next();
		assertCommit(b, secondOutput);
		assertEquals(2, secondOutput.getParentCount());
		assertCommit(a, secondOutput.getParent(0));
		assertCommit(a_2, secondOutput.getParent(1));

		// a and a_2 were skipped
		assertNull(rw.next());
	}

	@Test
	public void testNonTreeRevFilter_WithRewrite_parentsChanged()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit a_2 = commit(tree(file("d/f", blob("b"))));
		RevCommit b = commit(tree(file("d/f", blob("ab"))), a, a_2);

		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);

		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit h_2 = commit(tree(file("d/f", blob("c"))), g);
		RevCommit i = commit(tree(file("d/f", blob("bc"))), h, h_2);

		rw.setRevFilter(RevFilter.ONLY_MERGES);
		rw.setRewriteParents(true);
		markStart(i);

		RevCommit firstOutput = rw.next();
		assertCommit(i, firstOutput);
		assertEquals(1, firstOutput.getParentCount());
		assertCommit(b, firstOutput.getParent(0));

		RevCommit secondOutput = rw.next();
		assertCommit(b, secondOutput);
		assertEquals(0, secondOutput.getParentCount());

		// a and a_2 were skipped
		assertNull(rw.next());
	}

	@Test
	public void testPathFilterOrOtherFilter() throws Exception {
		RevFilter pathFilter = treeRevFilter();
		RevFilter skipFilter = SkipRevFilter.create(1);
		RevFilter orFilter = OrRevFilter.create(skipFilter, pathFilter);

		RevCommit a = parseBody(commit(5, tree(file("d/f", blob("a")))));
		RevCommit b = parseBody(commit(5, tree(file("d/f", blob("a"))), a));
		RevCommit c = parseBody(commit(5, tree(file("d/f", blob("b"))), b));

		// Path filter matches c, a.
		rw.setRevFilter(pathFilter);
		rw.setRewriteParents(false);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(a, rw.next());

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
