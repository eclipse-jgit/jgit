/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RevWalkCarryFlagsTest extends RevWalkTestCase {
	/**
	 * Test that the uninteresting flag is carried over correctly. Every commit
	 * should have the uninteresting flag resulting in a RevWalk returning no
	 * commit.
	 *
	 * @throws Exception
	 */
	@Test
	void testRevWalkCarryUninteresting_fastClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(b, d);

		markStart(d);
		markUninteresting(e);
		assertNull(rw.next(), "Found an unexpected commit");
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
	void testRevWalkCarryUninteresting_SlowClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(0, b, d);

		markStart(d);
		markUninteresting(e);
		assertNull(rw.next(), "Found an unexpected commit");
	}

	/**
	 * Similar to {@link #testRevWalkCarryUninteresting_SlowClock()} but the
	 * last merge commit is created with a inconsistent creationdate. The merge
	 * commit has a older creationdate then one of the commits he is merging.
	 *
	 * @throws Exception
	 */
	@Test
	void testRevWalkCarryUninteresting_WrongClock() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(c);
		final RevCommit e = commit(-1, b, d);

		markStart(d);
		markUninteresting(e);
		assertNull(rw.next(), "Found an unexpected commit");
	}

	/**
	 * Same as {@link #testRevWalkCarryUninteresting_SlowClock()} but this time
	 * we focus on the carrying over a custom flag.
	 *
	 * @throws Exception
	 */
	@Test
	void testRevWalkCarryCustom_SlowClock() throws Exception {
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
					cm.has(customFlag),
					"Found a commit which doesn't have the custom flag: " + cm);
			count++;
		}
		assertEquals(count, 5, "Didn't walked over all commits");
	}
}
