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
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Checkout a branch or paths to the working tree.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
 *      >git-checkout(1)</a>
 */
public class GitCheckoutTask extends Task {

	private File src;
	private String branch;
	private boolean createBranch;
	private boolean force;

	/**
	 * Set the <code>src</code>
	 *
	 * @param src
	 *            the src to set
	 */
	public void setSrc(File src) {
		this.src = src;
	}

	/**
	 * Set <code>branch</code>
	 *
	 * @param branch
	 *            the initial branch to check out
	 */
	public void setBranch(String branch) {
		this.branch = branch;
	}

	/**
	 * Set if branch should be created if not yet existing
	 *
	 * @param createBranch
	 *            whether the branch should be created if it does not already
	 *            exist
	 */
	public void setCreateBranch(boolean createBranch) {
		this.createBranch = createBranch;
	}

	/**
	 * Set <code>force</code>
	 *
	 * @param force
	 *            if <code>true</code> and the branch with the given name
	 *            already exists, the start-point of an existing branch will be
	 *            set to a new start-point; if false, the existing branch will
	 *            not be changed
	 */
	public void setForce(boolean force) {
		this.force = force;
	}

	/** {@inheritDoc} */
	@Override
	public void execute() throws BuildException {
		CheckoutCommand checkout;
		try (Repository repo = new FileRepositoryBuilder().readEnvironment()
				.findGitDir(src).build();
			Git git = new Git(repo)) {
			checkout = git.checkout();
		} catch (IOException e) {
			throw new BuildException("Could not access repository " + src, e);
		}

		try {
			checkout.setCreateBranch(createBranch).setForceRefUpdate(force)
					.setName(branch);
			checkout.call();
		} catch (Exception e) {
			throw new BuildException("Could not checkout repository " + src, e);
		}
	}

}
