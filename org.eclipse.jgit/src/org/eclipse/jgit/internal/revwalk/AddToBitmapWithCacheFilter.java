/*
 * Copyright (c) 2020, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

/**
 * A RevFilter that adds the visited commits to {@code bitmap} as a side effect.
 * <p>
 * When the walk hits a commit that is the same as {@code cachedCommit} or is
 * part of {@code bitmap}'s BitmapIndex, that entire bitmap is ORed into
 * {@code bitmap} and the commit and its parents are marked as SEEN so that the
 * walk does not have to visit its ancestors. This ensures the walk is very
 * short if there is good bitmap coverage.
 */
public class AddToBitmapWithCacheFilter extends RevFilter {
	private final AnyObjectId cachedCommit;

	private final Bitmap cachedBitmap;

	private final BitmapBuilder bitmap;

	/**
	 * Create a filter with a cached BitmapCommit that adds visited commits to
	 * the given bitmap.
	 *
	 * @param cachedCommit
	 *            the cached commit
	 * @param cachedBitmap
	 *            the bitmap corresponds to {@code cachedCommit}}
	 * @param bitmap
	 *            bitmap to write visited commits to
	 */
	public AddToBitmapWithCacheFilter(AnyObjectId cachedCommit,
			Bitmap cachedBitmap,
			BitmapBuilder bitmap) {
		this.cachedCommit = cachedCommit;
		this.cachedBitmap = cachedBitmap;
		this.bitmap = bitmap;
	}

	/** {@inheritDoc} */
	@Override
	public final boolean include(RevWalk rw, RevCommit c) {
		Bitmap visitedBitmap;

		if (bitmap.contains(c)) {
			// already included
		} else if ((visitedBitmap = bitmap.getBitmapIndex()
				.getBitmap(c)) != null) {
			bitmap.or(visitedBitmap);
		} else if (cachedCommit.equals(c)) {
			bitmap.or(cachedBitmap);
		} else {
			bitmap.addObject(c, Constants.OBJ_COMMIT);
			return true;
		}

		for (RevCommit p : c.getParents()) {
			p.add(RevFlag.SEEN);
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public final RevFilter clone() {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public final boolean requiresCommitBody() {
		return false;
	}
}

