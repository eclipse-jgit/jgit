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

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * Command class to apply a stashed commit.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 */
public class StashApplyCommand extends GitCommand<ObjectId> {

	private static final String DEFAULT_REF = Constants.STASH + "@{0}";

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

	/**
	 * Apply the changes in a stashed commit to the working directory and index
	 *
	 * @return id of stashed commit that was applied
	 */
	public ObjectId call() throws GitAPIException, JGitInternalException {
		checkCallable();

		if (repo.getRepositoryState() != RepositoryState.SAFE)
			throw new WrongRepositoryStateException(MessageFormat.format(
					JGitText.get().stashApplyOnUnsafeRepository,
					repo.getRepositoryState()));

		final String revision = stashRef != null ? stashRef : DEFAULT_REF;
		final ObjectId stashId;
		try {
			stashId = repo.resolve(revision);
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashApplyFailed, e);
		}
		if (stashId == null)
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().stashResolveFailed, revision));

		ObjectReader reader = repo.newObjectReader();
		try {
			RevWalk revWalk = new RevWalk(reader);
			RevCommit wtCommit = revWalk.parseCommit(stashId);
			if (wtCommit.getParentCount() != 2)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().stashCommitMissingTwoParents,
						stashId.name()));

			// Apply index changes
			RevTree indexTree = revWalk.parseCommit(wtCommit.getParent(1))
					.getTree();
			DirCacheCheckout dco = new DirCacheCheckout(repo,
					repo.lockDirCache(), indexTree, new FileTreeIterator(repo));
			dco.setFailOnConflict(true);
			dco.checkout();

			// Apply working directory changes
			RevTree headTree = revWalk.parseCommit(wtCommit.getParent(0))
					.getTree();
			DirCache cache = repo.lockDirCache();
			DirCacheEditor editor = cache.editor();
			try {
				TreeWalk treeWalk = new TreeWalk(reader);
				treeWalk.setRecursive(true);
				treeWalk.addTree(headTree);
				treeWalk.addTree(indexTree);
				treeWalk.addTree(wtCommit.getTree());
				treeWalk.setFilter(TreeFilter.ANY_DIFF);
				File workingTree = repo.getWorkTree();
				while (treeWalk.next()) {
					String path = treeWalk.getPathString();
					File file = new File(workingTree, path);
					AbstractTreeIterator headIter = treeWalk.getTree(0,
							AbstractTreeIterator.class);
					AbstractTreeIterator indexIter = treeWalk.getTree(1,
							AbstractTreeIterator.class);
					AbstractTreeIterator wtIter = treeWalk.getTree(2,
							AbstractTreeIterator.class);
					if (wtIter != null) {
						DirCacheEntry entry = new DirCacheEntry(
								treeWalk.getRawPath());
						entry.setObjectId(wtIter.getEntryObjectId());
						DirCacheCheckout.checkoutEntry(repo, file, entry);
					} else {
						if (indexIter != null && headIter != null
								&& !indexIter.idEqual(headIter))
							editor.add(new DeletePath(path));
						FileUtils.delete(file, FileUtils.RETRY
								| FileUtils.SKIP_MISSING);
					}
				}
			} finally {
				editor.commit();
				cache.unlock();
			}
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashApplyFailed, e);
		} finally {
			reader.release();
		}
		return stashId;
	}
}
