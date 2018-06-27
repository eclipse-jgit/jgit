/*
 * Copyright (C) 2009, Google Inc.
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

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RevWalkCullTest extends RevWalkTestCase {
	@Test
	public void testProperlyCullAllAncestors1() throws Exception {
		// Credit goes to Junio C Hamano <gitster@pobox.com> for this
		// test case in git-core (t/t6009-rev-list-parent.sh)
		//
		// We induce a clock skew so two is dated before one.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(-2400, a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);

		markStart(a);
		markUninteresting(d);
		assertNull(rw.next());
	}

	@Test
	public void testProperlyCullAllAncestors2() throws Exception {
		// Despite clock skew on c1 being very old it should not
		// produce, neither should a or b, or any part of that chain.
		//
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(-5, b);
		final RevCommit c2 = commit(10, b);
		final RevCommit d = commit(c1, c2);

		markStart(d);
		markUninteresting(c1);
		assertCommit(d, rw.next());
		assertCommit(c2, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testProperlyCullAllAncestors_LongHistory() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		for (int i = 0; i < 24; i++) {
			b = commit(b);
			if ((i & 2) == 0)
				markUninteresting(b);
		}
		final RevCommit c = commit(b);

		// TestRepository eagerly parses newly created objects. The current rw
		// is caching that parsed state. To verify that RevWalk itself is lazy,
		// set up a new one.
		rw.close();
		rw = createRevWalk();
		RevCommit a2 = rw.lookupCommit(a);
		markStart(c);
		markUninteresting(b);
		assertCommit(c, rw.next());
		assertNull(rw.next());

		// We should have aborted before we got back so far that "a"
		// would be parsed. Thus, its parents shouldn't be allocated.
		//
		assertNull(a2.parents);
	}
}
