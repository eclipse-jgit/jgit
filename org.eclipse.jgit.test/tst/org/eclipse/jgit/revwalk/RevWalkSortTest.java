/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RevWalkSortTest extends RevWalkTestCase {
	@Test
	public void testSort_Default() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(1, a);
		final RevCommit c = commit(1, b);
		final RevCommit d = commit(1, c);

		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_COMMIT_TIME_DESC() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);

		rw.sort(RevSort.COMMIT_TIME_DESC);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_REVERSE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);

		rw.sort(RevSort.REVERSE);
		markStart(d);
		assertCommit(a, rw.next());
		assertCommit(b, rw.next());
		assertCommit(c, rw.next());
		assertCommit(d, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_COMMIT_TIME_DESC_OutOfOrder1() throws Exception {
		// Despite being out of order time-wise, a strand-of-pearls must
		// still maintain topological order.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(-5, b);
		final RevCommit d = commit(10, c);
		assertTrue(parseBody(a).getCommitTime() < parseBody(d).getCommitTime());
		assertTrue(parseBody(c).getCommitTime() < parseBody(b).getCommitTime());

		rw.sort(RevSort.COMMIT_TIME_DESC);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_COMMIT_TIME_DESC_OutOfOrder2() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit(c1, c2);

		rw.sort(RevSort.COMMIT_TIME_DESC);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertCommit(c1, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit(c1, c2);

		rw.sort(RevSort.TOPO);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO_REVERSE() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit(c1, c2);

		rw.sort(RevSort.TOPO);
		rw.sort(RevSort.REVERSE, true);
		markStart(d);
		assertCommit(a, rw.next());
		assertCommit(b, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(d, rw.next());
		assertNull(rw.next());
	}

        @Test
	public void testSort_TOPO_OutOfOrderCommitTimes() throws Exception {
		// b is committed before c2 in a different line of history.
		//
		final RevCommit a = commit();
		final RevCommit c1 = commit(a);
		final RevCommit b = commit(a);
		final RevCommit c2 = commit(c1);
		final RevCommit d = commit(b, c2);

		rw.sort(RevSort.TOPO);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO_MultipleLinesOfHistory() throws Exception {
		final RevCommit a1 = commit();
		final RevCommit b1 = commit(a1);
		final RevCommit a2 = commit(a1, b1);
		final RevCommit b2 = commit(b1);
		final RevCommit b3 = commit(b1);
		final RevCommit a3 = commit(a2, b2);
		final RevCommit a4 = commit(a3, b3);

		rw.sort(RevSort.TOPO);
		markStart(a4);
		assertCommit(a4, rw.next());
		assertCommit(b3, rw.next());
		assertCommit(a3, rw.next());
		assertCommit(b2, rw.next());
		assertCommit(a2, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a1, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO_REVERSE_MultipleLinesOfHistory()
			throws Exception {
		final RevCommit a1 = commit();
		final RevCommit b1 = commit(a1);
		final RevCommit a2 = commit(a1, b1);
		final RevCommit b2 = commit(b1);
		final RevCommit b3 = commit(b1);
		final RevCommit a3 = commit(a2, b2);
		final RevCommit a4 = commit(a3, b3);

		rw.sort(RevSort.TOPO);
		rw.sort(RevSort.REVERSE, true);
		markStart(a4);
		assertCommit(a1, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a2, rw.next());
		assertCommit(b2, rw.next());
		assertCommit(a3, rw.next());
		assertCommit(b3, rw.next());
		assertCommit(a4, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO_ParentOfMultipleStartChildren() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d1 = commit(a);
		final RevCommit d2 = commit(d1);
		final RevCommit e = commit(a);

		rw.sort(RevSort.TOPO);
		markStart(b);
		markStart(c);
		markStart(d2);
		markStart(e);
		assertCommit(e, rw.next());
		assertCommit(d2, rw.next());
		assertCommit(d1, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSort_TOPO_Uninteresting() throws Exception {
		final RevCommit a1 = commit();
		final RevCommit a2 = commit(a1);
		final RevCommit a3 = commit(a2);
		final RevCommit b = commit(a1);
		final RevCommit a4 = commit(a3, b);

		rw.sort(RevSort.TOPO);
		markStart(a4);
		markUninteresting(a2);
		assertCommit(a4, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a3, rw.next());
		assertNull(rw.next());
	}
}
