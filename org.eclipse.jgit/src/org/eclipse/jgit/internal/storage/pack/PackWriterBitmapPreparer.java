/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

import static org.eclipse.jgit.internal.storage.file.PackBitmapIndex.FLAG_REUSE;
import static org.eclipse.jgit.revwalk.RevFlag.SEEN;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.revwalk.AddUnseenToBitmapFilter;
import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl.CompressedBitmap;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexRemapper;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.BitmapWalker;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.SystemReader;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * Helper class for the {@link PackWriter} to select commits for which to build
 * pack index bitmaps.
 */
class PackWriterBitmapPreparer {

	private static final int DAY_IN_SECONDS = 24 * 60 * 60;

	private static final Comparator<RevCommit> ORDER_BY_REVERSE_TIMESTAMP = (RevCommit a, RevCommit b) -> Integer.signum(b.getCommitTime() - a.getCommitTime());

	private final ObjectReader reader;
	private final ProgressMonitor pm;
	private final Set<? extends ObjectId> want;
	private final PackBitmapIndexBuilder writeBitmaps;
	private final BitmapIndexImpl commitBitmapIndex;
	private final PackBitmapIndexRemapper bitmapRemapper;
	private final BitmapIndexImpl bitmapIndex;

	private final int contiguousCommitCount;
	private final int recentCommitCount;
	private final int recentCommitSpan;
	private final int distantCommitSpan;
	private final int excessiveBranchCount;
	private final long inactiveBranchTimestamp;

	PackWriterBitmapPreparer(ObjectReader reader,
			PackBitmapIndexBuilder writeBitmaps, ProgressMonitor pm,
			Set<? extends ObjectId> want, PackConfig config)
					throws IOException {
		this.reader = reader;
		this.writeBitmaps = writeBitmaps;
		this.pm = pm;
		this.want = want;
		this.commitBitmapIndex = new BitmapIndexImpl(writeBitmaps);
		this.bitmapRemapper = PackBitmapIndexRemapper.newPackBitmapIndex(
				reader.getBitmapIndex(), writeBitmaps);
		this.bitmapIndex = new BitmapIndexImpl(bitmapRemapper);
		this.contiguousCommitCount = config.getBitmapContiguousCommitCount();
		this.recentCommitCount = config.getBitmapRecentCommitCount();
		this.recentCommitSpan = config.getBitmapRecentCommitSpan();
		this.distantCommitSpan = config.getBitmapDistantCommitSpan();
		this.excessiveBranchCount = config.getBitmapExcessiveBranchCount();
		long now = SystemReader.getInstance().getCurrentTime();
		long ageInSeconds = config.getBitmapInactiveBranchAgeInDays()
				* DAY_IN_SECONDS;
		this.inactiveBranchTimestamp = (now / 1000) - ageInSeconds;
	}

