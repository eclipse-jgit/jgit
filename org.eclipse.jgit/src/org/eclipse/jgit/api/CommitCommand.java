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
import java.util.Date;
import java.util.concurrent.Callable;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
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
public class CommitCommand implements Callable<RevCommit> {
	/**
	 * Text for {@link IllegalStateException} when trying to commit without a
	 * HEAD
	 */
	public static final String CANNOT_COMMIT_WITHOUT_A_HEAD = "Cannot commit without a HEAD";

	/**
	 * Message text of the exception telling that no commit message was
	 * specified
	 */
	public static final String COMMIT_MESSAGE_NOT_SPECIFIED = "commit message not specified";

	private PersonIdent author;

	private PersonIdent committer;

	private String message;

	private final Repository repo;

	/**
	 * @param git
	 */
	protected CommitCommand(Git git) {
		repo = git.getRepository();
	}

	/**
	 * Executes the {@code commit} command with all the options and parameters
	 * collected by the setter methods of this class.
	 * <p>
	 * You may call run() multiple times on one instance of this class. If you
	 * don't specify additional parameters or options between the two calls to
	 * run() the commit will be executed with exactly the same parameters.
	 *
	 * @return a {@link Commit} object representing the succesfull commit
	 * @throws CorruptObjectException
	 * @throws UnmergedPathException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 *             thrown if no commit message was specified. Exception text
	 *             would be {@link CommitCommand#COMMIT_MESSAGE_NOT_SPECIFIED}
	 */
	public RevCommit call() throws CorruptObjectException,
			UnmergedPathException, IOException, IllegalArgumentException {
		processOptions();

		// determine the current HEAD and the commit it is referring to
		final Ref head = repo.getRef(Constants.HEAD);
		if (head == null)
			throw new IllegalStateException(CANNOT_COMMIT_WITHOUT_A_HEAD);
		final ObjectId parentID = repo.resolve(Constants.HEAD + "^{commit}");

		// lock the index
		final DirCache index = DirCache.lock(repo);
		try {
			final ObjectWriter repoWriter = new ObjectWriter(repo);

			// Write the index as tree to the object database. This may fail for
			// example when the index contains unmerged pathes (unresolved
			// conflicts)
			final ObjectId indexTreeId = index.writeTree(repoWriter);
			// Create a Commit object, populate it and write it
			final Commit commit = new Commit(repo);
			final Date currentDate = new Date();
			final PersonIdent currentCommitter = committer == null ? new PersonIdent(
					repo)
					: new PersonIdent(committer, currentDate);
			commit.setCommitter(currentCommitter);
			commit.setAuthor(author == null ? currentCommitter
					: new PersonIdent(author, currentDate));
			commit.setMessage(message);
			if (parentID != null)
				commit.setParentIds(new ObjectId[] { parentID });
			commit.setTreeId(indexTreeId);
			final ObjectId commitId = repoWriter.writeCommit(commit);
			commit.setCommitId(commitId);

			final RevCommit revCommit = new RevWalk(repo).parseCommit(commitId);
			final RefUpdate ru = repo.updateRef(Constants.HEAD);
			ru.setNewObjectId(commitId);
			ru.setRefLogMessage("commit : " + revCommit.getShortMessage(),
					false);

			ru.setExpectedOldObjectId(parentID);
			switch (ru.update()) {
			case NEW:
			case FAST_FORWARD:
				return revCommit;
			default:
				throw new IllegalStateException("Updating the ref "
						+ Constants.HEAD + " to " + commitId.toString()
						+ " failed");
			}
		} finally {
			index.unlock();
		}
	}

	/**
	 * Sets default values for not explicitly specified options. Then validates
	 * that all required data has been provided.
	 *
	 * @throws IllegalArgumentException
	 *             if the commit message has not been specified
	 */
	private void processOptions() throws IllegalArgumentException {
		if (message == null)
			// as long as we don't suppport -C option we have to have
			// an explicit message
			throw new IllegalArgumentException(COMMIT_MESSAGE_NOT_SPECIFIED);
	}

	/**
	 * @param message
	 *            the commit message used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setMessage(String message) {
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
		this.committer = new PersonIdent(name, email);
		return this;
	}

	/**
	 * @return the committer used for the {@code commit}. The timestamp in the
	 *         returned value is not accurate, because the timestamp will be
	 *         updated just before doing the {@code commit}
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
		this.author = new PersonIdent(name, email);
		return this;
	}

	/**
	 * @return the author used for the {@code commit}. The timestamp in the
	 *         returned value is not accurate, because the timestamp will be
	 *         updated just before doing the {@code commit}
	 */
	public PersonIdent getAuthor() {
		return author;
	}
}
