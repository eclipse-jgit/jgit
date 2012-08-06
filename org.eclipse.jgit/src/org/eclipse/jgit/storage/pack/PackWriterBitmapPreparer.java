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

package org.eclipse.jgit.storage.pack;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.util.BlockList;

/** Helper class for the PackWriter to select commits for pack index bitmaps. */
class PackWriterBitmapPreparer {

	private static final Comparator<BitmapBuilder> BUILDER_BY_CARDINALITY_DSC =
			new Comparator<BitmapBuilder>() {
		public int compare(BitmapBuilder a, BitmapBuilder b) {
			return Integer.signum(b.cardinality() - a.cardinality());
		}
	};

	private final ObjectReader reader;

	private final ProgressMonitor pm;

	private final Set<? extends ObjectId> want;

	private final PackBitmapIndexBuilder writeBitmaps;

	private final BitmapIndexImpl bitmapIndex;

	private final int minCommits = 100;

	private final int maxCommits = 10000;

	PackWriterBitmapPreparer(ObjectReader reader,
			PackBitmapIndexBuilder writeBitmaps, ProgressMonitor pm,
			Set<? extends ObjectId> want) {
		this.reader = reader;
		this.writeBitmaps = writeBitmaps;
		this.pm = pm;
		this.want = want;
		this.bitmapIndex = new BitmapIndexImpl(writeBitmaps);
	}

	Collection<BitmapCommit> doCommitSelection(int expectedNumCommits)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		pm.beginTask(JGitText.get().selectingCommits, expectedNumCommits);
		RevWalk rw = new RevWalk(reader);
		WalkResult result = findPaths(rw, expectedNumCommits);
		pm.endTask();

		int totalCommits = 0;
		for (BitmapBuilder bitmapableCommits : result.paths)
			totalCommits += bitmapableCommits.cardinality();

		if (totalCommits == 0)
			return Collections.emptyList();

		pm.beginTask(JGitText.get().selectingCommits, totalCommits);

		BlockList<BitmapCommit> selections = new BlockList<BitmapCommit>(
				totalCommits / minCommits + 1);
		for (BitmapBuilder bitmapableCommits : result.paths) {
			int cardinality = bitmapableCommits.cardinality();

			List<List<BitmapCommit>> running = new ArrayList<
					List<BitmapCommit>>();

			// Insert bitmaps at the offsets suggested by the
			// nextSelectionDistance() heuristic.
			int index = -1;
			int nextIn = nextSelectionDistance(0, cardinality);
			for (RevCommit c : result.commitsByOldest) {
				if (!bitmapableCommits.contains(c))
					continue;

				index++;
				nextIn--;
				pm.update(1);

				// Always pick the items in want and prefer merge commits.
				boolean mustPick = result.peeledWant.remove(c);
				if (!mustPick && ((nextIn > 0)
						|| (c.getParentCount() <= 1 && nextIn > -minCommits)))
					continue;


				nextIn = nextSelectionDistance(index, cardinality);

				BitmapBuilder fullBitmap = bitmapIndex.newBitmapBuilder();
				rw.reset();
				rw.markStart(c);
				rw.setRevFilter(
						PackWriterBitmapWalker.newRevFilter(null, fullBitmap));

				while (rw.next() != null) {
					// Work is done in the RevFilter.
				}

				List<List<BitmapCommit>> matches = new ArrayList<
						List<BitmapCommit>>();
				for (List<BitmapCommit> list : running) {
					BitmapCommit last = list.get(list.size() - 1);
					if (fullBitmap.contains(last.getObjectId()))
						matches.add(list);
				}

				List<BitmapCommit> match;
				if (matches.isEmpty()) {
					match = new ArrayList<BitmapCommit>();
					running.add(match);
				} else {
					match = matches.get(0);
					// Append to longest
					for (List<BitmapCommit> list : matches) {
						if (list.size() > match.size())
							match = list;
					}
				}
				match.add(new BitmapCommit(c, !match.isEmpty()));
				writeBitmaps.addBitmap(c, fullBitmap);
			}

			for (List<BitmapCommit> list : running)
				selections.addAll(list);
		}

		writeBitmaps.clearBitmaps(); // Remove the temporary commit bitmaps.

