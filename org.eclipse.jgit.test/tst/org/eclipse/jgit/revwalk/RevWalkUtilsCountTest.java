/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RevWalkUtilsCountTest extends RevWalkTestCase {

	@Test
	public void shouldWorkForNormalCase() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);

		assertEquals(1, count(b, a));
	}

	@Test
	public void shouldReturnZeroOnSameCommit() throws Exception {
		final RevCommit c1 = commit(commit(commit()));
		assertEquals(0, count(c1, c1));
	}

	@Test
	public void shouldReturnZeroWhenMergedInto() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);

		assertEquals(0, count(a, b));
	}

	@Test
	public void shouldWorkWithMerges() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c = commit(b1, b2);

		assertEquals(3, count(c, a));
	}

	@Test
	public void shouldWorkWithoutCommonAncestor() throws Exception {
		final RevCommit a1 = commit();
		final RevCommit a2 = commit();
		final RevCommit b = commit(a1);

		assertEquals(2, count(b, a2));
	}

	@Test
	public void shouldWorkWithZeroAsEnd() throws Exception {
		final RevCommit c = commit(commit());

		assertEquals(2, count(c, null));
	}

	private int count(RevCommit start, RevCommit end) throws Exception {
		return RevWalkUtils.count(rw, start, end);
	}
}
