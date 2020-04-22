/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.GcTestCase;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;

public class GcCommitSelectionTest extends GcTestCase {

	@Test
	public void testBitmapSpansNoMerges() throws Exception {
		testBitmapSpansNoMerges(false);
	}

	@Test
	public void testBitmapSpansNoMergesWithTags() throws Exception {
		testBitmapSpansNoMerges(true);
	}

	private void testBitmapSpansNoMerges(boolean withTags) throws Exception {
		/*
		 * Commit counts -> expected bitmap counts for history without merges.
		 * The top 100 contiguous commits should always have bitmaps, and the
		 * "recent" bitmaps beyond that are spaced out every 100-200 commits.
		 * (Starting at 100, the next 100 commits are searched for a merge
		 * commit. Since one is not found, the spacing between commits is 200.
		 */
		int[][] bitmapCounts = { //
				{ 1, 1 }, { 50, 50 }, { 99, 99 }, { 100, 100 }, { 101, 100 },
				{ 200, 100 }, { 201, 100 }, { 299, 100 }, { 300, 101 },
				{ 301, 101 }, { 401, 101 }, { 499, 101 }, { 500, 102 }, };
		int currentCommits = 0;
		BranchBuilder bb = tr.branch("refs/heads/main");

		for (int[] counts : bitmapCounts) {
			int nextCommitCount = counts[0];
			int expectedBitmapCount = counts[1];
			assertTrue(nextCommitCount > currentCommits); // programming error
			for (int i = currentCommits; i < nextCommitCount; i++) {
				String str = "A" + i;
				RevCommit rc = bb.commit().message(str).add(str, str).create();
				if (withTags) {
					tr.lightweightTag(str, rc);
				}
			}
			currentCommits = nextCommitCount;

			gc.setPackExpireAgeMillis(0); // immediately delete old packs
			gc.setExpireAgeMillis(0);
			gc.gc();
			assertEquals(currentCommits * 3, // commit/tree/object
					gc.getStatistics().numberOfPackedObjects);
			assertEquals(currentCommits + " commits: ", expectedBitmapCount,
					gc.getStatistics().numberOfBitmaps);
		}
	}

	@Test
	public void testBitmapSpansWithMerges() throws Exception {
		/*
		 * Commits that are merged. Since 55 is in the oldest history it is
		 * never considered. Searching goes from oldest to newest so 115 is the
		 * first merge commit found. After that the range 116-216 is ignored so
		 * 175 is never considered.
		 */
		List<Integer> merges = Arrays.asList(Integer.valueOf(55),
				Integer.valueOf(115), Integer.valueOf(175),
				Integer.valueOf(235));
		/*
		 * Commit counts -> expected bitmap counts for history with merges. The
		 * top 100 contiguous commits should always have bitmaps, and the
		 * "recent" bitmaps beyond that are spaced out every 100-200 commits.
		 * Merges in the < 100 range have no effect and merges in the > 100
		 * range will only be considered for commit counts > 200.
		 */
		int[][] bitmapCounts = { //
				{ 1, 1 }, { 55, 55 }, { 56, 57 }, // +1 bitmap from branch A55
				{ 99, 100 }, // still +1 branch @55
				{ 100, 100 }, // 101 commits, only 100 newest
				{ 116, 100 }, // @55 still in 100 newest bitmaps
				{ 176, 101 }, // @55 branch tip is not in 100 newest
				{ 213, 101 }, // 216 commits, @115&@175 in 100 newest
				{ 214, 102 }, // @55 branch tip, merge @115, @177 in newest
				{ 236, 102 }, // all 4 merge points in history
				{ 273, 102 }, // 277 commits, @175&@235 in newest
				{ 274, 103 }, // @55, @115, merge @175, @235 in newest
				{ 334, 103 }, // @55,@115,@175, @235 in newest
				{ 335, 104 }, // @55,@115,@175, merge @235
				{ 435, 104 }, // @55,@115,@175,@235 tips
				{ 436, 104 }, // force @236
		};

		int currentCommits = 0;
		BranchBuilder bb = tr.branch("refs/heads/main");

		for (int[] counts : bitmapCounts) {
			int nextCommitCount = counts[0];
			int expectedBitmapCount = counts[1];
			assertTrue(nextCommitCount > currentCommits); // programming error
			for (int i = currentCommits; i < nextCommitCount; i++) {
				String str = "A" + i;
				if (!merges.contains(Integer.valueOf(i))) {
					bb.commit().message(str).add(str, str).create();
				} else {
					BranchBuilder bbN = tr.branch("refs/heads/A" + i);
					bb.commit().message(str).add(str, str)
							.parent(bbN.commit().create()).create();
				}
			}
			currentCommits = nextCommitCount;

			gc.setPackExpireAgeMillis(0); // immediately delete old packs
			gc.setExpireAgeMillis(0);
			gc.gc();
			assertEquals(currentCommits + " commits: ", expectedBitmapCount,
					gc.getStatistics().numberOfBitmaps);
		}
	}

