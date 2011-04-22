/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.api;

import java.io.IOException;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lines.RevisionBuilder;
import org.eclipse.jgit.lines.RevisionContainer;

/**
 * Annotate command for building a {@link RevisionContainer} for a file path.
 *
 * @author Kevin Sawicki (kevin@github.com)
 */
public class LineHistoryCommand extends GitCommand<RevisionContainer> {

	private String path;

	/**
	 * @param repo
	 */
	protected LineHistoryCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set file path
	 *
	 * @param filePath
	 * @return this command
	 */
	public LineHistoryCommand setFilePath(String filePath) {
		this.path = filePath;
		return this;
	}

	/**
	 * @see java.util.concurrent.Callable#call()
	 */
	public RevisionContainer call() throws JGitInternalException {
		checkCallable();
		try {
			return new RevisionBuilder(repo, path).build();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

}
