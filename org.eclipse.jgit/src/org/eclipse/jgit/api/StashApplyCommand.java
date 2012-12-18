/*
 * Copyright (C) 2012, GitHub Inc.
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
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * Command class to apply a stashed commit.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 * @since 2.0
 */
public class StashApplyCommand extends GitCommand<ObjectId> {

	private static final String DEFAULT_REF = Constants.STASH + "@{0}";

	/**
	 * Stash diff filter that looks for differences in the first three trees
	 * which must be the stash head tree, stash index tree, and stash working
	 * directory tree in any order.
	 */
	private static class StashDiffFilter extends TreeFilter {

		@Override
		public boolean include(final TreeWalk walker) {
			final int m = walker.getRawMode(0);
			if (walker.getRawMode(1) != m || !walker.idEqual(1, 0))
				return true;
			if (walker.getRawMode(2) != m || !walker.idEqual(2, 0))
				return true;
			return false;
		}

		@Override
		public boolean shouldBeRecursive() {
			return false;
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "STASH_DIFF";
		}
	}

	private String stashRef;

	/**
	 * Create command to apply the changes of a stashed commit
	 *
	 * @param repo
	 */
	public StashApplyCommand(final Repository repo) {
		super(repo);
	}

	/**
	 * Set the stash reference to apply
	 * <p>
	 * This will default to apply the latest stashed commit (stash@{0}) if
	 * unspecified
	 *
	 * @param stashRef
	 * @return {@code this}
	 */
	public StashApplyCommand setStashRef(final String stashRef) {
		this.stashRef = stashRef;
		return this;
	}

	private boolean isEqualEntry(AbstractTreeIterator iter1,
			AbstractTreeIterator iter2) {
		if (!iter1.getEntryFileMode().equals(iter2.getEntryFileMode()))
			return false;
		ObjectId id1 = iter1.getEntryObjectId();
		ObjectId id2 = iter2.getEntryObjectId();
		return id1 != null ? id1.equals(id2) : id2 == null;
	}

	/**
	 * Would unstashing overwrite local changes?
	 *
	 * @param stashIndexIter
	 * @param stashWorkingTreeIter
	 * @param headIter
	 * @param indexIter
	 * @param workingTreeIter
	 * @return true if unstash conflict, false otherwise
	 */
	private boolean isConflict(AbstractTreeIterator stashIndexIter,
			AbstractTreeIterator stashWorkingTreeIter,
			AbstractTreeIterator headIter, AbstractTreeIterator indexIter,
			AbstractTreeIterator workingTreeIter) {
		// Is the current index dirty?
		boolean indexDirty = indexIter != null
				&& (headIter == null || !isEqualEntry(indexIter, headIter));

		// Is the current working tree dirty?
		boolean workingTreeDirty = workingTreeIter != null
				&& (headIter == null || !isEqualEntry(workingTreeIter, headIter));

		// Would unstashing overwrite existing index changes?
		if (indexDirty && stashIndexIter != null && indexIter != null
				&& !isEqualEntry(stashIndexIter, indexIter))
			return true;

		// Would unstashing overwrite existing working tree changes?
		if (workingTreeDirty && stashWorkingTreeIter != null
				&& workingTreeIter != null
				&& !isEqualEntry(stashWorkingTreeIter, workingTreeIter))
			return true;

		return false;
	}

