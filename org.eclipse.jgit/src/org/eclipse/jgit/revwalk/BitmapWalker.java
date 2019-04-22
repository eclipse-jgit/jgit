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

package org.eclipse.jgit.revwalk;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.BitmapCalculator.MissingStartHook;
import org.eclipse.jgit.revwalk.filter.ObjectFilter;

/**
 * Helper class to do ObjectWalks with pack index bitmaps.
 *
 * @since 4.10
 */
public final class BitmapWalker {

	private final BitmapCalculator bitmapCalculator;

	private static final MissingStartHook addAllObjectsHook = new MissingStartHook() {
		@Override
		public void preWalk(RevWalk walk, BitmapBuilder bitmapResult,
				ProgressMonitor pm) {
			// Do not add duplicates to the walk queue
			((ObjectWalk) walk)
					.setObjectFilter(new BitmapObjectFilter(bitmapResult));
		}

		@Override
		public void postWalk(RevWalk walk, BitmapBuilder bitmapResult,
				ProgressMonitor pm) throws IOException {
			// Add all found objects to the bitmap
			ObjectWalk objectWalk = (ObjectWalk) walk;
			RevObject ro;
			while ((ro = objectWalk.nextObject()) != null) {
				bitmapResult.addObject(ro, ro.getType());
				pm.update(1);
			}
		}
	};

	/**
	 * Create a BitmapWalker.
	 *
	 * @param walker
	 *            walker to use when traversing the object graph.
	 * @param bitmapIndex
	 *            index to obtain bitmaps from.
	 * @param pm
	 *            progress monitor to report progress on.
	 */
	public BitmapWalker(ObjectWalk walker, BitmapIndex bitmapIndex,
			ProgressMonitor pm) {
		this.bitmapCalculator = new BitmapCalculator(walker, bitmapIndex, pm,
				addAllObjectsHook);
	}

	/**
	 * Return the number of objects that had to be walked because they were not
	 * covered by a bitmap.
	 *
	 * @return the number of objects that had to be walked because they were not
	 *         covered by a bitmap.
	 */
	public long getCountOfBitmapIndexMisses() {
		return bitmapCalculator.getCountOfBitmapIndexMisses();
	}

	/**
	 * Return, as a bitmap, the objects reachable from the objects in start.
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
	public BitmapBuilder findObjects(Iterable<? extends ObjectId> start,
			BitmapBuilder seen, boolean ignoreMissing)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return this.bitmapCalculator.getBitmapFor(start, seen, ignoreMissing);
	}

	/**
	 * Filter that excludes objects already in the given bitmap.
	 */
	static class BitmapObjectFilter extends ObjectFilter {
		private final BitmapBuilder bitmap;

		BitmapObjectFilter(BitmapBuilder bitmap) {
			this.bitmap = bitmap;
		}

		@Override
		public final boolean include(ObjectWalk walker, AnyObjectId objid)
			throws MissingObjectException, IncorrectObjectTypeException,
			       IOException {
			return !bitmap.contains(objid);
		}
	}
}
