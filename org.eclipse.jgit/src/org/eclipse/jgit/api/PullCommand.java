/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 *
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
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;

/**
 * The Pull command
 */
public class PullCommand extends GitCommand<PullResult> {
	private int timeout = 0;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * @param repo
	 */
	protected PullCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @param timeout
	 *            in seconds
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * @param monitor
	 *            a progress monitor
	 */
	public void setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
	}

	public PullResult call() throws WrongRepositoryStateException,
			InvalidConfigurationException, DetachedHeadException,
			InvalidRemoteException, CanceledException {
		checkCallable();

		monitor.beginTask(JGitText.get().pullTaskName, 2);

		String branchName;
		try {
			if (!repo.getFullBranch().startsWith(Constants.R_HEADS)) {
				// we can not pull if HEAD is detached
				throw new DetachedHeadException();
			}
			branchName = repo.getBranch();
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfPullCommand,
					e);
		}

		if (!repo.getRepositoryState().equals(RepositoryState.SAFE))
			throw new WrongRepositoryStateException(MessageFormat.format(
					JGitText.get().cannotPullOnARepoWithState, repo
							.getRepositoryState().name()));

		// get the configured remote for the currently checked out branch
		// stored in configuration key branch.<branch name>.remote
		Config repoConfig = repo.getConfig();
		final String remote = repoConfig.getString("branch", branchName,
				"remote");
		if (remote == null) {
			String missingKey = "branch" + "." + branchName + "." + "remote";
			throw new InvalidConfigurationException(MessageFormat.format(
					JGitText.get().missingConfigurationForKey, missingKey));
		}

		// get the name of the branch in the remote repository
		// stored in configuration key branch.<branch name>.merge
		final String remoteBranchName = repoConfig.getString("branch",
				branchName, "merge");
		if (remoteBranchName == null) {
			String missingKey = "branch" + "." + branchName + "." + "merge";
			throw new InvalidConfigurationException(MessageFormat.format(
					JGitText.get().missingConfigurationForKey, missingKey));
		}

		if (monitor.isCancelled())
			throw new CanceledException(MessageFormat.format(
					JGitText.get().operationCanceled,
					JGitText.get().pullTaskName));
		// now we can try and fetch
		FetchCommand fetch = new FetchCommand(repo);
		fetch.setRemote(remote);
		if (monitor != null)
			fetch.setProgressMonitor(monitor);
		fetch.setTimeout(this.timeout);

		FetchResult fetchRes = fetch.call();

		monitor.update(1);

		// we check the updates to see which of the updated branches corresponds
		// to the remote branch name
		String remoteTrackingBranch = null;
		for (TrackingRefUpdate update : fetchRes.getTrackingRefUpdates()) {
			if (update.getRemoteName().equals(remoteBranchName)) {
				remoteTrackingBranch = update.getLocalName();
				break;
			}
		}

		if (remoteTrackingBranch == null) {
			// TODO: we should be able to get the mapping also if nothing was
			// updated by the fetch; for the time being, use the naming
			// convention as fall back
			remoteTrackingBranch = Constants.R_REMOTES + remote + '/'
					+ branchName;
		}

		Ref mergeBranch = null;
		try {
			mergeBranch = repo.getRef(remoteTrackingBranch);
			if (mergeBranch == null) {
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().branchNotFound, remoteTrackingBranch));

			}
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().branchNotFound, remoteTrackingBranch));
		}

		if (monitor.isCancelled())
			throw new CanceledException(MessageFormat.format(
					JGitText.get().operationCanceled,
					JGitText.get().pullTaskName));

		MergeCommand merge = new MergeCommand(repo);
		merge.include(mergeBranch);
		MergeResult mergeRes;
		try {
			mergeRes = merge.call();
			monitor.update(1);
		} catch (NoHeadException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConcurrentRefUpdateException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (CheckoutConflictException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (InvalidMergeHeadsException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (WrongRepositoryStateException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (NoMessageException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		monitor.endTask();
		return new PullResult(fetchRes, remote, mergeRes);
	}
}
