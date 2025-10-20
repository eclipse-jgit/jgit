/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Application level mark bit for {@link org.eclipse.jgit.revwalk.RevObject}s.
 * <p>
 * To create a flag use
 * {@link org.eclipse.jgit.revwalk.RevWalk#newFlag(String)}.
 */
public class RevFlag {
	/**
	 * Uninteresting by {@link RevWalk#markUninteresting(RevCommit)}.
	 * <p>
	 * We flag commits as uninteresting if the caller does not want commits
	 * reachable from a commit to {@link RevWalk#markUninteresting(RevCommit)}.
	 * This flag is always carried into the commit's parents and is a key part
	 * of the "rev-list B --not A" feature; A is marked UNINTERESTING.
	 * <p>
	 * This is a static flag. Its RevWalk is not available.
	 */
	public static final RevFlag UNINTERESTING = new StaticRevFlag(
			"UNINTERESTING", RevWalk.UNINTERESTING); //$NON-NLS-1$

	/**
	 * Set on RevCommit instances added to {@link RevWalk#pending} queue.
	 * <p>
	 * We use this flag to avoid adding the same commit instance twice to our
	 * queue, especially if we reached it by more than one path.
	 * <p>
	 * This is a static flag. Its RevWalk is not available.
	 *
	 * @since 3.0
	 */
	public static final RevFlag SEEN = new StaticRevFlag("SEEN", RevWalk.SEEN); //$NON-NLS-1$

	/**
	 * Set on RevObject instances when generating a navigation for unshallow request.
	 * <p>
	 * Commits which used to be shallow in the client, but which are
	 * being extended as part of this fetch.  These commits should be
	 * returned to the caller as UNINTERESTING so that their blobs/trees
	 * can be marked appropriately in the pack writer.
	 *
	 * @since 7.5
	 */
	public static final RevFlag UNSHALLOW = new StaticRevFlag("UNSHALLOW", RevWalk.UNSHALLOW); //$NON-NLS-1$

	final RevWalk walker;

	final String name;

	final int mask;

	RevFlag(RevWalk w, String n, int m) {
		walker = w;
		name = n;
		mask = m;
	}

	/**
	 * Get the revision walk instance this flag was created from.
	 *
	 * @return the walker this flag was allocated out of, and belongs to.
	 */
	public RevWalk getRevWalk() {
		return walker;
	}

	@Override
	public String toString() {
		return name;
	}

	static class StaticRevFlag extends RevFlag {
		StaticRevFlag(String n, int m) {
			super(null, n, m);
		}

		@Override
		public RevWalk getRevWalk() {
			throw new UnsupportedOperationException(MessageFormat.format(
					JGitText.get().isAStaticFlagAndHasNorevWalkInstance, toString()));
		}
	}
}
