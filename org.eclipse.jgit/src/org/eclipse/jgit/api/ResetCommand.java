/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Reset} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-reset.html"
 *      >Git documentation about Reset</a>
 */
public class ResetCommand extends GitCommand<Ref> {

	/**
	 * Kind of reset
	 */
	public enum ResetType {
		/**
		 * Just change the ref, the index and workdir are not changed.
		 */
		SOFT,

		/**
		 * Change the ref and the index, the workdir is not changed.
		 */
		MIXED,

		/**
		 * Change the ref, the index and the workdir
		 */
		HARD,

		/**
		 * Resets the index and updates the files in the working tree that are
		 * different between respective commit and HEAD, but keeps those which
		 * are different between the index and working tree
		 */
		MERGE, // TODO not implemented yet

		/**
		 * Change the ref, the index and the workdir that are different between
		 * respective commit and HEAD
		 */
		KEEP // TODO not implemented yet
	}

	private String ref;

	private ResetType mode;

	/**
	 *
	 * @param repo
	 */
	public ResetCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Reset} command. Each instance of this class should
	 * only be used for one invocation of the command. Don't call this method
	 * twice on an instance.
	 *
	 * @return the Ref after reset
	 */
	public Ref call() throws IOException {
		checkCallable();

		Ref r;
		RevCommit commit;

		try {
			RepositoryState state = repo.getRepositoryState();
			final boolean merging = state.equals(RepositoryState.MERGING)
					|| state.equals(RepositoryState.MERGING_RESOLVED);
			final boolean cherryPicking = state
					.equals(RepositoryState.CHERRY_PICKING)
					|| state.equals(RepositoryState.CHERRY_PICKING_RESOLVED);

			// resolve the ref to a commit
			final ObjectId commitId;
			try {
				commitId = repo.resolve(ref);
			} catch (IOException e) {
				throw new JGitInternalException(
						MessageFormat.format(JGitText.get().cannotRead, ref),
						e);
			}
			RevWalk rw = new RevWalk(repo);
			try {
				commit = rw.parseCommit(commitId);
			} catch (IOException e) {
				throw new JGitInternalException(
						MessageFormat.format(
						JGitText.get().cannotReadCommit, commitId.toString()),
						e);
			} finally {
				rw.release();
			}

			// write the ref
			final RefUpdate ru = repo.updateRef(Constants.HEAD);
			ru.setNewObjectId(commitId);

			String refName = Repository.shortenRefName(ref);
			String message = "reset --" //$NON-NLS-1$
					+ mode.toString().toLowerCase() + " " + refName; //$NON-NLS-1$
			ru.setRefLogMessage(message, false);
			if (ru.forceUpdate() == RefUpdate.Result.LOCK_FAILURE)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().cannotLock, ru.getName()));

			switch (mode) {
				case HARD:
					checkoutIndex(commit);
					break;
				case MIXED:
					resetIndex(commit);
					break;
				case SOFT: // do nothing, only the ref was changed
					break;
				case KEEP: // TODO
				case MERGE: // TODO
					throw new UnsupportedOperationException();

			}

			if (mode != ResetType.SOFT) {
				if (merging)
					resetMerge();
				else if (cherryPicking)
					resetCherryPick();
			}

			setCallable(false);
			r = ru.getRef();
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfResetCommand,
					e);
		}

		return r;
	}

	/**
	 * @param ref
	 *            the ref to reset to
	 * @return this instance
	 */
	public ResetCommand setRef(String ref) {
		this.ref = ref;
		return this;
	}

	/**
	 * @param mode
	 *            the mode of the reset command
	 * @return this instance
	 */
	public ResetCommand setMode(ResetType mode) {
		this.mode = mode;
		return this;
	}

	private void resetIndex(RevCommit commit) throws IOException {
		DirCache dc = null;
		try {
			dc = repo.lockDirCache();
			dc.clear();
			DirCacheBuilder dcb = dc.builder();
			dcb.addTree(new byte[0], 0, repo.newObjectReader(),
					commit.getTree());
			dcb.commit();
		} catch (IOException e) {
			throw e;
		} finally {
			if (dc != null)
				dc.unlock();
		}
	}

	private void checkoutIndex(RevCommit commit) throws IOException {
		DirCache dc = null;
		try {
			dc = repo.lockDirCache();
			DirCacheCheckout checkout = new DirCacheCheckout(repo, dc,
					commit.getTree());
			checkout.setFailOnConflict(false);
			checkout.checkout();
		} catch (IOException e) {
			throw e;
		} finally {
			if (dc != null)
				dc.unlock();
		}
	}

	private void resetMerge() throws IOException {
		repo.writeMergeHeads(null);
		repo.writeMergeCommitMsg(null);
	}

	private void resetCherryPick() throws IOException {
		repo.writeCherryPickHead(null);
		repo.writeMergeCommitMsg(null);
	}

}
