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

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

abstract class AbstractCommitRewriter {

	/** For {@link #cleanup(RevCommit[])} to remove duplicate parents. */
	private static final int DUPLICATE = RevWalk.TEMP_MARK;

	private final boolean firstParent;

	AbstractCommitRewriter(boolean firstParent) {
		this.firstParent = firstParent;
	}

	/**
	 * Makes sure that the {@link TreeRevFilter} has been applied to all parents
	 * of this commit
	 *
	 * @param c
	 *            given commit
	 * @throws MissingObjectException
	 *             if an object is missing
	 * @throws IncorrectObjectTypeException
	 *             if an object has an unexpected type
	 * @throws IOException
	 *             if an IO error occurred
	 */
	abstract void applyFilterToParents(RevCommit c)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException;

	final RevCommit rewriteParents(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		applyFilterToParents(c);

		boolean rewrote = false;
		RevCommit[] pList = c.getParents();
		int nParents = pList.length;
		for (int i = 0; i < nParents; i++) {
			RevCommit oldp = pList[i];
			RevCommit newp = rewrite(oldp);
			if (firstParent) {
				if (newp == null) {
					c.parents = RevCommit.NO_PARENTS;
				} else {
					c.parents = new RevCommit[] { newp };
				}
				return c;
			}
			if (oldp != newp) {
				pList[i] = newp;
				rewrote = true;
			}
		}
		if (rewrote) {
			c.parents = cleanup(pList);
		}
		return c;
	}

	private RevCommit rewrite(RevCommit p) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (true) {

			if (p.getParentCount() > 1) {
				// This parent is a merge, so keep it.
				//
				return p;
			}

			if ((p.flags & RevWalk.UNINTERESTING) != 0) {
				// Retain uninteresting parents. They show where the
				// DAG was cut off because it wasn't interesting.
				//
				return p;
			}

			if ((p.flags & RevWalk.REWRITE) == 0) {
				// This parent was not eligible for rewriting. We
				// need to keep it in the DAG.
				//
				return p;
			}

			if (p.getParentCount() == 0) {
				// We can't go back any further, other than to
				// just delete the parent entirely.
				//
				return null;
			}

			applyFilterToParents(p.getParent(0));
			p = p.getParent(0);

		}
	}

	private RevCommit[] cleanup(RevCommit[] oldList) {
		// Remove any duplicate parents caused due to rewrites (e.g. a merge
		// with two sides that both simplified back into the merge base).
		// We also may have deleted a parent by marking it null.
		//
		int newCnt = 0;
		for (int o = 0; o < oldList.length; o++) {
			RevCommit p = oldList[o];
			if (p == null) {
				continue;
			}
			if ((p.flags & DUPLICATE) != 0) {
				oldList[o] = null;
				continue;
			}
			p.flags |= DUPLICATE;
			newCnt++;
		}

		if (newCnt == oldList.length) {
			for (RevCommit p : oldList) {
				p.flags &= ~DUPLICATE;
			}
			return oldList;
		}

		RevCommit[] newList = new RevCommit[newCnt];
		newCnt = 0;
		for (RevCommit p : oldList) {
			if (p != null) {
				newList[newCnt++] = p;
				p.flags &= ~DUPLICATE;
			}
		}

		return newList;
	}

}
