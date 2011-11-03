package org.eclipse.jgit.revwalk;

import java.io.*;
import java.util.*;

import org.junit.*;

import static org.junit.Assert.*;

public class CommitReachabilityTest extends RevWalkTestCase {

	@Test
	public void testSelfReachability() throws Exception {
		final RevCommit base = commit();

		assertTrue(isAnyMergedInto(Collections.singleton(base), base));
		assertTrue(getBasesInTip(Collections.singleton(base), base).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(base)));
		assertTrue(getTipsInBase(base, Collections.singleton(base)).contains(
				base));
	}

	@Test
	public void testParentChild() throws Exception {
		final RevCommit base = commit();
		final RevCommit tip = commit(base);

		assertTrue(isAnyMergedInto(Collections.singleton(base), tip));
		assertTrue(getBasesInTip(Collections.singleton(base), tip).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(tip)));
		assertTrue(getTipsInBase(base, Collections.singleton(tip))
				.contains(tip));
	}

	@Test
	public void testParentIntermediateChild() throws Exception {
		final RevCommit base = commit();
		final RevCommit intermediate = commit(base);
		final RevCommit tip = commit(intermediate);

		assertTrue(isAnyMergedInto(Collections.singleton(base), tip));
		assertTrue(getBasesInTip(Collections.singleton(base), tip).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(tip)));
		assertTrue(getTipsInBase(base, Collections.singleton(tip))
				.contains(tip));
	}

	@Test
	public void testParent2IntermediatesChild() throws Exception {
		final RevCommit base = commit();
		final RevCommit intermediate1 = commit(base);
		final RevCommit intermediate2 = commit(intermediate1);
		final RevCommit tip = commit(intermediate2);

		assertTrue(isAnyMergedInto(Collections.singleton(base), tip));
		assertTrue(getBasesInTip(Collections.singleton(base), tip).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(tip)));
		assertTrue(getTipsInBase(base, Collections.singleton(tip))
				.contains(tip));
	}

	@Test
	public void testMergeCommit() throws Exception {
		final RevCommit base = commit();
		final RevCommit fork1 = commit(base);
		final RevCommit fork2 = commit(base);
		final RevCommit tip = commit(fork1, fork2);

		assertTrue(isAnyMergedInto(Collections.singleton(base), tip));
		assertTrue(getBasesInTip(Collections.singleton(base), tip).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(tip)));
		assertTrue(getTipsInBase(base, Collections.singleton(tip))
				.contains(tip));
	}

	@Test
	public void testMultipleMergeFork() throws Exception {
		final RevCommit base1 = commit();
		final RevCommit base2 = commit();
		final RevCommit fork1 = commit(base1);
		final RevCommit fork2 = commit(base1, base2);
		final RevCommit fork3 = commit(base2);
		final RevCommit tip1 = commit(fork1, fork2);
		final RevCommit tip2 = commit(fork2, fork3);

		assertTrue(isAnyMergedInto(Arrays.asList(base1, base2), tip1));
		assertEquals(new HashSet<RevCommit>(Arrays.asList(base1, base2)),
				getBasesInTip(Arrays.asList(base1, base2), tip1));
		assertEquals(new HashSet<RevCommit>(Arrays.asList(base1, base2)),
				getBasesInTip(Arrays.asList(base1, base2), tip2));
		assertTrue(isMergedIntoAny(base1, Arrays.asList(tip1, tip2)));
		assertEquals(new HashSet<RevCommit>(Arrays.asList(tip1, tip2)),
				getTipsInBase(base1, Arrays.asList(tip1, tip2)));
		assertEquals(new HashSet<RevCommit>(Arrays.asList(tip1, tip2)),
				getTipsInBase(base2, Arrays.asList(tip1, tip2)));
	}

	@Test
	public void testForksNotReachable() throws Exception {
		final RevCommit base = commit();
		final RevCommit fork1 = commit(base);
		final RevCommit fork2 = commit(base);

		assertFalse(isAnyMergedInto(Collections.singleton(fork1), fork2));
		assertTrue(getBasesInTip(Collections.singleton(fork1), fork2).isEmpty());
		assertFalse(isAnyMergedInto(Collections.singleton(fork2), fork1));
		assertTrue(getBasesInTip(Collections.singleton(fork2), fork1).isEmpty());
		assertFalse(isMergedIntoAny(fork1, Collections.singleton(fork2)));
		assertTrue(getTipsInBase(fork1, Collections.singleton(fork2)).isEmpty());
		assertFalse(isMergedIntoAny(fork2, Collections.singleton(fork1)));
		assertTrue(getTipsInBase(fork2, Collections.singleton(fork1)).isEmpty());
	}

	@Test
	public void testParentIntermediateChildrenWrongCommitTimeOrder()
			throws Exception {
		final RevCommit root = commit();
		final RevCommit base = commit(root);
		final RevCommit badIntermediate = commit(-10, base);
		final RevCommit tip = commit(badIntermediate);

		assertTrue(isAnyMergedInto(Collections.singleton(base), tip));
		assertTrue(getBasesInTip(Collections.singleton(base), tip).contains(
				base));
		assertTrue(isMergedIntoAny(base, Collections.singleton(tip)));
		assertTrue(getTipsInBase(base, Collections.singleton(tip))
				.contains(tip));
	}

	private Set<RevCommit> getTipsInBase(RevCommit base,
			Collection<RevCommit> tips) throws IOException {
		return CommitReachability.getTipsThatContainsBase(base, tips, rw);
	}

	private boolean isMergedIntoAny(RevCommit tip, Collection<RevCommit> bases)
			throws IOException {
		return CommitReachability.isMergedIntoAny(tip, bases, rw);
	}

	private Set<RevCommit> getBasesInTip(Collection<RevCommit> bases,
			RevCommit tip) throws IOException {
		return CommitReachability
				.getBasesWhichAreContainedInTip(bases, tip, rw);
	}

	private boolean isAnyMergedInto(Collection<RevCommit> bases, RevCommit tip)
			throws IOException {
		return CommitReachability.isAnyMergedInto(bases, tip, rw);
	}
}
