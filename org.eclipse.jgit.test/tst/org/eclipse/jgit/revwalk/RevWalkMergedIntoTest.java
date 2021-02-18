/*
 * Copyright (C) 2014, Sven Selberg <sven.selberg@sonymobile.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RevWalkMergedIntoTest extends RevWalkTestCase {

	@Test
	public void testOldCommitWalk() throws Exception {
		/*
		 * Sometimes a merge is performed on a machine with faulty time.
		 * This makes the traversal of the graph, when trying to find out if B
		 * is merged into T, complex since the algorithm uses the time stamps
		 * of commits to find the best route.
		 * When for example O(ld) has a very old time stamp compared to one of the
		 * commits (N(ew)) on the upper route between T and F(alse base), the route
		 * to False is deemed the better option even though the alternate route leeds
		 * to B(ase) which was the commit we were after.
		 *
		 *             o---o---o---o---N
		 *            /                 \
		 *           /   o---o---o---O---T
		 *          /   /
		 *      ---F---B
		 *
		 * This test is asserting that isMergedInto(B, T) returns true even
		 * under those circumstances.
		 */
		final int threeDaysInSecs = 3 * 24 * 60 * 60;
		final RevCommit f = commit();
		final RevCommit b = commit(f);
		final RevCommit o = commit(-threeDaysInSecs, commit(commit(commit(b))));
		final RevCommit n = commit(commit(commit(commit(commit(f)))));
		final RevCommit t = commit(n, o);
		assertTrue(rw.isMergedInto(b, t));
	}

	@Test
	public void testGetMergedInto() throws Exception {
		/*
		 *          i
		 *         / \
		 *        A   o
		 *       / \   \
		 *      o1  o2  E
		 *     / \ / \
		 *    B   C   D
		 */
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		final RevCommit e = commit(commit(i));
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		final RevCommit b = commit(o1);
		final RevCommit c = commit(o1, o2);
		final RevCommit d = commit(o2);

		List<RevCommit> haystacks = new ArrayList<>();
		haystacks.add(b);
		haystacks.add(c);
		haystacks.add(d);
		haystacks.add(e);
		List<RevCommit> result = rw.getMergedInto(a, haystacks);

		assertTrue(result.size() == 3);
		assertTrue(result.contains(b));
		assertTrue(result.contains(c));
		assertTrue(result.contains(d));
	}

	@Test
	public void testisMergedIntoAny() throws Exception {
		/*
		 *          i
		 *         / \
		 *        A   o
		 *       /     \
		 *      o       C
		 *     /
		 *    B
		 */
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		final RevCommit c = commit(commit(i));
		final RevCommit b = commit(commit(a));

		List<RevCommit> haystacks = new ArrayList<>();
		haystacks.add(b);
		haystacks.add(c);

		assertTrue( rw.isMergedIntoAny(a, haystacks));
	}

	@Test
	public void testisMergedIntoAll() throws Exception {
		/*
		 *
		 *        A
		 *       / \
		 *      o1  o2
		 *     / \ / \
		 *    B   C   D
		 */
		final RevCommit a = commit();
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		final RevCommit b = commit(o1);
		final RevCommit c = commit(o1, o2);
		final RevCommit d = commit(o2);

		List<RevCommit> haystacks = new ArrayList<>();
		haystacks.add(b);
		haystacks.add(c);
		haystacks.add(d);

		assertTrue(rw.isMergedIntoAll(a, haystacks));
	}
}