	/**
	 * Returns the commit objects for which bitmap indices should be built.
	 *
	 * @param expectedCommitCount
	 *            count of commits in the pack
	 * @param excludeFromBitmapSelection
	 *            commits that should be excluded from bitmap selection
	 * @return commit objects for which bitmap indices should be built
	 * @throws IncorrectObjectTypeException
	 *             if any of the processed objects is not a commit
	 * @throws IOException
	 *             on errors reading pack or index files
	 * @throws MissingObjectException
	 *             if an expected object is missing
	 */
	Collection<BitmapCommit> selectCommits(int expectedCommitCount,
			Set<? extends ObjectId> excludeFromBitmapSelection)
			throws IncorrectObjectTypeException, IOException,
			MissingObjectException {
		/*
		 * Thinking of bitmap indices as a cache, if we find bitmaps at or at a
		 * close ancestor to 'old' and 'new' when calculating old..new, then all
		 * objects can be calculated with minimal graph walking. A distribution
		 * that favors creating bitmaps for the most recent commits maximizes
		 * the cache hits for clients that are close to HEAD, which is the
		 * majority of calculations performed.
		 */
		try (RevWalk rw = new RevWalk(reader);
				RevWalk rw2 = new RevWalk(reader)) {
			pm.beginTask(JGitText.get().selectingCommits,
					ProgressMonitor.UNKNOWN);
			rw.setRetainBody(false);
			CommitSelectionHelper selectionHelper = captureOldAndNewCommits(rw,
					expectedCommitCount, excludeFromBitmapSelection);
			pm.endTask();

			// Add reused bitmaps from the previous GC pack's bitmap indices.
			// Currently they are always fully reused, even if their spans don't
			// match this run's PackConfig values.
			int newCommits = selectionHelper.getCommitCount();
			BlockList<BitmapCommit> selections = new BlockList<>(
					selectionHelper.reusedCommits.size()
							+ newCommits / recentCommitSpan + 1);
			for (BitmapCommit reuse : selectionHelper.reusedCommits) {
				selections.add(reuse);
			}

			if (newCommits == 0) {
				for (AnyObjectId id : selectionHelper.newWants) {
					selections.add(new BitmapCommit(id, false, 0));
				}
				return selections;
			}

			pm.beginTask(JGitText.get().selectingCommits, newCommits);
			int totalWants = want.size();
			BitmapBuilder seen = commitBitmapIndex.newBitmapBuilder();
			seen.or(selectionHelper.reusedCommitsBitmap);
			rw2.setRetainBody(false);
			rw2.setRevFilter(new NotInBitmapFilter(seen));

			// For each branch, do a revwalk to enumerate its commits. Exclude
			// both reused commits and any commits seen in a previous branch.
			// Then iterate through all new commits from oldest to newest,
			// selecting well-spaced commits in this branch.
			for (RevCommit rc : selectionHelper.newWantsByNewest) {
				BitmapBuilder tipBitmap = commitBitmapIndex.newBitmapBuilder();
				rw2.markStart((RevCommit) rw2.peel(rw2.parseAny(rc)));
				RevCommit rc2;
				while ((rc2 = rw2.next()) != null) {
					tipBitmap.addObject(rc2, Constants.OBJ_COMMIT);
				}
				int cardinality = tipBitmap.cardinality();
				seen.or(tipBitmap);

				// Within this branch, keep ordered lists of commits
				// representing chains in its history, where each chain is a
				// "sub-branch". Ordering commits by these chains makes for
				// fewer differences between consecutive selected commits, which
				// in turn provides better compression/on the run-length
				// encoding of the XORs between them.
				List<List<BitmapCommit>> chains = new ArrayList<>();

				// Mark the current branch as inactive if its tip commit isn't
				// recent and there are an excessive number of branches, to
				// prevent memory bloat of computing too many bitmaps for stale
				// branches.
				boolean isActiveBranch = true;
				if (totalWants > excessiveBranchCount && !isRecentCommit(rc)) {
					isActiveBranch = false;
				}

				// Insert bitmaps at the offsets suggested by the
				// nextSelectionDistance() heuristic. Only reuse bitmaps created
				// for more distant commits.
				int index = -1;
				int nextIn = nextSpan(cardinality);
				int nextFlg = nextIn == distantCommitSpan
						? PackBitmapIndex.FLAG_REUSE
						: 0;

				// For the current branch, iterate through all commits from
				// oldest to newest.
				for (RevCommit c : selectionHelper) {
					// Optimization: if we have found all the commits for this
					// branch, stop searching
					int distanceFromTip = cardinality - index - 1;
					if (distanceFromTip == 0) {
						break;
					}

					// Ignore commits that are not in this branch
					if (!tipBitmap.contains(c)) {
						continue;
					}

					index++;
					nextIn--;
					pm.update(1);

					// Always pick the items in wants, prefer merge commits.
					if (selectionHelper.newWants.remove(c)) {
						if (nextIn > 0) {
							nextFlg = 0;
						}
					} else {
						boolean stillInSpan = nextIn >= 0;
						boolean isMergeCommit = c.getParentCount() > 1;
						// Force selection if:
						// a) we have exhausted the window looking for merges
						// b) we are in the top commits of an active branch
						// c) we are at a branch tip
						boolean mustPick = (nextIn <= -recentCommitSpan)
								|| (isActiveBranch
										&& (distanceFromTip <= contiguousCommitCount))
								|| (distanceFromTip == 1); // most recent commit
						if (!mustPick && (stillInSpan || !isMergeCommit)) {
							continue;
						}
					}

					// This commit is selected.
					// Calculate where to look for the next one.
					int flags = nextFlg;
					nextIn = nextSpan(distanceFromTip);
					nextFlg = nextIn == distantCommitSpan
							? PackBitmapIndex.FLAG_REUSE
							: 0;

					// Create the commit bitmap for the current commit
					BitmapBuilder bitmap = commitBitmapIndex.newBitmapBuilder();
					rw.reset();
					rw.markStart(c);
					rw.setRevFilter(new AddUnseenToBitmapFilter(
							selectionHelper.reusedCommitsBitmap, bitmap));
					while (rw.next() != null) {
						// The filter adds the reachable commits to bitmap.
					}

					// Sort the commits by independent chains in this branch's
					// history, yielding better compression when building
					// bitmaps.
					List<BitmapCommit> longestAncestorChain = null;
					for (List<BitmapCommit> chain : chains) {
						BitmapCommit mostRecentCommit = chain
								.get(chain.size() - 1);
						if (bitmap.contains(mostRecentCommit)) {
							if (longestAncestorChain == null
									|| longestAncestorChain.size() < chain
											.size()) {
								longestAncestorChain = chain;
							}
						}
					}

					if (longestAncestorChain == null) {
						longestAncestorChain = new ArrayList<>();
						chains.add(longestAncestorChain);
					}
					longestAncestorChain.add(new BitmapCommit(c,
							!longestAncestorChain.isEmpty(), flags));
					writeBitmaps.addBitmap(c, bitmap, 0);
				}

				for (List<BitmapCommit> chain : chains) {
					selections.addAll(chain);
				}
			}
			writeBitmaps.clearBitmaps(); // Remove the temporary commit bitmaps.

			// Add the remaining peeledWant
			for (AnyObjectId remainingWant : selectionHelper.newWants) {
				selections.add(new BitmapCommit(remainingWant, false, 0));
			}

			pm.endTask();
			return selections;
		}
	}

