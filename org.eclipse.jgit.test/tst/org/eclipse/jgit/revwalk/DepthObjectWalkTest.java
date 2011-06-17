/*
 * Copyright (C) 2011, Google Inc.
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

import java.util.HashSet;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileTreeEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Tree;
import org.junit.Test;

public class DepthObjectWalkTest extends RevWalkTestCase {
	protected DepthObjectWalk dow;
	protected HashSet<RevCommit> shallows;

	@Override
	protected RevWalk createRevWalk() {
		shallows = new HashSet<RevCommit>();
		dow = new DepthObjectWalk(db, 1, shallows);
		dow.sort(RevSort.BOUNDARY, true);
		return dow;
	}

	@Test
	public void testThreeCommits() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		shallows.add(b);
		markStart(c);
		markUninteresting(b);

		assertCommit(c, dow.next());
		assertCommit(b, dow.next());
		assertTrue(b.has(RevFlag.UNINTERESTING));
		assertNull(dow.next());
		assertTrue(b.has(dow.BOUNDARY));
	}

	@Test
	public void testNoShallows() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		markStart(e);
		markUninteresting(c);
		dow.setDepth(3);

		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertCommit(c, dow.next());
		assertNull(dow.next());
		assertTrue(a.has(RevFlag.UNINTERESTING));
		assertTrue(b.has(RevFlag.UNINTERESTING));
		assertTrue(c.has(RevFlag.UNINTERESTING));
		assertTrue(c.has(dow.BOUNDARY));
	}

	@Test
	public void testOverdeepHaves() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		markStart(e);
		markUninteresting(a);
		dow.setDepth(2);

		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertCommit(c, dow.next());
		assertNull(dow.next());
		assertTrue(b.has(RevFlag.UNINTERESTING));
		assertTrue(a.has(RevFlag.UNINTERESTING));
		assertTrue(!c.has(dow.BOUNDARY));
	}

	@Test
	public void testOverdeepShallows() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		shallows.add(b);
		markStart(e);
		markUninteresting(d);
		dow.setDepth(2);

		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertNull(dow.next());
		assertTrue(d.has(dow.BOUNDARY));
		assertTrue(b.has(RevFlag.UNINTERESTING));
	}

	@Test
	public void testDeepening() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		shallows.add(c);
		markStart(e);
		markUninteresting(e);
		dow.setDepth(4);

		assertCommit(b, dow.next());
		assertCommit(a, dow.next());
		assertNull(dow.next());
	}

	@Test
	public void testDeepeningAndUpdate() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		final RevCommit f = commit(e);
		shallows.add(c);
		markStart(f);
		markUninteresting(d);
		dow.setDepth(4);

		assertCommit(f, dow.next());
		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertTrue(d.has(dow.BOUNDARY));
		assertTrue(d.has(RevFlag.UNINTERESTING));
		assertCommit(b, dow.next());
		assertNull(dow.next());
		assertTrue(a.has(RevFlag.UNINTERESTING));
	}

	@Test
	public void testBranchyTree() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c1 = commit(b1);
		final RevCommit c2 = commit(b2);
		final RevCommit d = commit(c1, c2);
		final RevCommit e = commit(d);
		shallows.add(b1);
		shallows.add(b2);
		markUninteresting(c1);
		markUninteresting(b2);
		markStart(e);
		dow.setDepth(4);

		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertCommit(c1, dow.next());
		assertTrue(c1.has(dow.BOUNDARY));
		assertTrue(c1.has(RevFlag.UNINTERESTING));
		assertCommit(c2, dow.next());
		assertCommit(b2, dow.next());
		assertTrue(b2.has(dow.BOUNDARY));
		assertTrue(b2.has(RevFlag.UNINTERESTING));
		assertCommit(a, dow.next());
		assertNull(dow.next());
		assertTrue(b1.has(RevFlag.UNINTERESTING));
	}

	@Test
	public void testMultipleWants() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c1 = commit(b1);
		final RevCommit c2 = commit(b2);
		final RevCommit c3 = commit(b2);
		final RevCommit d = commit(c1, c2);
		final RevCommit e1 = commit(c1);
		final RevCommit e2 = commit(d, c3);
		final RevCommit e3 = commit(c3);
		shallows.add(b1);
		markStart(e1);
		markStart(e2);
		markStart(e3);
		markUninteresting(c1);
		markUninteresting(c3);
		dow.setDepth(3);

		assertCommit(e3, dow.next());
		assertCommit(e2, dow.next());
		assertCommit(e1, dow.next());
		assertCommit(c3, dow.next());
		assertTrue(c3.has(dow.BOUNDARY));
		assertTrue(c3.has(RevFlag.UNINTERESTING));
		assertCommit(d, dow.next());
		assertCommit(c1, dow.next());
		assertTrue(c1.has(dow.BOUNDARY));
		assertTrue(c1.has(RevFlag.UNINTERESTING));
		assertCommit(c2, dow.next());
		assertCommit(b2, dow.next());
		assertNull(dow.next());
		assertTrue(a.has(RevFlag.UNINTERESTING));
		assertTrue(b2.has(dow.BOUNDARY));
		assertTrue(b2.has(RevFlag.UNINTERESTING));
	}

	@Test
	public void testBranchingHaves() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c1 = commit(b1);
		final RevCommit c2 = commit(b2);
		final RevCommit c3 = commit(b2);
		final RevCommit d = commit(c1, c2);
		final RevCommit e1 = commit(c1);
		final RevCommit e2 = commit(d, c3);
		final RevCommit e3 = commit(c3);
		shallows.add(b1);
		shallows.add(b2);
		markStart(e1);
		markStart(e3);
		markUninteresting(e2);
		dow.setDepth(3);

		assertCommit(e3, dow.next());
		assertCommit(e1, dow.next());
		assertCommit(c3, dow.next());
		assertTrue(c3.has(dow.BOUNDARY));
		assertTrue(c3.has(RevFlag.UNINTERESTING));
		assertCommit(c1, dow.next());
		assertTrue(c1.has(dow.BOUNDARY));
		assertTrue(c1.has(RevFlag.UNINTERESTING));
		assertCommit(a, dow.next());
		assertNull(dow.next());
	}

	@Test
	public void testIdenticalDepth() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit(c);
		final RevCommit e = commit(d);
		shallows.add(c);
		markStart(e);
		markUninteresting(e);
		dow.setDepth(2);

		assertNull(dow.next());
	}

	@Test
	public void testBranchHole() throws Exception {
		final RevCommit a = commit();
		final RevCommit b1 = commit(a);
		final RevCommit b2 = commit(a);
		final RevCommit c1 = commit(b1);
		final RevCommit c2 = commit(b2);
		final RevCommit d = commit(c1, c2);
		final RevCommit e = commit(d);
		shallows.add(c2);
		markUninteresting(c2);
		markStart(e);
		dow.setDepth(4);

		assertCommit(e, dow.next());
		assertCommit(d, dow.next());
		assertCommit(c1, dow.next());
		assertCommit(c2, dow.next());
		assertTrue(c2.has(dow.BOUNDARY));
		assertTrue(c2.has(RevFlag.UNINTERESTING));
		assertCommit(b1, dow.next());
		assertCommit(b2, dow.next());
		assertTrue(!b2.has(RevFlag.UNINTERESTING));
		assertCommit(a, dow.next());
		assertNull(dow.next());
	}
}
