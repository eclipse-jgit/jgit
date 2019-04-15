package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

/**
 * Test for detecting a bug that happens only in the combination of DepthWalk
 * and topological sort.
 */
@RunWith(JUnit4.class)
public class DepthWalkTopoSortGeneratorTest extends RevWalkTestCase {
	@Test
	public void visitMultipleTimes() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		RevCommit c = commit(b);
		RevCommit d = commit(c);

		try (DepthWalk.RevWalk depthWalk = new DepthWalk.RevWalk(
				rw.getObjectReader(), Integer.MAX_VALUE)) {
			depthWalk.sort(RevSort.TOPO);
			depthWalk.markRoot(depthWalk.lookupCommit(d));
			depthWalk.markStart(depthWalk.lookupCommit(b));
			depthWalk.lookupCommit(b).add(depthWalk.getUnshallowFlag());
			assertEquals(depthWalk.next().toObjectId(), d.toObjectId());
			assertEquals(depthWalk.next().toObjectId(), c.toObjectId());
			assertEquals(depthWalk.next().toObjectId(), b.toObjectId());
			assertEquals(depthWalk.next().toObjectId(), a.toObjectId());
			assertNull(depthWalk.next());
		}
	}
}