	private ObjectId getHeadTree() throws GitAPIException {
		final ObjectId headTree;
		try {
			headTree = repo.resolve(Constants.HEAD + "^{tree}");
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().cannotReadTree, e);
		}
		if (headTree == null)
			throw new NoHeadException(JGitText.get().cannotReadTree);
		return headTree;
	}

	private ObjectId getStashId() throws GitAPIException {
		final String revision = stashRef != null ? stashRef : DEFAULT_REF;
		final ObjectId stashId;
		try {
			stashId = repo.resolve(revision);
		} catch (IOException e) {
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().stashResolveFailed, revision), e);
		}
		if (stashId == null)
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().stashResolveFailed, revision));
		return stashId;
	}

	private void scanForConflicts(TreeWalk treeWalk) throws IOException,
			CheckoutConflictException {
		File workingTree = repo.getWorkTree();
		while (treeWalk.next()) {
			// State of the stashed index and working directory
			AbstractTreeIterator stashIndexIter = treeWalk.getTree(1,
					AbstractTreeIterator.class);
			AbstractTreeIterator stashWorkingIter = treeWalk.getTree(2,
					AbstractTreeIterator.class);

			// State of the current HEAD, index, and working directory
			AbstractTreeIterator headIter = treeWalk.getTree(3,
					AbstractTreeIterator.class);
			AbstractTreeIterator indexIter = treeWalk.getTree(4,
					AbstractTreeIterator.class);
			AbstractTreeIterator workingIter = treeWalk.getTree(5,
					AbstractTreeIterator.class);

			if (isConflict(stashIndexIter, stashWorkingIter, headIter,
					indexIter, workingIter)) {
				String path = treeWalk.getPathString();
				File file = new File(workingTree, path);
				throw new CheckoutConflictException(MessageFormat.format(
						JGitText.get().checkoutConflictWithFile,
						file.getAbsoluteFile()), Collections.singletonList(file
						.getAbsolutePath()));
			}
		}
	}

	private void applyChanges(TreeWalk treeWalk, DirCache cache,
			DirCacheEditor editor) throws IOException {
		File workingTree = repo.getWorkTree();
		while (treeWalk.next()) {
			String path = treeWalk.getPathString();
			File file = new File(workingTree, path);

			// State of the stashed HEAD, index, and working directory
			AbstractTreeIterator stashHeadIter = treeWalk.getTree(0,
					AbstractTreeIterator.class);
			AbstractTreeIterator stashIndexIter = treeWalk.getTree(1,
					AbstractTreeIterator.class);
			AbstractTreeIterator stashWorkingIter = treeWalk.getTree(2,
					AbstractTreeIterator.class);

			if (stashWorkingIter != null && stashIndexIter != null) {
				// Checkout index change
				DirCacheEntry entry = cache.getEntry(path);
				if (entry == null)
					entry = new DirCacheEntry(treeWalk.getRawPath());
				entry.setFileMode(stashIndexIter.getEntryFileMode());
				entry.setObjectId(stashIndexIter.getEntryObjectId());
				DirCacheCheckout.checkoutEntry(repo, file, entry,
						treeWalk.getObjectReader());
				final DirCacheEntry updatedEntry = entry;
				editor.add(new PathEdit(path) {

					public void apply(DirCacheEntry ent) {
						ent.copyMetaData(updatedEntry);
					}
				});

				// Checkout working directory change
				if (!stashWorkingIter.idEqual(stashIndexIter)) {
					entry = new DirCacheEntry(treeWalk.getRawPath());
					entry.setObjectId(stashWorkingIter.getEntryObjectId());
					DirCacheCheckout.checkoutEntry(repo, file, entry,
							treeWalk.getObjectReader());
				}
			} else {
				if (stashIndexIter == null
						|| (stashHeadIter != null && !stashIndexIter
								.idEqual(stashHeadIter)))
					editor.add(new DeletePath(path));
				FileUtils
						.delete(file, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			}
		}
	}

	/**
	 * Apply the changes in a stashed commit to the working directory and index
	 *
	 * @return id of stashed commit that was applied
	 * @throws CheckoutConflictException
	 * @throws GitAPIException
	 * @throws WrongRepositoryStateException
	 */
	public ObjectId call() throws GitAPIException, CheckoutConflictException,
			WrongRepositoryStateException {
		checkCallable();

		if (repo.getRepositoryState() != RepositoryState.SAFE)
			throw new WrongRepositoryStateException(MessageFormat.format(
					JGitText.get().stashApplyOnUnsafeRepository,
					repo.getRepositoryState()));

		final ObjectId headTree = getHeadTree();
		final ObjectId stashId = getStashId();

		ObjectReader reader = repo.newObjectReader();
		try {
			RevWalk revWalk = new RevWalk(reader);
			RevCommit stashCommit = revWalk.parseCommit(stashId);
			if (stashCommit.getParentCount() != 2)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().stashCommitMissingTwoParents,
						stashId.name()));

			RevTree stashWorkingTree = stashCommit.getTree();
			RevTree stashIndexTree = revWalk.parseCommit(
					stashCommit.getParent(1)).getTree();
			RevTree stashHeadTree = revWalk.parseCommit(
					stashCommit.getParent(0)).getTree();

			CanonicalTreeParser stashWorkingIter = new CanonicalTreeParser();
			stashWorkingIter.reset(reader, stashWorkingTree);
			CanonicalTreeParser stashIndexIter = new CanonicalTreeParser();
			stashIndexIter.reset(reader, stashIndexTree);
			CanonicalTreeParser stashHeadIter = new CanonicalTreeParser();
			stashHeadIter.reset(reader, stashHeadTree);
			CanonicalTreeParser headIter = new CanonicalTreeParser();
			headIter.reset(reader, headTree);

			DirCache cache = repo.lockDirCache();
			DirCacheEditor editor = cache.editor();
			try {
				DirCacheIterator indexIter = new DirCacheIterator(cache);
				FileTreeIterator workingIter = new FileTreeIterator(repo);

				TreeWalk treeWalk = new TreeWalk(reader);
				treeWalk.setRecursive(true);
				treeWalk.setFilter(new StashDiffFilter());

				treeWalk.addTree(stashHeadIter);
				treeWalk.addTree(stashIndexIter);
				treeWalk.addTree(stashWorkingIter);
				treeWalk.addTree(headIter);
				treeWalk.addTree(indexIter);
				treeWalk.addTree(workingIter);

				scanForConflicts(treeWalk);

				// Reset trees and walk
				treeWalk.reset();
				stashWorkingIter.reset(reader, stashWorkingTree);
				stashIndexIter.reset(reader, stashIndexTree);
				stashHeadIter.reset(reader, stashHeadTree);
				treeWalk.addTree(stashHeadIter);
				treeWalk.addTree(stashIndexIter);
				treeWalk.addTree(stashWorkingIter);

				applyChanges(treeWalk, cache, editor);
			} finally {
				editor.commit();
				cache.unlock();
			}
		} catch (JGitInternalException e) {
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashApplyFailed, e);
		} finally {
			reader.release();
		}
		return stashId;
	}
}
