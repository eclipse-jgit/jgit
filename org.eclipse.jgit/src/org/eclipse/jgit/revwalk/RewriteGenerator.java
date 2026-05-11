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

/**
 * Replaces a RevCommit's parents until not colored with REWRITE.
 * <p>
 * Before a RevCommit is returned to the caller its parents are updated to
 * create a dense DAG. Instead of reporting the actual parents as recorded when
 * the commit was created the returned commit will reflect the next closest
 * commit that matched the revision walker's filters.
 * <p>
 * This generator is the second phase of a path limited revision walk and
 * assumes it is receiving RevCommits from {@link TreeRevFilter}.
 *
 * @see TreeRevFilter
 */
class RewriteGenerator extends Generator {

	private final Generator source;

	private final FIFORevQueue pending;

	private final AbstractCommitRewriter rewriter;

	RewriteGenerator(Generator s) {
		super(s.firstParent);
		source = s;
		pending = new FIFORevQueue(s.firstParent);
		rewriter = new PendingRewriter();
	}

	@Override
	void shareFreeList(BlockRevQueue q) {
		source.shareFreeList(q);
	}

	@Override
	int outputType() {
		return source.outputType() & ~NEEDS_REWRITE;
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c = pending.next();

		if (c == null) {
			c = source.next();
			if (c == null) {
				// We are done: Both the source generator and our internal list
				// are completely exhausted.
				return null;
			}
		}

		return rewriter.rewriteParents(c);
	}

	class PendingRewriter extends AbstractCommitRewriter {
		PendingRewriter() {
			super(firstParent);
		}

		/**
		 * Makes sure that the {@link TreeRevFilter} has been applied to all
		 * parents of this commit by the previous {@link PendingGenerator}.
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
		@Override
		void applyFilterToParents(RevCommit c) throws MissingObjectException,
				IncorrectObjectTypeException, IOException {
			for (RevCommit parent : c.getParents()) {
				while ((parent.flags & RevWalk.TREE_REV_FILTER_APPLIED) == 0) {

					RevCommit n = source.next();

					if (n != null) {
						pending.add(n);
					} else {
						// Source generator is exhausted; filter has been
						// applied to all commits
						return;
					}

				}

			}
		}
	}
}
