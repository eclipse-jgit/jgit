/*
 * Copyright (C) 2011, Ketan Padegaonkar <KetanPadegaonkar@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ant.tasks;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.URIish;

/**
 * Clone a repository into a new directory.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
 *      >git-clone(1)</a>
 */
public class GitCloneTask extends Task {

	private String uri;
	private File destination;
	private boolean bare;
	private String branch = Constants.HEAD;

	/**
	 * Set the <code>uri</code>.
	 *
	 * @param uri
	 *            the uri to clone from
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * The optional directory associated with the clone operation. If the
	 * directory isn't set, a name associated with the source uri will be used.
	 *
	 * @see URIish#getHumanishName()
	 * @param destination
	 *            the directory to clone to
	 */
	public void setDest(File destination) {
		this.destination = destination;
	}

	/**
	 * Set <code>bare</code>
	 *
	 * @param bare
	 *            whether the cloned repository is bare or not
	 */
	public void setBare(boolean bare) {
		this.bare = bare;
	}

	/**
	 * Set the <code>branch</code>
	 *
	 * @param branch
	 *            the initial branch to check out when cloning the repository
	 */
	public void setBranch(String branch) {
		this.branch = branch;
	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws BuildException {
		log("Cloning repository " + uri);

		CloneCommand clone = Git.cloneRepository();
		try {
			clone.setURI(uri).setDirectory(destination).setBranch(branch).setBare(bare);
			clone.call().getRepository().close();
		} catch (GitAPIException | JGitInternalException e) {
			log("Could not clone repository: " + e, e, Project.MSG_ERR);
			throw new BuildException("Could not clone repository: " + e.getMessage(), e);
		}
	}
}
