/*
 * Copyright (C) 2015, Google Inc.
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

import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.FirstParentRevFilter;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.OrRevFilter;
import org.junit.Test;

public class FirstParentRevFilterTest extends RevWalkTestCase {
	@Test
	public void testStringOfPearls() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c = commit(b);

		rw.reset();
		rw.setRevFilter(FirstParentRevFilter.create(rw));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSideBranch() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setRevFilter(FirstParentRevFilter.create(rw));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testReachableAlongFirstAndLaterParents()
			throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit b3 = commit(a);
		RevCommit c = commit(b1, b2);
		RevCommit d = commit(b2, b3);

		rw.reset();
		rw.setRevFilter(FirstParentRevFilter.create(rw));
		markStart(c);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		// b3 is only reachable from c's second parent.
		// b2 is reachable from c's second parent but d's first parent.
		assertCommit(b2, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testStartCommitReachableOnlyFromLaterParents()
		throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setRevFilter(FirstParentRevFilter.create(rw));
		markStart(c);
		markStart(b2);
		assertCommit(c, rw.next());
		// b2 is only reachable from second parent, but is itself a start commit.
		assertCommit(b2, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testAndFilter() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commitBuilder().parent(a).message("commit b1").create();
		RevCommit b2 = commitBuilder().parent(a).message("commit b2").create();
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setRevFilter(AndRevFilter.create(
				FirstParentRevFilter.create(rw),
				MessageRevFilter.create("commit b")));
		rw.markStart(c);
		assertCommit(b1, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testOrFilter() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commitBuilder().parent(a).message("commit b2").create();
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setRevFilter(OrRevFilter.create(
				MessageRevFilter.create("b2"),
				FirstParentRevFilter.create(rw)));
		rw.markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b2, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}
}
