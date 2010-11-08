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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

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
	private RevCommit upstreamCommit;

	private RevCommit headCommit;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private final RevWalk walk;

	private boolean abort = false;

	private boolean continueRebase = false;

	private boolean skipRebase = false;

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

		if (this.abort)
			try {
				return abort();
			} catch (IOException ioe) {
				throw new JGitInternalException(ioe.getMessage(), ioe);
			}

		// the first list holds the commits that are to be cherry-picked
		// during this rebase command
		List<RevCommit> cherryPickList = new ArrayList<RevCommit>();
		try {
			String headName = repo.getFullBranch();
			ObjectId headId = repo.resolve(Constants.HEAD);
			if (headId == null)
				throw new RefNotFoundException(MessageFormat.format(JGitText
						.get().refNotResolved, Constants.HEAD));
			headCommit = walk.lookupCommit(headId);

			Git git = new Git(repo);

			monitor.beginTask("Obtain commits that need to be cherry-picked",
					ProgressMonitor.UNKNOWN);
			LogCommand cmd = git.log().addRange(upstreamCommit, headCommit);
			Iterable<RevCommit> commitsToUse = cmd.call();
			for (RevCommit commit : commitsToUse) {
				cherryPickList.add(commit);
			}

			monitor.beginTask("Obtain commits that need to be cherry-picked",
					ProgressMonitor.UNKNOWN);
			if (cherryPickList.isEmpty())
				return new RebaseResult(Status.UP_TO_DATE);
			// create the folder for the rebase meta information
			File rebaseDir = new File(repo.getDirectory(), "rebase-apply");
			rebaseDir.mkdir();

			createFile(repo.getDirectory(), "ORIG_HEAD", headCommit.name());
			createFile(rebaseDir, "quiet", "");
			createFile(rebaseDir, "head-name", headName);
			createFile(rebaseDir, "orig-head", headCommit.name());
			createFile(rebaseDir, "onto", upstreamCommit.name());
			createFile(rebaseDir, "apply-opt", "");
			createFile(rebaseDir, "last", Integer.toString(cherryPickList
					.size()));

			int total = cherryPickList.size();
			int current = 1;
			// create the files with the patches
			for (RevCommit commit : cherryPickList) {
				String filename = Integer.toString(current);
				while (filename.length() < 4)
					// patch with leading zeros
					filename = "0" + filename;
				createFile(rebaseDir, filename, "");
				File outFile = new File(rebaseDir, filename);
				FileOutputStream fos = new FileOutputStream(outFile);
				DiffFormatter df = new DiffFormatter(fos);
				df.setAbbreviationLength(40);
				df.setRepository(repo);

				df.format(commit.getParent(0), commit);
				df.flush();
				fos.close();
				current++;
			}

			current = 1;
			RevCommit newHead = null;
			// before cherry-picking, we need to point our current branch
			// the upstream branch
			checkoutCommit(upstreamCommit);
			for (RevCommit commitToPick : cherryPickList) {
				createFile(rebaseDir, "rebasing", "");
				monitor.beginTask("Applying commit " + current + " of " + total
						+ ": " + commitToPick.getShortMessage(),
						ProgressMonitor.UNKNOWN);
				newHead = git.cherryPick().include(commitToPick).call();
				monitor.endTask();
				if (newHead == null)
					return new RebaseResult(Status.STOPPED);
				createFile(rebaseDir, "next", Integer.toString(current));
				current++;
			}
			return new RebaseResult(Status.OK);
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private void checkParameters() {
		if (this.abort || this.continueRebase || this.skipRebase)
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
				// TODO JGIt APi Exception
				throw new IllegalStateException("Illegal Repository State");
			}
		else
			switch (repo.getRepositoryState()) {
			case SAFE:
				return;
			default:
				// TODO JGIt APi Exception
				throw new IllegalStateException("Illegal Repository State");
			}
	}

	private void createFile(File parentDir, String name, String content)
			throws IOException {
		File file = new File(parentDir, name);
		file.createNewFile();
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(content.getBytes("UTF-8"));
		} finally {
			if (fos != null)
				fos.close();
		}
	}

	private RebaseResult abort() throws IOException {
		try {
			File rebaseDir = new File(repo.getDirectory(), "rebase-apply");
			String commitId = readFile(rebaseDir, "orig-head");
			String headName = readFile(rebaseDir, "head-name");
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
		File file = new File(directory, fileName);
		FileReader fr = new FileReader(file);
		StringBuilder sb = new StringBuilder();
		char[] chars = new char[100];
		int read = fr.read(chars);
		while (read > -1) {
			sb.append(chars, 0, read);
			read = fr.read(chars);
		}
		return sb.toString();
	}

	private void checkoutCommit(RevCommit commit) throws IOException {
		try {
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
			refUpdate.setNewObjectId(commit);
			Result res = refUpdate.forceUpdate();
			switch (res) {
			case REJECTED:
			case IO_FAILURE:
			case LOCK_FAILURE:
				throw new IOException("Could not rewind to upstream commit");
			default:
				break;
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
	 * TODO not yet supported
	 *
	 * @return {@code this}
	 */
	public RebaseCommand setAbort() {
		this.abort = true;
		this.skipRebase = false;
		this.continueRebase = false;
		return this;
	}

	/**
	 * TODO not yet supported
	 *
	 * @return {@code this}
	 */
	public RebaseCommand setSkip() {
		this.abort = false;
		this.skipRebase = true;
		this.continueRebase = false;
		return this;
	}

	/**
	 * @return {@code this}
	 */
	public RebaseCommand setContinue() {
		this.abort = false;
		this.skipRebase = false;
		this.continueRebase = true;
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
			upstreamCommit = new RevWalk(repo).parseCommit(repo
					.resolve(upstream));
			return this;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
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
