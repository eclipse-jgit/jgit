/*
 * Copyright (C) 2010, 2013 Mathias Kinzler <mathias.kinzler@sap.com>
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.RebaseTodoLine.Action;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A class used to execute a {@code Rebase} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 * <p>
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html"
 *      >Git documentation about Rebase</a>
 */
public class RebaseCommand extends GitCommand<RebaseResult> {
	/**
	 * The name of the "rebase-merge" folder for interactive rebases.
	 */
	public static final String REBASE_MERGE = "rebase-merge"; //$NON-NLS-1$

	/**
	 * The name of the "rebase-apply" folder for non-interactive rebases.
	 */
	private static final String REBASE_APPLY = "rebase-apply"; //$NON-NLS-1$

	/**
	 * The name of the "stopped-sha" file
	 */
	public static final String STOPPED_SHA = "stopped-sha"; //$NON-NLS-1$

	private static final String AUTHOR_SCRIPT = "author-script"; //$NON-NLS-1$

	private static final String DONE = "done"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_DATE = "GIT_AUTHOR_DATE"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_EMAIL = "GIT_AUTHOR_EMAIL"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_NAME = "GIT_AUTHOR_NAME"; //$NON-NLS-1$

	private static final String GIT_REBASE_TODO = "git-rebase-todo"; //$NON-NLS-1$

	private static final String HEAD_NAME = "head-name"; //$NON-NLS-1$

	private static final String INTERACTIVE = "interactive"; //$NON-NLS-1$

	private static final String MESSAGE = "message"; //$NON-NLS-1$

	private static final String ONTO = "onto"; //$NON-NLS-1$

	private static final String ONTO_NAME = "onto-name"; //$NON-NLS-1$

	private static final String PATCH = "patch"; //$NON-NLS-1$

	private static final String REBASE_HEAD = "head"; //$NON-NLS-1$

	private static final String AMEND = "amend"; //$NON-NLS-1$

	/**
	 * The available operations
	 */
	public enum Operation {
		/**
		 * Initiates rebase
		 */
		BEGIN,
		/**
		 * Continues after a conflict resolution
		 */
		CONTINUE,
		/**
		 * Skips the "current" commit
		 */
		SKIP,
		/**
		 * Aborts and resets the current rebase
		 */
		ABORT;
	}

	private Operation operation = Operation.BEGIN;

	private RevCommit upstreamCommit;

	private String upstreamCommitName;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private final RevWalk walk;

	private final RebaseState rebaseState;

	private InteractiveHandler interactiveHandler;

