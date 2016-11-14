/*
 * Copyright (C) 2011, Ketan Padegaonkar <KetanPadegaonkar@gmail.com>
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
package org.eclipse.jgit.ant.tasks;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DirSet;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.Union;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;

/**
 * Adds a file to the git index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-add.html"
 *      >git-add(1)</a>
 */
public class GitAddTask extends Task {

	private File src;
	private Union path;

	/**
	 * @param src
	 *            the src to set
	 */
	public void setSrc(File src) {
		this.src = src;
	}

	/**
	 * Add a set of files to add.
	 *
	 * @param set
	 *            a set of files to add.
	 */
	public void addFileset(FileSet set) {
		getPath().add(set);
	}

	/**
	 * Add a set of files to add.
	 *
	 * @param set
	 *            a set of files to add.
	 */
	public void addDirset(DirSet set) {
		getPath().add(set);
	}

	private synchronized Union getPath() {
		if (path == null) {
			path = new Union();
			path.setProject(getProject());
		}
		return path;
	}

	@Override
	public void execute() throws BuildException {
		if (src == null) {
			throw new BuildException("Repository path not specified.");
		}
		if (!RepositoryCache.FileKey.isGitRepository(new File(src, ".git"),
				FS.DETECTED)) {
			throw new BuildException("Specified path (" + src
					+ ") is not a git repository.");
		}

		AddCommand gitAdd;
		try (Repository repo = new FileRepositoryBuilder().readEnvironment()
				.findGitDir(src).build();
			Git git = new Git(repo);) {
			gitAdd = git.add();
		} catch (IOException e) {
			throw new BuildException("Could not access repository " + src, e);
		}

		try {
			String prefix = src.getCanonicalPath();
			String[] allFiles = getPath().list();

			for (String file : allFiles) {
				String toAdd = translateFilePathUsingPrefix(file, prefix);
				log("Adding " + toAdd, Project.MSG_VERBOSE);
				gitAdd.addFilepattern(toAdd);
			}
			gitAdd.call();
		} catch (IOException | GitAPIException e) {
			throw new BuildException("Could not add files to index." + src, e);
		}

	}

	private String translateFilePathUsingPrefix(String file, String prefix)
			throws IOException {
		if (file.equals(prefix)) {
			return ".";
		}
		return new File(file).getCanonicalPath().substring(prefix.length() + 1);
	}

}
