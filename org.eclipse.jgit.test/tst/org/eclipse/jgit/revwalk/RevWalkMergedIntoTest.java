/*
 * Copyright (C) 2014, Sven Selberg <sven.selberg@sonymobile.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
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

	@Test
	public void testGetMergedInto() throws Exception {
		/*
		 *          i
		 *         / \
		 *        A   o
		 *       / \   \
		 *      o1  o2  E
		 *     / \ / \
		 *    B   C   D
		 */
		String b = "refs/heads/b";
		String c = "refs/heads/c";
		String d = "refs/heads/d";
		String e = "refs/heads/e";
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		createBranch(commit(o1), b);
		createBranch(commit(o1, o2), c);
		createBranch(commit(o2), d);
		createBranch(commit(commit(i)), e);

		List<String>  modifiedResult = rw.getMergedInto(a, getRefs())
				.stream().map(Ref::getName).collect(Collectors.toList());

		assertTrue(modifiedResult.size() == 3);
		assertTrue(modifiedResult.contains(b));
		assertTrue(modifiedResult.contains(c));
		assertTrue(modifiedResult.contains(d));
	}

	@Test
	public void testIsMergedIntoAny() throws Exception {
		/*
		 *          i
		 *         / \
		 *        A   o
		 *       /     \
		 *      o       C
		 *     /
		 *    B
		 */
		String b = "refs/heads/b";
		String c = "refs/heads/c";
		final RevCommit i = commit();
		final RevCommit a = commit(i);
		createBranch(commit(commit(a)), b);
		createBranch(commit(commit(i)), c);

		assertTrue(rw.isMergedIntoAny(a, getRefs()));
	}

	@Test
	public void testIsMergedIntoAll() throws Exception {
		/*
		 *
		 *        A
		 *       / \
		 *      o1  o2
		 *     / \ / \
		 *    B   C   D
		 */

		String b = "refs/heads/b";
		String c = "refs/heads/c";
		String d = "refs/heads/c";
		final RevCommit a = commit();
		final RevCommit o1 = commit(a);
		final RevCommit o2 = commit(a);
		createBranch(commit(o1), b);
		createBranch(commit(o1, o2), c);
		createBranch(commit(o2), d);

		assertTrue(rw.isMergedIntoAll(a, getRefs()));
	}

	@Test
	public void testMergeIntoAnnotatedTag() throws Exception {
		/*
		 *        a
		 *        |
		 *        b
		 *       / \
		 *      c  v1 (annotated tag)
		 */
		String c = "refs/heads/c";
		String v1 = "refs/tags/v1";
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		createBranch(commit(b), c);
		createBranch(tag("v1", b), v1);

		assertTrue(rw.isMergedIntoAll(a, getRefs()));
	}

	@Test
	public void testUnmergedWithoutGraphDoesNotWalkSharedHistory()
			throws Exception {
		RevCommit shared = commit();
		for (int i = 0; i < 128; i++) {
			shared = commit(shared);
		}
		RevCommit base = commit(shared);
		RevCommit tip = commit(shared);
		createBranch(tip, "refs/heads/tip");

		try (RevWalk walk = new RevWalk(db)) {
			assertTrue(walk.getMergedInto(walk.lookupCommit(base), getRefs())
					.isEmpty());
			assertTrue(
					"Should stop at the shared ancestor, not walk its history",
					parsedCommits(walk) < 16);
		}
	}

	@Test
	public void testUnmergedWithoutGraphIgnoresMissingBaseAncestor()
			throws Exception {
		ObjectId missing = ObjectId
				.fromString("0123456789012345678901234567890123456789");
		ObjectId base = unparsedCommit(missing);
		RevCommit tip = commit();
		try (RevWalk walk = new RevWalk(db)) {
			assertFalse(walk.isMergedIntoAnyCommit(walk.lookupCommit(base),
					Collections.singleton(walk.lookupCommit(tip))));
		}
	}

	@Test
	public void testUnmergedWithoutGraphReportsMissingTipAncestor()
			throws Exception {
		ObjectId missing = ObjectId
				.fromString("0123456789012345678901234567890123456789");
		RevCommit base = commit();
		ObjectId tip = unparsedCommit(missing);
		try (RevWalk walk = new RevWalk(db)) {
			assertThrows(MissingObjectException.class,
					() -> walk.isMergedIntoAnyCommit(walk.lookupCommit(base),
							Collections.singleton(walk.lookupCommit(tip))));
		}
	}

	@Test
	public void testMergedIntoWithoutGraphPreservesFirstParent()
			throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		RevCommit other = commit(root);
		RevCommit tip = commit(other, base);
		createBranch(tip, "refs/heads/tip");

		try (RevWalk walk = new RevWalk(db)) {
			walk.setFirstParent(true);
			assertFalse(
					walk.isMergedIntoAny(walk.lookupCommit(base), getRefs()));
			assertTrue(
					walk.isMergedIntoAny(walk.lookupCommit(other), getRefs()));
			assertTrue(walk.isFirstParent());
		}
	}

	@Test
	public void testMergedIntoWithoutGraphPreservesDepth() throws Exception {
		RevCommit base = commit();
		RevCommit tip = commit(commit(base));
		try (DepthWalk.RevWalk walk = new DepthWalk.RevWalk(db, 1)) {
			RevCommit localTip = walk.parseCommit(tip);
			walk.markRoot(localTip);
			assertFalse(walk.isMergedIntoAnyCommit(walk.lookupCommit(base),
					Collections.singleton(localTip)));
		}
	}

	@Test
	public void testMergedIntoWithoutGraphWithNoFreeFlags() throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		RevCommit tip = commit(base);
		RevCommit other = commit(root);

		try (RevWalk walk = new RevWalk(db)) {
			List<RevFlag> flags = new ArrayList<>();
			while (walk.freeFlags != 0) {
				flags.add(walk.newFlag("caller"));
			}
			walk.retainOnReset(flags);
			RevCommit localTip = walk.parseCommit(tip);
			for (RevFlag flag : flags) {
				localTip.add(flag);
			}
			assertTrue(walk.isMergedIntoAnyCommit(walk.lookupCommit(base),
					Collections.singleton(localTip)));
			assertFalse(walk.isMergedIntoAnyCommit(walk.lookupCommit(other),
					Collections.singleton(localTip)));
			for (RevFlag flag : flags) {
				assertTrue(localTip.has(flag));
			}
			assertEquals(0, walk.freeFlags);
		}
	}

	@Test
	public void testMergedIntoWithoutGraphWithTwoOrThreeFreeFlags()
			throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		RevCommit tip = commit(base);
		RevCommit other = commit(root);
		for (int available : new int[] { 2, 3 }) {
			try (RevWalk walk = new RevWalk(db)) {
				while (Integer.bitCount(walk.freeFlags) > available) {
					walk.newFlag("caller");
				}
				assertTrue(walk.isMergedIntoAnyCommit(walk.lookupCommit(base),
						Collections.singleton(walk.lookupCommit(tip))));
				assertFalse(walk.isMergedIntoAnyCommit(walk.lookupCommit(other),
						Collections.singleton(walk.lookupCommit(tip))));
				walk.reset();
				assertEquals(available, Integer.bitCount(walk.freeFlags));
			}
		}
	}

	@Test
	public void testMergedIntoCancellationRetainsCompletedResults()
			throws Exception {
		RevCommit base = commit();
		createBranch(commit(base), "refs/heads/one");
		createBranch(commit(base), "refs/heads/two");
		try (RevWalk walk = new RevWalk(db)) {
			ProgressMonitor monitor = new CancelAfterCommits(walk,
					Integer.MAX_VALUE) {
				@Override
				public boolean isCancelled() {
					return completed >= 3;
				}
			};
			assertEquals(1, walk
					.getMergedInto(walk.lookupCommit(base), getRefs(), monitor)
					.size());
		}
	}

	@Test
	public void testMergedIntoWithoutGraphRestoresFiltersAndRetainsBodies()
			throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		RevCommit tip = commit(base);
		createBranch(tip, "refs/heads/tip");

		try (RevWalk walk = new RevWalk(db)) {
			TreeFilter treeFilter = PathFilter.create("missing");
			walk.setTreeFilter(treeFilter);
			walk.setRevFilter(RevFilter.NONE);
			RevFlag flag = walk.newFlag("caller");
			walk.retainOnReset(flag);
			RevCommit localBase = walk.parseCommit(base);
			localBase.add(flag);
			assertEquals(1, walk.getMergedInto(localBase, getRefs()).size());
			assertSame(RevFilter.NONE, walk.getRevFilter());
			assertSame(treeFilter, walk.getTreeFilter());
			assertTrue(localBase.has(flag));
			assertTrue(walk.isRetainBody());
			assertNotNull(localBase.getRawBuffer());
			assertNotNull(walk.lookupCommit(tip).getRawBuffer());
			walk.reset();
			walk.setRevFilter(RevFilter.ALL);
			walk.setTreeFilter(TreeFilter.ALL);
			walk.markStart(walk.lookupCommit(tip));
			assertEquals(tip, walk.next());
			assertEquals(base, walk.next());
			assertEquals(root, walk.next());
		}
	}

	@Test
	public void testMergedIntoWithoutGraphCancellation() throws Exception {
		RevCommit base = commit();
		RevCommit tip = commit();
		for (int i = 0; i < 64; i++) {
			base = commit(base);
			tip = commit(tip);
		}
		createBranch(tip, "refs/heads/tip");

		try (RevWalk walk = new RevWalk(db)) {
			assertTrue(walk.getMergedInto(walk.lookupCommit(base), getRefs(),
					new CancelAfterCommits(walk, 8)).isEmpty());
			assertTrue("Cancellation must interrupt the ancestry search",
					parsedCommits(walk) < 16);
			assertTrue(walk.isMergedIntoAnyCommit(walk.lookupCommit(tip),
					Collections.singleton(walk.lookupCommit(tip))));
		}
	}

	@Test
	public void testMergeBaseCancellationDuringInitialization()
			throws Exception {
		RevCommit base = commit();
		RevCommit tip = commit();
		for (int i = 0; i < 64; i++) {
			base = commit(base);
			tip = commit(tip);
		}

		try (RevWalk walk = new RevWalk(db)) {
			RevCommit localBase = walk.lookupCommit(base);
			RevCommit localTip = walk.lookupCommit(tip);
			walk.setProgressMonitor(new CancelAfterCommits(walk, 8));
			assertThrows(CancelledException.class,
					() -> walk.isMergedInto(localBase, localTip));
			assertTrue("Cancellation must interrupt merge-base initialization",
					parsedCommits(walk) < 16);
		}
	}

	private static int parsedCommits(RevWalk walk) {
		int parsed = 0;
		for (RevObject object : walk.objects) {
			if (object instanceof RevCommit
					&& (object.flags & RevWalk.PARSED) != 0) {
				parsed++;
			}
		}
		return parsed;
	}

	private static class CancelAfterCommits implements ProgressMonitor {
		private final RevWalk walk;

		private final int limit;

		int completed;

		CancelAfterCommits(RevWalk walk, int limit) {
			this.walk = walk;
			this.limit = limit;
		}

		@Override
		public void start(int totalTasks) {
			// No progress display.
		}

		@Override
		public void beginTask(String title, int totalWork) {
			// No progress display.
		}

		@Override
		public void update(int completed) {
			this.completed += completed;
		}

		@Override
		public void endTask() {
			// No progress display.
		}

		@Override
		public boolean isCancelled() {
			return parsedCommits(walk) >= limit;
		}

		@Override
		public void showDuration(boolean enabled) {
			// No progress display.
		}
	}

}
