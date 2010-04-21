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
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a <code>Log</code> command. It has setters for all
 * supported options and arguments of this command and a {@link #run()} method
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

public class LogCommand {
	private Repository repo;

	private ObjectId start;

	/**
	 * @param git
	 */
	protected LogCommand(Git git) {
		repo = git.getRepository();
	}

	// Sets default values for not explicitly specified options. Afterwards
	// validates that we all the required data. Throws an
	// IllegalArgumentException
	// if an error situation is detected
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
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	public List<RevCommit> run() throws IllegalStateException,
			MissingObjectException, IncorrectObjectTypeException, IOException {
		processOptions();

		RevWalk walk = new RevWalk(repo);
		LinkedList<RevCommit> ret = new LinkedList<RevCommit>();
		walk.markStart(walk.parseCommit(start));
		RevCommit c = walk.next();
		while(c!=null) {
			ret.add(c);
			c = walk.next();
		}
		return ret;
	}

	/**
	 * @param start
	 *            the {@link ObjectId} representing the revision where log
	 *            should start
	 */
	public void setStart(ObjectId start) {
		this.start = start;
	}

	/**
	 * @param start
	 *            A git object references expression specifying the revision
	 *            where log should start
	 * @throws IOException
	 */
	public void setStart(String start) throws IOException {
		this.start = repo.resolve(start);
		if (start == null)
			throw new IllegalArgumentException("the string <" + start
					+ "> can't be resolved to an objectID");
	}

	/**
	 * @return the {@link ObjectId} representing the revision where log should
	 *         start
	 */
	public ObjectId getStart() {
		return start;
	}
}
