/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("Re-enable when I573f980abb0c97414e8e7dfcc3ac6dab2544f6c1 is reverted")
public class TopoSortPendingGeneratorTest
		extends AbstractRevWalkWithCommitGraphTest {

	public TopoSortPendingGeneratorTest() {
		super(true);
	}

	@Test
	public void testSort_TOPO_reset_doesNotLeakFlags() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);
		final RevCommit d = commit("main", c);

		int appFlags = Integer.bitCount(RevWalk.APP_FLAGS);

		initializeRevWalk();
		rw.sort(RevSort.TOPO);
		markStart(d);
		assertCommit(d, rw.next());
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());

		assertThat(Integer.bitCount(rw.freeFlags))
				.as("4 Flags should be allocated by TopoSortPendingGenerator")
				.isEqualTo(appFlags - 4);

		rw.reset();

		assertThat(Integer.bitCount(rw.freeFlags)) //
				.as("Flags allocated by TopoSortPendingGenerator should be freed when calling RevWalk.reset()")
				.isEqualTo(appFlags);
	}

}
