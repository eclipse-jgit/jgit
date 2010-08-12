/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import org.eclipse.jgit.lib.Repository;

/**
 * Offers a "GitPorcelain"-like API to interact with a git repository.
 * <p>
 * The GitPorcelain commands are described in the <a href="http://www.kernel.org/pub/software/scm/git/docs/git.html#_high_level_commands_porcelain"
 * >Git Documentation</a>.
 * <p>
 * This class only offers methods to construct so-called command classes. Each
 * GitPorcelain command is represented by one command class.<br>
 * Example: this class offers a {@code commit()} method returning an instance of
 * the {@code CommitCommand} class. The {@code CommitCommand} class has setters
 * for all the arguments and options. The {@code CommitCommand} class also has a
 * {@code call} method to actually execute the commit. The following code show's
 * how to do a simple commit:
 *
 * <pre>
 * Git git = new Git(myRepo);
 * git.commit().setMessage(&quot;Fix393&quot;).setAuthor(developerIdent).call();
 * </pre>
 *
 * All mandatory parameters for commands have to be specified in the methods of
 * this class, the optional parameters have to be specified by the
 * setter-methods of the Command class.
 * <p>
 * This class is intended to be used internally (e.g. by JGit tests) or by
 * external components (EGit, third-party tools) when they need exactly the
 * functionality of a GitPorcelain command. There are use-cases where this class
 * is not optimal and where you should use the more low-level JGit classes. The
 * methods in this class may for example offer too much functionality or they
 * offer the functionality with the wrong arguments.
 */
public class Git {
	/** The git repository this class is interacting with */
	private final Repository repo;

	/**
	 * Constructs a new {@link Git} object which can interact with the specified
	 * git repository. All command classes returned by methods of this class
	 * will always interact with this git repository.
	 *
	 * @param repo
	 *            the git repository this class is interacting with.
	 *            {@code null} is not allowed
	 */
	public Git(Repository repo) {
		if (repo == null)
			throw new NullPointerException();
		this.repo = repo;
	}

	/**
	 * Returns a command object to execute a {@code Commit} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
	 *      >Git documentation about Commit</a>
	 * @return a {@link CommitCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Commit} command
	 */
	public CommitCommand commit() {
		return new CommitCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Log} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-log.html"
	 *      >Git documentation about Log</a>
	 * @return a {@link LogCommand} used to collect all optional parameters and
	 *         to finally execute the {@code Log} command
	 */
	public LogCommand log() {
		return new LogCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Merge} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
	 *      >Git documentation about Merge</a>
	 * @return a {@link MergeCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Merge} command
	 */
	public MergeCommand merge() {
		return new MergeCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Add} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-add.html"
	 *      >Git documentation about Add</a>
	 * @return a {@link AddCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Add} command
	 */
	public AddCommand add() {
		return new AddCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Tag} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
	 *      >Git documentation about Tag</a>
	 * @return a {@link TagCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Tag} command
	 */
	public TagCommand tag() {
		return new TagCommand(repo);
	}

	/**
	 * @return the git repository this class is interacting with
	 */
	public Repository getRepository() {
		return repo;
	}

}