	private boolean isRecentCommit(RevCommit revCommit) {
		return revCommit.getCommitTime() > inactiveBranchTimestamp;
	}

	/**
	 * A RevFilter that excludes the commits named in a bitmap from the walk.
	 * <p>
	 * If a commit is in {@code bitmap} then that commit is not emitted by the
	 * walk and its parents are marked as SEEN so the walk can skip them.  The
	 * bitmaps passed in have the property that the parents of any commit in
	 * {@code bitmap} are also in {@code bitmap}, so marking the parents as
	 * SEEN speeds up the RevWalk by saving it from walking down blind alleys
	 * and does not change the commits emitted.
	 */
	private static class NotInBitmapFilter extends RevFilter {
		private final BitmapBuilder bitmap;

		NotInBitmapFilter(BitmapBuilder bitmap) {
			this.bitmap = bitmap;
		}

		@Override
		public final boolean include(RevWalk rw, RevCommit c) {
			if (!bitmap.contains(c)) {
				return true;
			}
			for (RevCommit p : c.getParents()) {
				p.add(SEEN);
			}
			return false;
		}

		@Override
		public final NotInBitmapFilter clone() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean requiresCommitBody() {
			return false;
		}
	}

	/**
	 * Records which of the {@code wants} can be found in the previous GC pack's
	 * bitmap indices and which are new.
	 *
	 * @param rw
	 *            a {@link RevWalk} to find reachable objects in this repository
	 * @param expectedCommitCount
	 *            expected count of commits. The actual count may be less due to
	 *            unreachable garbage.
	 * @param excludeFromBitmapSelection
	 *            commits that should be excluded from bitmap selection
	 * @return a {@link CommitSelectionHelper} capturing which commits are
	 *         covered by a previous pack's bitmaps and which new commits need
	 *         bitmap coverage
	 * @throws IncorrectObjectTypeException
	 *             if any of the processed objects is not a commit
	 * @throws IOException
	 *             on errors reading pack or index files
	 * @throws MissingObjectException
	 *             if an expected object is missing
	 */
	private CommitSelectionHelper captureOldAndNewCommits(RevWalk rw,
			int expectedCommitCount,
			Set<? extends ObjectId> excludeFromBitmapSelection)
			throws IncorrectObjectTypeException, IOException,
			MissingObjectException {
		// Track bitmaps and commits from the previous GC pack bitmap indices.
		BitmapBuilder reuse = commitBitmapIndex.newBitmapBuilder();
		List<BitmapCommit> reuseCommits = new ArrayList<>();
		for (PackBitmapIndexRemapper.Entry entry : bitmapRemapper) {
			// More recent commits did not have the reuse flag set, so skip them
			if ((entry.getFlags() & FLAG_REUSE) != FLAG_REUSE) {
				continue;
			}
			RevObject ro = rw.peel(rw.parseAny(entry));
			if (!(ro instanceof RevCommit)) {
				continue;
			}

			RevCommit rc = (RevCommit) ro;
			reuseCommits.add(new BitmapCommit(rc, false, entry.getFlags()));
			if (!reuse.contains(rc)) {
				EWAHCompressedBitmap bitmap = bitmapRemapper.ofObjectType(
						bitmapRemapper.getBitmap(rc), Constants.OBJ_COMMIT);
				reuse.or(new CompressedBitmap(bitmap, commitBitmapIndex));
			}
		}

		// Add branch tips that are not represented in a previous pack's bitmap
		// indices. Set up a RevWalk to find new commits not in the old packs.
		List<RevCommit> newWantsByNewest = new ArrayList<>(want.size());
		Set<RevCommit> newWants = new HashSet<>(want.size());
		for (AnyObjectId objectId : want) {
			RevObject ro = rw.peel(rw.parseAny(objectId));
			if (!(ro instanceof RevCommit) || reuse.contains(ro)
					|| excludeFromBitmapSelection.contains(ro)) {
				continue;
			}

			RevCommit rc = (RevCommit) ro;
			rw.markStart(rc);
			newWants.add(rc);
			newWantsByNewest.add(rc);
		}

		// Create a list of commits in reverse order (older to newer) that are
		// not in the previous bitmap indices and are reachable.
		rw.setRevFilter(new NotInBitmapFilter(reuse));
		RevCommit[] commits = new RevCommit[expectedCommitCount];
		int pos = commits.length;
		RevCommit rc;
		while ((rc = rw.next()) != null && pos > 0) {
			commits[--pos] = rc;
			pm.update(1);
		}

		// Sort the new wants by reverse commit time.
		Collections.sort(newWantsByNewest, ORDER_BY_REVERSE_TIMESTAMP);
		return new CommitSelectionHelper(newWants, commits, pos,
				newWantsByNewest, reuse, reuseCommits);
	}

