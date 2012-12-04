/*
 * Copyright (C) 2012, Research In Motion Limited
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
/*
 * Contributors:
 *    George Young - initial API and implementation and/or initial documentation
 */

package org.eclipse.jgit.merge;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

/**
 * A three-way merger performing a content-merge if necessary across multiple
 * bases using recursion
 *
 * This merger extends the resolve merger and does several things differently:
 *
 * - allow more than one merge base, up to a maximum
 *
 * - uses "Lists" instead of Arrays for chained types
 *
 * - reuses the tree merge mechanism from the resolve merger
 *
 * - recursively merges the merge bases together to compute a usable base
 *
 */

public class RecursiveMerger extends ResolveMerger {
	/**
	 * If the merge fails (means: not stopped because of unresolved conflicts)
	 * this enum is used to explain why it failed
	 */
	public enum RecursiveMergeFailureReason {
		/** the merge failed because of a dirty index */
		DIRTY_INDEX,
		/** the merge failed because of a dirty workingtree */
		DIRTY_WORKTREE,
		/** the merge failed because of a file could not be deleted */
		COULD_NOT_DELETE,
		/** asked to merge over more than MAX_BASES bases */
		TOO_MANY_BASES
	}

	private final int MAX_TIPS_TO_MERGE = 2;

	private final int MAX_BASES = 200;

	private RevTree[] baseTrees;

	private AbstractTreeIterator[] baseTreeIterators;

	/**
	 * Normal recursive merge when you want a choice of DirCache placement
	 * inCore
	 *
	 * @param local
	 * @param inCore
	 */
	protected RecursiveMerger(Repository local, boolean inCore) {
		super(local);
		SupportedAlgorithm diffAlg = local.getConfig().getEnum(
				ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_ALGORITHM,
				SupportedAlgorithm.HISTOGRAM);
		mergeAlgorithm = new MergeAlgorithm(DiffAlgorithm.getAlgorithm(diffAlg));
		commitNames = new String[] { "BASE", "OURS", "THEIRS" };
		this.inCore = inCore;

		if (inCore)
			dircache = DirCache.newInCore();
	}

	/**
	 * Normal recursive merge, implies not inCore
	 *
	 * @param local
	 */
	protected RecursiveMerger(Repository local) {
		this(local, false);
	}

	/**
	 * This merge strategy's implementation
	 *
	 * @return whether the merge was clean
	 * @throws IOException
	 */
	@Override
	protected boolean mergeImpl() throws IOException {
		boolean implicitDirCache = false;

		if (dircache == null) {
			dircache = getRepository().lockDirCache();
			implicitDirCache = true;
		}

		try {
			return mergeRecursive(0, sourceCommits[0], sourceCommits[1],
					getBaseCommits(0, 1));
		} finally {

			if (implicitDirCache)
				dircache.unlock();
		}
	}

	@Override
	public boolean merge(final AnyObjectId... tips) throws IOException {
		if (tips.length > MAX_TIPS_TO_MERGE)
			return false;
		return super.merge(tips);
	}

	/**
	 * Get the merge base of two commits.
	 *
	 * @param aIdx
	 *            index of the first commit in {@link #sourceObjects}.
	 * @param bIdx
	 *            index of the second commit in {@link #sourceObjects}.
	 * @return the merge bases of two commits
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit.
	 * @throws IOException
	 *             objects are missing or too many merge bases were found.
	 */
	public List<RevCommit> getBaseCommits(final int aIdx, final int bIdx)
			throws IncorrectObjectTypeException, IOException {
		if (sourceCommits[aIdx] == null)
			throw new IncorrectObjectTypeException(sourceObjects[aIdx],
					Constants.TYPE_COMMIT);
		if (sourceCommits[bIdx] == null)
			throw new IncorrectObjectTypeException(sourceObjects[bIdx],
					Constants.TYPE_COMMIT);
		walk.reset();
		walk.setRevFilter(RevFilter.MERGE_BASE);
		walk.markStart(sourceCommits[aIdx]);
		walk.markStart(sourceCommits[bIdx]);

		List<RevCommit> bases = new ArrayList<RevCommit>();
		int commitIdx = 0;

		for (RevCommit base : walk) {
			if (commitIdx < MAX_BASES) {
				bases.add(base);
				commitIdx++;
			} else {
				throw new IOException(MessageFormat.format(
						JGitText.get().mergeRecursiveTooManyMergeBasesFor,
						Integer.valueOf(MAX_BASES), sourceCommits[aIdx].name(),
						sourceCommits[bIdx].name(), Integer.valueOf(commitIdx),
						base.name()));
			}
		}

		return bases;
	}

