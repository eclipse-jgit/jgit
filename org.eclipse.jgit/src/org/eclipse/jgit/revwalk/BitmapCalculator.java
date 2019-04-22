/*
 * Copyright (C) 2019, Google LLC
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

package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.revwalk.AddToBitmapFilter;
import org.eclipse.jgit.internal.revwalk.AddUnseenToBitmapFilter;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Calculate the reachability bitmap of a commit.
 * <p>
 * If the commit doesn't have a precalculated bitmap, the calculator walks the
 * commit graph until finding a parent with one. The result is the union of
 * parent's bitmap, plus the commits found on the way.
 * <p>
 * Precalculated bitmaps contains reachability to ALL objects. The bitmap
 * calculated here has the closest precalculated bitmap (with all objects) plus
 * the commits (and ONLY the commits) found in the middle.
 * <p>
 * To get a bitmap of ALL objects reachable from a commit, see
 * {@link BitmapWalker}.
 *
 * @since 5.5
 */
final class BitmapCalculator {

	private final RevWalk walk;

	private final BitmapIndex bitmapIndex;

	private final ProgressMonitor pm;

	private long countOfBitmapIndexMisses;

	private final BitmapWalkHook preWalkHook;

	private final BitmapWalkHook postWalkHook;

	/**
	 * Hook that can be invoked before or after the walk building the bitmap of
	 * a commit that doesn't have one.
	 * <p>
	 * This is intended to be used only by {@link BitmapWalker}.
	 */
	interface BitmapWalkHook {
		/**
		 * Hooked invoked before and after traversing the tree building a commit
		 * bitmap.
		 *
		 * @param walk
		 *            revwalk in use.
		 * @param bitmapResult
		 *            bitmap calculated so far.
		 * @param pm
		 *            progress monitor
		 * @throws IOException
		 */
		void run(RevWalk walk, BitmapBuilder bitmapResult,
				ProgressMonitor pm) throws IOException;
	}

	private static final BitmapWalkHook NULL_BITMAP_HOOK = new BitmapWalkHook() {
		@Override
		public void run(RevWalk walk, BitmapBuilder bitmapResult,
				ProgressMonitor pm) {
			// Do-nothing hook
		}
	};

	/**
	 * Create a BitmapCalculator.
	 *
	 * @param walk
	 *            walker to use when traversing the commit graph.
	 * @param bitmapIndex
	 *            index to obtain bitmaps from.
	 * @param pm
	 *            progress monitor to report progress on.
	 */
	public BitmapCalculator(RevWalk walk, BitmapIndex bitmapIndex,
			ProgressMonitor pm) {
		this(walk, bitmapIndex, pm, NULL_BITMAP_HOOK, NULL_BITMAP_HOOK);
	}

	/**
	 * Create a BitmapCalculator with hooks to fine-tune the walk that runs when
	 * a commit doesn't have a bitmap.
	 * <p>
	 * This is intended to be used only by {@link BitmapWalker}. See
	 * {@link BitmapWalkHook} for details.
	 *
	 * @param walk
	 *            walker to use when traversing the commit graph.
	 * @param bitmapIndex
	 *            index to obtain bitmaps from.
	 * @param pm
	 *            progress monitor to report progress on.
	 * @param preWalkHook
	 *            invoked just before starting to walk from objects without
	 *            bitmap.
	 * @param postWalkHook
	 *            invoked after the regular walk is done and the commit bitmap
	 *            is ready.
	 */
	BitmapCalculator(RevWalk walk, BitmapIndex bitmapIndex,
			ProgressMonitor pm, BitmapWalkHook preWalkHook,
			BitmapWalkHook postWalkHook) {
		this.walk = walk;
		this.bitmapIndex = bitmapIndex;
		this.pm = (pm == null) ? NullProgressMonitor.INSTANCE : pm;
		this.preWalkHook = preWalkHook;
		this.postWalkHook = postWalkHook;
	}

	/**
	 * Return the number of commits that had to be walked because they were not
	 * covered by a bitmap.
	 *
	 * @return the number of commits that had to be walked because they were not
	 *         covered by a bitmap.
	 */
	public long getCountOfBitmapIndexMisses() {
		return countOfBitmapIndexMisses;
	}

