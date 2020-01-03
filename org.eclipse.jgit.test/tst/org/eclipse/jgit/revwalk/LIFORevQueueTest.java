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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;

public class LIFORevQueueTest extends RevQueueTestCase<LIFORevQueue> {
	@Override
	protected LIFORevQueue create() {
		return new LIFORevQueue();
	}

	@Override
	@Test
	public void testEmpty() throws Exception {
		super.testEmpty();
		assertEquals(0, q.outputType());
	}

	@Test
	public void testCloneEmpty() throws Exception {
		q = new LIFORevQueue(AbstractRevQueue.EMPTY_QUEUE);
		assertNull(q.next());
	}

	@Test
	public void testAddLargeBlocks() throws Exception {
		final ArrayList<RevCommit> lst = new ArrayList<>();
		for (int i = 0; i < 3 * BlockRevQueue.Block.BLOCK_SIZE; i++) {
			final RevCommit c = commit();
			lst.add(c);
			q.add(c);
		}
		Collections.reverse(lst);
		for (int i = 0; i < lst.size(); i++)
			assertSame(lst.get(i), q.next());
	}
}
