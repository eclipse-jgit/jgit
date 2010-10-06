/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Branch} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-branch.html"
 *      >Git documentation about Branch</a>
 */
public class BranchCommand extends GitCommand<List<Ref>> {
	/**
	 * The modes available for listing branches (corresponding to the -r and -a
	 * options)
	 */
	public enum ListMode {
		/**
		 * Corresponds to the -a option
		 */
		ALL,
		/**
		 * Corresponds to the -r option
		 */
		REMOTE;
	}

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

	private enum Mode {
		CREATE, LIST, RENAME, DELETE;
		private final Map<String, Object> myOptions = new HashMap<String, Object>();

		public Map<String, Object> getOptions() {
			return myOptions;
		}
	}

	private Mode mode = Mode.LIST;

	/**
	 * @param repo
	 */
	protected BranchCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @throws NotMergedException
	 *             when trying to delete a branch which has not been merged into
	 *             the currently checked out branch without force
	 * @throws RefAlreadyExistsException
	 *             when trying to create (without force) a branch with a name
	 *             that already exists or when trying to rename (without force)
	 *             a branch to an existing name
	 * @throws NoHeadException
	 *             if rename is tried without specifying the source branch and
	 *             HEAD is detached
	 * @throws IllegalArgumentException
	 *             if arguments are invalid
	 */
	public List<Ref> call() throws JGitInternalException, NotMergedException,
			RefAlreadyExistsException, NoHeadException,
			IllegalArgumentException {
		checkCallable();
		switch (mode) {
		case LIST:
			return list();
		case RENAME:
			return rename();
		case DELETE:
			return delete();
		case CREATE:
			return create();
		}
		throw new JGitInternalException(
				"Branch command not implemented properly"); //$NON-NLS-1$
	}