	/**
	 * @param repo
	 */
	protected RebaseCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
		rebaseState = new RebaseState(repo.getDirectory());
	}

	/**
	 * Executes the {@code Rebase} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
	 *
	 * @return an object describing the result of this command
	 * @throws GitAPIException
	 * @throws WrongRepositoryStateException
	 * @throws NoHeadException
	 * @throws RefNotFoundException
	 */
	public RebaseResult call() throws GitAPIException, NoHeadException,
			RefNotFoundException, WrongRepositoryStateException {
		RevCommit newHead = null;
		boolean lastStepWasForward = false;
		checkCallable();
		checkParameters();
		try {
			switch (operation) {
			case ABORT:
				try {
					return abort(RebaseResult.ABORTED_RESULT);
				} catch (IOException ioe) {
					throw new JGitInternalException(ioe.getMessage(), ioe);
				}
			case SKIP:
				// fall through
			case CONTINUE:
				String upstreamCommitId = rebaseState.readFile(ONTO);
				try {
					upstreamCommitName = rebaseState.readFile(ONTO_NAME);
				} catch (FileNotFoundException e) {
					// Fall back to commit ID if file doesn't exist (e.g. rebase
					// was started by C Git)
					upstreamCommitName = upstreamCommitId;
				}
				this.upstreamCommit = walk.parseCommit(repo
						.resolve(upstreamCommitId));
				break;
			case BEGIN:
				RebaseResult res = initFilesAndRewind();
				if (res != null)
					return res;
			}

			if (monitor.isCancelled())
				return abort(RebaseResult.ABORTED_RESULT);

			if (operation == Operation.CONTINUE) {
				newHead = continueRebase();

				File amendFile = rebaseState.getFile(AMEND);
				boolean amendExists = amendFile.exists();
				if (amendExists) {
					FileUtils.delete(amendFile);
				}
				if (newHead == null && !amendExists) {
					// continueRebase() returns null only if no commit was
					// neccessary. This means that no changes where left over
					// after resolving all conflicts. In this case, cgit stops
					// and displays a nice message to the user, telling him to
					// either do changes or skip the commit instead of continue.
					return RebaseResult.NOTHING_TO_COMMIT_RESULT;
				}
			}

			if (operation == Operation.SKIP)
				newHead = checkoutCurrentHead();

			ObjectReader or = repo.newObjectReader();

			List<RebaseTodoLine> steps = repo.readRebaseTodo(
					rebaseState.getPath(GIT_REBASE_TODO), false);
			if (isInteractive()) {
				interactiveHandler.prepareSteps(steps);
				repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
						steps, false);
			}
			for (RebaseTodoLine step : steps) {
				popSteps(1);
				Collection<ObjectId> ids = or.resolve(step.getCommit());
				if (ids.size() != 1)
					throw new JGitInternalException(
							"Could not resolve uniquely the abbreviated object ID");
				RevCommit commitToPick = walk
						.parseCommit(ids.iterator().next());
				if (monitor.isCancelled())
					return new RebaseResult(commitToPick);
				try {
					monitor.beginTask(MessageFormat.format(
							JGitText.get().applyingCommit,
							commitToPick.getShortMessage()),
							ProgressMonitor.UNKNOWN);
					// if the first parent of commitToPick is the current HEAD,
					// we do a fast-forward instead of cherry-pick to avoid
					// unnecessary object rewriting
					newHead = tryFastForward(commitToPick);
					lastStepWasForward = newHead != null;
					if (!lastStepWasForward) {
						// TODO if the content of this commit is already merged
						// here we should skip this step in order to avoid
						// confusing pseudo-changed
						String ourCommitName = getOurCommitName();
						CherryPickResult cherryPickResult = new Git(repo)
								.cherryPick().include(commitToPick)
								.setOurCommitName(ourCommitName)
								.setReflogPrefix("rebase:").call(); //$NON-NLS-1$
						switch (cherryPickResult.getStatus()) {
						case FAILED:
							if (operation == Operation.BEGIN)
								return abort(new RebaseResult(
										cherryPickResult.getFailingPaths()));
							else
								return stop(commitToPick);
						case CONFLICTING:
							return stop(commitToPick);
						case OK:
							newHead = cherryPickResult.getNewHead();
						}
					}
					switch (step.getAction()) {
					case PICK:
						continue; // continue rebase process on pick command
					case REWORD:
						String oldMessage = commitToPick.getFullMessage();
						String newMessage = interactiveHandler
								.modifyCommitMessage(oldMessage);
						newHead = new Git(repo).commit().setMessage(newMessage)
								.setAmend(true).call();
						continue;
					case EDIT:
						rebaseState.createFile(AMEND, commitToPick.name());
						return stop(commitToPick);
					case COMMENT:
						break;
					}
				} finally {
					monitor.endTask();
				}
			}
			if (newHead != null) {
				String headName = rebaseState.readFile(HEAD_NAME);
				updateHead(headName, newHead, upstreamCommit);
				FileUtils.delete(rebaseState.getDir(), FileUtils.RECURSIVE);
				if (lastStepWasForward)
					return RebaseResult.FAST_FORWARD_RESULT;
				return RebaseResult.OK_RESULT;
			}
			return RebaseResult.FAST_FORWARD_RESULT;
		} catch (CheckoutConflictException cce) {
			return new RebaseResult(cce.getConflictingPaths());
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private String getOurCommitName() {
		// If onto is different from upstream, this should say "onto", but
		// RebaseCommand doesn't support a different "onto" at the moment.
		String ourCommitName = "Upstream, based on " //$NON-NLS-1$
				+ Repository.shortenRefName(upstreamCommitName);
		return ourCommitName;
	}

	private void updateHead(String headName, RevCommit newHead, RevCommit onto)
			throws IOException {
		// point the previous head (if any) to the new commit

		if (headName.startsWith(Constants.R_REFS)) {
			RefUpdate rup = repo.updateRef(headName);
			rup.setNewObjectId(newHead);
			rup.setRefLogMessage("rebase finished: " + headName + " onto " //$NON-NLS-1$ //$NON-NLS-2$
					+ onto.getName(), false);
			Result res = rup.forceUpdate();
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException("Updating HEAD failed");
			}
			rup = repo.updateRef(Constants.HEAD);
			rup.setRefLogMessage("rebase finished: returning to " + headName, //$NON-NLS-1$
					false);
			res = rup.link(headName);
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException("Updating HEAD failed");
			}
		}
	}

	private RevCommit checkoutCurrentHead() throws IOException, NoHeadException {
		ObjectId headTree = repo.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
		if (headTree == null)
			throw new NoHeadException(
					JGitText.get().cannotRebaseWithoutCurrentHead);
		DirCache dc = repo.lockDirCache();
		try {
			DirCacheCheckout dco = new DirCacheCheckout(repo, dc, headTree);
			dco.setFailOnConflict(false);
			boolean needsDeleteFiles = dco.checkout();
			if (needsDeleteFiles) {
				List<String> fileList = dco.getToBeDeleted();
				for (String filePath : fileList) {
					File fileToDelete = new File(repo.getWorkTree(), filePath);
					if (fileToDelete.exists())
						FileUtils.delete(fileToDelete, FileUtils.RECURSIVE
								| FileUtils.RETRY);
				}
			}
		} finally {
			dc.unlock();
		}
		RevWalk rw = new RevWalk(repo);
		RevCommit commit = rw.parseCommit(repo.resolve(Constants.HEAD));
		rw.release();
		return commit;
	}

	/**
	 * @return the commit if we had to do a commit, otherwise null
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private RevCommit continueRebase() throws GitAPIException, IOException {
		// if there are still conflicts, we throw a specific Exception
		DirCache dc = repo.readDirCache();
		boolean hasUnmergedPaths = dc.hasUnmergedPaths();
		if (hasUnmergedPaths)
			throw new UnmergedPathsException();

		// determine whether we need to commit
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.reset();
		treeWalk.setRecursive(true);
		treeWalk.addTree(new DirCacheIterator(dc));
		ObjectId id = repo.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
		if (id == null)
			throw new NoHeadException(
					JGitText.get().cannotRebaseWithoutCurrentHead);

		treeWalk.addTree(id);

		treeWalk.setFilter(TreeFilter.ANY_DIFF);

		boolean needsCommit = treeWalk.next();
		treeWalk.release();

		if (needsCommit) {
			CommitCommand commit = new Git(repo).commit();
			commit.setMessage(rebaseState.readFile(MESSAGE));
			commit.setAuthor(parseAuthor());
			return commit.call();
		}
		return null;
	}

	private PersonIdent parseAuthor() throws IOException {
		File authorScriptFile = rebaseState.getFile(AUTHOR_SCRIPT);
		byte[] raw;
		try {
			raw = IO.readFully(authorScriptFile);
		} catch (FileNotFoundException notFound) {
			return null;
		}
		return parseAuthor(raw);
	}

	private RebaseResult stop(RevCommit commitToPick) throws IOException {
		PersonIdent author = commitToPick.getAuthorIdent();
		String authorScript = toAuthorScript(author);
		rebaseState.createFile(AUTHOR_SCRIPT, authorScript);
		rebaseState.createFile(MESSAGE, commitToPick.getFullMessage());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DiffFormatter df = new DiffFormatter(bos);
		df.setRepository(repo);
		df.format(commitToPick.getParent(0), commitToPick);
		rebaseState.createFile(PATCH, new String(bos.toByteArray(),
				Constants.CHARACTER_ENCODING));
		rebaseState.createFile(STOPPED_SHA,
				repo.newObjectReader()
				.abbreviate(
				commitToPick).name());
		// Remove cherry pick state file created by CherryPickCommand, it's not
		// needed for rebase
		repo.writeCherryPickHead(null);
		return new RebaseResult(commitToPick);
	}

	String toAuthorScript(PersonIdent author) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(GIT_AUTHOR_NAME);
		sb.append("='"); //$NON-NLS-1$
		sb.append(author.getName());
		sb.append("'\n"); //$NON-NLS-1$
		sb.append(GIT_AUTHOR_EMAIL);
		sb.append("='"); //$NON-NLS-1$
		sb.append(author.getEmailAddress());
		sb.append("'\n"); //$NON-NLS-1$
		// the command line uses the "external String"
		// representation for date and timezone
		sb.append(GIT_AUTHOR_DATE);
		sb.append("='"); //$NON-NLS-1$
		sb.append("@"); // @ for time in seconds since 1970 //$NON-NLS-1$
		String externalString = author.toExternalString();
		sb
				.append(externalString.substring(externalString
						.lastIndexOf('>') + 2));
		sb.append("'\n"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Removes the number of lines given in the parameter from the
	 * <code>git-rebase-todo</code> file but preserves comments and other lines
	 * that can not be parsed as steps
	 *
	 * @param numSteps
	 * @throws IOException
	 */
	private void popSteps(int numSteps) throws IOException {
		if (numSteps == 0)
			return;
		List<RebaseTodoLine> todoLines = new LinkedList<RebaseTodoLine>();
		List<RebaseTodoLine> poppedLines = new LinkedList<RebaseTodoLine>();

		for (RebaseTodoLine line : repo.readRebaseTodo(
				rebaseState.getPath(GIT_REBASE_TODO), true)) {
			if (poppedLines.size() >= numSteps
					|| RebaseTodoLine.Action.COMMENT.equals(line.getAction()))
				todoLines.add(line);
			else
				poppedLines.add(line);
		}

		repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
				todoLines, false);
		if (poppedLines.size() > 0) {
			repo.writeRebaseTodoFile(rebaseState.getPath(DONE), poppedLines,
					true);
		}
	}

	private RebaseResult initFilesAndRewind() throws IOException,
			GitAPIException {
		// we need to store everything into files so that we can implement
		// --skip, --continue, and --abort

		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));

		String headName;
		if (head.isSymbolic())
			headName = head.getTarget().getName();
		else
			headName = head.getObjectId().getName();
		ObjectId headId = head.getObjectId();
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		RevCommit upstream = walk.lookupCommit(upstreamCommit.getId());

		if (!isInteractive() && walk.isMergedInto(upstream, headCommit))
			return RebaseResult.UP_TO_DATE_RESULT;
		else if (!isInteractive() && walk.isMergedInto(headCommit, upstream)) {
			// head is already merged into upstream, fast-foward
			monitor.beginTask(MessageFormat.format(
					JGitText.get().resettingHead,
					upstreamCommit.getShortMessage()), ProgressMonitor.UNKNOWN);
			checkoutCommit(headName, upstreamCommit);
			monitor.endTask();

			updateHead(headName, upstreamCommit, upstream);
			return RebaseResult.FAST_FORWARD_RESULT;
		}

		monitor.beginTask(JGitText.get().obtainingCommitsForCherryPick,
				ProgressMonitor.UNKNOWN);

		// determine the commits to be applied
		LogCommand cmd = new Git(repo).log().addRange(upstreamCommit,
				headCommit);
		Iterable<RevCommit> commitsToUse = cmd.call();

		List<RevCommit> cherryPickList = new ArrayList<RevCommit>();
		for (RevCommit commit : commitsToUse) {
			if (commit.getParentCount() != 1)
				continue;
			cherryPickList.add(commit);
		}

		Collections.reverse(cherryPickList);
		// create the folder for the meta information
		FileUtils.mkdir(rebaseState.getDir());

		repo.writeOrigHead(headId);
		rebaseState.createFile(REBASE_HEAD, headId.name());
		rebaseState.createFile(HEAD_NAME, headName);
		rebaseState.createFile(ONTO, upstreamCommit.name());
		rebaseState.createFile(ONTO_NAME, upstreamCommitName);
		rebaseState.createFile(INTERACTIVE, ""); //$NON-NLS-1$

		ArrayList<RebaseTodoLine> toDoSteps = new ArrayList<RebaseTodoLine>();
		byte[] commentBytes = ("# Created by EGit: rebasing " + upstreamCommit.name() //$NON-NLS-1$
				+ " onto " + headId.name()).getBytes(Constants.CHARACTER_ENCODING); //$NON-NLS-1$
		toDoSteps.add(new RebaseTodoLine(commentBytes, 0, commentBytes.length));
		ObjectReader reader = walk.getObjectReader();
		for (RevCommit commit : cherryPickList) {
			byte[] shortMessageBytes = commit.getShortMessage().getBytes(
						Constants.CHARACTER_ENCODING);
			toDoSteps.add(new RebaseTodoLine(Action.PICK, reader
					.abbreviate(commit), shortMessageBytes, 0,
					shortMessageBytes.length));
		}
		repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
				toDoSteps, false);

		monitor.endTask();

		// we rewind to the upstream commit
		monitor.beginTask(MessageFormat.format(JGitText.get().rewinding,
				upstreamCommit.getShortMessage()), ProgressMonitor.UNKNOWN);
		boolean checkoutOk = false;
		try {
			checkoutOk = checkoutCommit(headName, upstreamCommit);
		} finally {
			if (!checkoutOk)
				FileUtils.delete(rebaseState.getDir(), FileUtils.RECURSIVE);
		}
		monitor.endTask();

		return null;
	}

	private boolean isInteractive() {
		return interactiveHandler != null;
	}

	/**
	 * checks if we can fast-forward and returns the new head if it is possible
	 *
	 * @param newCommit
	 * @return the new head, or null
	 * @throws IOException
	 * @throws GitAPIException
	 */
	public RevCommit tryFastForward(RevCommit newCommit) throws IOException,
			GitAPIException {
		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));

		ObjectId headId = head.getObjectId();
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		if (walk.isMergedInto(newCommit, headCommit))
			return newCommit;

		String headName;
		if (head.isSymbolic())
			headName = head.getTarget().getName();
		else
			headName = head.getObjectId().getName();
		return tryFastForward(headName, headCommit, newCommit);
	}

	private RevCommit tryFastForward(String headName, RevCommit oldCommit,
			RevCommit newCommit) throws IOException, GitAPIException {
		boolean tryRebase = false;
		for (RevCommit parentCommit : newCommit.getParents())
			if (parentCommit.equals(oldCommit))
				tryRebase = true;
		if (!tryRebase)
			return null;

		CheckoutCommand co = new CheckoutCommand(repo);
		try {
			co.setName(newCommit.name()).call();
			if (headName.startsWith(Constants.R_HEADS)) {
				RefUpdate rup = repo.updateRef(headName);
				rup.setExpectedOldObjectId(oldCommit);
				rup.setNewObjectId(newCommit);
				rup.setRefLogMessage("Fast-foward from " + oldCommit.name() //$NON-NLS-1$
						+ " to " + newCommit.name(), false); //$NON-NLS-1$
				Result res = rup.update(walk);
				switch (res) {
				case FAST_FORWARD:
				case NO_CHANGE:
				case FORCED:
					break;
				default:
					throw new IOException("Could not fast-forward"); //$NON-NLS-1$
				}
			}
			return newCommit;
		} catch (RefAlreadyExistsException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (RefNotFoundException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (InvalidRefNameException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (CheckoutConflictException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private void checkParameters() throws WrongRepositoryStateException {
		if (this.operation != Operation.BEGIN) {
			// these operations are only possible while in a rebasing state
			switch (repo.getRepositoryState()) {
			case REBASING_INTERACTIVE:
			case REBASING:
			case REBASING_REBASING:
			case REBASING_MERGE:
				break;
			default:
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));
			}
		} else
			switch (repo.getRepositoryState()) {
			case SAFE:
				if (this.upstreamCommit == null)
					throw new JGitInternalException(MessageFormat
							.format(JGitText.get().missingRequiredParameter,
									"upstream"));
				return;
			default:
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));

			}
	}

	private RebaseResult abort(RebaseResult result) throws IOException {
		try {
			ObjectId origHead = repo.readOrigHead();
			String commitId = origHead != null ? origHead.name() : null;
			monitor.beginTask(MessageFormat.format(
					JGitText.get().abortingRebase, commitId),
					ProgressMonitor.UNKNOWN);

			DirCacheCheckout dco;
			if (commitId == null)
				throw new JGitInternalException(
						JGitText.get().abortingRebaseFailedNoOrigHead);
			ObjectId id = repo.resolve(commitId);
			RevCommit commit = walk.parseCommit(id);
			if (result.getStatus().equals(Status.FAILED)) {
				RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
				dco = new DirCacheCheckout(repo, head.getTree(),
						repo.lockDirCache(), commit.getTree());
			} else {
				dco = new DirCacheCheckout(repo, repo.lockDirCache(),
						commit.getTree());
			}
			dco.setFailOnConflict(false);
			dco.checkout();
			walk.release();
		} finally {
			monitor.endTask();
		}
		try {
			String headName = rebaseState.readFile(HEAD_NAME);
			if (headName.startsWith(Constants.R_REFS)) {
				monitor.beginTask(MessageFormat.format(
						JGitText.get().resettingHead, headName),
						ProgressMonitor.UNKNOWN);

				// update the HEAD
				RefUpdate refUpdate = repo.updateRef(Constants.HEAD, false);
				refUpdate.setRefLogMessage("rebase: aborting", false); //$NON-NLS-1$
				Result res = refUpdate.link(headName);
				switch (res) {
				case FAST_FORWARD:
				case FORCED:
				case NO_CHANGE:
					break;
				default:
					throw new JGitInternalException(
							JGitText.get().abortingRebaseFailed);
				}
			}
			// cleanup the files
			FileUtils.delete(rebaseState.getDir(), FileUtils.RECURSIVE);
			repo.writeCherryPickHead(null);
			return result;

		} finally {
			monitor.endTask();
		}
	}

	private boolean checkoutCommit(String headName, RevCommit commit)
			throws IOException,
			CheckoutConflictException {
		try {
			RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
			DirCacheCheckout dco = new DirCacheCheckout(repo, head.getTree(),
					repo.lockDirCache(), commit.getTree());
			dco.setFailOnConflict(true);
			try {
				dco.checkout();
			} catch (org.eclipse.jgit.errors.CheckoutConflictException cce) {
				throw new CheckoutConflictException(dco.getConflicts(), cce);
			}
			// update the HEAD
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, true);
			refUpdate.setExpectedOldObjectId(head);
			refUpdate.setNewObjectId(commit);
			refUpdate.setRefLogMessage(
					"checkout: moving from " //$NON-NLS-1$
							+ Repository.shortenRefName(headName)
							+ " to " + commit.getName(), false); //$NON-NLS-1$
			Result res = refUpdate.forceUpdate();
			switch (res) {
			case FAST_FORWARD:
			case NO_CHANGE:
			case FORCED:
				break;
			default:
				throw new IOException("Could not rewind to upstream commit");
			}
		} finally {
			walk.release();
			monitor.endTask();
		}
		return true;
	}


	/**
	 * @param upstream
	 *            the upstream commit
	 * @return {@code this}
	 */
	public RebaseCommand setUpstream(RevCommit upstream) {
		this.upstreamCommit = upstream;
		this.upstreamCommitName = upstream.name();
		return this;
	}

	/**
	 * @param upstream
	 *            id of the upstream commit
	 * @return {@code this}
	 */
	public RebaseCommand setUpstream(AnyObjectId upstream) {
		try {
			this.upstreamCommit = walk.parseCommit(upstream);
			this.upstreamCommitName = upstream.name();
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().couldNotReadObjectWhileParsingCommit,
					upstream.name()), e);
		}
		return this;
	}

	/**
	 * @param upstream
	 *            the upstream branch
	 * @return {@code this}
	 * @throws RefNotFoundException
	 */
	public RebaseCommand setUpstream(String upstream)
			throws RefNotFoundException {
		try {
			ObjectId upstreamId = repo.resolve(upstream);
			if (upstreamId == null)
				throw new RefNotFoundException(MessageFormat.format(JGitText
						.get().refNotResolved, upstream));
			upstreamCommit = walk.parseCommit(repo.resolve(upstream));
			upstreamCommitName = upstream;
			return this;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * Optionally override the name of the upstream. If this is used, it has to
	 * come after any {@link #setUpstream} call.
	 *
	 * @param upstreamName
	 *            the name which will be used to refer to upstream in conflicts
	 * @return {@code this}
	 */
	public RebaseCommand setUpstreamName(String upstreamName) {
		if (upstreamCommit == null) {
			throw new IllegalStateException(
					"setUpstreamName must be called after setUpstream.");
		}
		this.upstreamCommitName = upstreamName;
		return this;
	}

	/**
	 * @param operation
	 *            the operation to perform
	 * @return {@code this}
	 */
	public RebaseCommand setOperation(Operation operation) {
		this.operation = operation;
		return this;
	}

	/**
	 * @param monitor
	 *            a progress monitor
	 * @return this instance
	 */
	public RebaseCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Enables interactive rebase
	 *
	 * @param handler
	 * @return this
	 */
	public RebaseCommand runInteractively(InteractiveHandler handler) {
		this.interactiveHandler = handler;
		return this;
	}

	/**
	 * Allows configure rebase interactive process and modify commit message
	 */
	public interface InteractiveHandler {
		/**
		 * Given list of {@code steps} should be modified according to user
		 * rebase configuration
		 * @param steps
		 *            initial configuration of rebase interactive
		 */
		void prepareSteps(List<RebaseTodoLine> steps);

		/**
		 * Used for editing commit message on REWORD
		 *
		 * @param commit
		 * @return new commit message
		 */
		String modifyCommitMessage(String commit);
	}


	PersonIdent parseAuthor(byte[] raw) {
		if (raw.length == 0)
			return null;

		Map<String, String> keyValueMap = new HashMap<String, String>();
		for (int p = 0; p < raw.length;) {
			int end = RawParseUtils.nextLF(raw, p);
			if (end == p)
				break;
			int equalsIndex = RawParseUtils.next(raw, p, '=');
			if (equalsIndex == end)
				break;
			String key = RawParseUtils.decode(raw, p, equalsIndex - 1);
			String value = RawParseUtils.decode(raw, equalsIndex + 1, end - 2);
			p = end;
			keyValueMap.put(key, value);
		}

		String name = keyValueMap.get(GIT_AUTHOR_NAME);
		String email = keyValueMap.get(GIT_AUTHOR_EMAIL);
		String time = keyValueMap.get(GIT_AUTHOR_DATE);

		// the time is saved as <seconds since 1970> <timezone offset>
		int timeStart = 0;
		if (time.startsWith("@")) //$NON-NLS-1$
			timeStart = 1;
		else
			timeStart = 0;
		long when = Long
				.parseLong(time.substring(timeStart, time.indexOf(' '))) * 1000;
		String tzOffsetString = time.substring(time.indexOf(' ') + 1);
		int multiplier = -1;
		if (tzOffsetString.charAt(0) == '+')
			multiplier = 1;
		int hours = Integer.parseInt(tzOffsetString.substring(1, 3));
		int minutes = Integer.parseInt(tzOffsetString.substring(3, 5));
		// this is in format (+/-)HHMM (hours and minutes)
		// we need to convert into minutes
		int tz = (hours * 60 + minutes) * multiplier;
		if (name != null && email != null)
			return new PersonIdent(name, email, when, tz);
		return null;
	}

	private static class RebaseState {

		private final File repoDirectory;
		private File dir;

		public RebaseState(File repoDirectory) {
			this.repoDirectory = repoDirectory;
		}

		public File getDir() {
			if (dir == null) {
				File rebaseApply = new File(repoDirectory, REBASE_APPLY);
				if (rebaseApply.exists()) {
					dir = rebaseApply;
				} else {
					File rebaseMerge = new File(repoDirectory, REBASE_MERGE);
					dir = rebaseMerge;
				}
			}
			return dir;
		}

		public String readFile(String name) throws IOException {
			return readFile(getDir(), name);
		}

		public void createFile(String name, String content) throws IOException {
			createFile(getDir(), name, content);
		}

		public File getFile(String name) {
			return new File(getDir(), name);
		}

		public String getPath(String name) {
			return (getDir().getName() + "/" + name); //$NON-NLS-1$
		}

		private static String readFile(File directory, String fileName)
				throws IOException {
			byte[] content = IO.readFully(new File(directory, fileName));
			// strip off the last LF
			int end = RawParseUtils.prevLF(content, content.length);
			return RawParseUtils.decode(content, 0, end + 1);
		}

		private static void createFile(File parentDir, String name,
				String content)
				throws IOException {
			File file = new File(parentDir, name);
			FileOutputStream fos = new FileOutputStream(file);
			try {
				fos.write(content.getBytes(Constants.CHARACTER_ENCODING));
				fos.write('\n');
			} finally {
				fos.close();
			}
		}
	}
}
