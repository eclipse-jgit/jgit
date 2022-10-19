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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

public class AlwaysEmptyRevQueueTest extends RevWalkTestCase {
	private final AbstractRevQueue q = AbstractRevQueue.EMPTY_QUEUE;

	@Test
	void testEmpty() throws Exception {
		assertNull(q.next());
		assertTrue(q.everbodyHasFlag(RevWalk.UNINTERESTING));
		assertFalse(q.anybodyHasFlag(RevWalk.UNINTERESTING));
		assertEquals(0, q.outputType());
	}

	@Test
	void testClear() throws Exception {
		q.clear();
		testEmpty();
	}

	@Test
	void testAddFails() throws Exception {
		try {
			q.add(commit());
			fail("Did not throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected result
		}
	}
}
