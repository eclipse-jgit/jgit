/*
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

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/**
 * A class used to execute a {@code Branch} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-branch.html"
 *      >Git documentation about branch</a>
 */
public class BranchCommand extends GitCommand<Ref> {
	private String id;

	private String name;

	private boolean delete;

	private boolean forceUpdate;

	private boolean remote;

	private String oldBranch;

	private String newBranch;

	/**
	 * @param repo
	 */
	protected BranchCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code branch} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @return a {@link Ref} object representing a successful branch creation,
	 *         move or deletion.
	 * @throws NoHeadException
	 *             when called on a git repo without a HEAD reference
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}. Expect only
	 *             {@code IOException's} to be wrapped.
	 */
	public Ref call() throws JGitInternalException,
			ConcurrentRefUpdateException,
			NoHeadException {
		checkCallable();

		Ref ref = null;

		try {

			if (delete)
				ref = delete();
			else if (newBranch != null)
				ref = rename();
			else
				ref = create();

		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfBranchCommand,
					e);
		}

		return ref;
	}

	/**
	 * @param oldBranchName
	 * @param newBranchName
	 *
	 * @return {@code this}
	 */
	public BranchCommand rename(String oldBranchName, String newBranchName) {
		this.oldBranch = oldBranchName;
		this.newBranch = newBranchName;
		return this;
	}

	private Ref delete() throws IOException {
		if (name == null)
			throw new JGitInternalException("branch name is null");

		String current = repo.getBranch();
		ObjectId head = repo.resolve(Constants.HEAD);
		if (current.equals(name)) {
			// TODO throw exception?
		}
		RefUpdate update = repo.updateRef((remote ? Constants.R_REMOTES
				: Constants.R_HEADS) + name);
		update.setNewObjectId(head);
		update.setForceUpdate(forceUpdate || remote);
		Result result = update.delete();
		if (result == Result.REJECTED) {
			// TODO throw exception, branch not an ancester of HEAD
		} else if (result == Result.NEW) {
			// TODO throw exception, branch not found...
		}

		return update.getRef();
	}

	private Ref rename() throws IOException {
		String source = oldBranch;
		String destination = newBranch;
		if (oldBranch == null) { // use HEAD if the oldBranch isn't specified
			final Ref head = repo.getRef(Constants.HEAD);
			if (head != null && head.isSymbolic())
				source = head.getLeaf().getName();
			else
				throw new JGitInternalException("can't rename detached HEAD");
		} else {
			final Ref old = repo.getRef(source);
			if (old == null)
				throw new JGitInternalException("branch doesn't exist");
		}

		if (!destination.startsWith(Constants.R_HEADS))
			destination = Constants.R_HEADS + destination;

		RefRename r = repo.renameRef(source, destination);
		if (r.rename() != Result.RENAMED)
			throw new JGitInternalException("branch rename fail");

		final Ref ref = repo.getRef(destination);
		return ref;
	}

	private Ref create() throws IOException {
		// check for any issues...
		if (name == null)
			throw new JGitInternalException("branch name is null");

		String startBranch = id;
		if (startBranch == null)
			startBranch = Constants.HEAD;

		// resolve the object
		ObjectId startAt = repo.resolve(startBranch + "^0");

		// create the ref (branch)
		String branchName = name;
		// the branch name needs to properly be suffixed if it isn't already
		if (!branchName.startsWith(Constants.R_HEADS))
			branchName = Constants.R_HEADS + branchName;

		if (!Repository.isValidRefName(branchName))
			throw new JGitInternalException("branch name is invalid");

		if (!forceUpdate && repo.resolve(branchName) != null)
			throw new JGitInternalException("branch name already exists");

		RefUpdate refUpdate = repo.updateRef(branchName);
		refUpdate.setForceUpdate(forceUpdate);
		refUpdate.setNewObjectId(startAt);
		// refUpdate.setRefLogMessage() TODO do we want to have some msg

		Result result;
		result = refUpdate.update();

		if (result == Result.REJECTED)
			throw new JGitInternalException("fail", new IOException(refUpdate
					.getResult().name()));

		return null;
	}

	/**
	 * @param name
	 *            the branch name used for create or delete operations
	 * @return {@code this}
	 */
	public BranchCommand setName(String name) {
		checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * @return the branch name used for create or delete operations
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return whether the branch should be deleted
	 */
	public boolean isDelete() {
		return delete;
	}

	/**
	 * If set to true the branch command will attempt to delete a branch. This
	 * corresponds to the parameter -d on the command line.
	 *
	 * @param delete
	 * @return {@code this}
	 */
	public BranchCommand setDelete(boolean delete) {
		this.delete = delete;
		return this;
	}

	/**
	 * @return include remote branches in delete or list operations
	 */
	public boolean isRemote() {
		return remote;
	}

	/**
	 * If set to true remote branches will be included in delete or list
	 * operations. This corresponds to the parameter -r on the command line.
	 *
	 * @param remote
	 * @return {@code this}
	 */
	public BranchCommand setRemote(boolean remote) {
		this.remote = remote;
		return this;
	}

	/**
	 * @return the object id of the tag
	 */
	public String getObjectId() {
		return id;
	}

	/**
	 * Sets the object id of the tag. If the object id is null, the commit
	 * pointed to from HEAD will be used.
	 *
	 * @param id
	 * @return {@code this}
	 */
	public BranchCommand setObjectId(String id) {
		this.id = id;
		return this;
	}

	/**
	 * @return is this a force update
	 */
	public boolean isForceUpdate() {
		return forceUpdate;
	}

	/**
	 * If set to true the Branch command may replace an existing branch. This
	 * corresponds to the parameter -f on the command line.
	 *
	 * @param forceUpdate
	 * @return {@code this}
	 */
	public BranchCommand setForceUpdate(boolean forceUpdate) {
		this.forceUpdate = forceUpdate;
		return this;
	}

}