	/*-
	 * Returns the desired distance to the next bitmap based on the distance
	 * from the tip commit. Only differentiates recent from distant spans,
	 * selectCommits() handles the contiguous commits at the tip for active
	 * or inactive branches.
	 *
	 * A graph of this function looks like this, where
	 * the X axis is the distance from the tip commit and the Y axis is the
	 * bitmap selection distance.
	 *
	 * 5000                ____...
	 *                    /
	 *                  /
	 *                /
	 *              /
	 *  100  _____/
	 *       0  20100  25000
	 *
	 * Linear scaling between 20100 and 25000 prevents spans >100 for distances
	 * <20000 (otherwise, a span of 5000 would be returned for a distance of
	 * 21000, and the range 16000-20000 would have no selections).
	 */
	int nextSpan(int distanceFromTip) {
		if (distanceFromTip < 0) {
			throw new IllegalArgumentException();
		}

		// Commits more toward the start will have more bitmaps.
		if (distanceFromTip <= recentCommitCount) {
			return recentCommitSpan;
		}

		int next = Math.min(distanceFromTip - recentCommitCount,
				distantCommitSpan);
		return Math.max(next, recentCommitSpan);
	}

	BitmapWalker newBitmapWalker() {
		return new BitmapWalker(
				new ObjectWalk(reader), bitmapIndex, null);
	}

	/**
	 * A commit object for which a bitmap index should be built.
	 */
	static final class BitmapCommit extends ObjectId {
		private final boolean reuseWalker;
		private final int flags;

		BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags) {
			super(objectId);
			this.reuseWalker = reuseWalker;
			this.flags = flags;
		}

		boolean isReuseWalker() {
			return reuseWalker;
		}

		int getFlags() {
			return flags;
		}
	}

	/**
	 * Container for state used in the first phase of selecting commits, which
	 * walks all of the reachable commits via the branch tips that are not
	 * covered by a previous pack's bitmaps ({@code newWants}) and stores them
	 * in {@code newCommitsByOldest}. {@code newCommitsByOldest} is initialized
	 * with an expected size of all commits, but may be smaller if some commits
	 * are unreachable and/or some commits are covered by a previous pack's
	 * bitmaps. {@code commitStartPos} will contain a positive offset to either
	 * the root commit or the oldest commit not covered by previous bitmaps.
	 */
	private static final class CommitSelectionHelper implements Iterable<RevCommit> {
		final Set<? extends ObjectId> newWants;

		final List<RevCommit> newWantsByNewest;
		final BitmapBuilder reusedCommitsBitmap;

		final List<BitmapCommit> reusedCommits;

		final RevCommit[] newCommitsByOldest;

		final int newCommitStartPos;

		CommitSelectionHelper(Set<? extends ObjectId> newWants,
				RevCommit[] commitsByOldest, int commitStartPos,
				List<RevCommit> newWantsByNewest,
				BitmapBuilder reusedCommitsBitmap,
				List<BitmapCommit> reuse) {
			this.newWants = newWants;
			this.newCommitsByOldest = commitsByOldest;
			this.newCommitStartPos = commitStartPos;
			this.newWantsByNewest = newWantsByNewest;
			this.reusedCommitsBitmap = reusedCommitsBitmap;
			this.reusedCommits = reuse;
		}

		@Override
		public Iterator<RevCommit> iterator() {
			// Member variables referenced by this iterator will have synthetic
			// accessors generated for them if they are made private.
			return new Iterator<RevCommit>() {
				int pos = newCommitStartPos;

				@Override
				public boolean hasNext() {
					return pos < newCommitsByOldest.length;
				}

				@Override
				public RevCommit next() {
					return newCommitsByOldest[pos++];
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}

		int getCommitCount() {
			return newCommitsByOldest.length - newCommitStartPos;
		}
	}
}
