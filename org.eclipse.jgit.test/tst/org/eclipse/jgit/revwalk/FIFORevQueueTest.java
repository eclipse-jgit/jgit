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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

public class FIFORevQueueTest extends RevQueueTestCase<FIFORevQueue> {
	@Override
	protected FIFORevQueue create() {
		return new FIFORevQueue();
	}

	@Override
	@Test
	public void testEmpty() throws Exception {
		super.testEmpty();
		assertEquals(0, q.outputType());
	}

	@Test
	void testCloneEmpty() throws Exception {
		q = new FIFORevQueue(AbstractRevQueue.EMPTY_QUEUE);
		assertNull(q.next());
	}

	@Test
	void testAddLargeBlocks() throws Exception {
		final ArrayList<RevCommit> lst = new ArrayList<>();
		for (int i = 0; i < 3 * BlockRevQueue.Block.BLOCK_SIZE; i++) {
			final RevCommit c = commit();
			lst.add(c);
			q.add(c);
		}
		for (int i = 0; i < lst.size(); i++)
			assertSame(lst.get(i), q.next());
	}

	@Test
	void testUnpopAtFront() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit();
		final RevCommit c = commit();

		q.add(a);
		q.unpop(b);
		q.unpop(c);

		assertSame(c, q.next());
		assertSame(b, q.next());
		assertSame(a, q.next());
	}
}
