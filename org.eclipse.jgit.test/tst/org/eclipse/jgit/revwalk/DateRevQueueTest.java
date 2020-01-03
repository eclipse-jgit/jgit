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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DateRevQueueTest extends RevQueueTestCase<DateRevQueue> {
	@Override
	protected DateRevQueue create() {
		return new DateRevQueue();
	}

	@Override
	@Test
	public void testEmpty() throws Exception {
		super.testEmpty();
		assertNull(q.peek());
		assertEquals(Generator.SORT_COMMIT_TIME_DESC, q.outputType());
	}

	@Test
	public void testCloneEmpty() throws Exception {
		q = new DateRevQueue(AbstractRevQueue.EMPTY_QUEUE);
		assertNull(q.next());
	}

	@Test
	public void testInsertOutOfOrder() throws Exception {
		final RevCommit a = parseBody(commit());
		final RevCommit b = parseBody(commit(10, a));
		final RevCommit c1 = parseBody(commit(5, b));
		final RevCommit c2 = parseBody(commit(-50, b));

		q.add(c2);
		q.add(a);
		q.add(b);
		q.add(c1);

		assertCommit(c1, q.next());
		assertCommit(b, q.next());
		assertCommit(a, q.next());
		assertCommit(c2, q.next());
		assertNull(q.next());
	}

	@Test
	public void testInsertTie() throws Exception {
		final RevCommit a = parseBody(commit());
		final RevCommit b = parseBody(commit(0, a));
		{
			q = create();
			q.add(a);
			q.add(b);

			assertCommit(a, q.next());
			assertCommit(b, q.next());
			assertNull(q.next());
		}
		{
			q = create();
			q.add(b);
			q.add(a);

			assertCommit(b, q.next());
			assertCommit(a, q.next());
			assertNull(q.next());
		}
	}

	@Test
	public void testCloneFIFO() throws Exception {
		final RevCommit a = parseBody(commit());
		final RevCommit b = parseBody(commit(200, a));
		final RevCommit c = parseBody(commit(200, b));

		final FIFORevQueue src = new FIFORevQueue();
		src.add(a);
		src.add(b);
		src.add(c);

		q = new DateRevQueue(src);
		assertFalse(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertFalse(q.anybodyHasFlag(RevWalk.UNINTERESTING));
		assertCommit(c, q.peek());
		assertCommit(c, q.peek());

		assertCommit(c, q.next());
		assertCommit(b, q.next());
		assertCommit(a, q.next());
		assertNull(q.next());
	}
}
