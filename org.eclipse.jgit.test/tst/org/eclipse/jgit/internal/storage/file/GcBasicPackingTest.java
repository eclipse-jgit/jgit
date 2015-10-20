/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class GcBasicPackingTest extends GcTestCase {
	@DataPoints
	public static boolean[] aggressiveValues = { true, false };

	@Theory
	public void repackEmptyRepo_noPackCreated(boolean aggressive)
			throws IOException {
		configureGc(gc, aggressive);
		gc.repack();
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Theory
	public void testPackRepoWithNoRefs(boolean aggressive) throws Exception {
		tr.commit().add("A", "A").add("B", "B").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);
		assertEquals(0, stats.numberOfBitmaps);
	}

	@Theory
	public void testPack2Commits(boolean aggressive) throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(2, stats.numberOfBitmaps);
	}

	@Theory
	public void testPackAllObjectsInOnePack(boolean aggressive)
			throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);

		// Do the gc again and check that it hasn't changed anything
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);
	}

	@Theory
	public void testPackCommitsAndLooseOne(boolean aggressive)
			throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);
	}

	@Theory
	public void testNotPackTwice(boolean aggressive) throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().message("M").add("M", "M").create();
		bb.commit().message("B").add("B", "Q").create();
		bb.commit().message("A").add("A", "A").create();
		RevCommit second = tr.commit().parent(first).message("R").add("R", "Q")
				.create();
		tr.update("refs/tags/t1", second);

		Collection<PackFile> oldPacks = tr.getRepository().getObjectDatabase()
				.getPacks();
		assertEquals(0, oldPacks.size());
		stats = gc.getStatistics();
		assertEquals(11, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		gc.setExpireAgeMillis(0);
		fsTick();
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);

		Iterator<PackFile> pIt = repo.getObjectDatabase().getPacks().iterator();
		long c = pIt.next().getObjectCount();
		if (c == 9)
			assertEquals(2, pIt.next().getObjectCount());
		else {
			assertEquals(2, c);
			assertEquals(9, pIt.next().getObjectCount());
		}
	}

	@Test
	public void testDonePruneTooYoungPacks() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("M").add("M", "M").create();

		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(3, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		File oldPackfile = tr.getRepository().getObjectDatabase().getPacks()
				.iterator().next().getPackFile();

		fsTick();
		bb.commit().message("B").add("B", "Q").create();

		// The old packfile is too young to be deleted. We should end up with
		// two pack files
		gc.setExpire(new Date(oldPackfile.lastModified() - 1));
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		// if objects exist in multiple packFiles then they are counted multiple
		// times
		assertEquals(9, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);

		// repack again but now without a grace period for packfiles. We should
		// end up with one packfile
		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		// if objects exist in multiple packFiles then they are counted multiple
		// times
		assertEquals(6, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);

	}

	@Test
	public void testBitmapSpansNoMerges() throws Exception {
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
				bb.commit().message(str).add(str, str).create();
			}
			currentCommits = nextCommitCount;

			gc.setExpireAgeMillis(0); // immediately delete old packs
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

			gc.setExpireAgeMillis(0); // immediately delete old packs
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
		gc.gc();
		assertEquals(
				commitsForSparseBranch + commitsForFullBranch
						+ commitsForShallowBranches,
				gc.getStatistics().numberOfBitmaps);
	}

	private void configureGc(GC myGc, boolean aggressive) {
		PackConfig pconfig = new PackConfig(repo);
		if (aggressive) {
			pconfig.setDeltaSearchWindowSize(250);
			pconfig.setMaxDeltaDepth(250);
			pconfig.setReuseObjects(false);
		} else
			pconfig = new PackConfig(repo);
		myGc.setPackConfig(pconfig);
	}
}
