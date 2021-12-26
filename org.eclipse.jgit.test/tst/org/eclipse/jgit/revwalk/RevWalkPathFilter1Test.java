/*
 * Copyright (C) 2009, Google Inc. and others
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

import java.util.Collections;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class RevWalkPathFilter1Test extends RevWalkTestCase {
	protected void filter(String path) {
		rw.setTreeFilter(AndTreeFilter.create(PathFilterGroup
				.createFromStrings(Collections.singleton(path)),
				TreeFilter.ANY_DIFF));
	}

	@Test
	public void testEmpty_EmptyTree() throws Exception {
		final RevCommit a = commit();
		filter("a");
		markStart(a);
		assertNull(rw.next());
	}

	@Test
	public void testEmpty_NoMatch() throws Exception {
		final RevCommit a = commit(tree(file("0", blob("0"))));
		filter("a");
		markStart(a);
		assertNull(rw.next());
	}

	@Test
	public void testSimple1() throws Exception {
		final RevCommit a = commit(tree(file("0", blob("0"))));
		filter("0");
		markStart(a);
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testEdits_MatchNone() throws Exception {
		final RevCommit a = commit(tree(file("0", blob("a"))));
		final RevCommit b = commit(tree(file("0", blob("b"))), a);
		final RevCommit c = commit(tree(file("0", blob("c"))), b);
		final RevCommit d = commit(tree(file("0", blob("d"))), c);
		filter("a");
		markStart(d);
		assertNull(rw.next());
	}

	@Test
	public void testEdits_MatchAll() throws Exception {
		final RevCommit a = commit(tree(file("0", blob("a"))));
		final RevCommit b = commit(tree(file("0", blob("b"))), a);
		final RevCommit c = commit(tree(file("0", blob("c"))), b);
		final RevCommit d = commit(tree(file("0", blob("d"))), c);
		filter("0");
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath1() throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		filter("d/f");
		markStart(c);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0)); // b was skipped

		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath1_NoParentRewriting()
			throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		filter("d/f");
		markStart(c);
		rw.setRewriteParents(false);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		assertCommit(a, rw.next()); // b was skipped
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2() throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		filter("d/f");
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0)); // b was skipped

		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2_NoParentRewriting()
	throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		filter("d/f");
		markStart(d);
		rw.setRewriteParents(false);

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
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		filter("d");
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0)); // b was skipped

		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_DirPath2_NoParentRewriting()
			throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		filter("d");
		markStart(d);
		rw.setRewriteParents(false);

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
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		final RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		final RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		final RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		final RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		final RevCommit i = commit(tree(file("d/f", blob("c"))), h);
		filter("d/f");
		markStart(i);

		assertCommit(i, rw.next());
		assertEquals(1, i.getParentCount());
		assertCommit(c, i.getParent(0)); // h..d was skipped

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(a, c.getParent(0)); // b was skipped

		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath3_NoParentRewriting()
			throws Exception {
		final RevCommit a = commit(tree(file("d/f", blob("a"))));
		final RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		final RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		final RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		final RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		final RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		final RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		final RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		final RevCommit i = commit(tree(file("d/f", blob("c"))), h);
		filter("d/f");
		markStart(i);
		rw.setRewriteParents(false);

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
	public void testStopWhenPathDisappears() throws Exception {
		DirCacheEntry file1 = file("src/d1/file1", blob("a"));
		DirCacheEntry file2 = file("src/d1/file2", blob("a"));
		DirCacheEntry file3 = file("src/d1/file3", blob("a"));
		RevCommit a = commit(tree(file1));
		RevCommit b = commit(tree(file1, file2), a);
		RevCommit c = commit(tree(file1, file3), a);
		RevCommit d = commit(tree(file1, file2, file3), b, c);
		filter("src/d1");
		markStart(d);
		rw.setRewriteParents(false);

		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
	}
}
