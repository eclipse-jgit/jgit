/*
 * Copyright (C) 2023, Google Inc. and others
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

import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class TreeRevFilterWithRewriteParentsTest extends RevWalkTestCase {
	private RevFilter treeRevFilter() {
		rw.setRewriteParents(true);
		return new TreeRevFilter(rw, TreeFilter.ANY_DIFF);
	}

	@Test
	public void testStringOfPearls_FilePath1()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		rw.setRevFilter(treeRevFilter());
		markStart(c);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
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
		rw.setRevFilter(treeRevFilter());
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
	public void testStringOfPearls_DirPath2() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter());
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
		rw.setRevFilter(treeRevFilter());
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
}
