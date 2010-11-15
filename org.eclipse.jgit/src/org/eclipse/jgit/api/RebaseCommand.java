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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
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

	private final File rebaseDir;

	/**
	 * @param repo
	 */
	protected RebaseCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
		rebaseDir = new File(repo.getDirectory(), "rebase-merge");
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

			if (monitor.isCancelled())
				return abort();

			if (this.operation == Operation.CONTINUE)
				throw new UnsupportedOperationException(
						"--continue Not yet implemented");

			if (this.operation == Operation.SKIP)
				throw new UnsupportedOperationException(
						"--skip Not yet implemented");

			RevCommit newHead = null;

			List<Step> steps = loadSteps();
			ObjectReader or = repo.newObjectReader();
			int stepsToPop = 0;

			for (Step step : steps) {
				if (step.action != Action.PICK)
					continue;
				Collection<ObjectId> ids = or.resolve(step.commit);
				if (ids.size() != 1)
					throw new JGitInternalException(
							"Could not resolve uniquely the abbreviated object ID");
				RevCommit commitToPick = walk
						.parseCommit(ids.iterator().next());
				if (monitor.isCancelled())
					return new RebaseResult(commitToPick);
				// TOOD message localization
				monitor.beginTask("Applying " + commitToPick.getShortMessage(),
						ProgressMonitor.UNKNOWN);
				// TODO if the first parent of commitToPick is the current HEAD,
				// we should fast-forward instead of cherry-pick to avoid
				// unnecessary object rewriting
				newHead = new Git(repo).cherryPick().include(commitToPick)
						.call();
				monitor.endTask();
				if (newHead == null) {
					popSteps(stepsToPop);
					return new RebaseResult(commitToPick);
				}
				stepsToPop++;
			}
			if (newHead != null) {
				// point the previous head (if any) to the new commit
				String headName = readFile(rebaseDir, "head-name");
				if (headName.startsWith(Constants.R_REFS)) {
					RefUpdate rup = repo.updateRef(headName);
					rup.setNewObjectId(newHead);
					rup.forceUpdate();
					rup = repo.updateRef(Constants.HEAD);
					rup.link(headName);
				}
				deleteRecursive(rebaseDir);
				return new RebaseResult(Status.OK);
			}
			return new RebaseResult(Status.UP_TO_DATE);
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
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
		List<String> lines = new ArrayList<String>();
		File file = new File(rebaseDir, "git-rebase-todo");
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), "UTF-8"));
		int popped = 0;
		try {
			// check if the line starts with a action tag (pick, skip...)
			while (popped < numSteps) {
				String popCandidate = br.readLine();
				if (popCandidate == null)
					break;
				int spaceIndex = popCandidate.indexOf(' ');
				boolean pop = false;
				if (spaceIndex >= 0) {
					String actionToken = popCandidate.substring(0, spaceIndex);
					pop = Action.parse(actionToken) != null;
				}
				if (pop)
					popped++;
				else
					lines.add(popCandidate);
			}
			String readLine = br.readLine();
			while (readLine != null) {
				lines.add(readLine);
				readLine = br.readLine();
			}
		} finally {
			br.close();
		}

		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file), "UTF-8"));
		try {
			for (String writeLine : lines) {
				bw.write(writeLine);
				bw.newLine();
			}
		} finally {
			bw.close();
		}
	}

	private RebaseResult initFilesAndRewind() throws RefNotFoundException,
			IOException, NoHeadException, JGitInternalException {
		// we need to store everything into files so that we can implement
		// --skip, --continue, and --abort

		// first of all, we determine the commits to be applied
		List<RevCommit> cherryPickList = new ArrayList<RevCommit>();

		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));

		String headName;
		if (head.isSymbolic())
			headName = head.getTarget().getName();
		else
			headName = "detached HEAD";
		ObjectId headId = head.getObjectId();
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
			cherryPickList.add(commit);
		}

		// nothing to do: return with UP_TO_DATE_RESULT
		if (cherryPickList.isEmpty())
			return RebaseResult.UP_TO_DATE_RESULT;

		Collections.reverse(cherryPickList);
		// create the folder for the meta information
		rebaseDir.mkdir();

		createFile(repo.getDirectory(), "ORIG_HEAD", headId.name());
		createFile(rebaseDir, "head", headId.name());
		createFile(rebaseDir, "head-name", headName);
		createFile(rebaseDir, "onto", upstreamCommit.name());
		BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(new File(rebaseDir, "git-rebase-todo")),
				"UTF-8"));
		fw.write("# Created by EGit: rebasing " + upstreamCommit.name()
				+ " onto " + headId.name());
		fw.newLine();
		try {
			StringBuilder sb = new StringBuilder();
			ObjectReader reader = walk.getObjectReader();
			for (RevCommit commit : cherryPickList) {
				sb.setLength(0);
				sb.append(Action.PICK.toToken());
				sb.append(" ");
				sb.append(reader.abbreviate(commit).name());
				sb.append(" ");
				sb.append(commit.getShortMessage());
				fw.write(sb.toString());
				fw.newLine();
			}
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
			// TODO task name localization
			monitor.beginTask("Aborting rebase: resetting to " + commitId,
					ProgressMonitor.UNKNOWN);

			RevCommit commit = walk.parseCommit(repo.resolve(commitId));
			// no head in order to reset --hard
			DirCacheCheckout dco = new DirCacheCheckout(repo, repo
					.lockDirCache(), commit.getTree());
			dco.setFailOnConflict(false);
			dco.checkout();
			walk.release();
		} finally {
			monitor.endTask();
		}
		try {
			String headName = readFile(rebaseDir, "head-name");
			if (headName.startsWith(Constants.R_REFS)) {
				// TODO task name localization
				monitor.beginTask("Resetting head to " + headName,
						ProgressMonitor.UNKNOWN);

				// update the HEAD
				RefUpdate refUpdate = repo.updateRef(Constants.HEAD, false);
				Result res = refUpdate.link(headName);
				switch (res) {
				case FAST_FORWARD:
				case FORCED:
				case NO_CHANGE:
					break;
				default:
					throw new IOException("Could not abort rebase");
				}
			}
			// cleanup the files
			deleteRecursive(rebaseDir);
			return new RebaseResult(Status.ABORTED);

		} finally {
			monitor.endTask();
		}
	}

	private void deleteRecursive(File fileOrFolder) throws IOException {
		File[] children = fileOrFolder.listFiles();
		if (children != null) {
			for (File child : children)
				deleteRecursive(child);
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
			DirCacheCheckout dco = new DirCacheCheckout(repo, head.getTree(),
					repo.lockDirCache(), commit.getTree());
			dco.setFailOnConflict(true);
			dco.checkout();
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
			walk.release();
			monitor.endTask();
		}
	}

	private List<Step> loadSteps() throws IOException {
		byte[] buf = IO.readFully(new File(rebaseDir, "git-rebase-todo"));
		int ptr = 0;
		int tokenBegin = 0;
		ArrayList<Step> r = new ArrayList<Step>();
		while (ptr < buf.length) {
			tokenBegin = ptr;
			ptr = RawParseUtils.nextLF(buf, ptr);
			int nextSpace = 0;
			int tokenCount = 0;
			Step current = null;
			while (tokenCount < 3 && nextSpace < ptr) {
				switch (tokenCount) {
				case 0:
					nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
					String actionToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					Action action = Action.parse(actionToken);
					if (action != null)
						current = new Step(Action.parse(actionToken));
					break;
				case 1:
					if (current == null)
						break;
					nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
					String commitToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					current.commit = AbbreviatedObjectId
							.fromString(commitToken);
					break;
				case 2:
					if (current == null)
						break;
					nextSpace = ptr;
					int length = ptr - tokenBegin;
					current.shortMessage = new byte[length];
					System.arraycopy(buf, tokenBegin, current.shortMessage, 0,
							length);
					r.add(current);
					break;
				}
				tokenCount++;
			}
		}
		return r;
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

	static enum Action {
		PICK("pick"); // later add SQUASH, EDIT, etc.

		private final String token;

		private Action(String token) {
			this.token = token;
		}

		public String toToken() {
			return this.token;
		}

		static Action parse(String token) {
			if (token.equals("pick") || token.equals("p"))
				return PICK;
			return null;
		}
	}

	static class Step {
		Action action;

		AbbreviatedObjectId commit;

		byte[] shortMessage;

		Step(Action action) {
			this.action = action;
		}
	}
}
