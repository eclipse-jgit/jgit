/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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
import java.io.IOException;
import java.util.concurrent.Callable;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;

/**
 * Create an empty git repository or reinitalize an existing one
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-init.html"
 *      >Git documentation about init</a>
 */
public class InitCommand implements Callable<Git> {
	private File directory;

	private boolean bare;

	/**
	 * Executes the {@code Init} command.
	 *
	 * @return the newly created {@code Git} object with associated repository
	 */
	public Git call() throws GitAPIException {
		try {
			RepositoryBuilder builder = new RepositoryBuilder();
			if (bare)
				builder.setBare();
			builder.readEnvironment();
			if (directory != null) {
				File d = directory;
				if (!bare)
					d = new File(d, Constants.DOT_GIT);
				builder.setGitDir(d);
			} else if (builder.getGitDir() == null) {
				File d = new File(".");
				if (d.getParentFile() != null)
					d = d.getParentFile();
				if (!bare)
					d = new File(d, Constants.DOT_GIT);
				builder.setGitDir(d);
			}
			Repository repository = builder.build();
			if (!repository.getObjectDatabase().exists())
				repository.create(bare);
			return new Git(repository);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * The optional directory associated with the init operation. If no
	 * directory is set, we'll use the current directory
	 *
	 * @param directory
	 *            the directory to init to
	 * @return this instance
	 */
	public InitCommand setDirectory(File directory) {
		this.directory = directory;
		return this;
	}

	/**
	 * @param bare
	 *            whether the repository is bare or not
	 * @return this instance
	 */
	public InitCommand setBare(boolean bare) {
		this.bare = bare;
		return this;
	}

}
