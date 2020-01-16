/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.blame;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

final class ReverseWalk extends RevWalk {
	ReverseWalk(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public ReverseCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		ReverseCommit c = (ReverseCommit) super.next();
		if (c == null)
			return null;
		for (int pIdx = 0; pIdx < c.getParentCount(); pIdx++)
			((ReverseCommit) c.getParent(pIdx)).addChild(c);
		return c;
	}

	/** {@inheritDoc} */
	@Override
	protected RevCommit createCommit(AnyObjectId id) {
		return new ReverseCommit(id);
	}

	static final class ReverseCommit extends RevCommit {
		private static final ReverseCommit[] NO_CHILDREN = {};

		private ReverseCommit[] children = NO_CHILDREN;

		ReverseCommit(AnyObjectId id) {
			super(id);
		}

		void addChild(ReverseCommit c) {
			// Always put the most recent child onto the front of the list.
			// This works correctly because our ReverseWalk parent (above)
			// runs in COMMIT_TIME_DESC order. Older commits will be popped
			// later and should go in front of the children list so they are
			// visited first by BlameGenerator when considering candidates.

			int cnt = children.length;
			switch (cnt) {
			case 0:
				children = new ReverseCommit[] { c };
				break;
			case 1:
				children = new ReverseCommit[] { c, children[0] };
				break;
			default:
				ReverseCommit[] n = new ReverseCommit[1 + cnt];
				n[0] = c;
				System.arraycopy(children, 0, n, 1, cnt);
				children = n;
				break;
			}
		}

		int getChildCount() {
			return children.length;
		}

		ReverseCommit getChild(int nth) {
			return children[nth];
		}
	}
}
