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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.MaxCountRevFilter;
import org.eclipse.jgit.revwalk.filter.SkipRevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * A class used to execute a {@code Log} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 * <p>
 * This is currently a very basic implementation which takes only one starting
 * revision as option.
 *
 * TODO: add more options (revision ranges, sorting, ...)
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-log.html"
 *      >Git documentation about Log</a>
 */
public class LogCommand extends GitCommand<Iterable<RevCommit>> {
	private RevWalk walk;

	private boolean startSpecified = false;

	private final List<PathFilter> pathFilters = new ArrayList<PathFilter>();

	private int maxCount = -1;

	private int skip = -1;

	/**
	 * @param repo
	 */
	protected LogCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
	}

	/**
	 * Executes the {@code Log} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #add(AnyObjectId)},
	 * {@link #not(AnyObjectId)}, ..) of this class. Each instance of this class
	 * should only be used for one invocation of the command. Don't call this
	 * method twice on an instance.
	 *
	 * @return an iteration over RevCommits
	 * @throws NoHeadException
	 *             of the references ref cannot be resolved
	 */
	public Iterable<RevCommit> call() throws GitAPIException, NoHeadException {
		checkCallable();
		if (pathFilters.size() > 0)
			walk.setTreeFilter(AndTreeFilter.create(
					PathFilterGroup.create(pathFilters), TreeFilter.ANY_DIFF));
		if (skip > -1 && maxCount > -1)
			walk.setRevFilter(AndRevFilter.create(SkipRevFilter.create(skip),
					MaxCountRevFilter.create(maxCount)));
		else if (skip > -1)
			walk.setRevFilter(SkipRevFilter.create(skip));
		else if (maxCount > -1)
			walk.setRevFilter(MaxCountRevFilter.create(maxCount));
		if (!startSpecified) {
			try {
				ObjectId headId = repo.resolve(Constants.HEAD);
				if (headId == null)
					throw new NoHeadException(
							JGitText.get().noHEADExistsAndNoExplicitStartingRevisionWasSpecified);
				add(headId);
			} catch (IOException e) {
				// all exceptions thrown by add() shouldn't occur and represent
				// severe low-level exception which are therefore wrapped
				throw new JGitInternalException(
						JGitText.get().anExceptionOccurredWhileTryingToAddTheIdOfHEAD,
						e);
			}
		}
		setCallable(false);
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
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}. Expect only
	 *             {@code IOException's} to be wrapped. Subclasses of
	 *             {@link IOException} (e.g. {@link MissingObjectException}) are
	 *             typically not wrapped here but thrown as original exception
	 */
	public LogCommand add(AnyObjectId start) throws MissingObjectException,
			IncorrectObjectTypeException {
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
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}. Expect only
	 *             {@code IOException's} to be wrapped. Subclasses of
	 *             {@link IOException} (e.g. {@link MissingObjectException}) are
	 *             typically not wrapped here but thrown as original exception
	 */
	public LogCommand not(AnyObjectId start) throws MissingObjectException,
			IncorrectObjectTypeException {
		return add(false, start);
	}

	/**
	 * Adds the range {@code since..until}
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
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}. Expect only
	 *             {@code IOException's} to be wrapped. Subclasses of
	 *             {@link IOException} (e.g. {@link MissingObjectException}) are
	 *             typically not wrapped here but thrown as original exception
	 */
	public LogCommand addRange(AnyObjectId since, AnyObjectId until)
			throws MissingObjectException, IncorrectObjectTypeException {
		return not(since).add(until);
	}

	/**
	 * Add all refs as commits to start the graph traversal from.
	 *
	 * @see #add(AnyObjectId)
	 * @return {@code this}
	 * @throws IOException
	 *             the references could not be accessed
	 */
	public LogCommand all() throws IOException {
		for (Ref ref : getRepository().getAllRefs().values()) {
			if(!ref.isPeeled())
				ref = getRepository().peel(ref);

			ObjectId objectId = ref.getPeeledObjectId();
			if (objectId == null)
				objectId = ref.getObjectId();
			RevCommit commit = null;
			try {
				commit = walk.parseCommit(objectId);
			} catch (MissingObjectException e) {
				// ignore: the ref points to an object that does not exist;
				// it should be ignored as traversal starting point.
			} catch (IncorrectObjectTypeException e) {
				// ignore: the ref points to an object that is not a commit
				// (e.g. a tree or a blob);
				// it should be ignored as traversal starting point.
			}
			if (commit != null)
				add(commit);
		}
		return this;
	}

	/**
	 * Show only commits that affect any of the specified paths. The path must
	 * either name a file or a directory exactly. Note that regex expressions or
	 * wildcards are not supported.
	 *
	 * @param path
	 *            a path is relative to the top level of the repository
	 * @return {@code this}
	 */
	public LogCommand addPath(String path) {
		checkCallable();
		pathFilters.add(PathFilter.create(path));
		return this;
	}

	/**
	 * Skip the number of commits before starting to show the commit output.
	 *
	 * @param skip
	 *            the number of commits to skip
	 * @return {@code this}
	 */
	public LogCommand setSkip(int skip) {
		checkCallable();
		this.skip = skip;
		return this;
	}

	/**
	 * Limit the number of commits to output.
	 *
	 * @param maxCount
	 *            the limit
	 * @return {@code this}
	 */
	public LogCommand setMaxCount(int maxCount) {
		checkCallable();
		this.maxCount = maxCount;
		return this;
	}

	private LogCommand add(boolean include, AnyObjectId start)
			throws MissingObjectException, IncorrectObjectTypeException,
			JGitInternalException {
		checkCallable();
		try {
			if (include) {
				walk.markStart(walk.lookupCommit(start));
				startSpecified = true;
			} else
				walk.markUninteresting(walk.lookupCommit(start));
			return this;
		} catch (MissingObjectException e) {
			throw e;
		} catch (IncorrectObjectTypeException e) {
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionOccurredDuringAddingOfOptionToALogCommand
					, start), e);
		}
	}
}
