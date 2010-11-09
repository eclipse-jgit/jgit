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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private final RevWalk walk;

	private File rebaseDir = new File(repo.getDirectory(), "rebase-merge");

	/**
	 * @param repo
	 */
	protected RebaseCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
	}

	/**
	 * Executes the {@code Rebase} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
	 *
	 * @return an object describing the result of this command
	 */
	public RebaseResult call() throws NoHeadException, RefNotFoundException,
			JGitInternalException, GitAPIException {
		checkCallable();
		checkParameters();
		try {
			switch (operation) {
			case ABORT:
				try {
					return abort();
				} catch (IOException ioe) {
					throw new JGitInternalException(ioe.getMessage(), ioe);
				}
			case SKIP:
				// fall through
			case CONTINUE:
				String upstreamCommitName = readFile(rebaseDir, "onto");
				this.upstreamCommit = walk.parseCommit(repo
						.resolve(upstreamCommitName));
				break;
			case BEGIN:
				RebaseResult res = initFilesAndRewind();
				if (res != null)
					return res;
			}

			if (this.operation == Operation.CONTINUE)
				throw new UnsupportedOperationException(
						"--continue Not yet implemented");

			if (this.operation == Operation.SKIP)
				throw new UnsupportedOperationException(
						"--skip Not yet implemented");

			RevCommit newHead = null;

			String nextPatchCommit = getNextCommit(false);
			while (nextPatchCommit != null) {
				RevCommit commitToPick = walk.parseCommit(repo
						.resolve(nextPatchCommit));
				monitor.beginTask("Applying " + commitToPick.getShortMessage(),
						ProgressMonitor.UNKNOWN);
				newHead = new Git(repo).cherryPick().include(commitToPick)
						.call();
				monitor.endTask();
				if (newHead == null)
					return new RebaseResult(Status.STOPPED);
				nextPatchCommit = getNextCommit(true);
			}
			RebaseResult result = new RebaseResult(Status.OK);
			if (newHead != null) {
				// checkout old head
				String headName = readFile(rebaseDir, "head-name");
				RefUpdate rup = repo.updateRef(headName);
				rup.setNewObjectId(newHead);
				rup.forceUpdate();
				rup = repo.updateRef(Constants.HEAD);
				rup.link(headName);
				deleteRecursive(rebaseDir);
			}
			return result;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private String getNextCommit(boolean removeCurrent) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(
				rebaseDir, "git-rebase-todo")));
		BufferedWriter bw = null;
		try {
			if (removeCurrent) {
				br.readLine();
			}
			String line = br.readLine();
			String commitLine = line;
			if (removeCurrent && line != null) {
				bw = new BufferedWriter(new FileWriter(new File(rebaseDir,
						"git-rebase-todo")));
				while (line != null) {
					bw.write(line);
					bw.newLine();
					line = br.readLine();
				}
			}
			if (commitLine != null) {
				StringTokenizer stok = new StringTokenizer(line, " ");
				if (stok.countTokens() > 2) {
					stok.nextToken();
					return stok.nextToken();
				}
			}
		} finally {
			br.close();
			if (bw != null)
				bw.close();
		}
		return null;
	}

	private RebaseResult initFilesAndRewind() throws RefNotFoundException,
			IOException, NoHeadException, JGitInternalException {
		// we need to store everything into files so that we can implement
		// --skip, --continue, and --abort

		// first of all, we determine the commits to be applied
		List<RevCommit> cherryPickList = new ArrayList<RevCommit>();

		String headName = repo.getFullBranch();
		ObjectId headId = repo.resolve(Constants.HEAD);
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		// TODO task name localization
		monitor.beginTask("Obtaining commits that need to be cherry-picked",
				ProgressMonitor.UNKNOWN);
		LogCommand cmd = new Git(repo).log().addRange(upstreamCommit,
				headCommit);
		Iterable<RevCommit> commitsToUse = cmd.call();
		for (RevCommit commit : commitsToUse) {
			if (walk.isMergedInto(commit, headCommit))
				continue;
			cherryPickList.add(commit);
		}

		// nothing to do: return with UP_TO_DATE_RESULT
		if (cherryPickList.isEmpty())
			return RebaseResult.UP_TO_DATE_RESULT;

		// we need to go over the list from last to first
		// Collections.reverse(cherryPickList);
		// create the folder for the meta information
		rebaseDir.mkdir();

		createFile(repo.getDirectory(), "ORIG_HEAD", headId.name());
		createFile(rebaseDir, "head", headId.name());
		createFile(rebaseDir, "head-name", headName);
		createFile(rebaseDir, "onto", upstreamCommit.name());
		FileWriter fw = new FileWriter(new File(rebaseDir, "git-rebase-todo"));
		try {
			StringBuilder sb = new StringBuilder();
			ObjectReader reader = repo.newObjectReader();
			for (RevCommit commit : cherryPickList) {
				sb.setLength(0);
				sb.append("pick ");
				sb.append(reader.abbreviate(commit).name());
				sb.append(" ");
				sb.append(commit.getShortMessage());
			}
			fw.write(sb.toString());
		} finally {
			fw.close();
		}

		monitor.endTask();
		// we rewind to the upstream commit
		// TODO task name localization
		monitor.beginTask("Rewinding", ProgressMonitor.UNKNOWN);
		checkoutCommit(upstreamCommit);
		monitor.endTask();
		return null;
	}

	private void checkParameters() throws WrongRepositoryStateException {
		if (this.operation != Operation.BEGIN) {
			// these operations are only possible while in a rebasing sate
			switch (repo.getRepositoryState()) {
			case REBASING:
				// fall through
			case REBASING_INTERACTIVE:
				// fall through
			case REBASING_MERGE:
				// fall through
			case REBASING_REBASING:
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

	private void createFile(File parentDir, String name, String content)
			throws IOException {
		File file = new File(parentDir, name);
		FileOutputStream fos = new FileOutputStream(file);
		try {
			fos.write(content.getBytes("UTF-8"));
		} finally {
			fos.close();
		}
	}

	private RebaseResult abort() throws IOException {
		try {
			String commitId = readFile(repo.getDirectory(), "ORIG_HEAD");
			String headName = readFile(rebaseDir, "head-name");
			// TODO task name localization
			monitor.beginTask("Aborting rebase: resetting " + headName + " to "
					+ commitId, ProgressMonitor.UNKNOWN);

			boolean failure = true;
			// TODO the following fails with some "Duplicate stages not allowed"
			// Exception (when using setFailOnConflict(false)) or a checkout
			// conflict
			// Exception (when using setFailOnConflict(true)).

			// RevCommit commit = walk.parseCommit(repo.resolve(commitId));
			// RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
			// DirCacheCheckout dco = new DirCacheCheckout(repo, head.getTree()
			// .getId(), repo.lockDirCache(), commit.getTree().getId());
			// dco.setFailOnConflict(false);
			// dco.checkout();
			// walk.release();
			// // update the HEAD
			// RefUpdate refUpdate = repo.updateRef(headName, false);
			// refUpdate.setNewObjectId(commit);
			// Result res = refUpdate.forceUpdate();
			// switch (res) {
			// case REJECTED:
			// case IO_FAILURE:
			// case LOCK_FAILURE:
			// throw new IOException("Could not abort rebase");
			// default:
			// }
			if (!failure)
				// cleanup the rebase directory
				deleteRecursive(rebaseDir);
			// return new RebaseResult(Status.ABORTED);
			throw new IOException("Abort not yet implemented");
		} finally {
			monitor.endTask();
		}
	}

	private void deleteRecursive(File fileOrFolder) throws IOException {
		if (fileOrFolder.isDirectory()) {
			for (File child : fileOrFolder.listFiles()) {
				deleteRecursive(child);
			}
		}
		if (!fileOrFolder.delete())
			throw new IOException("Could not delete " + fileOrFolder.getPath());
	}

	private String readFile(File directory, String fileName) throws IOException {
		return RawParseUtils
				.decode(IO.readFully(new File(directory, fileName)));
	}

	private void checkoutCommit(RevCommit commit) throws IOException {
		try {
			// TODO task name localization
			monitor.beginTask(
					"Rewinding to commit " + commit.getShortMessage(),
					ProgressMonitor.UNKNOWN);
			RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
			DirCacheCheckout dco = new DirCacheCheckout(repo, head.getTree()
					.getId(), repo.lockDirCache(), commit.getTree().getId());
			dco.setFailOnConflict(true);
			dco.checkout();
			walk.release();
			// update the HEAD
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, true);
			refUpdate.setExpectedOldObjectId(head);
			refUpdate.setNewObjectId(commit);
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
			monitor.endTask();
		}
	}

	/**
	 * @param upstream
	 *            the upstream commit
	 * @return {@code this}
	 */
	public RebaseCommand setUpstream(RevCommit upstream) {
		this.upstreamCommit = upstream;
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
			return this;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * @return {@code this}
	 */
	public RebaseCommand setAbort() {
		this.operation = Operation.ABORT;
		return this;
	}

	/**
	 * TODO not yet supported
	 *
	 * @return {@code this}
	 */
	public RebaseCommand setSkip() {
		this.operation = Operation.SKIP;
		return this;
	}

	/**
	 * TODO not yet supported
	 *
	 * @return {@code this}
	 */
	public RebaseCommand setContinue() {
		this.operation = Operation.CONTINUE;
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
}
