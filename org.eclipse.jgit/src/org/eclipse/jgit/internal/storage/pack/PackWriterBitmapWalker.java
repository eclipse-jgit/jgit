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

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/** Helper class for PackWriter to do ObjectWalks with pack index bitmaps. */
final class PackWriterBitmapWalker {

	private final ObjectWalk walker;

	private final BitmapIndex bitmapIndex;

	private final ProgressMonitor pm;

	PackWriterBitmapWalker(
			ObjectWalk walker, BitmapIndex bitmapIndex, ProgressMonitor pm) {
		this.walker = walker;
		this.bitmapIndex = bitmapIndex;
		this.pm = (pm == null) ? NullProgressMonitor.INSTANCE : pm;
	}

	BitmapBuilder findObjects(Set<? extends ObjectId> start, BitmapBuilder seen)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final BitmapBuilder bitmapResult = bitmapIndex.newBitmapBuilder();

		for (ObjectId obj : start) {
			Bitmap bitmap = bitmapIndex.getBitmap(obj);
			if (bitmap != null)
				bitmapResult.or(bitmap);
		}

		boolean marked = false;
		for (ObjectId obj : start) {
			if (!bitmapResult.contains(obj)) {
				walker.markStart(walker.parseAny(obj));
				marked = true;
			}
		}

		if (marked) {
			walker.setRevFilter(newRevFilter(seen, bitmapResult));

			while (walker.next() != null) {
				// Iterate through all of the commits. The BitmapRevFilter does
				// the work.
				pm.update(1);
			}

			RevObject ro;
			while ((ro = walker.nextObject()) != null) {
				bitmapResult.add(ro, ro.getType());
				pm.update(1);
			}
		}

		return bitmapResult;
	}

	void reset() {
		walker.reset();
	}

	static RevFilter newRevFilter(
			final BitmapBuilder seen, final BitmapBuilder bitmapResult) {
		if (seen != null) {
			return new BitmapRevFilter() {
				protected boolean load(RevCommit cmit) {
					if (seen.contains(cmit))
						return false;
					return bitmapResult.add(cmit, Constants.OBJ_COMMIT);
				}
			};
		}
		return new BitmapRevFilter() {
			@Override
			protected boolean load(RevCommit cmit) {
				return bitmapResult.add(cmit, Constants.OBJ_COMMIT);
			}
		};
	}

	static abstract class BitmapRevFilter extends RevFilter {
		protected abstract boolean load(RevCommit cmit);

		@Override
		public final boolean include(RevWalk walker, RevCommit cmit) {
			if (load(cmit))
				return true;
			for (RevCommit p : cmit.getParents())
				p.add(RevFlag.SEEN);
			return false;
		}

		@Override
		public final RevFilter clone() {
			return this;
		}

		@Override
		public final boolean requiresCommitBody() {
			return false;
		}
	}
}