	/**
	 * Will return the common predecessor commit if there was exactly one common
	 * predecessor for the commits to be merged. Otherwise <code>null</code> is
	 * returned
	 */
	@Override
	public RevCommit getBaseCommit(int aIdx, int bIdx)
			throws IncorrectObjectTypeException, IOException {
		List<RevCommit> baseCommits = getBaseCommits(aIdx, bIdx);
		if (baseCommits == null || baseCommits.size() != 1)
			return null;
		return baseCommits.get(0);
	}

	private static <T> T[] copyOf(T[] original, int newLength) {
		return (T[]) copyOf(original, newLength, original.getClass());
	}

	private static <T, U> T[] copyOf(U[] original, int newLength,
			Class<? extends T[]> newType) {
		T[] copy = ((Object) newType == (Object) Object[].class) ? (T[]) new Object[newLength]
				: (T[]) Array
						.newInstance(newType.getComponentType(), newLength);
		System.arraycopy(original, 0, copy, 0,
				Math.min(original.length, newLength));
		return copy;
	}

	/**
	 * Create an iterator to walk the merge bases.
	 *
	 * @return an array of iterators, over the natural merge bases between each
	 *         pair of the input source commits.
	 * @throws IOException
	 */
	protected AbstractTreeIterator[] mergeBases() throws IOException {
		final int numsourceTrees = sourceTrees.length;
		int commitIdx = 0;

		RevTree tempTrees[] = new RevTree[MAX_BASES];
		AbstractTreeIterator tempIterators[] = new AbstractTreeIterator[MAX_BASES];

		if (baseTrees == null) {
			baseTrees = new RevTree[MAX_BASES];
			baseTreeIterators = new AbstractTreeIterator[MAX_BASES];
		}

		for (int aIdx = 0; aIdx < numsourceTrees; aIdx++) {
			for (int bIdx = aIdx + 1; bIdx < numsourceTrees; bIdx++) {
				List<RevCommit> baseCommits = getBaseCommits(aIdx, bIdx);
				for (RevCommit baseCommit : baseCommits) {
					if (commitIdx < MAX_BASES) {
						tempTrees[commitIdx] = baseCommit.getTree();
						tempIterators[commitIdx] = openTree(baseCommit
								.getTree());
						commitIdx++;
					} else {
						throw new IOException(
								MessageFormat.format(
										JGitText.get().mergeRecursiveTooManyMergeBasesFor,
										Integer.valueOf(MAX_BASES),
										sourceCommits[aIdx].name(),
										sourceCommits[bIdx].name(),
										Integer.valueOf(commitIdx),
										baseCommit.name()));
					}
				}
			}
		}

		if (commitIdx == 0) {
			baseTreeIterators[commitIdx] = new EmptyTreeIterator();
			commitIdx++;
		} else {
			baseTrees = copyOf(tempTrees, commitIdx);
			baseTreeIterators = copyOf(tempIterators, commitIdx);
		}

		return copyOf(baseTreeIterators, baseTreeIterators.length);
	}

