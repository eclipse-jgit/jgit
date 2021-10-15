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

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Ref;
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
		String b = "refs/heads/b";
		String c = "refs/heads/c";
		String d = "refs/heads/d";
		String e = "refs/heads/e";
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		createBranch(commit(o1), b);
		createBranch(commit(o1, o2), c);
		createBranch(commit(o2), d);
		createBranch(commit(commit(i)), e);

		List<String>  modifiedResult = rw.getMergedInto(a, getRefs())
				.stream().map(Ref::getName).collect(Collectors.toList());

		assertTrue(modifiedResult.size() == 3);
		assertTrue(modifiedResult.contains(b));
		assertTrue(modifiedResult.contains(c));
		assertTrue(modifiedResult.contains(d));
	}

	@Test
	public void testIsMergedIntoAny() throws Exception {
		/*
		 *          i
		 *         / \
		 *        A   o
		 *       /     \
		 *      o       C
		 *     /
		 *    B
		 */
		String b = "refs/heads/b";
		String c = "refs/heads/c";
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		createBranch(commit(commit(a)), b);
		createBranch(commit(commit(i)), c);

		assertTrue(rw.isMergedIntoAny(a, getRefs()));
	}

	@Test
	public void testIsMergedIntoAll() throws Exception {
		/*
		 *
		 *        A
		 *       / \
		 *      o1  o2
		 *     / \ / \
		 *    B   C   D
		 */

		String b = "refs/heads/b";
		String c = "refs/heads/c";
		String d = "refs/heads/c";
		final RevCommit a = commit();
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		createBranch(commit(o1), b);
		createBranch(commit(o1, o2), c);
		createBranch(commit(o2), d);

		assertTrue(rw.isMergedIntoAll(a, getRefs()));
	}

	@Test
	public void testMergeIntoAnnotatedTag() throws Exception {
		/*
		 *        a
		 *        |
		 *        b
		 *       / \
		 *      c  v1 (annotated tag)
		 */
		String c = "refs/heads/c";
		String v1 = "refs/tags/v1";
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		createBranch(commit(b), c);
		createBranch(tag("v1", b), v1);

		assertTrue(rw.isMergedIntoAll(a, getRefs()));
	}
}
