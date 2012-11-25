/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FileUtils;

/**
 * Checkout a branch to the working tree.
 * <p>
 * Examples (<code>git</code> is a {@link Git} instance):
 * <p>
 * Check out an existing branch:
 *
 * <pre>
 * git.checkout().setName(&quot;feature&quot;).call();
 * </pre>
 * <p>
 * Check out paths from the index:
 *
 * <pre>
 * git.checkout().addPath(&quot;file1.txt&quot;).addPath(&quot;file2.txt&quot;).call();
 * </pre>
 * <p>
 * Check out a path from a commit:
 *
 * <pre>
 * git.checkout().setStartPoint(&quot;HEAD&circ;&quot;).addPath(&quot;file1.txt&quot;).call();
 * </pre>
 *
 * <p>
 * Create a new branch and check it out:
 *
 * <pre>
 * git.checkout().setCreateBranch(true).setName(&quot;newbranch&quot;).call();
 * </pre>
 * <p>
 * Create a new tracking branch for a remote branch and check it out:
 *
 * <pre>
 * git.checkout().setCreateBranch(true).setName(&quot;stable&quot;)
 * 		.setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
 * 		.setStartPoint(&quot;origin/stable&quot;).call();
 * </pre>
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
 *      >Git documentation about Checkout</a>
 */
public class CheckoutCommand extends GitCommand<Ref> {

	/**
	 * Stage to check out, see {@link CheckoutCommand#setStage(Stage)}.
	 */
	public static enum Stage {
		/**
		 * Base stage (#1)
		 */
		BASE(DirCacheEntry.STAGE_1),

		/**
		 * Ours stage (#2)
		 */
		OURS(DirCacheEntry.STAGE_2),

		/**
		 * Theirs stage (#3)
		 */
		THEIRS(DirCacheEntry.STAGE_3);

		private final int number;

		private Stage(int number) {
			this.number = number;
		}
	}

	private String name;

	private boolean force = false;

	private boolean createBranch = false;

	private CreateBranchCommand.SetupUpstreamMode upstreamMode;

	private String startPoint = null;

	private RevCommit startCommit;

	private Stage checkoutStage = null;

	private CheckoutResult status;

	private List<String> paths;

	private boolean checkoutAllPaths;

	/**
	 * @param repo
	 */
	protected CheckoutCommand(Repository repo) {
		super(repo);
		this.paths = new LinkedList<String>();
	}