	@Test
	public void testBitmapsForExcessiveBranches() throws Exception {
		int oneDayInSeconds = 60 * 60 * 24;

		// All of branch A is committed on day1
		BranchBuilder bbA = tr.branch("refs/heads/A");
		for (int i = 0; i < 1001; i++) {
			String msg = "A" + i;
			bbA.commit().message(msg).add(msg, msg).create();
		}
		// All of in branch B is committed on day91
		tr.tick(oneDayInSeconds * 90);
		BranchBuilder bbB = tr.branch("refs/heads/B");
		for (int i = 0; i < 1001; i++) {
			String msg = "B" + i;
			bbB.commit().message(msg).add(msg, msg).create();
		}
		// Create 100 other branches with a single commit
		for (int i = 0; i < 100; i++) {
			BranchBuilder bb = tr.branch("refs/heads/N" + i);
			String msg = "singlecommit" + i;
			bb.commit().message(msg).add(msg, msg).create();
		}
		// now is day92
		tr.tick(oneDayInSeconds);

		// Since there are no merges, commits in recent history are selected
		// every 200 commits.
		final int commitsForSparseBranch = 1 + (1001 / 200);
		final int commitsForFullBranch = 100 + (901 / 200);
		final int commitsForShallowBranches = 100;

		// Excessive branch history pruning, one old branch.
		gc.setPackExpireAgeMillis(0); // immediately delete old packs
		gc.setExpireAgeMillis(0);
		gc.gc();
		assertEquals(
				commitsForSparseBranch + commitsForFullBranch
						+ commitsForShallowBranches,
				gc.getStatistics().numberOfBitmaps);
	}

	@Test
	public void testSelectionOrderingWithChains() throws Exception {
		/*-
		 * Create a history like this, where 'N' is the number of seconds from
		 * the first commit in the branch:
		 *
		 *      ---o---o---o        commits b3,b5,b7
		 *     /            \
		 * o--o--o---o---o---o--o   commits m0,m1,m2,m4,m6,m8,m9
		 */
		BranchBuilder bb = tr.branch("refs/heads/main");
		RevCommit m0 = addCommit(bb, "m0");
		RevCommit m1 = addCommit(bb, "m1", m0);
		RevCommit m2 = addCommit(bb, "m2", m1);
		RevCommit b3 = addCommit(bb, "b3", m1);
		RevCommit m4 = addCommit(bb, "m4", m2);
		RevCommit b5 = addCommit(bb, "m5", b3);
		RevCommit m6 = addCommit(bb, "m6", m4);
		RevCommit b7 = addCommit(bb, "m7", b5);
		RevCommit m8 = addCommit(bb, "m8", m6, b7);
		RevCommit m9 = addCommit(bb, "m9", m8);

		List<RevCommit> commits = Arrays.asList(m0, m1, m2, b3, m4, b5, m6, b7,
				m8, m9);
		PackWriterBitmapPreparer preparer = newPreparer(
				Collections.singleton(m9), commits, new PackConfig());
		List<BitmapCommit> selection = new ArrayList<>(
				preparer.selectCommits(commits.size(), PackWriter.NONE));

		// Verify that the output is ordered by the separate "chains"
		String[] expected = { m0.name(), m1.name(), m2.name(), m4.name(),
				m6.name(), m8.name(), m9.name(), b3.name(), b5.name(),
				b7.name() };
		assertEquals(expected.length, selection.size());
		for (int i = 0; i < expected.length; i++) {
			assertEquals("Entry " + i, expected[i], selection.get(i).getName());
		}
	}

	private RevCommit addCommit(BranchBuilder bb, String msg,
			RevCommit... parents) throws Exception {
		CommitBuilder commit = bb.commit().message(msg).add(msg, msg).tick(1)
				.noParents();
		for (RevCommit parent : parents) {
			commit.parent(parent);
		}
		return commit.create();
	}

