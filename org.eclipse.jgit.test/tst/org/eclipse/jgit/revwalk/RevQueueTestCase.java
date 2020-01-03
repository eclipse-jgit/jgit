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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public abstract class RevQueueTestCase<T extends AbstractRevQueue> extends
		RevWalkTestCase {
	protected T q;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		q = create();
	}

	protected abstract T create();

	@Test
	public void testEmpty() throws Exception {
		assertNull(q.next());
		assertTrue(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertFalse(q.anybodyHasFlag(RevWalk.UNINTERESTING));
	}

	@Test
	public void testClear() throws Exception {
		final RevCommit a = parseBody(commit());
		final RevCommit b = parseBody(commit(a));

		q.add(a);
		q.add(b);
		q.clear();
		assertNull(q.next());
	}

	@Test
	public void testHasFlags() throws Exception {
		final RevCommit a = parseBody(commit());
		final RevCommit b = parseBody(commit(a));

		q.add(a);
		q.add(b);

		assertFalse(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertFalse(q.anybodyHasFlag(RevWalk.UNINTERESTING));

		a.flags |= RevWalk.UNINTERESTING;
		assertFalse(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertTrue(q.anybodyHasFlag(RevWalk.UNINTERESTING));

		b.flags |= RevWalk.UNINTERESTING;
		assertTrue(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertTrue(q.anybodyHasFlag(RevWalk.UNINTERESTING));
	}
}
