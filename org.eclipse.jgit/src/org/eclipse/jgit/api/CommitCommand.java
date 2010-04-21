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

/**
 * A class used to execute a <code>Commit</code> command. It has setters for all
 * supported options and arguments of this command and a {@link #run()} method
 * to finally execute the command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
 *      >Git documentation about Commit</a>
 *
 */
public class CommitCommand {
	private PersonIdent author;

	private PersonIdent committer;

	private String message;

	private Repository repo;

	/**
	 * @param git
	 */
	protected CommitCommand(Git git) {
		repo = git.getRepository();
	}

	/**
	 * Executes the <code>commit</code> command with all the options and
	 * parameters collected by the setter methods of this class.
	 * <p>
	 * You may call run() multiple times on one instance of this class. If you
	 * don't specify additional parameters or options between the two calls to
	 * run() the commit will be executed with exactly the same parameters.
	 *
	 * @return a {@link Commit} object representing the succesfull commit
	 * @throws CorruptObjectException
	 * @throws UnmergedPathException
	 * @throws IOException
	 */
	public Commit run() throws CorruptObjectException, UnmergedPathException,
			IOException {
		ObjectId commitID;
		ObjectId indexTreeId;
		final Commit commit;
		int firstLineEnd;
		ObjectWriter repoWriter = new ObjectWriter(repo);

		processOptions();

		// determine the current HEAD and the commit he is referring to
		final Ref head = repo.getRef(Constants.HEAD);
		if (head == null || !head.isSymbolic())
			throw new IllegalStateException("Cannot commit on detached HEAD");
		ObjectId parentID = repo.resolve(Constants.HEAD + "^{commit}");

		// lock the index
		DirCache index = DirCache.lock(repo);
		try {
			// Write the index as tree to the object database. This may fail for
			// example when the index contains unmerged pathes (unresolved
			// conflicts)
			indexTreeId = index.writeTree(repoWriter);

			// Create a Commit object, populate it and write it
			commit = new Commit(repo);
			commit.setAuthor(author);
			commit.setCommitter(committer);
			commit.setMessage(message);
			if (parentID!=null)
				commit.setParentIds(new ObjectId[] { parentID });
			commit.setTreeId(indexTreeId);
			commitID = repoWriter.writeCommit(commit);
			commit.setCommitId(commitID);

			// Update the Reference
			firstLineEnd = message.indexOf('\n');
			final RefUpdate ru = repo.updateRef(Constants.HEAD);
			ru.setNewObjectId(commitID);
			ru.setRefLogMessage("commit : "
					+ ((firstLineEnd == -1) ? message : message.substring(0,
							firstLineEnd)), false);
			ru.forceUpdate();

			return commit;
		} finally {
			index.unlock();
		}
	}

	// Sets default values for not explicitly specified options. Afterwards
	// validates that we all the required data. Throws an
	// IllegalArgumentException
	// if an error situation is detected
	private void processOptions() throws IllegalArgumentException {
		if (committer == null) {
			committer = new PersonIdent(repo);
		}
		if (author == null) {
			author = committer;
		}
		if (message == null) {
			// as long as we don't suppport -C option we have to have
			// an explicit message
			throw new IllegalArgumentException("commit message not specified");
		}
	}

	/**
	 * @param message
	 *            the commit message used for the <code>commit</code>
	 * @return this class
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
	 * @param committer
	 *            the committer used for the <code>commit</code>
	 * @return this class
	 */
	public CommitCommand setCommitter(PersonIdent committer) {
		this.committer = committer;
		return this;
	}

	/**
	 * @return the committer used for the <code>commit</code>
	 */
	public PersonIdent getCommitter() {

		return committer;
	}

	/**
	 * @param author
	 *            the author used for the <code>commit</code>
	 * @return this class
	 */
	public CommitCommand setAuthor(PersonIdent author) {
		this.author = author;
		return this;
	}

	/**
	 * @return the author used for the <code>commit</code>
	 */
	public PersonIdent getAuthor() {
		return author;
	}
}
