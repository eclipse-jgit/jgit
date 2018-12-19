/*
 * Copyright (C) 2010, 2013 Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2016, Laurent Delaigue <laurent.delaigue@obeo.fr>
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

import static java.nio.charset.StandardCharsets.UTF_8;

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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRebaseStepException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
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
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode;
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
 * This implements rebasing via cherry-picking, like native git's rebase
 * --interactive or rebase --preserve-merges, and uses that even for the
 * equivalent of native git rebase --merge. There is no support for the default
 * mode of native git's rebase, which is based on git-am (repeated application
 * of patches).
 * </p>
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html" >Git
 *      documentation about Rebase</a>
 */
public class RebaseCommand extends GitCommand<RebaseResult> {

	/**
	 * The name of the "rebase-merge" folder for merging rebases (all JGit
	 * rebases, and native git's rebase --merge, rebase --interactive, and
	 * rebase --preserve-merges).
	 */
	public static final String REBASE_MERGE = "rebase-merge"; //$NON-NLS-1$

	/**
	 * The name of the "rebase-apply" folder for native git's rebase using
	 * git-am (repeated patch application).
	 * <p>
	 * Note that this implementation is not compatible at all with that basic
	 * native git mode, and a plain git rebase using git-am (which is the
	 * default for native git) cannot be continued with JGit.
	 * </p>
	 */
	private static final String REBASE_APPLY = "rebase-apply"; //$NON-NLS-1$

	/**
	 * The name of the "stopped-sha" file used to record the commit upon
	 * conflicts in interactive rebases. See also {@link #CURRENT}.
	 */
	public static final String STOPPED_SHA = "stopped-sha"; //$NON-NLS-1$

	private static final String AUTHOR_SCRIPT = "author-script"; //$NON-NLS-1$

	/**
	 * The name of the file used to record the current commit upon conflicts in
	 * non-interactive rebases. See also {@link #STOPPED_SHA}.
	 */
	private static final String CURRENT = "current"; //$NON-NLS-1$

	private static final String DONE = "done"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_DATE = "GIT_AUTHOR_DATE"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_EMAIL = "GIT_AUTHOR_EMAIL"; //$NON-NLS-1$

	private static final String GIT_AUTHOR_NAME = "GIT_AUTHOR_NAME"; //$NON-NLS-1$

	private static final String GIT_REBASE_TODO = "git-rebase-todo"; //$NON-NLS-1$

	private static final String HEAD_NAME = "head-name"; //$NON-NLS-1$

	private static final String INTERACTIVE = "interactive"; //$NON-NLS-1$

	private static final String QUIET = "quiet"; //$NON-NLS-1$

	private static final String MESSAGE = "message"; //$NON-NLS-1$

	private static final String ONTO = "onto"; //$NON-NLS-1$

	private static final String ONTO_NAME = "onto_name"; //$NON-NLS-1$

	private static final String PATCH = "patch"; //$NON-NLS-1$

	private static final String REBASE_HEAD = "orig-head"; //$NON-NLS-1$

	/** Pre git 1.7.6 file name for {@link #REBASE_HEAD}. */
	private static final String REBASE_HEAD_LEGACY = "head"; //$NON-NLS-1$

	private static final String AMEND = "amend"; //$NON-NLS-1$

	private static final String MESSAGE_FIXUP = "message-fixup"; //$NON-NLS-1$

	private static final String MESSAGE_SQUASH = "message-squash"; //$NON-NLS-1$

	private static final String AUTOSTASH = "autostash"; //$NON-NLS-1$

	private static final String AUTOSTASH_MSG = "On {0}: autostash"; //$NON-NLS-1$

	/**
	 * The folder containing the hashes of (potentially) rewritten commits when
	 * --preserve-merges is used.
	 * <p>
	 * Native git rebase --merge uses a <em>file</em> of that name to record
	 * commits to copy notes at the end of the whole rebase.
	 * </p>
	 */
	private static final String REWRITTEN = "rewritten"; //$NON-NLS-1$

	/**
	 * File containing the current commit(s) to cherry pick when --preserve-merges
	 * is used.
	 */
	private static final String CURRENT_COMMIT = "current-commit"; //$NON-NLS-1$

	/**
	 * Native git rebase --merge records the commits not in a to-do list but in
	 * individual "cmt.N" files, N >= 1. It also maintains {@link #MSGNUM} and
	 * {@link #END} files.
	 */
	private static final String CMT_PREFIX = "cmt."; //$NON-NLS-1$

	/**
	 * Records the number of {@link #CMT_PREFIX cmt.N} files for git rebase
	 * --merge.
	 */
	private static final String END = "end"; //$NON-NLS-1$

	/**
	 * Records the current commit index during git rebase --merge.
	 */
	private static final String MSGNUM = "msgnum"; //$NON-NLS-1$