	/**
	 * Invert order of a List of commits.
	 *
	 * @param commitList
	 *            original ordered list
	 * @return a new List made of the original items in reverse order
	 */
	protected List<RevCommit> reverseCommitList(List<RevCommit> commitList) {
		int headSize = commitList.size();
		List<RevCommit> newList = new ArrayList<RevCommit>();

		while (headSize > 0) {
			newList.add(commitList.get(headSize - 1));
			headSize--;
		}
		return newList;
	}

	List<RevCommit> commitListInsert(RevCommit item, List<RevCommit> commitList) {
		List<RevCommit> itemList = new ArrayList<RevCommit>();
		itemList.add(item);
		commitList.addAll(0, itemList);
		return itemList.isEmpty() ? null : itemList;
	}

	List<RevCommit> getMergeBases(RevCommit one, RevCommit two)
			throws IncorrectObjectTypeException, IOException {
		if (one == null)
			throw new IncorrectObjectTypeException(one, Constants.TYPE_COMMIT);
		if (two == null)
			throw new IncorrectObjectTypeException(two, Constants.TYPE_COMMIT);
		List<RevCommit> mergeBases = new ArrayList<RevCommit>();
		walk.reset();
		walk.setRevFilter(RevFilter.MERGE_BASE);
		walk.markStart(one);
		walk.markStart(two);

		int commitIdx = 0;

		for (RevCommit base : walk) {
			if (commitIdx < MAX_BASES) {
				mergeBases.add(base);
				commitIdx++;
			} else {
				throw new IOException(MessageFormat.format(
						JGitText.get().mergeRecursiveTooManyMergeBasesFor,
						Integer.valueOf(MAX_BASES), one.name(), two.name(),
						Integer.valueOf(commitIdx), base.name()));
			}
		}

		return mergeBases;
	}

	/**
	 * @param callDepth
	 * @param h1
	 * @param h2
	 * @param commonAncestors
	 * @return whether the merge is clean
	 * @throws IOException
	 */
	protected boolean mergeRecursive(int callDepth, RevCommit h1, RevCommit h2,
			List<RevCommit> commonAncestors) throws IOException {

		RevCommit firstCommonAncestor = null;
		if (commonAncestors == null)
			commonAncestors = new ArrayList<RevCommit>();

		if (commonAncestors.isEmpty())
			commonAncestors.addAll(reverseCommitList(getMergeBases(h1, h2)));

		if (!commonAncestors.isEmpty()) {
			firstCommonAncestor = commonAncestors.remove(0);
		}

		/*
		 * At this point, if we got called with:
		 *
		 * - no commonAncestors: now we have no firstCommonAncestor, nor any
		 * commonAncestors left to loop over
		 *
		 * - one commonAncestors: now we have a firstCommonAncestor still no
		 * commonAncestors for looping
		 *
		 * - two or more commonAncestors: now we have a firstCommonAncestor and
		 * one less commonAncestors
		 */
		if (commonAncestors.isEmpty()) {
			resultTree = (firstCommonAncestor == null ? null
					: firstCommonAncestor.getTree().getId());
		} else {
			// With at least one commonAncerstor left, we can loop
			for (RevCommit commonAncestor : commonAncestors) {
				resultTree = null;

				if (!mergeRecursive(callDepth + 1, firstCommonAncestor,
						commonAncestor, null)) {
					// cleanUp was called for any unsuccessful merge.
					// Do not proceed since intermediate merge failed.
					return false;
				}

				// paranoia check
				if (resultTree == null) {
					throw new IOException(MessageFormat.format(
							JGitText.get().mergeRecursiveReturnedNoCommit,
							callDepth + 1,
							(firstCommonAncestor == null ? "null"
									: firstCommonAncestor.name()),
							commonAncestor.name()));
				}
			}
		}

		// we either have our firstCommonAncestor (and only) or all ancestors
		// merged into the resultTree
		return mergeTrees(openTree(resultTree), h1.getTree(), h2.getTree());
	}
}
