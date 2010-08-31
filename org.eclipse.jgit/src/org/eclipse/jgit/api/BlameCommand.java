/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
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
import java.util.List;

import org.eclipse.jgit.blame.BlameEntry;
import org.eclipse.jgit.blame.MyersDiffImpl;
import org.eclipse.jgit.blame.Origin;
import org.eclipse.jgit.blame.Scoreboard;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A class used to execute a {@code Blame} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-blame.html"
 *      >Git documentation about Blame</a>
 */
public class BlameCommand extends GitCommand<Iterable<BlameEntry>> {

	private final RevWalk revWalk;

	private String path;

	private RevCommit initialCommit;

	/**
	 *
	 * @param repo
	 */
	public BlameCommand(Repository repo) {
		super(repo);
		this.revWalk = new RevWalk(repo);
	}

	/**
	 * Executes the {@code Blame} command. Each instance of this class should
	 * only be used for one invocation of the command. Don't call this method
	 * twice on an instance.
	 *
	 * @throws NoHeadException
	 *             when called on a git repo without a HEAD reference
	 *
	 * @return an iteration over {@link BlameEntry}s
	 */
	public Iterable<BlameEntry> call() throws NoHeadException {
		checkCallable();
		if (path == null)
			throw new IllegalArgumentException("No path given");

		List<BlameEntry> blame;
		if (initialCommit == null) {
			try {
				ObjectId headId = getRepository().resolve(Constants.HEAD);
				if (headId == null)
					throw new NoHeadException("no head found");
				initialCommit = revWalk.parseCommit(headId);
			} catch (IOException e) {
				throw new JGitInternalException(
						"Exeception during blame command", e);
			}
		}

		Origin finalOrigin = new Origin(getRepository(), initialCommit, path);
		Scoreboard scoreboard = new Scoreboard(finalOrigin, new MyersDiffImpl());
		blame = scoreboard.assingBlame();
		setCallable(false);
		return blame;
	}

	/**
	 * @param filepath
	 *            File to blame.
	 * @return {@code this}
	 */
	public BlameCommand setPath(String filepath) {
		checkCallable();
		this.path = filepath;
		return this;
	}

	/**
	 * Sets the commit where the blaming should start from.
	 *
	 * @param startCommit
	 * @return {@code this}
	 */
	public BlameCommand setStartCommit(RevCommit startCommit) {
		checkCallable();
		initialCommit = startCommit;
		return this;
	}
}
