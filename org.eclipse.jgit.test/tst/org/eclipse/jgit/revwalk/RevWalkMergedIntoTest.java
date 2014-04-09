/*
 * Copyright (C) 2014, Sven Selberg <sven.selberg@sonymobile.com>
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RevWalkMergedIntoTest extends RevWalkTestCase {

	@Test
	public void testOldCommitWalk() throws Exception {
		/*
		 * Sometimes a merge is performed on a machine with faulty time.
		 * This makes the traversal of the graph, when trying to find out if B
		 * is merged into T, complex since the algorithm uses the time stamps
		 * of commits to find the best route.
		 * When for example O(ld) has a very old time stamp compared to one of the
		 * commits (N(ew)) on the upper route between T and F(alse base), the route
		 * to False is deemed the better option even though the alternate route leeds
		 * to B(ase) which was the commit we were after.
		 *
		 *             o---o---o---o---N
		 *            /                 \
		 *           /   o---o---o---O---T
		 *          /   /
		 *      ---F---B
		 *
		 * This test is asserting that isMergedInto(B, T) returns true even
		 * under those circumstances.
		 */
		final int threeDaysInSecs = 3 * 24 * 60 * 60;
		final RevCommit f = commit();
		final RevCommit b = commit(f);
		final RevCommit o = commit(-threeDaysInSecs, commit(commit(commit(b))));
		final RevCommit n = commit(commit(commit(commit(commit(f)))));
		final RevCommit t = commit(n, o);
		assertTrue(rw.isMergedInto(b, t));
	}
}
