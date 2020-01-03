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

import org.junit.Test;

public class RevWalkCullTest extends RevWalkTestCase {
	@Test
	public void testProperlyCullAllAncestors1() throws Exception {
		// Credit goes to Junio C Hamano <gitster@pobox.com> for this
		// test case in git-core (t/t6009-rev-list-parent.sh)
		//
		// We induce a clock skew so two is dated before one.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(-2400, a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);

		markStart(a);
		markUninteresting(d);
		assertNull(rw.next());
	}

	@Test
	public void testProperlyCullAllAncestors2() throws Exception {
		// Despite clock skew on c1 being very old it should not
		// produce, neither should a or b, or any part of that chain.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit(c1, c2);

		markStart(d);
		markUninteresting(c1);
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testProperlyCullAllAncestors_LongHistory() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		for (int i = 0; i < 24; i++) {
			b = commit(b);
			if ((i & 2) == 0)
				markUninteresting(b);
		}
		final RevCommit c = commit(b);

		// TestRepository eagerly parses newly created objects. The current rw
		// is caching that parsed state. To verify that RevWalk itself is lazy,
		// set up a new one.
		rw.close();
		rw = createRevWalk();
		RevCommit a2 = rw.lookupCommit(a);
		markStart(c);
		markUninteresting(b);
		assertCommit(c, rw.next());
		assertNull(rw.next());

		// We should have aborted before we got back so far that "a"
		// would be parsed. Thus, its parents shouldn't be allocated.
		//
		assertNull(a2.parents);
	}
}
