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

import org.eclipse.jgit.revwalk.filter.MaxCountRevFilter;
import org.junit.Test;

public class MaxCountRevFilterTest extends RevWalkTestCase {
	@Test
	public void testMaxCountRevFilter() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(b);
		final RevCommit c2 = commit(b);
		final RevCommit d = commit(c1, c2);
		final RevCommit e = commit(d);

		rw.reset();
		rw.setRevFilter(MaxCountRevFilter.create(3));
		markStart(e);
		assertCommit(e, rw.next());
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertNull(rw.next());

		rw.reset();
		rw.setRevFilter(MaxCountRevFilter.create(0));
		markStart(e);
		assertNull(rw.next());
	}

	@Test
	public void testMaxCountRevFilter0() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);

		rw.reset();
		rw.setRevFilter(MaxCountRevFilter.create(0));
		markStart(b);
		assertNull(rw.next());
	}
}
