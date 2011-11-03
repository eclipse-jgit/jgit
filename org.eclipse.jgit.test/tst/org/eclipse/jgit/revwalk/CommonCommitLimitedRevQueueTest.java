package org.eclipse.jgit.revwalk;

import java.io.*;
import java.util.*;

import org.junit.*;

import static org.junit.Assert.*;

public class CommonCommitLimitedRevQueueTest extends RevWalkTestCase {

	@Test
	public void testSingleCommit() throws Exception {
		final RevCommit before = commit();
		final RevCommit base = commit(before);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Collections.singletonList(base), null);
		assertRevWalk(rw, base);
	}

	@Test
	public void testParentChild() throws Exception {
		final RevCommit before = commit();
		final RevCommit base = commit(before);
		final RevCommit tip = commit(base);

		CommonCommitLimitedRevQueue.configureRevWalk(rw, Arrays.asList(tip),
				null);
		assertRevWalk(rw, tip);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tip, base), null);
		assertRevWalk(rw, tip, base);
	}

	@Test
	public void testParentIntermediateChild() throws Exception {
		final RevCommit before = commit();
		final RevCommit base = commit(before);
		final RevCommit intermediate = commit(base);
		final RevCommit tip = commit(intermediate);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tip, base), null);
		assertRevWalk(rw, tip, intermediate, base);
	}

	@Test
	public void testMergeCommit() throws Exception {
		final RevCommit before = commit();
		final RevCommit base = commit(before);
		final RevCommit fork1 = commit(base);
		final RevCommit fork2 = commit(base);
		final RevCommit tip = commit(fork1, fork2);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tip, base), null);
		assertRevWalk(rw, tip, fork2, fork1, base);
	}

	@Test
	public void testForksBase() throws Exception {
		final RevCommit before = commit();
		final RevCommit base = commit(before);
		final RevCommit fork1 = commit(base);
		final RevCommit fork2 = commit(base);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(fork1, fork2), null);
		assertRevWalk(rw, fork2, fork1, base);
	}

	@Test
	public void testMultipleMergeFork() throws Exception {
		final RevCommit before = commit();
		final RevCommit root1 = commit(before);
		final RevCommit root2 = commit(before);
		final RevCommit base1 = commit(root1, root2);
		final RevCommit base2 = commit(root1, root2);
		final RevCommit fork1 = commit(base1);
		final RevCommit fork2 = commit(base1, base2);
		final RevCommit fork3 = commit(base2);
		final RevCommit tip1 = commit(fork1, fork2);
		final RevCommit tip2 = commit(fork2, fork3);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tip1, tip2, base1, base2), null);
		assertRevWalk(rw, tip2, tip1, fork3, fork2, fork1, base2, base1, root2,
				root1);
	}

	@Test
	public void testParentIntermediateChildrenWrongCommitTimeOrder()
			throws Exception {
		final RevCommit earlier = commit(-10);
		final RevCommit before = commit(earlier);
		final RevCommit base = commit(10, before);
		final RevCommit intermediate1B = commit(base);
		final RevCommit badIntermediate = commit(-10, intermediate1B);
		final RevCommit intermediate3B = commit(11, badIntermediate);
		final RevCommit intermediate1A = commit(base);
		final RevCommit intermediate2A = commit(intermediate1A);
		final RevCommit intermediate3A = commit(intermediate2A);
		final RevCommit tipA = commit(intermediate3A);
		final RevCommit tipB = commit(intermediate3B);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tipA, tipB), null);
		// Due to "badIntermediate", "before" will be touched, but not
		// processed, hence
		// "before" may be reported, but "earlier" must not be reported.
		assertRevWalk(rw, tipB, tipA, intermediate3A, intermediate2A,
				intermediate1A, intermediate3B, base, badIntermediate,
				intermediate1B, before);

		CommonCommitLimitedRevQueue.configureRevWalk(rw,
				Arrays.asList(tipA, tipB), null);
		rw.sort(RevSort.TOPO);
		assertRevWalk(rw, tipB, tipA, intermediate3A, intermediate2A,
				intermediate1A, intermediate3B, badIntermediate,
				intermediate1B, base, before);
	}

	// Utils ==================================================================

	private void assertRevWalk(RevWalk rw, RevCommit... commits)
			throws IOException {
		final List<RevCommit> commitsList = new ArrayList<RevCommit>(
				Arrays.asList(commits));
		for (RevCommit commit = rw.next(); commit != null; commit = rw.next()) {
			assertEquals(commitsList.remove(0), commit);
		}
		assertTrue(commitsList.isEmpty());
	}
}
