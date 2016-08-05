/*
 * Copyright (C) 2016, Justin Georgeson <jgeorgeson@lopht.net>
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
package org.eclipse.jgit.ant.tasks;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.StringUtils;

import java.io.File;

/**
 * Report general information about the state of a specified path.
 */
public class GitInfoTask extends Task {
	private File path;
	private String prefix;
	private boolean failonmissing = true;

	/**
	 * Set the to path for which to report status.
	 *
	 * @param path
	 *            the path for which status will be reported.
	 */
	public void setPath(File path) {
		this.path = path;
	}

	/**
	 * Set the prefix for properties set by task.
	 *
	 * @param prefix
	 *            the prefix for properties set.
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * @param failonmissing
	 *            the prefix for properties set.
	 */
	public void setFailonmissing(boolean failonmissing) {
		this.failonmissing = failonmissing;
	}

	@Override
	public void execute() throws BuildException {
		if (!path.exists()) {
			if (failonmissing) {
				throw new BuildException("Specified path does not exist: " + path);
			} else {
				log("Specified path does not exist, ignoring per configuration: " + path,Project.MSG_WARN);
				return;
			}
		}
		if (StringUtils.isEmptyOrNull(prefix)) {
			setPrefix("git");
		}
		try {
			log("Opening path as Git repo: " + path,Project.MSG_INFO);
			Repository repo = new FileRepositoryBuilder().readEnvironment()
				.findGitDir(path).build();
			Git git = new Git(repo);
			Project p = this.getProject();
			p.setProperty(prefix + ".branch",repo.getBranch());
			p.setProperty(prefix + ".commitId",repo.resolve(repo.getFullBranch()).getName());
			p.setProperty(prefix + ".state",repo.getRepositoryState().toString());
			log("Running 'git status " + path + "'",Project.MSG_INFO);
			StatusCommand statusCmd = git.status();
			Status status = statusCmd.call();
			p.setProperty(prefix + ".isClean",String.valueOf(status.isClean()));
		} catch (Exception e) {
			throw new BuildException("Could not open path as Git repo.", e);
		}
	}
}