	@Test
	public void testDistributionOnMultipleBranches() throws Exception {
		BranchBuilder[] branches = { tr.branch("refs/heads/main"),
				tr.branch("refs/heads/a"), tr.branch("refs/heads/b"),
				tr.branch("refs/heads/c") };
		RevCommit[] tips = new RevCommit[branches.length];
		List<RevCommit> commits = createHistory(branches, tips);
		PackConfig config = new PackConfig();
		config.setBitmapContiguousCommitCount(1);
		config.setBitmapRecentCommitSpan(5);
		config.setBitmapDistantCommitSpan(20);
		config.setBitmapRecentCommitCount(100);
		Set<RevCommit> wants = new HashSet<>(Arrays.asList(tips));
		PackWriterBitmapPreparer preparer = newPreparer(wants, commits, config);
		List<BitmapCommit> selection = new ArrayList<>(
				preparer.selectCommits(commits.size(), PackWriter.NONE));
		Set<ObjectId> selected = new HashSet<>();
		for (BitmapCommit c : selection) {
			selected.add(c.toObjectId());
		}

		// Verify that each branch has uniform bitmap selection coverage
		for (RevCommit c : wants) {
			assertTrue(selected.contains(c.toObjectId()));
			int count = 1;
			int selectedCount = 1;
			RevCommit parent = c;
			while (parent.getParentCount() != 0) {
				parent = parent.getParent(0);
				count++;
				if (selected.contains(parent.toObjectId())) {
					selectedCount++;
				}
			}
			// The selection algorithm prefers merges and will look in the
			// current range plus the recent commit span before selecting a
			// commit. Since this history has no merges, we expect the recent
			// span should have 100/10=10 and distant commit spans should have
			// 100/25=4 per 100 commit range.
			int expectedCount = 10 + (count - 100 - 24) / 25;
			assertTrue(expectedCount <= selectedCount);
		}
	}

	private List<RevCommit> createHistory(BranchBuilder[] branches,
			RevCommit[] tips) throws Exception {
		/*-
		 * Create a history like this, where branches a, b and c branch off of the main branch
		 * at commits 100, 200 and 300, and where commit times move forward alternating between
		 * branches.
		 *
		 * o...o...o...o...o      commits root,m0,m1,...,m399
		 *      \   \   \
		 *       \   \   o...     commits branch_c,c300,c301,...,c399
		 *        \   \
		 *         \   o...o...   commits branch_b,b200,b201,...,b399
		 *          \
		 *           o...o...o... commits branch_a,b100,b101,...,a399
		 */
		List<RevCommit> commits = new ArrayList<>();
		String[] prefixes = { "m", "a", "b", "c" };
		int branchCount = branches.length;
		tips[0] = addCommit(commits, branches[0], "root");
		int counter = 0;

		for (int b = 0; b < branchCount; b++) {
			for (int i = 0; i < 100; i++, counter++) {
				for (int j = 0; j <= b; j++) {
					tips[j] = addCommit(commits, branches[j],
							prefixes[j] + counter);
				}
			}
			// Create a new branch from current value of the master branch
			if (b < branchCount - 1) {
				tips[b + 1] = addCommit(branches[b + 1],
						"branch_" + prefixes[b + 1], tips[0]);
			}
		}
		return commits;
	}

	private RevCommit addCommit(List<RevCommit> commits, BranchBuilder bb,
			String msg, RevCommit... parents) throws Exception {
		CommitBuilder commit = bb.commit().message(msg).add(msg, msg).tick(1);
		if (parents.length > 0) {
			commit.noParents();
			for (RevCommit parent : parents) {
				commit.parent(parent);
			}
		}
		RevCommit c = commit.create();
		commits.add(c);
		return c;
	}

	private PackWriterBitmapPreparer newPreparer(Set<RevCommit> wants,
			List<RevCommit> commits, PackConfig config) throws IOException {
		List<ObjectToPack> objects = new ArrayList<>(commits.size());
		for (RevCommit commit : commits) {
			objects.add(new ObjectToPack(commit, Constants.OBJ_COMMIT));
		}
		PackBitmapIndexBuilder builder = new PackBitmapIndexBuilder(objects);
		return new PackWriterBitmapPreparer(
				tr.getRepository().newObjectReader(), builder,
				NullProgressMonitor.INSTANCE, wants, config);
	}
}
