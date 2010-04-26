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
import java.util.concurrent.Callable;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Log} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 * <p>
 * This is currently a very basic implementation which takes only one starting
 * revision as option.
 *
 * @TODO add more options (revision ranges, sorting, ...)
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-log.html"
 *      >Git documentation about Log</a>
 */
public class LogCommand implements Callable<Iterable<RevCommit>> {
	private final Repository repo;

	private ObjectId start;

	private RevWalk walk;

	/**
	 * @param git
	 */
	protected LogCommand(Git git) {
		repo = git.getRepository();
		walk = new RevWalk(repo);
	}

	/**
	 * Sets default values for not explicitly specified options. Then validates
	 * that all required data has been provided.
	 *
	 * @throws IllegalArgumentException
	 *             if an error situation is detected
	 * @throws IOException
	 *             for I/O error or unexpected object type.
	 */
	private void processOptions() throws IllegalArgumentException, IOException {
		if (start == null) {
			start = repo.resolve(Constants.HEAD);
			if (start == null)
				throw new IllegalStateException("Cannot resolve "
						+ Constants.HEAD);
		}
	}

	/**
	 * @return a list of RevCommits
	 * @throws IllegalStateException
	 *             The current HEAD could not be resolved
	 * @throws MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public Iterable<RevCommit> call() throws IllegalStateException,
			MissingObjectException, IncorrectObjectTypeException, IOException {
		processOptions();

		walk = new RevWalk(repo);
		walk.markStart(walk.parseCommit(start));
		return walk;
	}

	/**
	 * Mark a commit to start graph traversal from.
	 *
	 * @see RevWalk#markStart(RevCommit)
	 * @param start
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public LogCommand add(ObjectId start) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return add(true, start);
	}

	/**
	 * Same as {@code --not start}, or {@code ^start}
	 *
	 * @param start
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 * */
	public LogCommand not(ObjectId start) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return add(false, start);
	}

	/**
	 * Adds the range {@code p..q}
	 *
	 * @param since
	 * @param until
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link RevWalk#lookupCommit(AnyObjectId)}.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public LogCommand addRange(ObjectId since, ObjectId until)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return not(since).add(until);
	}

	private LogCommand add(boolean include, ObjectId start)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (include)
			walk.markStart(walk.lookupCommit(start));
		else
			walk.markUninteresting(walk.lookupCommit(start));
		return this;
	}
}