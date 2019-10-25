/*
 * Copyright (C) 2019, Google LLC.
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
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.junit.Test;

public class FirstParentRevWalkTest extends RevWalkTestCase {
	@Test
	public void testStringOfPearls() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c = commit(b);

		rw.reset();
		rw.setFirstParent(true);
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
		RevCommit c1 = commit(b1);
		RevCommit c2 = commit(b2);
		RevCommit d = commit(c1, c2);

		rw.reset();
		rw.setFirstParent(true);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testSecondParentAncestorOfFirstParent() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c = commit(b, a);

		rw.reset();
		rw.setFirstParent(true);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFirstParentMultipleOccurrences() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c = commit(b);
		RevCommit d = commit(b);

		rw.reset();
		rw.setFirstParent(true);
		markStart(c);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testReachableAlongFirstAndLaterParents() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit b3 = commit(a);
		RevCommit c = commit(b1, b2);
		RevCommit d = commit(b2, b3);

		rw.reset();
		rw.setFirstParent(true);
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
		rw.setFirstParent(true);
		markStart(c);
		markStart(b2);
		assertCommit(c, rw.next());
		// b2 is only reachable from second parent, but is itself a start
		// commit.
		assertCommit(b2, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testRevFilter() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commitBuilder().parent(a).message("commit b1").create();
		RevCommit b2 = commitBuilder().parent(a).message("commit b2").create();
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setFirstParent(true);
		rw.setRevFilter(MessageRevFilter.create("commit b"));
		rw.markStart(c);
		assertCommit(b1, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testTopoSort() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.sort(RevSort.TOPO);
		rw.setFirstParent(true);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testCommitTimeSort() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.sort(RevSort.COMMIT_TIME_DESC);
		rw.setFirstParent(true);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testReverseSort() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.sort(RevSort.REVERSE);
		rw.setFirstParent(true);
		markStart(c);
		assertCommit(a, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(c, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testBoundarySort() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c1 = commit(b);
		RevCommit c2 = commit(b);
		RevCommit d = commit(c1, c2);

		rw.reset();
		rw.sort(RevSort.BOUNDARY);
		rw.setFirstParent(true);
		markStart(d);
		markUninteresting(a);
		assertCommit(d, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFirstParentOfFirstParentMarkedUninteresting()
			throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c1 = commit(b1);
		RevCommit c2 = commit(b2);
		RevCommit d = commit(c1, c2);

		rw.reset();
		rw.setFirstParent(true);
		markStart(d);
		markUninteresting(b1);
		assertCommit(d, rw.next());
		assertCommit(c1, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testUnparsedFirstParentOfFirstParentMarkedUninteresting()
			throws Exception {
		ObjectId a = unparsedCommit();
		ObjectId b1 = unparsedCommit(a);
		ObjectId b2 = unparsedCommit(a);
		ObjectId c1 = unparsedCommit(b1);
		ObjectId c2 = unparsedCommit(b2);
		ObjectId d = unparsedCommit(c1, c2);

		rw.reset();
		rw.setFirstParent(true);
		RevCommit parsedD = rw.parseCommit(d);
		markStart(parsedD);
		markUninteresting(rw.parseCommit(b1));
		assertCommit(parsedD, rw.next());
		assertCommit(rw.parseCommit(c1), rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFirstParentMarkedUninteresting() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setFirstParent(true);
		markStart(c);
		markUninteresting(b1);
		assertCommit(c, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testUnparsedFirstParentMarkedUninteresting() throws Exception {
		ObjectId a = unparsedCommit();
		ObjectId b1 = unparsedCommit(a);
		ObjectId b2 = unparsedCommit(a);
		ObjectId c = unparsedCommit(b1, b2);

		rw.reset();
		rw.setFirstParent(true);
		RevCommit parsedC = rw.parseCommit(c);
		markStart(parsedC);
		markUninteresting(rw.parseCommit(b1));
		assertCommit(parsedC, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testUninterestingCommitWithTwoParents() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c1 = commit(b);
		RevCommit c2 = commit(b);
		RevCommit d = commit(c1);
		RevCommit e = commit(c1, c2);

		RevCommit uA = commit(a, b);
		RevCommit uB1 = commit(uA, c2);
		RevCommit uB2 = commit(uA, d);
		RevCommit uninteresting = commit(uB1, uB2);

		rw.reset();
		rw.setFirstParent(true);
		markStart(e);
		markUninteresting(uninteresting);

		assertCommit(e, rw.next());
		assertNull(rw.next());
	}

	/**
	 * This fails if we try to propagate flags before parsing commits.
	 *
	 * @throws Exception
	 */
	@Test
	public void testUnparsedUninterestingCommitWithTwoParents()
			throws Exception {
		ObjectId a = unparsedCommit();
		ObjectId b = unparsedCommit(a);
		ObjectId c1 = unparsedCommit(b);
		ObjectId c2 = unparsedCommit(b);
		ObjectId d = unparsedCommit(c1);
		ObjectId e = unparsedCommit(c1, c2);

		ObjectId uA = unparsedCommit(a, b);
		ObjectId uB1 = unparsedCommit(uA, c2);
		ObjectId uB2 = unparsedCommit(uA, d);
		ObjectId uninteresting = unparsedCommit(uB1, uB2);

		rw.reset();
		rw.setFirstParent(true);
		RevCommit parsedE = rw.parseCommit(e);
		markStart(parsedE);
		markUninteresting(rw.parseCommit(uninteresting));

		assertCommit(parsedE, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testDepthWalk() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		try (DepthWalk.RevWalk dw = new DepthWalk.RevWalk(db, 1)) {
			dw.setFirstParent(true);
			dw.markRoot(dw.parseCommit(c));
			dw.markStart(dw.parseCommit(c));
			assertEquals(c, dw.next());
			assertEquals(b1, dw.next());
			assertNull(dw.next());
		}
	}

	@Test
	public void testDoNotRewriteParents() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(a);
		RevCommit c = commit(b1, b2);

		rw.reset();
		rw.setFirstParent(true);
		rw.setRewriteParents(false);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test(expected = IllegalStateException.class)
	public void testMarkStartBeforeSetFirstParent() throws Exception {
		RevCommit a = commit();

		rw.reset();
		markStart(a);
		rw.setFirstParent(true);
	}

	@Test(expected = IllegalStateException.class)
	public void testMergeBaseWithFirstParentNotAllowed() throws Exception {
		RevCommit a = commit();

		rw.reset();
		rw.setFirstParent(true);
		rw.setRevFilter(RevFilter.MERGE_BASE);
		markStart(a);
		assertNull(rw.next());
	}

	@Test
	public void testWithTopoSortAndTreeFilter() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(tree(file("0", blob("b"))), a);
		RevCommit c = commit(tree(file("0", blob("c"))), b, a);
		RevCommit d = commit(tree(file("0", blob("d"))), c);

		rw.reset();
		rw.setFirstParent(true);
		rw.sort(RevSort.TOPO, true);
		rw.setTreeFilter(PathFilterGroup.createFromStrings("0"));
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testWithTopoSortAndTreeFilter2() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(tree(file("0", blob("b"))), a);
		RevCommit c = commit(tree(file("0", blob("c"))), a, b);
		RevCommit d = commit(tree(file("0", blob("d"))), c);

		rw.reset();
		rw.setFirstParent(true);
		rw.sort(RevSort.TOPO, true);
		rw.setTreeFilter(PathFilterGroup.createFromStrings("0"));
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertNull(rw.next());
	}
}
