/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Used to create a local branch.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-branch.html"
 *      >Git documentation about Branch</a>
 */
public class CreateBranchCommand extends GitCommand<Ref> {
	private String name;

	private boolean force = false;

	private SetupUpstreamMode upstreamMode;

	private String startPoint;

	/**
	 * The modes available for setting up the upstream configuration
	 * (corresponding to the --set-upstram, --track, --no-track options
	 *
	 */
	public enum SetupUpstreamMode {
		/**
		 * Corresponds to the --track option
		 */
		TRACK,
		/**
		 * Corresponds to the --no-track option
		 */
		NOTRACK,
		/**
		 * Corresponds to the --set-upstream option
		 */
		SETUPSTREAM;
	}

	/**
	 * @param repo
	 */
	protected CreateBranchCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @throws RefAlreadyExistsException
	 *             when trying to create (without force) a branch with a name
	 *             that already exists
	 * @throws RefNotFoundException
	 *             if the start point can not be resolved
	 * @throws InvalidRefNameException
	 *             if the provided name is <code>null</code> or otherwise
	 *             invalid
	 * @return the newly created branch
	 */
	public Ref call() throws JGitInternalException, RefAlreadyExistsException,
			RefNotFoundException, InvalidRefNameException {
		checkCallable();
		processOptions();
		try {
			boolean exists = repo.getRef(name) != null;
			if (!force && exists)
				throw new RefAlreadyExistsException(MessageFormat.format(
						JGitText.get().refAlreadExists, name));

			if (startPoint == null)
				startPoint = repo.getFullBranch();

			ObjectId startAt;
			String baseBranch = "";
			String baseTag = "";
			String baseCommit = "";
			if (ObjectId.isId(startPoint)) {
				// the start-point looks like a commit-id
				RevCommit commit = new RevWalk(repo).parseCommit(ObjectId
						.fromString(startPoint));
				startAt = commit.getId();
				baseCommit = commit.getShortMessage();
			} else {
				// the start-point might be a branch or a tag
				Ref ref = repo.getRef(startPoint);
				if (ref == null)
					throw new RefNotFoundException(MessageFormat.format(
							JGitText.get().refNotResolved, startPoint));
				if (ref.getName().startsWith(Constants.R_TAGS)) {
					baseTag = ref.getName();
					RevCommit commit = new RevWalk(repo).parseCommit(ref
							.getLeaf().getObjectId());
					startAt = commit.getId();
				} else if (ref.getName().startsWith(Constants.R_REFS)) {
					baseBranch = ref.getName();
					startAt = new RevWalk(repo).parseCommit(ref.getObjectId());
				} else {
					throw new RefNotFoundException(MessageFormat.format(
							JGitText.get().refNotResolved, startPoint));
				}
			}

			String refLogMessage;
			if (exists) {
				if (baseBranch.length() > 0)
					refLogMessage = "branch: Reset start-point to branch "
							+ baseBranch;
				else if (baseTag.length() > 0)
					refLogMessage = "branch: Reset start-point to tag "
							+ baseTag;
				else
					refLogMessage = "branch: Reset start-point to commit "
							+ baseCommit;
			} else {
				if (baseBranch.length() > 0)
					refLogMessage = "branch: Created from branch " + baseBranch;
				else if (baseTag.length() > 0)
					refLogMessage = "branch: Created from tag " + baseTag;
				else
					refLogMessage = "branch: Created from commit " + baseCommit;
			}

			RefUpdate updateRef = repo.updateRef(Constants.R_HEADS + name);
			updateRef.setNewObjectId(startAt);
			updateRef.setRefLogMessage(refLogMessage, false);
			Result updateResult;
			if (exists && force)
				updateResult = updateRef.forceUpdate();
			else
				updateResult = updateRef.update();

			setCallable(false);

			boolean ok = false;
			switch (updateResult) {
			case NEW:
				ok = !exists;
				break;
			case NO_CHANGE:
			case FAST_FORWARD:
			case FORCED:
				ok = exists;
				break;
			default:
				break;
			}

			if (!ok)
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().createBranchUnexpectedResult, updateResult
						.name()));