	/**
	 * Return, as a bitmap, the commits reachable from the commits in start.
	 *
	 * @param start
	 *            the objects to start the object traversal from.
	 * @param seen
	 *            the objects to skip if encountered during traversal.
	 * @param ignoreMissing
	 *            true to ignore missing objects, false otherwise.
	 * @return as a bitmap, the objects reachable from the objects in start.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object supplied is not available from the object
	 *             database. This usually indicates the supplied object is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to
	 *             {@link org.eclipse.jgit.revwalk.RevWalk#lookupAny(AnyObjectId, int)}.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually the type of the instance
	 *             passed in. This usually indicates the caller used the wrong
	 *             type in a
	 *             {@link org.eclipse.jgit.revwalk.RevWalk#lookupAny(AnyObjectId, int)}
	 *             call.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public BitmapBuilder getBitmapFor(Iterable<? extends ObjectId> start,
			BitmapBuilder seen, boolean ignoreMissing)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (!ignoreMissing) {
			return walkBuildingBitmap(start, seen, false);
		}

		try {
			return walkBuildingBitmap(start, seen, true);
		} catch (MissingObjectException ignore) {
			// An object reachable from one of the "start"s is missing.
			// Walk from the "start"s one at a time so it can be excluded.
		}

		final BitmapBuilder result = bitmapIndex.newBitmapBuilder();
		for (ObjectId obj : start) {
			Bitmap bitmap = bitmapIndex.getBitmap(obj);
			if (bitmap != null) {
				result.or(bitmap);
			}
		}

		for (ObjectId obj : start) {
			if (result.contains(obj)) {
				continue;
			}
			try {
				result.or(walkBuildingBitmap(Arrays.asList(obj), result, false));
			} catch (MissingObjectException ignore) {
				// An object reachable from this "start" is missing.
				//
				// This can happen when the client specified a "have" line
				// pointing to an object that is present but unreachable:
				// "git prune" and "git fsck" only guarantee that the object
				// database will continue to contain all objects reachable
				// from a ref and does not guarantee connectivity for other
				// objects in the object database.
				//
				// In this situation, skip the relevant "start" and move on
				// to the next one.
				//
				// TODO(czhen): Make walkBuildingBitmap resume the walk instead
				// once RevWalk and ObjectWalk support that.
			}
		}
		return result;
	}

	private BitmapBuilder walkBuildingBitmap(Iterable<? extends ObjectId> start,
			BitmapBuilder seen, boolean ignoreMissingStart)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		walk.reset();
		final BitmapBuilder bitmapResult = bitmapIndex.newBitmapBuilder();

		for (ObjectId obj : start) {
			Bitmap bitmap = bitmapIndex.getBitmap(obj);
			if (bitmap != null)
				bitmapResult.or(bitmap);
		}

		boolean marked = false;
		for (ObjectId obj : start) {
			try {
				if (!bitmapResult.contains(obj)) {
					walk.markStart(walk.parseCommit(obj));
					marked = true;
				}
			} catch (MissingObjectException e) {
				if (ignoreMissingStart)
					continue;
				throw e;
			}
		}

		if (marked) {
			if (seen == null) {
				walk.setRevFilter(new AddToBitmapFilter(bitmapResult));
			} else {
				walk.setRevFilter(
						new AddUnseenToBitmapFilter(seen, bitmapResult));
			}

			preWalkHook.run(walk, bitmapResult, pm);

			while (walk.next() != null) {
				// Iterate through all of the commits. The BitmapRevFilter does
				// the work.
				//
				// filter.include returns true for commits that do not have
				// a bitmap in bitmapIndex and are not reachable from a
				// bitmap in bitmapIndex encountered earlier in the walk.
				// Thus the number of commits returned by next() measures how
				// much history was traversed without being able to make use
				// of bitmaps.
				pm.update(1);
				countOfBitmapIndexMisses++;
			}

			postWalkHook.run(walk, bitmapResult, pm);
		}

		return bitmapResult;
	}
}
