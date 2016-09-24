/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RevWalkCarryFlagsTest extends RevWalkTestCase {
	/**
	 * Test that the uninteresting flag is carried over correctly. Every commit
	 * should have the uninteresting flag resulting in a RevWalk returning no
	 * commit.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRevWalkCarryUninteresting_fastClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(b, d);

		markStart(d);
		markUninteresting(e);
		assertNull("Found an unexpected commit", rw.next());
	}

	/**
	 * Similar to {@link #testRevWalkCarryUninteresting_fastClock()} but the
	 * last merge commit is created so fast that he has the same creationdate as
	 * the previous commit. This will cause the underlying {@link DateRevQueue}
	 * is not able to sort the commits in a way matching the topology. A parent
	 * (one of the commits which are merged) is handled before the child (the
	 * merge commit). This makes carrying over flags more complicated
	 *
	 * @throws Exception
	 */
	@Test
	public void testRevWalkCarryUninteresting_SlowClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(0, b, d);

		markStart(d);
		markUninteresting(e);
		assertNull("Found an unexpected commit", rw.next());
	}

	/**
	 * Similar to {@link #testRevWalkCarryUninteresting_SlowClock()} but the
	 * last merge commit is created with a inconsistent creationdate. The merge
	 * commit has a older creationdate then one of the commits he is merging.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRevWalkCarryUninteresting_WrongClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(-1, b, d);

		markStart(d);
		markUninteresting(e);
		assertNull("Found an unexpected commit", rw.next());
	}

	/**
	 * Same as {@link #testRevWalkCarryUninteresting_SlowClock()} but this time
	 * we focus on the carrying over a custom flag.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRevWalkCarryCustom_SlowClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(0, b, d);

		markStart(d);
		markStart(e);
		RevFlag customFlag = rw.newFlag("CUSTOM");
		e.flags |= customFlag.mask;
		rw.carry(customFlag);

		// the merge commit has the flag and it should be carried over -> every
		// commit should have this flag
		int count = 0;
		for (RevCommit cm : rw) {
			assertTrue(
					"Found a commit which doesn't have the custom flag: " + cm,
					cm.has(customFlag));
			count++;
		}
		assertTrue("Didn't walked over all commits", count == 5);
	}
}
