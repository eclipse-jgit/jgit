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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Command class to apply a stashed commit.
 *
 * This class behaves like <em>git stash apply --index</em>, i.e. it tries to
 * recover the stashed index state in addition to the working tree state.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 *
 * @since 2.0
 */
public class StashApplyCommand extends GitCommand<ObjectId> {

	private static final String DEFAULT_REF = Constants.STASH + "@{0}"; //$NON-NLS-1$

	private String stashRef;

	private boolean applyIndex = true;

	private boolean applyUntracked = true;

	private boolean ignoreRepositoryState;

	private MergeStrategy strategy = MergeStrategy.RECURSIVE;

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
	 * @param ignoreRepositoryState
	 * @return {@code this}
	 * @since 3.2
	 */
	public StashApplyCommand ignoreRepositoryState(boolean ignoreRepositoryState) {
		this.ignoreRepositoryState = ignoreRepositoryState;
		return this;
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

	/**
	 * Apply the changes in a stashed commit to the working directory and index
	 *
	 * @return id of stashed commit that was applied TODO: Does anyone depend on
	 *         this, or could we make it more like Merge/CherryPick/Revert?
	 * @throws GitAPIException
	 * @throws WrongRepositoryStateException
	 * @throws NoHeadException
	 * @throws StashApplyFailureException
	 */
	public ObjectId call() throws GitAPIException,
			WrongRepositoryStateException, NoHeadException,
			StashApplyFailureException {
		checkCallable();

		if (!ignoreRepositoryState
				&& repo.getRepositoryState() != RepositoryState.SAFE)
			throw new WrongRepositoryStateException(MessageFormat.format(
					JGitText.get().stashApplyOnUnsafeRepository,
					repo.getRepositoryState()));

		ObjectReader reader = repo.newObjectReader();
		try {
			RevWalk revWalk = new RevWalk(reader);

			ObjectId headCommit = repo.resolve(Constants.HEAD);
			if (headCommit == null)
				throw new NoHeadException(JGitText.get().stashApplyWithoutHead);

			final ObjectId stashId = getStashId();
			RevCommit stashCommit = revWalk.parseCommit(stashId);
			if (stashCommit.getParentCount() < 2
					|| stashCommit.getParentCount() > 3)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().stashCommitMissingTwoParents,
						stashId.name()));

			ObjectId headTree = repo.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
			ObjectId stashIndexCommit = revWalk.parseCommit(stashCommit
					.getParent(1));
			ObjectId stashHeadCommit = stashCommit.getParent(0);
			ObjectId untrackedCommit = null;
			if (applyUntracked && stashCommit.getParentCount() == 3) {
				untrackedCommit = revWalk.parseCommit(stashCommit.getParent(2));
			}

			ResolveMerger merger = (ResolveMerger) strategy.newMerger(repo);
			merger.setCommitNames(new String[] { "stashed HEAD", "HEAD",
					"stash" });
			merger.setBase(stashHeadCommit);
			merger.setWorkingTreeIterator(new FileTreeIterator(repo));
			if (merger.merge(headCommit, stashCommit)) {
				DirCache dc = repo.lockDirCache();
				DirCacheCheckout dco = new DirCacheCheckout(repo, headTree,
						dc, merger.getResultTreeId());
				dco.setFailOnConflict(true);
				dco.checkout(); // Ignoring failed deletes....
				if (applyIndex) {
					ResolveMerger ixMerger = (ResolveMerger) strategy
							.newMerger(repo, true);
					ixMerger.setCommitNames(new String[] { "stashed HEAD",
							"HEAD", "stashed index" });
					ixMerger.setBase(stashHeadCommit);
					boolean ok = ixMerger.merge(headCommit, stashIndexCommit);
					if (ok) {
						resetIndex(revWalk
								.parseTree(ixMerger.getResultTreeId()));
					} else {
						throw new StashApplyFailureException(
								JGitText.get().stashApplyConflict);
					}
				}

				if (untrackedCommit != null) {
					ResolveMerger untrackedMerger = (ResolveMerger) strategy
							.newMerger(repo, true);
					untrackedMerger.setCommitNames(new String[] {
							"stashed HEAD", "HEAD", "untracked files" }); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
					untrackedMerger.setBase(stashHeadCommit);
					boolean ok = untrackedMerger.merge(headCommit,
							untrackedCommit);
					if (ok) {
						try {
							RevTree untrackedTree = revWalk
									.parseTree(untrackedMerger
											.getResultTreeId());
							resetUntracked(untrackedTree);

						} catch (CheckoutConflictException e) {
							throw new StashApplyFailureException(
									JGitText.get().stashApplyConflict);
						}
					} else {
						throw new StashApplyFailureException(
								JGitText.get().stashApplyConflict);
					}
				}

			} else {
				throw new StashApplyFailureException(
						JGitText.get().stashApplyConflict);
			}
			return stashId;

		} catch (JGitInternalException e) {
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashApplyFailed, e);
		} finally {
			reader.release();
		}
	}

	/**
	 * @param applyIndex
	 *            true (default) if the command should restore the index state
	 */
	public void setApplyIndex(boolean applyIndex) {
		this.applyIndex = applyIndex;
	}

	/**
	 * @param strategy
	 *            The merge strategy to use in order to merge during this
	 *            command execution.
	 * @return {@code this}
	 * @since 3.4
	 */
	public StashApplyCommand setStrategy(MergeStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	/**
	 * @param applyUntracked
	 *            true (default) if the command should restore untracked files
	 */
	public void setApplyUntracked(boolean applyUntracked) {
		this.applyUntracked = applyUntracked;
	}

	private void resetIndex(RevTree tree) throws IOException {
		DirCache dc = repo.lockDirCache();
		TreeWalk walk = null;
		try {
			DirCacheBuilder builder = dc.builder();

			walk = new TreeWalk(repo);
			walk.addTree(tree);
			walk.addTree(new DirCacheIterator(dc));
			walk.setRecursive(true);

			while (walk.next()) {
				AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				if (cIter == null) {
					// Not in commit, don't add to new index
					continue;
				}

				final DirCacheEntry entry = new DirCacheEntry(walk.getRawPath());
				entry.setFileMode(cIter.getEntryFileMode());
				entry.setObjectIdFromRaw(cIter.idBuffer(), cIter.idOffset());

				DirCacheIterator dcIter = walk.getTree(1,
						DirCacheIterator.class);
				if (dcIter != null && dcIter.idEqual(cIter)) {
					DirCacheEntry indexEntry = dcIter.getDirCacheEntry();
					entry.setLastModified(indexEntry.getLastModified());
					entry.setLength(indexEntry.getLength());
				}

				builder.add(entry);
			}

			builder.commit();
		} finally {
			dc.unlock();
			if (walk != null)
				walk.release();
		}
	}

	private void resetUntracked(RevTree tree) throws CheckoutConflictException,
			IOException {
		TreeWalk walk = null;
		try {
			walk = new TreeWalk(repo); // maybe NameConflictTreeWalk?
			walk.addTree(tree);
			walk.addTree(new FileTreeIterator(repo));
			walk.setRecursive(true);

			final ObjectReader reader = walk.getObjectReader();

			while (walk.next()) {
				final AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				if (cIter == null)
					// Not in commit, don't create untracked
					continue;

				final DirCacheEntry entry = new DirCacheEntry(walk.getRawPath());
				entry.setFileMode(cIter.getEntryFileMode());
				entry.setObjectIdFromRaw(cIter.idBuffer(), cIter.idOffset());

				FileTreeIterator fIter = walk
						.getTree(1, FileTreeIterator.class);
				if (fIter != null) {
					if (fIter.isModified(entry, true, reader)) {
						// file exists and is dirty
						throw new CheckoutConflictException(
								entry.getPathString());
					}
				}

				checkoutPath(entry, reader);
			}
		} finally {
			if (walk != null)
				walk.release();
		}
	}

	private void checkoutPath(DirCacheEntry entry, ObjectReader reader) {
		try {
			File file = new File(repo.getWorkTree(), entry.getPathString());
			DirCacheCheckout.checkoutEntry(repo, file, entry, reader);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().checkoutConflictWithFile,
					entry.getPathString()), e);
		}
	}
}
