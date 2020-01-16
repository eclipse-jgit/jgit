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
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;

/**
 * Create an empty git repository.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-init.html"
 *      >git-init(1)</a>
 */
public class GitInitTask extends Task {
	private File destination;
	private boolean bare;

	/**
	 * Set the destination git repository.
	 *
	 * @param dest
	 *            the destination directory that should be initialized with the
	 *            git repository.
	 */
	public void setDest(File dest) {
		this.destination = dest;
	}

	/**
	 * Configure if the repository should be <code>bare</code>
	 *
	 * @param bare
	 *            whether the repository should be initialized to a bare
	 *            repository or not.
	 */
	public void setBare(boolean bare) {
		this.bare = bare;
	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws BuildException {
		if (bare) {
			log("Initializing bare repository at " + destination);
		} else {
			log("Initializing repository at " + destination);
		}
		try {
			InitCommand init = Git.init();
			init.setBare(bare).setDirectory(destination);
			init.call();
		} catch (Exception e) {
			throw new BuildException("Could not initialize repository", e);
		}
	}
}
