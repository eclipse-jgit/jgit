/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.revwalk;

import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

/**
 * A RevFilter that adds the visited commits to {@code bitmap} as a side
 * effect.
 * <p>
 * When the walk hits a commit that is part of {@code bitmap}'s
 * BitmapIndex, that entire bitmap is ORed into {@code bitmap} and the
 * commit and its parents are marked as SEEN so that the walk does not
 * have to visit its ancestors.  This ensures the walk is very short if
 * there is good bitmap coverage.
 * <p>
 * Commits named in {@code seen} are considered already seen.  If one is
 * encountered, that commit and its parents will be marked with the SEEN
 * flag to prevent the walk from visiting its ancestors.
 */
public class AddUnseenToBitmapFilter extends RevFilter {
	private final BitmapBuilder seen;
	private final BitmapBuilder bitmap;

	/**
	 * Create a filter that adds visited commits to the given bitmap, but does not walk
	 * through the objects in {@code seen}.
	 *
	 * @param seen objects that are already seen
	 * @param bitmap bitmap to write visited commits to
	 */
	public AddUnseenToBitmapFilter(BitmapBuilder seen, BitmapBuilder bitmap) {
		this.seen = seen;
		this.bitmap = bitmap;
	}

	/** {@inheritDoc} */
	@Override
	public final boolean include(RevWalk walker, RevCommit cmit) {
		Bitmap visitedBitmap;

		if (seen.contains(cmit) || bitmap.contains(cmit)) {
			// already seen or included
		} else if ((visitedBitmap = bitmap.getBitmapIndex()
				.getBitmap(cmit)) != null) {
			bitmap.or(visitedBitmap);
		} else {
			bitmap.addObject(cmit, Constants.OBJ_COMMIT);
			return true;
		}

		for (RevCommit p : cmit.getParents()) {
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