	/**
	 * @throws RefAlreadyExistsException
	 *             when trying to create (without force) a branch with a name
	 *             that already exists
	 * @throws RefNotFoundException
	 *             if the start point or branch can not be found
	 * @throws InvalidRefNameException
	 *             if the provided name is <code>null</code> or otherwise
	 *             invalid
	 * @throws CheckoutConflictException
	 *             if the checkout results in a conflict
	 * @return the newly created branch
	 */
	public Ref call() throws GitAPIException, RefAlreadyExistsException,
			RefNotFoundException, InvalidRefNameException,
			CheckoutConflictException {
		checkCallable();
		processOptions();
		try {
			if (checkoutAllPaths || !paths.isEmpty()) {
				checkoutPaths();
				status = new CheckoutResult(Status.OK, paths);
				setCallable(false);
				return null;
			}

			if (createBranch) {
				Git git = new Git(repo);
				CreateBranchCommand command = git.branchCreate();
				command.setName(name);
				command.setStartPoint(getStartPoint().name());
				if (upstreamMode != null)
					command.setUpstreamMode(upstreamMode);
				command.call();
			}

			Ref headRef = repo.getRef(Constants.HEAD);
			String shortHeadRef = getShortBranchName(headRef);
			String refLogMessage = "checkout: moving from " + shortHeadRef; //$NON-NLS-1$
			ObjectId branch = repo.resolve(name);
			if (branch == null)
				throw new RefNotFoundException(MessageFormat.format(JGitText
						.get().refNotResolved, name));

			RevWalk revWalk = new RevWalk(repo);
			AnyObjectId headId = headRef.getObjectId();
			RevCommit headCommit = headId == null ? null : revWalk
					.parseCommit(headId);
			RevCommit newCommit = revWalk.parseCommit(branch);
			RevTree headTree = headCommit == null ? null : headCommit.getTree();
			DirCacheCheckout dco;
			DirCache dc = repo.lockDirCache();
			try {
				dco = new DirCacheCheckout(repo, headTree, dc,
						newCommit.getTree());
				dco.setFailOnConflict(true);
				try {
					dco.checkout();
				} catch (org.eclipse.jgit.errors.CheckoutConflictException e) {
					status = new CheckoutResult(Status.CONFLICTS,
							dco.getConflicts());
					throw new CheckoutConflictException(dco.getConflicts(), e);
				}
			} finally {
				dc.unlock();
			}
			Ref ref = repo.getRef(name);
			if (ref != null && !ref.getName().startsWith(Constants.R_HEADS))
				ref = null;
			String toName = Repository.shortenRefName(name);
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, ref == null);
			refUpdate.setForceUpdate(force);
			refUpdate.setRefLogMessage(refLogMessage + " to " + toName, false); //$NON-NLS-1$
			Result updateResult;
			if (ref != null)
				updateResult = refUpdate.link(ref.getName());
			else {
				refUpdate.setNewObjectId(newCommit);
				updateResult = refUpdate.forceUpdate();
			}

			setCallable(false);

			boolean ok = false;
			switch (updateResult) {
			case NEW:
				ok = true;
				break;
			case NO_CHANGE:
			case FAST_FORWARD:
			case FORCED:
				ok = true;
				break;
			default:
				break;
			}

			if (!ok)
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().checkoutUnexpectedResult, updateResult.name()));


			if (!dco.getToBeDeleted().isEmpty()) {
				status = new CheckoutResult(Status.NONDELETED,
						dco.getToBeDeleted());
			} else
				status = new CheckoutResult(new ArrayList<String>(dco
						.getUpdated().keySet()), dco.getRemoved());

			return ref;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		} finally {
			if (status == null)
				status = CheckoutResult.ERROR_RESULT;
		}
	}

	private String getShortBranchName(Ref headRef) {
		if (headRef.getTarget().getName().equals(headRef.getName()))
			return headRef.getTarget().getObjectId().getName();
		return Repository.shortenRefName(headRef.getTarget().getName());
	}

	/**
	 * Add a single path to the list of paths to check out. To check out all
	 * paths, use {@link #setAllPaths(boolean)}.
	 * <p>
	 * If this option is set, neither the {@link #setCreateBranch(boolean)} nor
	 * {@link #setName(String)} option is considered. In other words, these
	 * options are exclusive.
	 *
	 * @param path
	 *            path to update in the working tree and index
	 * @return {@code this}
	 */
	public CheckoutCommand addPath(String path) {
		checkCallable();
		this.paths.add(path);
		return this;
	}

	/**
	 * Set whether to checkout all paths.
	 * <p>
	 * This options should be used when you want to do a path checkout on the
	 * entire repository and so calling {@link #addPath(String)} is not possible
	 * since empty paths are not allowed.
	 * <p>
	 * If this option is set, neither the {@link #setCreateBranch(boolean)} nor
	 * {@link #setName(String)} option is considered. In other words, these
	 * options are exclusive.
	 *
	 * @param all
	 *            <code>true</code> to checkout all paths, <code>false</code>
	 *            otherwise
	 * @return {@code this}
	 * @since 2.0
	 */
	public CheckoutCommand setAllPaths(boolean all) {
		checkoutAllPaths = all;
		return this;
	}

	/**
	 * Checkout paths into index and working directory
	 *
	 * @return this instance
	 * @throws IOException
	 * @throws RefNotFoundException
	 */
	protected CheckoutCommand checkoutPaths() throws IOException,
			RefNotFoundException {
		RevWalk revWalk = new RevWalk(repo);
		DirCache dc = repo.lockDirCache();
		try {
			TreeWalk treeWalk = new TreeWalk(revWalk.getObjectReader());
			treeWalk.setRecursive(true);
			if (!checkoutAllPaths)
				treeWalk.setFilter(PathFilterGroup.createFromStrings(paths));
			try {
				if (isCheckoutIndex())
					checkoutPathsFromIndex(treeWalk, dc);
				else {
					RevCommit commit = revWalk.parseCommit(getStartPoint());
					checkoutPathsFromCommit(treeWalk, dc, commit);
				}
			} finally {
				treeWalk.release();
			}
		} finally {
			dc.unlock();
			revWalk.release();
		}
		return this;
	}

	private void checkoutPathsFromIndex(TreeWalk treeWalk, DirCache dc)
			throws IOException {
		DirCacheIterator dci = new DirCacheIterator(dc);
		treeWalk.addTree(dci);

		final ObjectReader r = treeWalk.getObjectReader();
		DirCacheEditor editor = dc.editor();
		while (treeWalk.next()) {
			DirCacheEntry entry = dci.getDirCacheEntry();
			// Only add one edit per path
			if (entry != null && entry.getStage() > DirCacheEntry.STAGE_1)
				continue;
			editor.add(new PathEdit(treeWalk.getPathString()) {
				public void apply(DirCacheEntry ent) {
					int stage = ent.getStage();
					if (stage > DirCacheEntry.STAGE_0) {
						if (checkoutStage != null) {
							if (stage == checkoutStage.number)
								checkoutPath(ent, r);
						} else {
							UnmergedPathException e = new UnmergedPathException(
									ent);
							throw new JGitInternalException(e.getMessage(), e);
						}
					} else {
						checkoutPath(ent, r);
					}
				}
			});
		}
		editor.commit();
	}

	private void checkoutPathsFromCommit(TreeWalk treeWalk, DirCache dc,
			RevCommit commit) throws IOException {
		treeWalk.addTree(commit.getTree());
		final ObjectReader r = treeWalk.getObjectReader();
		DirCacheEditor editor = dc.editor();
		while (treeWalk.next()) {
			final ObjectId blobId = treeWalk.getObjectId(0);
			final FileMode mode = treeWalk.getFileMode(0);
			editor.add(new PathEdit(treeWalk.getPathString()) {
				public void apply(DirCacheEntry ent) {
					ent.setObjectId(blobId);
					ent.setFileMode(mode);
					checkoutPath(ent, r);
				}
			});
		}
		editor.commit();
	}

	private void checkoutPath(DirCacheEntry entry, ObjectReader reader) {
		File file = new File(repo.getWorkTree(), entry.getPathString());
		File parentDir = file.getParentFile();
		try {
			FileUtils.mkdirs(parentDir, true);
			DirCacheCheckout.checkoutEntry(repo, file, entry, reader);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().checkoutConflictWithFile,
					entry.getPathString()), e);
		}
	}

	private boolean isCheckoutIndex() {
		return startCommit == null && startPoint == null;
	}

	private ObjectId getStartPoint() throws AmbiguousObjectException,
			RefNotFoundException, IOException {
		if (startCommit != null)
			return startCommit.getId();
		ObjectId result = null;
		try {
			result = repo.resolve((startPoint == null) ? Constants.HEAD
					: startPoint);
		} catch (AmbiguousObjectException e) {
			throw e;
		}
		if (result == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved,
					startPoint != null ? startPoint : Constants.HEAD));
		return result;
	}

	private void processOptions() throws InvalidRefNameException {
		if ((!checkoutAllPaths && paths.isEmpty())
				&& (name == null || !Repository
						.isValidRefName(Constants.R_HEADS + name)))
			throw new InvalidRefNameException(MessageFormat.format(JGitText
					.get().branchNameInvalid, name == null ? "<null>" : name)); //$NON-NLS-1$
	}

	/**
	 * Specify the name of the branch or commit to check out, or the new branch
	 * name.
	 * <p>
	 * When only checking out paths and not switching branches, use
	 * {@link #setStartPoint(String)} or {@link #setStartPoint(RevCommit)} to
	 * specify from which branch or commit to check out files.
	 * <p>
	 * When {@link #setCreateBranch(boolean)} is set to <code>true</code>, use
	 * this method to set the name of the new branch to create and
	 * {@link #setStartPoint(String)} or {@link #setStartPoint(RevCommit)} to
	 * specify the start point of the branch.
	 *
	 * @param name
	 *            the name of the branch or commit
	 * @return this instance
	 */
	public CheckoutCommand setName(String name) {
		checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * Specify whether to create a new branch.
	 * <p>
	 * If <code>true</code> is used, the name of the new branch must be set
	 * using {@link #setName(String)}. The commit at which to start the new
	 * branch can be set using {@link #setStartPoint(String)} or
	 * {@link #setStartPoint(RevCommit)}; if not specified, HEAD is used. Also
	 * see {@link #setUpstreamMode} for setting up branch tracking.
	 *
	 * @param createBranch
	 *            if <code>true</code> a branch will be created as part of the
	 *            checkout and set to the specified start point
	 * @return this instance
	 */
	public CheckoutCommand setCreateBranch(boolean createBranch) {
		checkCallable();
		this.createBranch = createBranch;
		return this;
	}

	/**
	 * Specify to force the ref update in case of a branch switch.
	 *
	 * @param force
	 *            if <code>true</code> and the branch with the given name
	 *            already exists, the start-point of an existing branch will be
	 *            set to a new start-point; if false, the existing branch will
	 *            not be changed
	 * @return this instance
	 */
	public CheckoutCommand setForce(boolean force) {
		checkCallable();
		this.force = force;
		return this;
	}

	/**
	 * Set the name of the commit that should be checked out.
	 * <p>
	 * When checking out files and this is not specified or <code>null</code>,
	 * the index is used.
	 * <p>
	 * When creating a new branch, this will be used as the start point. If not
	 * specified or <code>null</code>, the current HEAD is used.
	 *
	 * @param startPoint
	 *            commit name to check out
	 * @return this instance
	 */
	public CheckoutCommand setStartPoint(String startPoint) {
		checkCallable();
		this.startPoint = startPoint;
		this.startCommit = null;
		checkOptions();
		return this;
	}

	/**
	 * Set the commit that should be checked out.
	 * <p>
	 * When creating a new branch, this will be used as the start point. If not
	 * specified or <code>null</code>, the current HEAD is used.
	 * <p>
	 * When checking out files and this is not specified or <code>null</code>,
	 * the index is used.
	 *
	 * @param startCommit
	 *            commit to check out
	 * @return this instance
	 */
	public CheckoutCommand setStartPoint(RevCommit startCommit) {
		checkCallable();
		this.startCommit = startCommit;
		this.startPoint = null;
		checkOptions();
		return this;
	}

	/**
	 * When creating a branch with {@link #setCreateBranch(boolean)}, this can
	 * be used to configure branch tracking.
	 *
	 * @param mode
	 *            corresponds to the --track/--no-track options; may be
	 *            <code>null</code>
	 * @return this instance
	 */
	public CheckoutCommand setUpstreamMode(
			CreateBranchCommand.SetupUpstreamMode mode) {
		checkCallable();
		this.upstreamMode = mode;
		return this;
	}

	/**
	 * When checking out the index, check out the specified stage (ours or
	 * theirs) for unmerged paths.
	 * <p>
	 * This can not be used when checking out a branch, only when checking out
	 * the index.
	 *
	 * @param stage
	 *            the stage to check out
	 * @return this
	 */
	public CheckoutCommand setStage(Stage stage) {
		checkCallable();
		this.checkoutStage = stage;
		checkOptions();
		return this;
	}

	/**
	 * @return the result, never <code>null</code>
	 */
	public CheckoutResult getResult() {
		if (status == null)
			return CheckoutResult.NOT_TRIED_RESULT;
		return status;
	}

	private void checkOptions() {
		if (checkoutStage != null && !isCheckoutIndex())
			throw new IllegalStateException(
					"Checking out ours/theirs is only possible when checking out index, "
							+ "not when switching branches.");
	}
}