	private List<Ref> create() throws RefAlreadyExistsException {
		try {
			List<Ref> result = new ArrayList<Ref>(1);
			Map<String, Object> options = mode.getOptions();
			boolean force = options.containsKey("f");
			String name = (String) options.get("branchname");
			if (name == null)
				throw new IllegalArgumentException(
						JGitText.get().createBranchMissingName);
			boolean exists = repo.getRef(name) != null;
			if (!force && exists)
				throw new RefAlreadyExistsException(MessageFormat.format(
						JGitText.get().refAlreadExists, name));

			String startPoint = (String) options.get("start-point");
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
				if (ref.getName().startsWith(Constants.R_TAGS)) {
					baseTag = ref.getName();
					RevCommit commit = new RevWalk(repo).parseCommit(ref
							.getLeaf().getObjectId());
					startAt = commit.getId();
				} else if (ref.getName().startsWith(Constants.R_REFS)) {
					baseBranch = ref.getName();
					startAt = new RevWalk(repo).parseCommit(ref.getObjectId());
				} else {
					throw new IllegalArgumentException();
				}
			}

			String commitMessage;
			if (exists) {
				if (baseBranch.length() > 0)
					commitMessage = "branch: Reset start-point to branch "
							+ baseBranch;
				else if (baseTag.length() > 0)
					commitMessage = "branch: Reset start-point to tag "
							+ baseTag;
				else
					commitMessage = "branch: Reset start-point to commit "
							+ baseCommit;
			} else {
				if (baseBranch.length() > 0)
					commitMessage = "branch: Created from branch " + baseBranch;
				else if (baseTag.length() > 0)
					commitMessage = "branch: Created from tag " + baseTag;
				else
					commitMessage = "branch: Created from commit " + baseCommit;
			}

			RefUpdate updateRef = repo.updateRef(Constants.R_HEADS + name);
			updateRef.setNewObjectId(startAt);
			updateRef.setRefLogMessage(commitMessage, false);
			Result updateResult;
			if (exists && force)
				updateResult = updateRef.forceUpdate();
			else
				updateResult = updateRef.update();

			if ((!exists && updateResult != Result.NEW)
					|| (exists && updateResult != Result.FORCED
							&& updateResult != Result.NO_CHANGE && updateResult != Result.FAST_FORWARD))
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().createBranchUnexpectedResult, updateResult
						.name()));

			if (repo.getRef(name) == null)
				throw new JGitInternalException(
						JGitText.get().createBranchFailedUnknownReason);

			setCallable(false);
			result.add(repo.getRef(name));

			if (baseBranch.length() == 0) {
				return result;
			}

			// if we are based on another branch, see
			// if we need to configure upstream configuration: first check
			// whether the setting was done explicitly
			SetupUpstreamMode explicitMode = (SetupUpstreamMode) options
					.get("upstreamMode");
			boolean doConfigure;
			if (explicitMode == SetupUpstreamMode.SETUPSTREAM
					|| explicitMode == SetupUpstreamMode.TRACK)
				// explicitly set to configure
				doConfigure = true;
			else if (explicitMode == SetupUpstreamMode.NOTRACK)
				// explicitly set to not configure
				doConfigure = false;
			else {
				// if there was no explicit setting, check the configuration
				String autosetupflag = repo.getConfig().getString("branch",
						null, "autosetupmerge");
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
					config.setString("branch", name, "remote", remoteName);
					config.setString("branch", name, "merge", Constants.R_HEADS
							+ branchName);
				} else {
					// refs/heads/<branch>
					config.setString("branch", name, "remote", ".");
					config.setString("branch", name, "merge", baseBranch);
				}
				config.save();
			}
			return result;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private List<Ref> delete() throws NotMergedException {
		Map<String, Object> options = mode.getOptions();
		String[] branchnames = (String[]) options.get("branchname");
		if (branchnames == null)
			return new ArrayList<Ref>(0);
		boolean force = options.containsKey("D");
		try {
			if (!force) {
				RevWalk walk = null;
				RevCommit tip = null;
				walk = new RevWalk(repo);
				tip = walk.parseCommit(repo.resolve(Constants.HEAD));
				for (String branchName : branchnames) {
					if (branchName == null)
						continue;
					Ref currentRef = repo.getRef(branchName);
					if (currentRef == null)
						continue;
					if (walk != null) {
						RevCommit base = walk.parseCommit(repo
								.resolve(branchName));
						if (!walk.isMergedInto(base, tip)) {
							throw new NotMergedException();
						}
					}
				}
			}
			setCallable(false);
			for (String branchName : branchnames) {
				if (branchName == null)
					continue;
				Ref currentRef = repo.getRef(branchName);
				if (currentRef == null)
					continue;
				RefUpdate update = repo.updateRef(currentRef.getName());
				update.setRefLogMessage("branch deleted", false);
				update.setForceUpdate(true);
				Result deleteRes = update.delete();
				if (deleteRes == Result.REJECTED) {
					throw new JGitInternalException(deleteRes.name());
				}
				repo.getConfig().unsetSection("branch", branchName);
				repo.getConfig().save();
			}
			return new ArrayList<Ref>(0);
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private List<Ref> rename() throws RefAlreadyExistsException,
			NoHeadException {
		Map<String, Object> options = mode.getOptions();
		boolean force = options.containsKey("M");
		String newName = (String) options.get("newbranch");
		String oldName = (String) options.get("oldbranch");
		try {
			boolean exists = repo.resolve(newName) != null;
			if (!force && exists)
				throw new RefAlreadyExistsException(MessageFormat.format(
						JGitText.get().refAlreadExists, newName));
			if (oldName != null) {
				Ref ref = repo.getRef(oldName);
				if (ref == null)
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().refNotResolved, oldName));
				if (ref.getName().startsWith(Constants.R_TAGS))
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().renameBranchFailedBecauseTag,
							oldName));
				else
					oldName = ref.getName();
			} else {
				oldName = repo.getFullBranch();
				if (ObjectId.isId(oldName))
					// TODO wait for DetachedHeadException
					throw new NoHeadException(
							"Can not rename currently checked out branch, as HEAD is detached");
			}

			setCallable(false);
			RefRename rename = repo.renameRef(oldName, newName);
			Result renameResult = rename.rename();
			if (Result.RENAMED != renameResult)
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().renameBranchUnexpectedResult, renameResult
						.name()));
			ArrayList<Ref> resultList = new ArrayList<Ref>();
			Ref resultRef = repo.getRef(newName);
			if (resultRef == null)
				throw new JGitInternalException(
						JGitText.get().renameBranchFailedUnknownReason);
			resultList.add(resultRef);
			return resultList;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private List<Ref> list() {
		Map<String, Object> options = mode.getOptions();
		ListMode listMode = (ListMode) options.get("listmode");
		Map<String, Ref> refList;
		try {
			if (listMode == null) {
				refList = repo.getRefDatabase().getRefs(Constants.R_HEADS);
			} else if (listMode == ListMode.REMOTE) {
				refList = repo.getRefDatabase().getRefs(Constants.R_REMOTES);
			} else {
				refList = repo.getRefDatabase().getRefs(Constants.R_HEADS);
				refList.putAll(repo.getRefDatabase().getRefs(
						Constants.R_REMOTES));
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		List<Ref> resultRefs = new ArrayList<Ref>();
		resultRefs.addAll(refList.values());
		Collections.sort(resultRefs, new Comparator<Ref>() {
			public int compare(Ref o1, Ref o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		setCallable(false);
		return resultRefs;
	}

	/**
	 * @param branchnames
	 *            the names of the branches to delete
	 * @param force
	 *            <code>true</code> corresponds to the -D option,
	 *            <code>false</code> to the -d option
	 * @return this instance
	 */
	public BranchCommand setDelete(String[] branchnames, boolean force) {
		checkCallable();
		mode = Mode.DELETE;
		mode.getOptions().clear();
		mode.getOptions().put("branchname", branchnames);
		if (force)
			mode.getOptions().put("D", null);
		return this;
	}

	/**
	 * @param branchname
	 *            the name of the branch to delete, must not be
	 *            <code>null</code>
	 * @param force
	 *            <code>true</code> corresponds to the -D option,
	 *            <code>false</code> to the -d option
	 * @return this instance
	 */
	public BranchCommand setDelete(String branchname, boolean force) {
		return setDelete(new String[] { branchname }, force);
	}

	/**
	 * @param oldbranch
	 *            the name of the branch to rename; may be <code>null</code> if
	 *            the currently checked out branch is to be renamed
	 * @param newbranch
	 *            the new name; must not be <code>null</code>
	 * @param force
	 *            <code>true</code> corresponds to the -M option,
	 *            <code>false</code> to the -m option
	 * @return this instance
	 */
	public BranchCommand setRename(String oldbranch, String newbranch,
			boolean force) {
		checkCallable();
		mode = Mode.RENAME;
		mode.getOptions().clear();
		mode.getOptions().put("newbranch", newbranch);
		if (force)
			mode.getOptions().put("M", null);
		if (oldbranch != null)
			mode.getOptions().put("oldbranch", oldbranch);
		return this;
	}

	/**
	 * @param listMode
	 *            corresponds to the -r/-a options; may be <code>null</code>
	 * @return this instance
	 */
	public BranchCommand setList(ListMode listMode) {
		checkCallable();
		mode = Mode.LIST;
		mode.getOptions().clear();
		mode.getOptions().put("listmode", listMode);
		return this;
	}

	/**
	 * @param name
	 *            the name of the new branch
	 * @param force
	 *            if <code>true</code> and the branch with the given name
	 *            already exists, the start-point of an existing branch will be
	 *            set to a new start-point; if false, the existing branch will
	 *            not be changed
	 * @param startpoint
	 *            corresponds to the start-point option; if <code>null</code>,
	 *            the current HEAD will be used
	 * @param upstreamMode
	 *            corresponds to the --track/--no-track/--set-upstream options;
	 *            may be <code>null</code>
	 * @return this instance
	 */
	public BranchCommand setCreate(String name, boolean force,
			String startpoint, SetupUpstreamMode upstreamMode) {
		checkCallable();
		mode = Mode.CREATE;
		mode.getOptions().clear();
		mode.getOptions().put("branchname", name);
		if (force)
			mode.getOptions().put("f", null);
		mode.getOptions().put("upstreamMode", upstreamMode);
		mode.getOptions().put("start-point", startpoint);
		return this;
	}
}
