/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a {@code Status} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-status.html"
 *      >Git documentation about Status</a>
 */
public class StatusCommand extends GitCommand<Status> {
	private WorkingTreeIterator workingTreeIt;
	private List<String> paths = null;
	private ProgressMonitor progressMonitor = null;

	private IgnoreSubmoduleMode ignoreSubmoduleMode = null;

	/**
	 * @param repo
	 */
	protected StatusCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @param mode
	 * @return {@code this}
	 * @since 3.6
	 */
	public StatusCommand setIgnoreSubmodules(IgnoreSubmoduleMode mode) {
		ignoreSubmoduleMode = mode;
		return this;
	}

	/**
	 * Show only the status of files which match the given paths. The path must
	 * either name a file or a directory exactly. All paths are always relative
	 * to the repository root. If a directory is specified all files recursively
	 * underneath that directory are matched. If this method is called multiple
	 * times then the status of those files is reported which match at least one
	 * of the given paths. Note that regex expressions or wildcards are not
	 * supported.
	 *
	 * @param path
	 *            repository-relative path of file/directory to show status for
	 *            (with <code>/</code> as separator)
	 * @return {@code this}
	 * @since 3.1
	 */
	public StatusCommand addPath(String path) {
		if (paths == null)
			paths = new LinkedList<>();
		paths.add(path);
		return this;
	}

	/**
	 * Returns the paths filtering this status.
	 *
	 * @return the paths for which the status is shown or <code>null</code> if
	 *         the complete status for the whole repo is shown.
	 * @since 3.1
	 */
	public List<String> getPaths() {
		return paths;
	}

	/**
	 * Executes the {@code Status} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
	 *
	 * @return a {@link Status} object telling about each path where working
	 *         tree, index or HEAD differ from each other.
	 */
	@Override
	public Status call() throws GitAPIException, NoWorkTreeException {
		if (workingTreeIt == null)
			workingTreeIt = new FileTreeIterator(repo);

		try {
			IndexDiff diff = new IndexDiff(repo, Constants.HEAD, workingTreeIt);
			if (ignoreSubmoduleMode != null)
				diff.setIgnoreSubmoduleMode(ignoreSubmoduleMode);
			if (paths != null)
				diff.setFilter(PathFilterGroup.createFromStrings(paths));
			if (progressMonitor == null)
				diff.diff();
			else
				diff.diff(progressMonitor, ProgressMonitor.UNKNOWN,
						ProgressMonitor.UNKNOWN, ""); //$NON-NLS-1$
			return new Status(diff);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * To set the {@link WorkingTreeIterator} which should be used. If this
	 * method is not called a standard {@link FileTreeIterator} is used.
	 *
	 * @param workingTreeIt
	 *            a working tree iterator
	 * @return {@code this}
	 */
	public StatusCommand setWorkingTreeIt(WorkingTreeIterator workingTreeIt) {
		this.workingTreeIt = workingTreeIt;
		return this;
	}

	/**
	 * To set the {@link ProgressMonitor} which contains callback methods to
	 * inform you about the progress of this command.
	 *
	 * @param progressMonitor
	 * @return {@code this}
	 * @since 3.1
	 */
	public StatusCommand setProgressMonitor(ProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
		return this;
	}
}
