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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.googlecode.javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexRemapper;
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
	private final BitmapIndexImpl commitBitmapIndex;
	private final PackBitmapIndexRemapper bitmapRemapper;
	private final BitmapIndexImpl bitmapIndex;
	private final int minCommits = 100;
	private final int maxCommits = 5000;

	PackWriterBitmapPreparer(ObjectReader reader,
			PackBitmapIndexBuilder writeBitmaps, ProgressMonitor pm,
			Set<? extends ObjectId> want) throws IOException {
		this.reader = reader;
		this.writeBitmaps = writeBitmaps;
		this.pm = pm;
		this.want = want;
		this.commitBitmapIndex = new BitmapIndexImpl(writeBitmaps);
		this.bitmapRemapper = PackBitmapIndexRemapper.newPackBitmapIndex(
				reader.getBitmapIndex(), writeBitmaps);
		this.bitmapIndex = new BitmapIndexImpl(bitmapRemapper);
	}

	Collection<BitmapCommit> doCommitSelection(int expectedNumCommits)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		pm.beginTask(JGitText.get().selectingCommits, ProgressMonitor.UNKNOWN);
		RevWalk rw = new RevWalk(reader);
		WalkResult result = findPaths(rw, expectedNumCommits);
		pm.endTask();

		int totCommits = result.commitsByOldest.length - result.commitStartPos;
		BlockList<BitmapCommit> selections = new BlockList<BitmapCommit>(
				totCommits / minCommits + 1);
		for (BitmapCommit reuse : result.reuse)
			selections.add(reuse);

		if (totCommits == 0) {
			for (AnyObjectId id : result.peeledWant)
				selections.add(new BitmapCommit(id, false, 0));
			return selections;
		}

		pm.beginTask(JGitText.get().selectingCommits, totCommits);

		for (BitmapBuilder bitmapableCommits : result.paths) {
			int cardinality = bitmapableCommits.cardinality();

			List<List<BitmapCommit>> running = new ArrayList<
					List<BitmapCommit>>();

			// Insert bitmaps at the offsets suggested by the
			// nextSelectionDistance() heuristic.
			int index = -1;
			int nextIn = nextSelectionDistance(0, cardinality);
			int nextFlg = nextIn == maxCommits ? PackBitmapIndex.FLAG_REUSE : 0;
			boolean mustPick = nextIn == 0;
			for (RevCommit c : result) {
				if (!bitmapableCommits.contains(c))
					continue;

				index++;
				nextIn--;
				pm.update(1);

				// Always pick the items in want and prefer merge commits.
				if (result.peeledWant.remove(c)) {
					if (nextIn > 0)
						nextFlg = 0;
				} else if (!mustPick && ((nextIn > 0)
						|| (c.getParentCount() <= 1 && nextIn > -minCommits))) {
					continue;
				}

				int flags = nextFlg;
				nextIn = nextSelectionDistance(index, cardinality);
				nextFlg = nextIn == maxCommits ? PackBitmapIndex.FLAG_REUSE : 0;
				mustPick = nextIn == 0;

				BitmapBuilder fullBitmap = commitBitmapIndex.newBitmapBuilder();
				rw.reset();
				rw.markStart(c);
				for (AnyObjectId objectId : result.reuse)
					rw.markUninteresting(rw.parseCommit(objectId));
				rw.setRevFilter(
						PackWriterBitmapWalker.newRevFilter(null, fullBitmap));

				while (rw.next() != null) {
					// Work is done in the RevFilter.
				}

				List<List<BitmapCommit>> matches = new ArrayList<
						List<BitmapCommit>>();
				for (List<BitmapCommit> list : running) {
					BitmapCommit last = list.get(list.size() - 1);
					if (fullBitmap.contains(last))
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
				match.add(new BitmapCommit(c, !match.isEmpty(), flags));
				writeBitmaps.addBitmap(c, fullBitmap, 0);
			}

			for (List<BitmapCommit> list : running)
				selections.addAll(list);
		}
		writeBitmaps.clearBitmaps(); // Remove the temporary commit bitmaps.

		// Add the remaining peeledWant
		for (AnyObjectId remainingWant : result.peeledWant)
			selections.add(new BitmapCommit(remainingWant, false, 0));

		pm.endTask();
		return selections;
	}

	private WalkResult findPaths(RevWalk rw, int expectedNumCommits)
			throws MissingObjectException, IOException {
		BitmapBuilder reuseBitmap = commitBitmapIndex.newBitmapBuilder();
		List<BitmapCommit> reuse = new ArrayList<BitmapCommit>();
		for (PackBitmapIndexRemapper.Entry entry : bitmapRemapper) {
			if ((entry.getFlags() & FLAG_REUSE) != FLAG_REUSE)
				continue;

			RevObject ro = rw.peel(rw.parseAny(entry));
			if (ro instanceof RevCommit) {
				RevCommit rc = (RevCommit) ro;
				reuse.add(new BitmapCommit(rc, false, entry.getFlags()));
				rw.markUninteresting(rc);

				EWAHCompressedBitmap bitmap = bitmapRemapper.ofObjectType(
						bitmapRemapper.getBitmap(rc), Constants.OBJ_COMMIT);
				writeBitmaps.addBitmap(rc, bitmap, 0);
				reuseBitmap.add(rc, Constants.OBJ_COMMIT);
			}
		}
		writeBitmaps.clearBitmaps(); // Remove temporary bitmaps

		// Do a RevWalk by commit time descending. Keep track of all the paths
		// from the wants.
		List<BitmapBuilder> paths = new ArrayList<BitmapBuilder>(want.size());
		Set<RevCommit> peeledWant = new HashSet<RevCommit>(want.size());
		for (AnyObjectId objectId : want) {
			RevObject ro = rw.peel(rw.parseAny(objectId));
			if (ro instanceof RevCommit && !reuseBitmap.contains(ro)) {
				RevCommit rc = (RevCommit) ro;
				peeledWant.add(rc);
				rw.markStart(rc);

				BitmapBuilder bitmap = commitBitmapIndex.newBitmapBuilder();
				bitmap.or(reuseBitmap);
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

		// Remove the reused bitmaps from the paths
		if (!reuse.isEmpty())
			for (BitmapBuilder bitmap : paths)
				bitmap.andNot(reuseBitmap);

		// Sort the paths
		List<BitmapBuilder> distinctPaths = new ArrayList<BitmapBuilder>(paths.size());
		while (!paths.isEmpty()) {
			Collections.sort(paths, BUILDER_BY_CARDINALITY_DSC);
			BitmapBuilder largest = paths.remove(0);
			distinctPaths.add(largest);

			// Update the remaining paths, by removing the objects from
			// the path that was just added.
			for (int i = paths.size() - 1; i >= 0; i--)
				paths.get(i).andNot(largest);
		}

		return new WalkResult(peeledWant, commits, pos, distinctPaths, reuse);
	}

	private int nextSelectionDistance(int idx, int cardinality) {
		if (idx > cardinality)
			throw new IllegalArgumentException();
		int idxFromStart = cardinality - idx;
		int mustRegionEnd = 100;
		if (idxFromStart <= mustRegionEnd)
			return 0;

		// Commits more toward the start will have more bitmaps.
		int minRegionEnd = 20000;
		if (idxFromStart <= minRegionEnd)
			return Math.min(idxFromStart - mustRegionEnd, minCommits);

		// Commits more toward the end will have fewer.
		int next = Math.min(idxFromStart - minRegionEnd, maxCommits);
		return Math.max(next, minCommits);
	}

	PackWriterBitmapWalker newBitmapWalker() {
		return new PackWriterBitmapWalker(
				new ObjectWalk(reader), bitmapIndex, null);
	}

	static final class BitmapCommit extends ObjectId {
		private final boolean reuseWalker;
		private final int flags;

		private BitmapCommit(
				AnyObjectId objectId, boolean reuseWalker, int flags) {
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

	private static final class WalkResult implements Iterable<RevCommit> {
		private final Set<? extends ObjectId> peeledWant;
		private final RevCommit[] commitsByOldest;
		private final int commitStartPos;
		private final List<BitmapBuilder> paths;
		private final Iterable<BitmapCommit> reuse;

		private WalkResult(Set<? extends ObjectId> peeledWant,
				RevCommit[] commitsByOldest, int commitStartPos,
				List<BitmapBuilder> paths, Iterable<BitmapCommit> reuse) {
			this.peeledWant = peeledWant;
			this.commitsByOldest = commitsByOldest;
			this.commitStartPos = commitStartPos;
			this.paths = paths;
			this.reuse = reuse;
		}

		public Iterator<RevCommit> iterator() {
			return new Iterator<RevCommit>() {
				int pos = commitStartPos;

				public boolean hasNext() {
					return pos < commitsByOldest.length;
				}

				public RevCommit next() {
					return commitsByOldest[pos++];
				}

				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
