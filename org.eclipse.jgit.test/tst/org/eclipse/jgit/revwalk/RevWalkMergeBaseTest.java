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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class RevWalkMergeBaseTest extends RevWalkTestCase {
	@Test
	public void testNone() throws Exception {
		final RevCommit c1 = commit(commit(commit()));
		final RevCommit c2 = commit(commit(commit()));

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(c1);
		markStart(c2);
		assertNull(rw.next());
	}

	@Test
	public void testDisallowTreeFilter() throws Exception {
		final RevCommit c1 = commit();
		final RevCommit c2 = commit();

		rw.setRevFilter(RevFilter.MERGE_BASE);
		rw.setTreeFilter(TreeFilter.ANY_DIFF);
		markStart(c1);
		markStart(c2);
		try {
			assertNull(rw.next());
			fail("did not throw IllegalStateException");
		} catch (IllegalStateException ise) {
			// expected result
		}
	}

	@Test
	public void testSimple() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(commit(commit(commit(commit(b)))));
		final RevCommit c2 = commit(commit(commit(commit(commit(b)))));

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(c1);
		markStart(c2);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testMultipleHeads_SameBase1() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(commit(commit(commit(commit(b)))));
		final RevCommit c2 = commit(commit(commit(commit(commit(b)))));
		final RevCommit c3 = commit(commit(commit(b)));

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(c1);
		markStart(c2);
		markStart(c3);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testMultipleHeads_SameBase2() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d1 = commit(commit(commit(commit(commit(b)))));
		final RevCommit d2 = commit(commit(commit(commit(commit(c)))));
		final RevCommit d3 = commit(commit(commit(c)));

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(d1);
		markStart(d2);
		markStart(d3);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testCrissCross() throws Exception {
		// See http://marc.info/?l=git&m=111463358500362&w=2 for a nice
		// description of what this test is creating. We don't have a
		// clean merge base for d,e as they each merged the parents b,c
		// in different orders.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(b, c);
		final RevCommit e = commit(c, b);

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(d);
		markStart(e);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testInconsistentCommitTimes() throws Exception {
		// When commit times are inconsistent (a parent is younger than a child)
		// make sure that not both, parent and child, are reported as merge
		// base. In the following repo the merge base between C,D should be B.
		// But when A is younger than B the MergeBaseGenerator used to generate
		// A before it detected that B is also a merge base.
		//
		//   +---C
		//  /   /
		// A---B---D

		final RevCommit a = commit(2);
		final RevCommit b = commit(-1, a);
		final RevCommit c = commit(2, b, a);
		final RevCommit d = commit(1, b);

		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(d);
		markStart(c);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

}