		// Add the remaining peeledWant
		for (AnyObjectId remainingWant : result.peeledWant)
			selections.add(new BitmapCommit(remainingWant, false));

		pm.endTask();
		return selections;
	}

	private WalkResult findPaths(RevWalk rw, int expectedNumCommits)
			throws MissingObjectException, IOException {
		// Do a RevWalk by commit time descending. Keep track of all the paths
		// from the wants.
		List<BitmapBuilder> paths = new ArrayList<BitmapBuilder>(want.size());
		Set<RevCommit> peeledWant = new HashSet<RevCommit>(want.size());
		for (AnyObjectId objectId : want) {
			RevObject ro = rw.peel(rw.parseAny(objectId));
			if (ro instanceof RevCommit) {
				RevCommit rc = (RevCommit) ro;
				peeledWant.add(rc);
				rw.markStart(rc);

				BitmapBuilder bitmap = bitmapIndex.newBitmapBuilder();
				bitmap.add(rc, Constants.OBJ_COMMIT);
				paths.add(bitmap);
			}
		}

		// Update the paths from the wants and create a list of commits in
		// reverse iteration order.
		RevCommit[] commits = new RevCommit[expectedNumCommits];
		int pos = commits.length;
		RevCommit rc;
		while ((rc = rw.next()) != null) {
			commits[--pos] = rc;
			for (BitmapBuilder path : paths) {
				if (path.contains(rc)) {
					for (RevCommit c : rc.getParents())
						path.add(c, Constants.OBJ_COMMIT);
				}
			}

			pm.update(1);
		}

		if (pos != 0)
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().expectedGot, 0, String.valueOf(pos)));

		// Sort the paths
		List<BitmapBuilder> distinctPaths = new ArrayList<BitmapBuilder>(paths.size());
		while (!paths.isEmpty()) {
			Collections.sort(paths, BUILDER_BY_CARDINALITY_DSC);
			BitmapBuilder largest = paths.remove(0);
			int minSize = 2 * minCommits;
			if (largest.cardinality() < minSize)
				break;
			distinctPaths.add(largest);

			// Update the remaining paths, by removing the objects from
			// the path that was just added.
			for (int i = paths.size() - 1; i >= 0; i--) {
				if (paths.get(i).andNot(largest).cardinality() < minSize)
					paths.remove(i);
			}
		}

		return new WalkResult(peeledWant, commits, distinctPaths);
	}


	private int nextSelectionDistance(int idx, int cardinality) {
		if (idx > cardinality)
			throw new IllegalArgumentException();
		int idxFromStart = cardinality - idx;
		int shift = 200 * minCommits;
		// Commits more toward the start will have more bitmaps.
		if (cardinality <= shift || idxFromStart <= shift)
			return minCommits;

		// Later commits spacing grows linearly as we get closer to the end.
		int shiftedCardinality = cardinality - shift;
		long shiftedIdxFromStart = idxFromStart - shift;
		long numerator = shiftedIdxFromStart * (maxCommits - minCommits);
		int minDesired = (int) (numerator / shiftedCardinality + minCommits);
		int minAllowed = Math.max(shiftedCardinality / 2, minCommits);
		return Math.min(minDesired, minAllowed);
	}

	PackWriterBitmapWalker newBitmapWalker() {
		return new PackWriterBitmapWalker(
				new ObjectWalk(reader), bitmapIndex, null);
	}

	static final class BitmapCommit {
		private final ObjectId objectId;

		private final boolean reuseWalker;

		private BitmapCommit(AnyObjectId objectId, boolean reuseWalker) {
			this.objectId = objectId.toObjectId();
			this.reuseWalker = reuseWalker;
		}

		ObjectId getObjectId() {
			return objectId;
		}

		boolean isReuseWalker() {
			return reuseWalker;
		}
	}

	private static final class WalkResult {
		private final RevCommit[] commitsByOldest;

		private final List<BitmapBuilder> paths;

		private final Set<? extends ObjectId> peeledWant;

		private WalkResult(Set<? extends ObjectId> peeledWant,
				RevCommit[] commitsByOldest, List<BitmapBuilder> paths) {
			this.peeledWant = peeledWant;
			this.commitsByOldest = commitsByOldest;
			this.paths = paths;
		}
	}
}