			Ref result = repo.getRef(name);
			if (result == null)
				throw new JGitInternalException(
						JGitText.get().createBranchFailedUnknownReason);

			if (baseBranch.length() == 0) {
				return result;
			}

			// if we are based on another branch, see
			// if we need to configure upstream configuration: first check
			// whether the setting was done explicitly
			boolean doConfigure;
			if (upstreamMode == SetupUpstreamMode.SETUPSTREAM
					|| upstreamMode == SetupUpstreamMode.TRACK)
				// explicitly set to configure
				doConfigure = true;
			else if (upstreamMode == SetupUpstreamMode.NOTRACK)
				// explicitly set to not configure
				doConfigure = false;
			else {
				// if there was no explicit setting, check the configuration
				String autosetupflag = repo.getConfig().getString(
						ConfigConstants.CONFIG_BRANCH_SECTION, null,
						ConfigConstants.CONFIG_KEY_AUTOSETUPMERGE);
				if ("false".equals(autosetupflag)) {
					doConfigure = false;
				} else if ("always".equals(autosetupflag)) {
					doConfigure = true;
				} else {
					// in this case, the default is to configure
					// only in case the base branch was a remote branch
					doConfigure = baseBranch.startsWith(Constants.R_REMOTES);
				}
			}

			if (doConfigure) {
				StoredConfig config = repo.getConfig();
				String[] tokens = baseBranch.split("/", 4);
				boolean isRemote = tokens[1].equals("remotes");
				if (isRemote) {
					// refs/remotes/<remote name>/<branch>
					String remoteName = tokens[2];
					String branchName = tokens[3];
					config
							.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
									name, ConfigConstants.CONFIG_KEY_REMOTE,
									remoteName);
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
							name, ConfigConstants.CONFIG_KEY_MERGE,
							Constants.R_HEADS + branchName);
				} else {
					// set "." as remote
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
							name, ConfigConstants.CONFIG_KEY_REMOTE, ".");
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
							name, ConfigConstants.CONFIG_KEY_MERGE, baseBranch);
				}
				config.save();
			}
			return result;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private void processOptions() throws InvalidRefNameException {
		if (name == null
				|| !Repository.isValidRefName(Constants.R_HEADS + name))
			throw new InvalidRefNameException(MessageFormat.format(JGitText
					.get().branchNameInvalid, name == null ? "<null>" : name));
	}

	/**
	 * @param name
	 *            the name of the new branch
	 * @return this instance
	 */
	public CreateBranchCommand setName(String name) {
		checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * @param force
	 *            if <code>true</code> and the branch with the given name
	 *            already exists, the start-point of an existing branch will be
	 *            set to a new start-point; if false, the existing branch will
	 *            not be changed
	 * @return this instance
	 */
	public CreateBranchCommand setForce(boolean force) {
		checkCallable();
		this.force = force;
		return this;
	}

	/**
	 * @param startPoint
	 *            corresponds to the start-point option; if <code>null</code>,
	 *            the current HEAD will be used
	 * @return this instance
	 */
	public CreateBranchCommand setStartPoint(String startPoint) {
		checkCallable();
		this.startPoint = startPoint;
		return this;
	}

	/**
	 * @param mode
	 *            corresponds to the --track/--no-track/--set-upstream options;
	 *            may be <code>null</code>
	 * @return this instance
	 */
	public CreateBranchCommand setUpstreamMode(SetupUpstreamMode mode) {
		checkCallable();
		this.upstreamMode = mode;
		return this;
	}

	/**
	 * Convenience method to set important parameters at once
	 *
	 * @param name
	 *            see {@link #setName(String)}
	 * @param force
	 *            see {@link #setForce(boolean)}
	 * @param startPoint
	 *            may be <code>null</code> see {@link #setStartPoint(String)}
	 * @param mode
	 *            may be <code>null</code> see
	 *            {@link #setUpstreamMode(SetupUpstreamMode)}
	 * @return this instance
	 */
	public CreateBranchCommand setParameters(String name, boolean force,
			String startPoint, SetupUpstreamMode mode) {
		setName(name);
		setForce(force);
		setStartPoint(startPoint);
		setUpstreamMode(mode);
		return this;
	}
}
