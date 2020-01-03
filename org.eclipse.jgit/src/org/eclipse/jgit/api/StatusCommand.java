/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-status.html" >Git
 *      documentation about Status</a>
 */
public class StatusCommand extends GitCommand<Status> {
	private WorkingTreeIterator workingTreeIt;
	private List<String> paths = null;
	private ProgressMonitor progressMonitor = null;

	private IgnoreSubmoduleMode ignoreSubmoduleMode = null;

	/**
	 * Constructor for StatusCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected StatusCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Whether to ignore submodules
	 *
	 * @param mode
	 *            the
	 *            {@link org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode}
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
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Status} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
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
	 * To set the {@link org.eclipse.jgit.treewalk.WorkingTreeIterator} which
	 * should be used. If this method is not called a standard
	 * {@link org.eclipse.jgit.treewalk.FileTreeIterator} is used.
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
	 * To set the {@link org.eclipse.jgit.lib.ProgressMonitor} which contains
	 * callback methods to inform you about the progress of this command.
	 *
	 * @param progressMonitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor} object.
	 * @return {@code this}
	 * @since 3.1
	 */
	public StatusCommand setProgressMonitor(ProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
		return this;
	}
}
