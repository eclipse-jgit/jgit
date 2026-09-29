/*
 * Copyright (C) 2009-2026, Google Inc. and others
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

import org.junit.Test;

public class RevWalkSortTopoTest
		extends RevWalkWithAndWithoutCommitGraphTestCase {

	public RevWalkSortTopoTest(boolean withCommitGraph) {
		super(withCommitGraph);
	}

	@Test
	public void testSort_TOPO() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit("main", c1, c2);

		initializeRevWalk();
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
	public void testSort_TOPO_reachable() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit("main", c1, c2);

		assertEquals("", d.getShortMessage());
		assertEquals("", c2.getShortMessage());
		assertEquals("", c1.getShortMessage());
		assertEquals("", b.getShortMessage());
		initializeRevWalk();
		rw.sort(RevSort.TOPO);
		markStart(d);
		markUninteresting(b);
		assertEquals("", d.getShortMessage());
		assertEquals("", c2.getShortMessage());
		assertEquals("", c1.getShortMessage());
		assertEquals("", b.getShortMessage());
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(c1, rw.next());
		assertNull(rw.next());
		assertEquals("", d.getShortMessage());
		assertEquals("", c2.getShortMessage());
		assertEquals("", c1.getShortMessage());
		assertEquals("", b.getShortMessage());
	}

	@Test
	public void testSort_TOPO_reachable2() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit("main", c1, c2);

		assertEquals("", d.getShortMessage());
		initializeRevWalk();
		rw.sort(RevSort.TOPO);
		markStart(d);
		markUninteresting(d);
		assertEquals("", d.getShortMessage());
		assertNull(rw.next());
		assertEquals("", d.getShortMessage());
	}

	@Test
	public void testSort_TOPO_REVERSE() throws Exception {
		// c1 is back dated before its parent.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit("main", c1, c2);

		initializeRevWalk();
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

}
