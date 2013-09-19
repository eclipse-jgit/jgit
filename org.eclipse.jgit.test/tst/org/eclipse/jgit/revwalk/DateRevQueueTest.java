/*
 * Copyright (C) 2009-2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DateRevQueueTest extends RevQueueTestCase<DateRevQueue> {
	protected DateRevQueue create() {
		return new DateRevQueue();
	}

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
		final RevCommit c = parseBody(commit(0, b));
		{
			q = create();
			q.add(a);
			q.add(b);
			q.add(c);

			assertCommit(a, q.next());
			assertCommit(b, q.next());
			assertCommit(c, q.next());
			assertNull(q.next());
		}
		{
			q = create();
			q.add(c);
			q.add(b);
			q.add(a);

			assertCommit(c, q.next());
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
