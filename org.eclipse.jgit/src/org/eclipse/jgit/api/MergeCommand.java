/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
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

import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.WorkDirCheckout;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Merge} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 * <p>
 * This is currently a very basic implementation which takes only one commit to
 * merge with as option. Furthermore it does supports only fast forward.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
 *      >Git documentation about Merge</a>
 */
public class MergeCommand extends GitCommand<MergeResult> {

	private MergeStrategy mergeStrategy = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE;

	private Ref commit;

	/**
	 * @param repo
	 */
	protected MergeCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Merge} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #setCommit(Ref)}) of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 *
	 * @return the result of the merge
	 */
	public MergeResult call() throws NoHeadException,
			ConcurrentRefUpdateException {
		checkCallable();

		try {
			Ref head = repo.getRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						"Commit on repo without HEAD currently not supported");
			StringBuilder refLogMessage = new StringBuilder("merge ");
			refLogMessage.append(commit.getName() + ": ");

			// Check for FAST_FORWARD, ALREADY_UP_TO_DATE
			RevWalk revWalk = new RevWalk(repo);
			RevCommit headCommit = revWalk.lookupCommit(head.getObjectId());
			RevCommit srcCommit = revWalk.lookupCommit(commit.getLeaf()
					.getObjectId());
			if (revWalk.isMergedInto(srcCommit, headCommit)) {
				// ALREADY_UP_TO_DATE: nothing to be done
				return new MergeResult(headCommit,
						MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
						mergeStrategy);
			}

			Commit newHeadCommit;
			MergeResult.MergeStatus mergeStatus = null;
			if (revWalk.isMergedInto(headCommit, srcCommit)) {
				// FAST_FORWARD detected: skip doing a real merge but only
				// update
				// HEAD
				newHeadCommit = repo.mapCommit(srcCommit);
				refLogMessage.append("Fast forward");
				mergeStatus = MergeResult.MergeStatus.FAST_FORWARD;

			} else {
				//TODO: implement real merge
				return new MergeResult(null,
						MergeResult.MergeStatus.NOT_SUPPORTED, mergeStrategy,
						"only already-up-to-date and fast forward merges are available");

			}
			checkoutNewHead(revWalk, headCommit, newHeadCommit);

			// update the HEAD
			return updateHead(refLogMessage, newHeadCommit, mergeStatus);

		} catch (IOException e) {
			throw new JGitInternalException(
					"Exception caught during execution of merge command", e);
		}
	}

	private void checkoutNewHead(RevWalk revWalk, RevCommit headCommit,
			Commit newHeadCommit) throws IOException {
		GitIndex index = repo.getIndex();

		File workDir = repo.getWorkDir();
		if (workDir != null) {
			WorkDirCheckout workDirCheckout = new WorkDirCheckout(repo,
					workDir, headCommit.asCommit(revWalk).getTree(), index,
					newHeadCommit.getTree());
			workDirCheckout.setFailOnConflict(true);
			try {
				workDirCheckout.checkout();
			} catch (CheckoutConflictException e) {
				throw new JGitInternalException(
						"Couldn't check out because of conflicts", e);
			}
			index.write();
		}
	}

	private MergeResult updateHead(StringBuilder refLogMessage,
			Commit newHeadCommit, MergeResult.MergeStatus mergeStatus)
			throws IOException, ConcurrentRefUpdateException {
		RefUpdate refUpdate = repo.updateRef(Constants.HEAD);
		refUpdate.setNewObjectId(newHeadCommit.getCommitId());
		refUpdate.setRefLogMessage(refLogMessage.toString(), false);
		Result rc = refUpdate.update();
		switch (rc) {
		case NEW:
		case FAST_FORWARD:
			setCallable(false);
			return new MergeResult(newHeadCommit.getCommitId(), mergeStatus,
					mergeStrategy);
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(
					"Could lock HEAD during commit", refUpdate.getRef(), rc);
		default:
			throw new JGitInternalException("Updating the ref "
					+ Constants.HEAD + " to "
					+ newHeadCommit.getCommitId().toString()
					+ " failed. ReturnCode from RefUpdate.update() was " + rc);
		}
	}

	/**
	 *
	 * @param mergeStrategy
	 *            the {@link MergeStrategy} to be used
	 * @return {@code this}
	 */
	public MergeCommand setStrategy(MergeStrategy mergeStrategy) {
		checkCallable();
		this.mergeStrategy = mergeStrategy;
		return this;
	}

	/**
	 *
	 * @param commit
	 *            a reference to the commit which is merged with the current
	 *            head
	 * @return {@code this}
	 */
	public MergeCommand setCommit(Ref commit) {
		checkCallable();
		this.commit = commit;
		return this;
	}

}
