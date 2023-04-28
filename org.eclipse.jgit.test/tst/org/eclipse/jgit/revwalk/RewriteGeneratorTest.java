package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class RewriteGeneratorTest extends RevWalkTestCase {

	/**
	 * Tests that the RewriteGenerator does not exhaust the previous generator.
	 *
	 * @throws Exception
	 */
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

	/**
	 * Test if the RewriteGenerator correctly rewrites a parent.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRewriteGeneratorRewritesParent() throws Exception {
		RevCommit a = commit();
		a.flags |= RevWalk.TREE_REV_FILTER_APPLIED;
		a.flags |= RevWalk.REWRITE;
		RevCommit b = commit(a);
		assertEquals(1, b.getParentCount());

		LIFORevQueue q = new LIFORevQueue();
		/*
		 * We are only adding commit b (and not a), because commit a would not
		 * be produced by the PendingGenerator (we are only simulating the
		 * PendingGenerator here with the queue).
		 */
		q.add(b);

		RewriteGenerator rewriteGenerator = new RewriteGenerator(q);
		RevCommit returnedB = rewriteGenerator.next();
		assertEquals(b.getId(), returnedB.getId());
		assertEquals(0, returnedB.getParentCount());

	}



}
