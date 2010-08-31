/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Commit} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
 *      >Git documentation about Commit</a>
 */
public class CommitCommand extends GitCommand<RevCommit> {
	private PersonIdent author;

	private PersonIdent committer;

	private String message;

	private boolean all;

	/**
	 * parents this commit should have. The current HEAD will be in this list
	 * and also all commits mentioned in .git/MERGE_HEAD
	 */
	private List<ObjectId> parents = new LinkedList<ObjectId>();

	/**
	 * @param repo
	 */
	protected CommitCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code commit} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @return a {@link RevCommit} object representing the successful commit.
	 * @throws NoHeadException
	 *             when called on a git repo without a HEAD reference
	 * @throws NoMessageException
	 *             when called without specifying a commit message
	 * @throws UnmergedPathException
	 *             when the current index contained unmerged pathes (conflicts)
	 * @throws WrongRepositoryStateException
	 *             when repository is not in the right state for committing
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}. Expect only
	 *             {@code IOException's} to be wrapped. Subclasses of
	 *             {@link IOException} (e.g. {@link UnmergedPathException}) are
	 *             typically not wrapped here but thrown as original exception
	 */
	public RevCommit call() throws NoHeadException, NoMessageException,
			UnmergedPathException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException {
		checkCallable();

		RepositoryState state = repo.getRepositoryState();
		if (!state.canCommit())
			throw new WrongRepositoryStateException(MessageFormat.format(
					JGitText.get().cannotCommitOnARepoWithState, state.name()));
		processOptions(state);

		try {
			if (all && !repo.isBare() && repo.getWorkTree() != null) {
				Git git = new Git(repo);
				try {
					git.add()
							.addFilepattern(".")
							.setUpdate(true).call();
				} catch (NoFilepatternException e) {
					// should really not happen
					throw new JGitInternalException(e.getMessage(), e);
				}
			}

			Ref head = repo.getRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);

			// determine the current HEAD and the commit it is referring to
			ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}");
			if (headId != null)
				parents.add(0, headId);

			// lock the index
			DirCache index = repo.lockDirCache();
			try {
				ObjectInserter odi = repo.newObjectInserter();
				try {
					// Write the index as tree to the object database. This may
					// fail for example when the index contains unmerged paths
					// (unresolved conflicts)
					ObjectId indexTreeId = index.writeTree(odi);

					// Create a Commit object, populate it and write it
					CommitBuilder commit = new CommitBuilder();
					commit.setCommitter(committer);
					commit.setAuthor(author);
					commit.setMessage(message);

					commit.setParentIds(parents);
					commit.setTreeId(indexTreeId);
					ObjectId commitId = odi.insert(commit);
					odi.flush();

					RevWalk revWalk = new RevWalk(repo);
					try {
						RevCommit revCommit = revWalk.parseCommit(commitId);
						RefUpdate ru = repo.updateRef(Constants.HEAD);
						ru.setNewObjectId(commitId);
						ru.setRefLogMessage("commit : "
								+ revCommit.getShortMessage(), false);

						ru.setExpectedOldObjectId(headId);
						Result rc = ru.update();
						switch (rc) {
						case NEW:
						case FAST_FORWARD: {
							setCallable(false);
							if (state == RepositoryState.MERGING_RESOLVED) {
								// Commit was successful. Now delete the files
								// used for merge commits
								repo.writeMergeCommitMsg(null);
								repo.writeMergeHeads(null);
							}
							return revCommit;
						}
						case REJECTED:
						case LOCK_FAILURE:
							throw new ConcurrentRefUpdateException(JGitText
									.get().couldNotLockHEAD, ru.getRef(), rc);
						default:
							throw new JGitInternalException(MessageFormat
									.format(JGitText.get().updatingRefFailed,
											Constants.HEAD,
											commitId.toString(), rc));
						}
					} finally {
						revWalk.release();
					}
				} finally {
					odi.release();
				}
			} finally {
				index.unlock();
			}
		} catch (UnmergedPathException e) {
			// since UnmergedPathException is a subclass of IOException
			// which should not be wrapped by a JGitInternalException we
			// have to catch and re-throw it here
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfCommitCommand, e);
		}
	}

	/**
	 * Sets default values for not explicitly specified options. Then validates
	 * that all required data has been provided.
	 *
	 * @param state
	 *            the state of the repository we are working on
	 *
	 * @throws NoMessageException
	 *             if the commit message has not been specified
	 */
	private void processOptions(RepositoryState state) throws NoMessageException {
		if (committer == null)
			committer = new PersonIdent(repo);
		if (author == null)
			author = committer;

		// when doing a merge commit parse MERGE_HEAD and MERGE_MSG files
		if (state == RepositoryState.MERGING_RESOLVED) {
			try {
				parents = repo.readMergeHeads();
			} catch (IOException e) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().exceptionOccuredDuringReadingOfGIT_DIR,
						Constants.MERGE_HEAD, e), e);
			}
			if (message == null) {
				try {
					message = repo.readMergeCommitMsg();
				} catch (IOException e) {
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().exceptionOccuredDuringReadingOfGIT_DIR,
							Constants.MERGE_MSG, e), e);
				}
			}
		}
		if (message == null)
			// as long as we don't suppport -C option we have to have
			// an explicit message
			throw new NoMessageException(JGitText.get().commitMessageNotSpecified);
	}

	/**
	 * @param message
	 *            the commit message used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setMessage(String message) {
		checkCallable();
		this.message = message;
		return this;
	}

	/**
	 * @return the commit message used for the <code>commit</code>
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the committer for this {@code commit}. If no committer is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the committer will be deduced from config info in repository,
	 * with current time.
	 *
	 * @param committer
	 *            the committer used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setCommitter(PersonIdent committer) {
		checkCallable();
		this.committer = committer;
		return this;
	}

	/**
	 * Sets the committer for this {@code commit}. If no committer is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the committer will be deduced from config info in repository,
	 * with current time.
	 *
	 * @param name
	 *            the name of the committer used for the {@code commit}
	 * @param email
	 *            the email of the committer used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setCommitter(String name, String email) {
		checkCallable();
		return setCommitter(new PersonIdent(name, email));
	}

	/**
	 * @return the committer used for the {@code commit}. If no committer was
	 *         specified {@code null} is returned and the default
	 *         {@link PersonIdent} of this repo is used during execution of the
	 *         command
	 */
	public PersonIdent getCommitter() {
		return committer;
	}

	/**
	 * Sets the author for this {@code commit}. If no author is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the author will be set to the committer.
	 *
	 * @param author
	 *            the author used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setAuthor(PersonIdent author) {
		checkCallable();
		this.author = author;
		return this;
	}

	/**
	 * Sets the author for this {@code commit}. If no author is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the author will be set to the committer.
	 *
	 * @param name
	 *            the name of the author used for the {@code commit}
	 * @param email
	 *            the email of the author used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setAuthor(String name, String email) {
		checkCallable();
		return setAuthor(new PersonIdent(name, email));
	}

	/**
	 * @return the author used for the {@code commit}. If no author was
	 *         specified {@code null} is returned and the default
	 *         {@link PersonIdent} of this repo is used during execution of the
	 *         command
	 */
	public PersonIdent getAuthor() {
		return author;
	}

	/**
	 * If set to true the Commit command automatically stages files that have
	 * been modified and deleted, but new files you not known by the repository
	 * are not affected. This corresponds to the parameter -a on the command
	 * line.
	 *
	 * @param all
	 * @return {@code this}
	 */
	public CommitCommand setAll(boolean all) {
		this.all = all;
		return this;
	}

}
