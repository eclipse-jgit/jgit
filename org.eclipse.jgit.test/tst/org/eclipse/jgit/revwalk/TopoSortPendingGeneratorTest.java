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

import org.junit.Test;

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


	@Test
	public void testSort_TOPO_ExplorePhase_earlyReturn() throws Exception {
		final RevCommit root = commit();
		final RevCommit beforeUninteresting = commit(root);
		final RevCommit unintersting = commit(beforeUninteresting);
		final RevCommit afterBoundary = commit(unintersting);
		final RevCommit head = commit("main", afterBoundary);

		initializeRevWalk();
		rw.sort(RevSort.TOPO);
		markStart(head);
		markUninteresting(unintersting);

		assertCommit(head, rw.next());
		assertCommit(afterBoundary, rw.next());
		assertNull(rw.next());

		// Explore phase should stop as soon as only UNINTERESTING commits
		// remain, without parsing commits before 'uninteresting'
		assertNull(rw.lookupCommit(beforeUninteresting).getParents());
	}

}