	private static final String REFLOG_PREFIX = "rebase:"; //$NON-NLS-1$

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
		ABORT,
		/**
		 * Starts processing steps
		 * @since 3.2
		 */
		PROCESS_STEPS;
	}

	private Operation operation = Operation.BEGIN;

	private RevCommit upstreamCommit;

	private String upstreamCommitName;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private final RevWalk walk;

	private final RebaseState rebaseState;

	private InteractiveHandler interactiveHandler;

	private boolean stopAfterInitialization = false;

	private RevCommit newHead;

	private boolean lastStepWasForward;

	private MergeStrategy strategy = MergeStrategy.RECURSIVE;

	private boolean preserveMerges = false;

	/**
	 * <p>
	 * Constructor for RebaseCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RebaseCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
		rebaseState = new RebaseState(repo.getDirectory());
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Rebase} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
	 */
	@Override
	public RebaseResult call() throws GitAPIException, NoHeadException,
			RefNotFoundException, WrongRepositoryStateException {
		newHead = null;
		lastStepWasForward = false;
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
			case PROCESS_STEPS:
			case SKIP:
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
				preserveMerges = rebaseState.getRewrittenDir().isDirectory();
				convertMergingRebase();
				break;
			case BEGIN:
				autoStash();
				if (stopAfterInitialization
						|| !walk.isMergedInto(
								walk.parseCommit(repo.resolve(Constants.HEAD)),
								upstreamCommit)) {
					org.eclipse.jgit.api.Status status = Git.wrap(repo)
							.status().setIgnoreSubmodules(IgnoreSubmoduleMode.ALL).call();
					if (status.hasUncommittedChanges()) {
						List<String> list = new ArrayList<>();
						list.addAll(status.getUncommittedChanges());
						return RebaseResult.uncommittedChanges(list);
					}
				}
				RebaseResult res = initFilesAndRewind();
				if (stopAfterInitialization)
					return RebaseResult.INTERACTIVE_PREPARED_RESULT;
				if (res != null) {
					autoStashApply();
					if (rebaseState.getDir().exists())
						FileUtils.delete(rebaseState.getDir(),
								FileUtils.RECURSIVE);
					return res;
				}
			}

			if (monitor.isCancelled())
				return abort(RebaseResult.ABORTED_RESULT);

			int numberOfDoneSteps = 0;
			if (operation == Operation.CONTINUE) {
				List<RebaseTodoLine> doneLines = repo.readRebaseTodo(
						rebaseState.getPath(DONE), true);
				numberOfDoneSteps = doneLines.size();
				RebaseTodoLine step = catchUpWithMsgNum(numberOfDoneSteps);
				if (step == null) {
					// No catching up done.
					step = doneLines.get(numberOfDoneSteps - 1);
				}
				newHead = continueRebase();
				if (newHead != null
						&& step.getAction() != Action.PICK) {
					RebaseTodoLine newStep = new RebaseTodoLine(
							step.getAction(),
							AbbreviatedObjectId.fromObjectId(newHead),
							step.getShortMessage());
					RebaseResult result = processStep(newStep, false);
					if (result != null)
						return result;
				}
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

			List<RebaseTodoLine> steps = repo.readRebaseTodo(
					rebaseState.getPath(GIT_REBASE_TODO), false);
			if (steps.size() == 0) {
				return finishRebase(walk.parseCommit(repo.resolve(Constants.HEAD)), false);
			}
			if (isInteractive()) {
				interactiveHandler.prepareSteps(steps);
				repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
						steps, false);
			}
			checkSteps(steps);
			for (int i = 0; i < steps.size(); i++) {
				RebaseTodoLine step = steps.get(i);
				popSteps(1);
				RebaseResult result = processStep(step, true);
				if (result != null) {
					rebaseState.createFile(MSGNUM,
							Integer.toString(numberOfDoneSteps + i + 1));
					return result;
				}
			}
			return finishRebase(newHead, lastStepWasForward);
		} catch (CheckoutConflictException cce) {
			return RebaseResult.conflicts(cce.getConflictingPaths());
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private void convertMergingRebase() throws IOException {
		File file = rebaseState.getDir();
		if (REBASE_APPLY.equals(file.getName())) {
			return;
		}
		file = rebaseState.getFile(GIT_REBASE_TODO);
		if (file.exists()) {
			return;
		}
		File done = rebaseState.getFile(DONE);
		if (done.exists()) {
			return;
		}
		int msgNum = -1;
		int end = -1;
		try {
			msgNum = Integer.parseInt(rebaseState.readFile(MSGNUM));
			end = Integer.parseInt(rebaseState.readFile(END));
		} catch (IOException | NumberFormatException e) {
			// Just don't do anything, we return below.
		}
		if (msgNum <= 0 || end <= 0) {
			return;
		}
		// Create a DONE file from cmt.1 to cmt.msgNum, put all others in the
		// to-do list.
		List<RebaseTodoLine> lines = new ArrayList<>();
		try {
			for (int i = 1; i <= msgNum; i++) {
				AbbreviatedObjectId id = AbbreviatedObjectId
						.fromString(rebaseState.readFile(CMT_PREFIX + i));
				lines.add(new RebaseTodoLine(Action.PICK, id, "")); //$NON-NLS-1$
			}
			repo.writeRebaseTodoFile(rebaseState.getPath(DONE), lines, false);
			lines.clear();
			lines.add(new RebaseTodoLine("# Created by JGit; conversion of cmt." //$NON-NLS-1$
					+ msgNum + " to cmt." + end)); //$NON-NLS-1$
			for (int i = msgNum + 1; i <= end; i++) {
				AbbreviatedObjectId id = AbbreviatedObjectId
						.fromString(rebaseState.readFile(CMT_PREFIX + i));
				lines.add(new RebaseTodoLine(Action.PICK, id, "")); //$NON-NLS-1$
			}
			repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
					lines, false);
		} catch (IOException e) {
			FileUtils.delete(file, FileUtils.IGNORE_ERRORS);
			FileUtils.delete(done, FileUtils.IGNORE_ERRORS);
		}
	}

	private RebaseTodoLine catchUpWithMsgNum(int doneCount) throws IOException {
		int msgNum = -1;
		try {
			msgNum = Integer.parseInt(rebaseState.readFile(MSGNUM));
		} catch (IOException | NumberFormatException e) {
			// Just don't do anything, we return below.
		}
		if (msgNum <= doneCount) {
			return null;
		}
		return popSteps(msgNum - doneCount);
	}

	private void autoStash() throws GitAPIException, IOException {
		if (repo.getConfig().getBoolean(ConfigConstants.CONFIG_REBASE_SECTION,
				ConfigConstants.CONFIG_KEY_AUTOSTASH, false)) {
			String message = MessageFormat.format(
							AUTOSTASH_MSG,
							Repository
									.shortenRefName(getHeadName(getHead())));
			RevCommit stashCommit = Git.wrap(repo).stashCreate().setRef(null)
					.setWorkingDirectoryMessage(
							message)
					.call();
			if (stashCommit != null) {
				FileUtils.mkdir(rebaseState.getDir());
				rebaseState.createFile(AUTOSTASH, stashCommit.getName());
			}
		}
	}

	private boolean autoStashApply() throws IOException, GitAPIException {
		boolean conflicts = false;
		if (rebaseState.getFile(AUTOSTASH).exists()) {
			String stash = rebaseState.readFile(AUTOSTASH);
			try (Git git = Git.wrap(repo)) {
				git.stashApply().setStashRef(stash)
						.ignoreRepositoryState(true).setStrategy(strategy)
						.call();
			} catch (StashApplyFailureException e) {
				conflicts = true;
				try (RevWalk rw = new RevWalk(repo)) {
					ObjectId stashId = repo.resolve(stash);
					RevCommit commit = rw.parseCommit(stashId);
					updateStashRef(commit, commit.getAuthorIdent(),
							commit.getShortMessage());
				}
			}
		}
		return conflicts;
	}

	private void updateStashRef(ObjectId commitId, PersonIdent refLogIdent,
			String refLogMessage) throws IOException {
		Ref currentRef = repo.exactRef(Constants.R_STASH);
		RefUpdate refUpdate = repo.updateRef(Constants.R_STASH);
		refUpdate.setNewObjectId(commitId);
		refUpdate.setRefLogIdent(refLogIdent);
		refUpdate.setRefLogMessage(refLogMessage, false);
		refUpdate.setForceRefLog(true);
		if (currentRef != null)
			refUpdate.setExpectedOldObjectId(currentRef.getObjectId());
		else
			refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
		refUpdate.forceUpdate();
	}

	private RebaseResult processStep(RebaseTodoLine step, boolean shouldPick)
			throws IOException, GitAPIException {
		if (Action.COMMENT.equals(step.getAction()))
			return null;
		if (preserveMerges
				&& shouldPick
				&& (Action.EDIT.equals(step.getAction()) || Action.PICK
						.equals(step.getAction()))) {
			writeRewrittenHashes();
		}
		ObjectReader or = repo.newObjectReader();

		Collection<ObjectId> ids = or.resolve(step.getCommit());
		if (ids.size() != 1)
			throw new JGitInternalException(
					JGitText.get().cannotResolveUniquelyAbbrevObjectId);
		RevCommit commitToPick = walk.parseCommit(ids.iterator().next());
		if (!isInteractive() && !preserveMerges) {
			rebaseState.createFile(CURRENT, commitToPick.name());
		}
		if (shouldPick) {
			if (monitor.isCancelled())
				return RebaseResult.result(Status.STOPPED, commitToPick);
			RebaseResult result = cherryPickCommit(commitToPick);
			if (result != null)
				return result;
		}
		boolean isSquash = false;
		switch (step.getAction()) {
		case PICK:
			return null; // continue rebase process on pick command
		case REWORD:
			String oldMessage = commitToPick.getFullMessage();
			String newMessage = interactiveHandler
					.modifyCommitMessage(oldMessage);
			try (Git git = new Git(repo)) {
				newHead = git.commit().setMessage(newMessage).setAmend(true)
						.setNoVerify(true).call();
			}
			return null;
		case EDIT:
			rebaseState.createFile(AMEND, commitToPick.name());
			return stop(commitToPick, Status.EDIT);
		case COMMENT:
			break;
		case SQUASH:
			isSquash = true;
			//$FALL-THROUGH$
		case FIXUP:
			resetSoftToParent();
			List<RebaseTodoLine> steps = repo.readRebaseTodo(
					rebaseState.getPath(GIT_REBASE_TODO), false);
			RebaseTodoLine nextStep = steps.size() > 0 ? steps.get(0) : null;
			File messageFixupFile = rebaseState.getFile(MESSAGE_FIXUP);
			File messageSquashFile = rebaseState.getFile(MESSAGE_SQUASH);
			if (isSquash && messageFixupFile.exists())
				messageFixupFile.delete();
			newHead = doSquashFixup(isSquash, commitToPick, nextStep,
					messageFixupFile, messageSquashFile);
		}
		return null;
	}

	private RebaseResult cherryPickCommit(RevCommit commitToPick)
			throws IOException, GitAPIException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, NoHeadException {
		try {
			monitor.beginTask(MessageFormat.format(
					JGitText.get().applyingCommit,
					commitToPick.getShortMessage()), ProgressMonitor.UNKNOWN);
			if (preserveMerges)
				return cherryPickCommitPreservingMerges(commitToPick);
			else
				return cherryPickCommitFlattening(commitToPick);
		} finally {
			monitor.endTask();
		}
	}

	private RebaseResult cherryPickCommitFlattening(RevCommit commitToPick)
			throws IOException, GitAPIException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, NoHeadException {
		// If the first parent of commitToPick is the current HEAD,
		// we do a fast-forward instead of cherry-pick to avoid
		// unnecessary object rewriting
		newHead = tryFastForward(commitToPick);
		lastStepWasForward = newHead != null;
		if (!lastStepWasForward) {
			// TODO if the content of this commit is already merged
			// here we should skip this step in order to avoid
			// confusing pseudo-changed
			String ourCommitName = getOurCommitName();
			try (Git git = new Git(repo)) {
				CherryPickResult cherryPickResult = git.cherryPick()
					.include(commitToPick).setOurCommitName(ourCommitName)
					.setReflogPrefix(REFLOG_PREFIX).setStrategy(strategy)
					.call();
				switch (cherryPickResult.getStatus()) {
				case FAILED:
					if (operation == Operation.BEGIN)
						return abort(RebaseResult
								.failed(cherryPickResult.getFailingPaths()));
					else
						return stop(commitToPick, Status.STOPPED);
				case CONFLICTING:
					return stop(commitToPick, Status.STOPPED);
				case OK:
					newHead = cherryPickResult.getNewHead();
				}
			}
		}
		return null;
	}

	private RebaseResult cherryPickCommitPreservingMerges(RevCommit commitToPick)
			throws IOException, GitAPIException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, NoHeadException {

		writeCurrentCommit(commitToPick);

		List<RevCommit> newParents = getNewParents(commitToPick);
		boolean otherParentsUnchanged = true;
		for (int i = 1; i < commitToPick.getParentCount(); i++)
			otherParentsUnchanged &= newParents.get(i).equals(
					commitToPick.getParent(i));
		// If the first parent of commitToPick is the current HEAD,
		// we do a fast-forward instead of cherry-pick to avoid
		// unnecessary object rewriting
		newHead = otherParentsUnchanged ? tryFastForward(commitToPick) : null;
		lastStepWasForward = newHead != null;
		if (!lastStepWasForward) {
			ObjectId headId = getHead().getObjectId();
			// getHead() checks for null
			assert headId != null;
			if (!AnyObjectId.equals(headId, newParents.get(0)))
				checkoutCommit(headId.getName(), newParents.get(0));

			// Use the cherry-pick strategy if all non-first parents did not
			// change. This is different from C Git, which always uses the merge
			// strategy (see below).
			try (Git git = new Git(repo)) {
				if (otherParentsUnchanged) {
					boolean isMerge = commitToPick.getParentCount() > 1;
					String ourCommitName = getOurCommitName();
					CherryPickCommand pickCommand = git.cherryPick()
							.include(commitToPick)
							.setOurCommitName(ourCommitName)
							.setReflogPrefix(REFLOG_PREFIX)
							.setStrategy(strategy);
					if (isMerge) {
						pickCommand.setMainlineParentNumber(1);
						// We write a MERGE_HEAD and later commit explicitly
						pickCommand.setNoCommit(true);
						writeMergeInfo(commitToPick, newParents);
					}
					CherryPickResult cherryPickResult = pickCommand.call();
					switch (cherryPickResult.getStatus()) {
					case FAILED:
						if (operation == Operation.BEGIN)
							return abort(RebaseResult.failed(
									cherryPickResult.getFailingPaths()));
						else
							return stop(commitToPick, Status.STOPPED);
					case CONFLICTING:
						return stop(commitToPick, Status.STOPPED);
					case OK:
						if (isMerge) {
							// Commit the merge (setup above using
							// writeMergeInfo())
							CommitCommand commit = git.commit();
							commit.setAuthor(commitToPick.getAuthorIdent());
							commit.setReflogComment(REFLOG_PREFIX + " " //$NON-NLS-1$
									+ commitToPick.getShortMessage());
							newHead = commit.call();
						} else
							newHead = cherryPickResult.getNewHead();
						break;
					}
				} else {
					// Use the merge strategy to redo merges, which had some of
					// their non-first parents rewritten
					MergeCommand merge = git.merge()
							.setFastForward(MergeCommand.FastForwardMode.NO_FF)
							.setProgressMonitor(monitor)
							.setCommit(false);
					for (int i = 1; i < commitToPick.getParentCount(); i++)
						merge.include(newParents.get(i));
					MergeResult mergeResult = merge.call();
					if (mergeResult.getMergeStatus().isSuccessful()) {
						CommitCommand commit = git.commit();
						commit.setAuthor(commitToPick.getAuthorIdent());
						commit.setMessage(commitToPick.getFullMessage());
						commit.setReflogComment(REFLOG_PREFIX + " " //$NON-NLS-1$
								+ commitToPick.getShortMessage());
						newHead = commit.call();
					} else {
						if (operation == Operation.BEGIN && mergeResult
								.getMergeStatus() == MergeResult.MergeStatus.FAILED)
							return abort(RebaseResult
									.failed(mergeResult.getFailingPaths()));
						return stop(commitToPick, Status.STOPPED);
					}
				}
			}
		}
		return null;
	}

	// Prepare MERGE_HEAD and message for the next commit
	private void writeMergeInfo(RevCommit commitToPick,
			List<RevCommit> newParents) throws IOException {
		repo.writeMergeHeads(newParents.subList(1, newParents.size()));
		repo.writeMergeCommitMsg(commitToPick.getFullMessage());
	}

	// Get the rewritten equivalents for the parents of the given commit
	private List<RevCommit> getNewParents(RevCommit commitToPick)
			throws IOException {
		List<RevCommit> newParents = new ArrayList<>();
		for (int p = 0; p < commitToPick.getParentCount(); p++) {
			String parentHash = commitToPick.getParent(p).getName();
			if (!new File(rebaseState.getRewrittenDir(), parentHash).exists())
				newParents.add(commitToPick.getParent(p));
			else {
				String newParent = RebaseState.readFile(
						rebaseState.getRewrittenDir(), parentHash);
				if (newParent.length() == 0)
					newParents.add(walk.parseCommit(repo
							.resolve(Constants.HEAD)));
				else
					newParents.add(walk.parseCommit(ObjectId
							.fromString(newParent)));
			}
		}
		return newParents;
	}

	private void writeCurrentCommit(RevCommit commit) throws IOException {
		RebaseState.appendToFile(rebaseState.getFile(CURRENT_COMMIT),
				commit.name());
	}

	private void writeRewrittenHashes() throws RevisionSyntaxException,
			IOException, RefNotFoundException {
		File currentCommitFile = rebaseState.getFile(CURRENT_COMMIT);
		if (!currentCommitFile.exists())
			return;

		ObjectId headId = getHead().getObjectId();
		// getHead() checks for null
		assert headId != null;
		String head = headId.getName();
		String currentCommits = rebaseState.readFile(CURRENT_COMMIT);
		for (String current : currentCommits.split("\n")) //$NON-NLS-1$
			RebaseState
					.createFile(rebaseState.getRewrittenDir(), current, head);
		FileUtils.delete(currentCommitFile);
	}

	private RebaseResult finishRebase(RevCommit finalHead,
			boolean lastStepIsForward) throws IOException, GitAPIException {
		String headName = rebaseState.readFile(HEAD_NAME);
		updateHead(headName, finalHead, upstreamCommit);
		boolean stashConflicts = autoStashApply();
		getRepository().autoGC(monitor);
		FileUtils.delete(rebaseState.getDir(), FileUtils.RECURSIVE);
		if (stashConflicts)
			return RebaseResult.STASH_APPLY_CONFLICTS_RESULT;
		if (lastStepIsForward || finalHead == null)
			return RebaseResult.FAST_FORWARD_RESULT;
		return RebaseResult.OK_RESULT;
	}

	private void checkSteps(List<RebaseTodoLine> steps)
			throws InvalidRebaseStepException, IOException {
		if (steps.isEmpty())
			return;
		if (RebaseTodoLine.Action.SQUASH.equals(steps.get(0).getAction())
				|| RebaseTodoLine.Action.FIXUP.equals(steps.get(0).getAction())) {
			if (!rebaseState.getFile(DONE).exists()
					|| rebaseState.readFile(DONE).trim().length() == 0) {
				throw new InvalidRebaseStepException(MessageFormat.format(
						JGitText.get().cannotSquashFixupWithoutPreviousCommit,
						steps.get(0).getAction().name()));
			}
		}

	}

	private RevCommit doSquashFixup(boolean isSquash, RevCommit commitToPick,
			RebaseTodoLine nextStep, File messageFixup, File messageSquash)
			throws IOException, GitAPIException {

		if (!messageSquash.exists()) {
			// init squash/fixup sequence
			ObjectId headId = repo.resolve(Constants.HEAD);
			RevCommit previousCommit = walk.parseCommit(headId);

			initializeSquashFixupFile(MESSAGE_SQUASH,
					previousCommit.getFullMessage());
			if (!isSquash)
				initializeSquashFixupFile(MESSAGE_FIXUP,
					previousCommit.getFullMessage());
		}
		String currSquashMessage = rebaseState
				.readFile(MESSAGE_SQUASH);

		int count = parseSquashFixupSequenceCount(currSquashMessage) + 1;

		String content = composeSquashMessage(isSquash,
				commitToPick, currSquashMessage, count);
		rebaseState.createFile(MESSAGE_SQUASH, content);
		if (messageFixup.exists())
			rebaseState.createFile(MESSAGE_FIXUP, content);

		return squashIntoPrevious(
				!messageFixup.exists(),
				nextStep);
	}

	private void resetSoftToParent() throws IOException,
			GitAPIException, CheckoutConflictException {
		Ref ref = repo.exactRef(Constants.ORIG_HEAD);
		ObjectId orig_head = ref == null ? null : ref.getObjectId();
		try (Git git = Git.wrap(repo)) {
			// we have already committed the cherry-picked commit.
			// what we need is to have changes introduced by this
			// commit to be on the index
			// resetting is a workaround
			git.reset().setMode(ResetType.SOFT)
					.setRef("HEAD~1").call(); //$NON-NLS-1$
		} finally {
			// set ORIG_HEAD back to where we started because soft
			// reset moved it
			repo.writeOrigHead(orig_head);
		}
	}

	private RevCommit squashIntoPrevious(boolean sequenceContainsSquash,
			RebaseTodoLine nextStep)
			throws IOException, GitAPIException {
		RevCommit retNewHead;
		String commitMessage = rebaseState
				.readFile(MESSAGE_SQUASH);

		try (Git git = new Git(repo)) {
			if (nextStep == null || ((nextStep.getAction() != Action.FIXUP)
					&& (nextStep.getAction() != Action.SQUASH))) {
				// this is the last step in this sequence
				if (sequenceContainsSquash) {
					commitMessage = interactiveHandler
							.modifyCommitMessage(commitMessage);
				}
				retNewHead = git.commit()
						.setMessage(stripCommentLines(commitMessage))
						.setAmend(true).setNoVerify(true).call();
				rebaseState.getFile(MESSAGE_SQUASH).delete();
				rebaseState.getFile(MESSAGE_FIXUP).delete();

			} else {
				// Next step is either Squash or Fixup
				retNewHead = git.commit().setMessage(commitMessage)
						.setAmend(true).setNoVerify(true).call();
			}
		}
		return retNewHead;
	}

	private static String stripCommentLines(String commitMessage) {
		StringBuilder result = new StringBuilder();
		for (String line : commitMessage.split("\n")) { //$NON-NLS-1$
			if (!line.trim().startsWith("#")) //$NON-NLS-1$
				result.append(line).append("\n"); //$NON-NLS-1$
		}
		if (!commitMessage.endsWith("\n")) { //$NON-NLS-1$
			int bufferSize = result.length();
			if (bufferSize > 0 && result.charAt(bufferSize - 1) == '\n') {
				result.deleteCharAt(bufferSize - 1);
			}
		}
		return result.toString();
	}

	@SuppressWarnings("nls")
	private static String composeSquashMessage(boolean isSquash,
			RevCommit commitToPick, String currSquashMessage, int count) {
		StringBuilder sb = new StringBuilder();
		String ordinal = getOrdinal(count);
		sb.setLength(0);
		sb.append("# This is a combination of ").append(count)
				.append(" commits.\n");
		// Add the previous message without header (i.e first line)
		sb.append(currSquashMessage.substring(currSquashMessage.indexOf("\n") + 1));
		sb.append("\n");
		if (isSquash) {
			sb.append("# This is the ").append(count).append(ordinal)
					.append(" commit message:\n");
			sb.append(commitToPick.getFullMessage());
		} else {
			sb.append("# The ").append(count).append(ordinal)
					.append(" commit message will be skipped:\n# ");
			sb.append(commitToPick.getFullMessage().replaceAll("([\n\r])",
					"$1# "));
		}
		return sb.toString();
	}

	private static String getOrdinal(int count) {
		switch (count % 10) {
		case 1:
			return "st"; //$NON-NLS-1$
		case 2:
			return "nd"; //$NON-NLS-1$
		case 3:
			return "rd"; //$NON-NLS-1$
		default:
			return "th"; //$NON-NLS-1$
		}
	}

	/**
	 * Parse the count from squashed commit messages
	 *
	 * @param currSquashMessage
	 *            the squashed commit message to be parsed
	 * @return the count of squashed messages in the given string
	 */
	static int parseSquashFixupSequenceCount(String currSquashMessage) {
		String regex = "This is a combination of (.*) commits"; //$NON-NLS-1$
		String firstLine = currSquashMessage.substring(0,
				currSquashMessage.indexOf("\n")); //$NON-NLS-1$
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(firstLine);
		if (!matcher.find())
			throw new IllegalArgumentException();
		return Integer.parseInt(matcher.group(1));
	}

	private void initializeSquashFixupFile(String messageFile,
			String fullMessage) throws IOException {
		rebaseState
				.createFile(
						messageFile,
						"# This is a combination of 1 commits.\n# The first commit's message is:\n" + fullMessage); //$NON-NLS-1$);
	}

	private String getOurCommitName() {
		// If onto is different from upstream, this should say "onto", but
		// RebaseCommand doesn't support a different "onto" at the moment.
		String ourCommitName = "Upstream, based on " //$NON-NLS-1$
				+ Repository.shortenRefName(upstreamCommitName);
		return ourCommitName;
	}

	private void updateHead(String headName, RevCommit aNewHead, RevCommit onto)
			throws IOException {
		// point the previous head (if any) to the new commit

		if (headName.startsWith(Constants.R_REFS)) {
			RefUpdate rup = repo.updateRef(headName);
			rup.setNewObjectId(aNewHead);
			rup.setRefLogMessage("rebase finished: " + headName + " onto " //$NON-NLS-1$ //$NON-NLS-2$
					+ onto.getName(), false);
			Result res = rup.forceUpdate();
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException(
						JGitText.get().updatingHeadFailed);
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
				throw new JGitInternalException(
						JGitText.get().updatingHeadFailed);
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
			dco.setProgressMonitor(monitor);
			boolean needsDeleteFiles = dco.checkout();
			if (needsDeleteFiles) {
				List<String> fileList = dco.getToBeDeleted();
				for (String filePath : fileList) {
					File fileToDelete = new File(repo.getWorkTree(), filePath);
					if (repo.getFS().exists(fileToDelete))
						FileUtils.delete(fileToDelete, FileUtils.RECURSIVE
								| FileUtils.RETRY);
				}
			}
		} finally {
			dc.unlock();
		}
		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit commit = rw.parseCommit(repo.resolve(Constants.HEAD));
			return commit;
		}
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
		boolean needsCommit;
		try (TreeWalk treeWalk = new TreeWalk(repo)) {
			treeWalk.reset();
			treeWalk.setRecursive(true);
			treeWalk.addTree(new DirCacheIterator(dc));
			ObjectId id = repo.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
			if (id == null)
				throw new NoHeadException(
						JGitText.get().cannotRebaseWithoutCurrentHead);

			treeWalk.addTree(id);

			treeWalk.setFilter(TreeFilter.ANY_DIFF);

			needsCommit = treeWalk.next();
		}
		if (needsCommit) {
			try (Git git = new Git(repo)) {
				CommitCommand commit = git.commit();
				File messageFile = rebaseState.getFile(MESSAGE);
				if (messageFile.exists()) {
					commit.setMessage(RebaseState.readFile(messageFile));
					commit.setAuthor(parseAuthor());
				} else {
					// Rebase started via native git rebase --merge doesn't use
					// the MESSAGE file but the -C option with the commit ID of
					// the current commit.
					RevCommit current = walk.parseCommit(
							repo.resolve(rebaseState.readFile(CURRENT)));
					commit.setMessage(current.getFullMessage());
					commit.setAuthor(current.getAuthorIdent());
				}
				return commit.call();
			}
		}
		return null;
	}

	private PersonIdent parseAuthor() throws IOException {
		File authorScriptFile = rebaseState.getFile(AUTHOR_SCRIPT);
		byte[] raw;
		try {
			raw = IO.readFully(authorScriptFile);
		} catch (FileNotFoundException notFound) {
			if (authorScriptFile.exists()) {
				throw notFound;
			}
			return null;
		}
		return parseAuthor(raw);
	}

	private RebaseResult stop(RevCommit commitToPick, RebaseResult.Status status)
			throws IOException {
		PersonIdent author = commitToPick.getAuthorIdent();
		String authorScript = toAuthorScript(author);
		rebaseState.createFile(AUTHOR_SCRIPT, authorScript);
		rebaseState.createFile(MESSAGE, commitToPick.getFullMessage());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DiffFormatter df = new DiffFormatter(bos)) {
			df.setRepository(repo);
			df.format(commitToPick.getParent(0), commitToPick);
		}
		rebaseState.createFile(PATCH, new String(bos.toByteArray(), UTF_8));
		rebaseState.createFile(STOPPED_SHA, commitToPick.name());
		// Remove cherry pick state file created by CherryPickCommand, it's not
		// needed for rebase
		repo.writeCherryPickHead(null);
		return RebaseResult.result(status, commitToPick);
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
	 * @return the last line popped, or {@code null} if {@code numSteps == 0}
	 * @throws IOException
	 */
	private RebaseTodoLine popSteps(int numSteps) throws IOException {
		if (numSteps == 0) {
			return null;
		}
		List<RebaseTodoLine> todoLines = new LinkedList<>();
		List<RebaseTodoLine> poppedLines = new LinkedList<>();
		RebaseTodoLine lastPopped = null;
		for (RebaseTodoLine line : repo.readRebaseTodo(
				rebaseState.getPath(GIT_REBASE_TODO), true)) {
			if (poppedLines.size() >= numSteps
					|| RebaseTodoLine.Action.COMMENT.equals(line.getAction())) {
				todoLines.add(line);
			} else {
				lastPopped = line;
				poppedLines.add(line);
			}
		}

		repo.writeRebaseTodoFile(rebaseState.getPath(GIT_REBASE_TODO),
				todoLines, false);
		if (!poppedLines.isEmpty()) {
			repo.writeRebaseTodoFile(rebaseState.getPath(DONE), poppedLines,
					true);
		}
		return lastPopped;
	}

	private RebaseResult initFilesAndRewind() throws IOException,
			GitAPIException {
		// we need to store everything into files so that we can implement
		// --skip, --continue, and --abort

		Ref head = getHead();

		ObjectId headId = head.getObjectId();
		if (headId == null) {
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		}
		String headName = getHeadName(head);
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

		// create the folder for the meta information
		FileUtils.mkdir(rebaseState.getDir(), true);

		repo.writeOrigHead(headId);
		rebaseState.createFile(REBASE_HEAD, headId.name());
		rebaseState.createFile(REBASE_HEAD_LEGACY, headId.name());
		rebaseState.createFile(HEAD_NAME, headName);
		rebaseState.createFile(ONTO, upstreamCommit.name());
		rebaseState.createFile(ONTO_NAME, upstreamCommitName);
		if (isInteractive() || preserveMerges) {
			// --preserve-merges is an interactive mode for native git. Without
			// this, native git rebase --continue after a conflict would fall
			// into merge mode.
			rebaseState.createFile(INTERACTIVE, ""); //$NON-NLS-1$
		}
		rebaseState.createFile(QUIET, ""); //$NON-NLS-1$

		ArrayList<RebaseTodoLine> toDoSteps = new ArrayList<>();
		toDoSteps.add(new RebaseTodoLine("# Created by EGit: rebasing " + headId.name() //$NON-NLS-1$
						+ " onto " + upstreamCommit.name())); //$NON-NLS-1$
		// determine the commits to be applied
		List<RevCommit> cherryPickList = calculatePickList(headCommit);
		ObjectReader reader = walk.getObjectReader();
		int n = 0;
		for (RevCommit commit : cherryPickList) {
			n++;
			toDoSteps.add(new RebaseTodoLine(Action.PICK, reader
					.abbreviate(commit), commit.getShortMessage()));
			if (!isInteractive() && !preserveMerges) {
				// Write the files native git expects
				rebaseState.createFile(CMT_PREFIX + n, commit.name());
			}
		}
		if (!isInteractive() && !preserveMerges) {
			// Write the files native git expects
			rebaseState.createFile(END, Integer.toString(n));
			rebaseState.createFile(MSGNUM, "1"); //$NON-NLS-1$
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

	private List<RevCommit> calculatePickList(RevCommit headCommit)
			throws GitAPIException, NoHeadException, IOException {
		Iterable<RevCommit> commitsToUse;
		try (Git git = new Git(repo)) {
			LogCommand cmd = git.log().addRange(upstreamCommit, headCommit);
			commitsToUse = cmd.call();
		}
		List<RevCommit> cherryPickList = new ArrayList<>();
		for (RevCommit commit : commitsToUse) {
			if (preserveMerges || commit.getParentCount() == 1)
				cherryPickList.add(commit);
		}
		Collections.reverse(cherryPickList);

		if (preserveMerges) {
			// When preserving merges we only rewrite commits which have at
			// least one parent that is itself rewritten (or a merge base)
			File rewrittenDir = rebaseState.getRewrittenDir();
			FileUtils.mkdir(rewrittenDir, false);
			walk.reset();
			walk.setRevFilter(RevFilter.MERGE_BASE);
			walk.markStart(upstreamCommit);
			walk.markStart(headCommit);
			RevCommit base;
			while ((base = walk.next()) != null)
				RebaseState.createFile(rewrittenDir, base.getName(),
						upstreamCommit.getName());

			Iterator<RevCommit> iterator = cherryPickList.iterator();
			pickLoop: while(iterator.hasNext()){
				RevCommit commit = iterator.next();
				for (int i = 0; i < commit.getParentCount(); i++) {
					boolean parentRewritten = new File(rewrittenDir, commit
							.getParent(i).getName()).exists();
					if (parentRewritten) {
						new File(rewrittenDir, commit.getName()).createNewFile();
						continue pickLoop;
					}
				}
				// commit is only merged in, needs not be rewritten
				iterator.remove();
			}
		}
		return cherryPickList;
	}

	private static String getHeadName(Ref head) {
		String headName;
		if (head.isSymbolic()) {
			headName = head.getTarget().getName();
		} else {
			ObjectId headId = head.getObjectId();
			// the callers are checking this already
			assert headId != null;
			headName = headId.getName();
		}
		return headName;
	}

	private Ref getHead() throws IOException, RefNotFoundException {
		Ref head = repo.exactRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		return head;
	}

	private boolean isInteractive() {
		return interactiveHandler != null;
	}

	/**
	 * Check if we can fast-forward and returns the new head if it is possible
	 *
	 * @param newCommit
	 *            a {@link org.eclipse.jgit.revwalk.RevCommit} object to check
	 *            if we can fast-forward to.
	 * @return the new head, or null
	 * @throws java.io.IOException
	 * @throws org.eclipse.jgit.api.errors.GitAPIException
	 */
	public RevCommit tryFastForward(RevCommit newCommit) throws IOException,
			GitAPIException {
		Ref head = getHead();

		ObjectId headId = head.getObjectId();
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		if (walk.isMergedInto(newCommit, headCommit))
			return newCommit;

		String headName = getHeadName(head);
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
			co.setProgressMonitor(monitor);
			co.setName(newCommit.name()).call();
			if (headName.startsWith(Constants.R_HEADS)) {
				RefUpdate rup = repo.updateRef(headName);
				rup.setExpectedOldObjectId(oldCommit);
				rup.setNewObjectId(newCommit);
				rup.setRefLogMessage("Fast-forward from " + oldCommit.name() //$NON-NLS-1$
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
		if (this.operation == Operation.PROCESS_STEPS) {
			if (rebaseState.getFile(DONE).exists())
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));
		}
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
									"upstream")); //$NON-NLS-1$
				return;
			default:
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));

			}
	}

	private RebaseResult abort(RebaseResult result) throws IOException,
			GitAPIException {
		ObjectId origHead = getOriginalHead();
		try {
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
			walk.close();
		} finally {
			monitor.endTask();
		}
		try {
			String headName = rebaseState.readFile(HEAD_NAME);
				monitor.beginTask(MessageFormat.format(
						JGitText.get().resettingHead, headName),
						ProgressMonitor.UNKNOWN);

			Result res = null;
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, false);
			refUpdate.setRefLogMessage("rebase: aborting", false); //$NON-NLS-1$
			if (headName.startsWith(Constants.R_REFS)) {
				// update the HEAD
				res = refUpdate.link(headName);
			} else {
				refUpdate.setNewObjectId(origHead);
				res = refUpdate.forceUpdate();

			}
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException(
						JGitText.get().abortingRebaseFailed);
			}
			boolean stashConflicts = autoStashApply();
			// cleanup the files
			FileUtils.delete(rebaseState.getDir(), FileUtils.RECURSIVE);
			repo.writeCherryPickHead(null);
			repo.writeMergeHeads(null);
			if (stashConflicts)
				return RebaseResult.STASH_APPLY_CONFLICTS_RESULT;
			return result;

		} finally {
			monitor.endTask();
		}
	}

	private ObjectId getOriginalHead() throws IOException {
		try {
			return ObjectId.fromString(rebaseState.readFile(REBASE_HEAD));
		} catch (FileNotFoundException e) {
			try {
				return ObjectId
						.fromString(rebaseState.readFile(REBASE_HEAD_LEGACY));
			} catch (FileNotFoundException ex) {
				return repo.readOrigHead();
			}
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
			dco.setProgressMonitor(monitor);
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
				throw new IOException(
						JGitText.get().couldNotRewindToUpstreamCommit);
			}
		} finally {
			walk.close();
			monitor.endTask();
		}
		return true;
	}


	/**
	 * Set upstream {@code RevCommit}
	 *
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
	 * Set the upstream commit
	 *
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
	 * Set the upstream branch
	 *
	 * @param upstream
	 *            the name of the upstream branch
	 * @return {@code this}
	 * @throws org.eclipse.jgit.api.errors.RefNotFoundException
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
					"setUpstreamName must be called after setUpstream."); //$NON-NLS-1$
		}
		this.upstreamCommitName = upstreamName;
		return this;
	}

	/**
	 * Set the operation to execute during rebase
	 *
	 * @param operation
	 *            the operation to perform
	 * @return {@code this}
	 */
	public RebaseCommand setOperation(Operation operation) {
		this.operation = operation;
		return this;
	}

	/**
	 * Set progress monitor
	 *
	 * @param monitor
	 *            a progress monitor
	 * @return this instance
	 */
	public RebaseCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}

	/**
	 * Enable interactive rebase
	 * <p>
	 * Does not stop after initialization of interactive rebase. This is
	 * equivalent to
	 * {@link org.eclipse.jgit.api.RebaseCommand#runInteractively(InteractiveHandler, boolean)
	 * runInteractively(handler, false)};
	 * </p>
	 *
	 * @param handler
	 *            the
	 *            {@link org.eclipse.jgit.api.RebaseCommand.InteractiveHandler}
	 *            to use
	 * @return this
	 */
	public RebaseCommand runInteractively(InteractiveHandler handler) {
		return runInteractively(handler, false);
	}

	/**
	 * Enable interactive rebase
	 * <p>
	 * If stopAfterRebaseInteractiveInitialization is {@code true} the rebase
	 * stops after initialization of interactive rebase returning
	 * {@link org.eclipse.jgit.api.RebaseResult#INTERACTIVE_PREPARED_RESULT}
	 * </p>
	 *
	 * @param handler
	 *            the
	 *            {@link org.eclipse.jgit.api.RebaseCommand.InteractiveHandler}
	 *            to use
	 * @param stopAfterRebaseInteractiveInitialization
	 *            if {@code true} the rebase stops after initialization
	 * @return this instance
	 * @since 3.2
	 */
	public RebaseCommand runInteractively(InteractiveHandler handler,
			final boolean stopAfterRebaseInteractiveInitialization) {
		this.stopAfterInitialization = stopAfterRebaseInteractiveInitialization;
		this.interactiveHandler = handler;
		return this;
	}

	/**
	 * Set the <code>MergeStrategy</code>.
	 *
	 * @param strategy
	 *            The merge strategy to use during this rebase operation.
	 * @return {@code this}
	 * @since 3.4
	 */
	public RebaseCommand setStrategy(MergeStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	/**
	 * Whether to preserve merges during rebase
	 *
	 * @param preserve
	 *            {@code true} to re-create merges during rebase. Defaults to
	 *            {@code false}, a flattening rebase.
	 * @return {@code this}
	 * @since 3.5
	 */
	public RebaseCommand setPreserveMerges(boolean preserve) {
		this.preserveMerges = preserve;
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

		Map<String, String> keyValueMap = new HashMap<>();
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

		/**
		 * @return Directory with rewritten commit hashes, usually exists if
		 *         {@link RebaseCommand#preserveMerges} is true
		 **/
		public File getRewrittenDir() {
			return new File(getDir(), REWRITTEN);
		}

		public String readFile(String name) throws IOException {
			try {
				return readFile(getDir(), name);
			} catch (FileNotFoundException e) {
				if (ONTO_NAME.equals(name)) {
					// Older JGit mistakenly wrote a file "onto-name" instead of
					// "onto_name". Try that wrong name just in case somebody
					// upgraded while a rebase started by JGit was in progress.
					File oldFile = getFile(ONTO_NAME.replace('_', '-'));
					if (oldFile.exists()) {
						return readFile(oldFile);
					}
				}
				throw e;
			}
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

		private static String readFile(File file) throws IOException {
			byte[] content = IO.readFully(file);
			// strip off the last LF
			int end = RawParseUtils.prevLF(content, content.length);
			return RawParseUtils.decode(content, 0, end + 1);
		}

		private static String readFile(File directory, String fileName)
				throws IOException {
			return readFile(new File(directory, fileName));
		}

		private static void createFile(File parentDir, String name,
				String content)
				throws IOException {
			File file = new File(parentDir, name);
			try (FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(content.getBytes(UTF_8));
				fos.write('\n');
			}
		}

		private static void appendToFile(File file, String content)
				throws IOException {
			try (FileOutputStream fos = new FileOutputStream(file, true)) {
				fos.write(content.getBytes(UTF_8));
				fos.write('\n');
			}
		}
	}
}
