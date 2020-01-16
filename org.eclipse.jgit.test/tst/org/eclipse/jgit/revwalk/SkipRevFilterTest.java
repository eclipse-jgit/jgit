/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNull;

import org.eclipse.jgit.revwalk.filter.SkipRevFilter;
import org.junit.Test;

public class SkipRevFilterTest extends RevWalkTestCase {
	@Test
	public void testSkipRevFilter() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c = commit(b1, b2);
		final RevCommit d = commit(c);

		rw.reset();
		rw.setRevFilter(SkipRevFilter.create(3));
		markStart(d);
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSkipRevFilter0() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);

		rw.reset();
		rw.setRevFilter(SkipRevFilter.create(0));
		markStart(b);
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSkipRevFilterNegative() throws Exception {
		SkipRevFilter.create(-1);
	}
}
