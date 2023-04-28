/*
 * Copyright (C) 2023, HIS eG
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class RewriteGeneratorTest extends RevWalkTestCase {

	@Test
	public void testRewriteGeneratorDoesNotExhaustPreviousGenerator()
			throws Exception {
		RevCommit a = commit();
		a.flags |= RevWalk.TREE_REV_FILTER_APPLIED;
		RevCommit b = commit(a);

		LIFORevQueue q = new LIFORevQueue();
		q.add(a);
		q.add(b);

		/*
		 * Since the TREE_REV_FILTER has been applied to commit a and the
		 * REWRITE flag has not been applied to commit a, the RewriteGenerator
		 * must not rewrite the parent of b and thus must not call the previous
		 * generator (since b already has its correct parent).
		 */

		RewriteGenerator rewriteGenerator = new RewriteGenerator(q);
		rewriteGenerator.next();

		assertNotNull(
				"Previous generator was unnecessarily exhausted by RewriteGenerator",
				q.next());
	}

	@Test
	public void testRewriteGeneratorRewritesParent() throws Exception {
		RevCommit a = commit();
		a.flags |= RevWalk.TREE_REV_FILTER_APPLIED;
		a.flags |= RevWalk.REWRITE;
		RevCommit b = commit(a);
		assertEquals(1, b.getParentCount());

		LIFORevQueue q = new LIFORevQueue();
		/*
		 * We are only adding commit b (and not a), because PendingGenerator
		 * should never emit a commit that has the REWRITE flag set.
		 */
		q.add(b);

		RewriteGenerator rewriteGenerator = new RewriteGenerator(q);
		RevCommit returnedB = rewriteGenerator.next();
		assertEquals(b.getId(), returnedB.getId());
		assertEquals(0, returnedB.getParentCount());

	}



}
