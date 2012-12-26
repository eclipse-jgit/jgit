/*
 * Copyright (C) 2011, Chris Aniszczyk <zx@redhat.com>
 * Copyright (C) 2011, Abhishek Bhatnagar <abhatnag@redhat.com>
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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/**
 * Remove untracked files from the working tree
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-clean.html"
 *      >Git documentation about Clean</a>
 */
public class CleanCommand extends GitCommand<Set<String>> {

	private Set<String> paths = Collections.emptySet();

	private boolean dryRun;

	private boolean directories;

	private boolean ignore = true;

	/**
	 * @param repo
	 */
	protected CleanCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code clean} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @return a set of strings representing each file cleaned.
	 * @throws GitAPIException
	 * @throws NoWorkTreeException
	 */
	public Set<String> call() throws NoWorkTreeException, GitAPIException {
		Set<String> files = new TreeSet<String>();
		try {
			StatusCommand command = new StatusCommand(repo);
			Status status = command.call();

			Set<String> untrackedAndIgnoredFiles = new TreeSet<String>(
					status.getUntracked());
			Set<String> untrackedAndIgnoredDirs = new TreeSet<String>(
					status.getUntrackedFolders());

			FS fs = getRepository().getFS();
			for (String p : status.getIgnoredNotInIndex()) {
				File f = new File(repo.getWorkTree(), p);
				if (fs.isFile(f) || fs.isSymLink(f)) {
					untrackedAndIgnoredFiles.add(p);
				} else if (fs.isDirectory(f)) {
					untrackedAndIgnoredDirs.add(p);
				}
			}

			Set<String> filtered = filterFolders(untrackedAndIgnoredFiles,
					untrackedAndIgnoredDirs);

			Set<String> notIgnoredFiles = filterIgnorePaths(filtered,
					status.getIgnoredNotInIndex(), true);
			Set<String> notIgnoredDirs = filterIgnorePaths(
					untrackedAndIgnoredDirs,
					status.getIgnoredNotInIndex(), false);

			for (String file : notIgnoredFiles)
				if (paths.isEmpty() || paths.contains(file)) {
					if (!dryRun)
						FileUtils.delete(new File(repo.getWorkTree(), file));
					files.add(file);
				}

			if (directories)
				for (String dir : notIgnoredDirs)
					if (paths.isEmpty() || paths.contains(dir)) {
						if (!dryRun)
							FileUtils.delete(new File(repo.getWorkTree(), dir),
									FileUtils.RECURSIVE);
						files.add(dir + "/");
					}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return files;
	}

	private Set<String> filterIgnorePaths(Set<String> inputPaths,
			Set<String> ignoredNotInIndex, boolean exact) {
		if (ignore) {
			Set<String> filtered = new TreeSet<String>(inputPaths);
			for (String path : inputPaths)
				for (String ignored : ignoredNotInIndex)
					if ((exact && path.equals(ignored))
							|| (!exact && path.startsWith(ignored))) {
						filtered.remove(path);
						break;
					}

			return filtered;
		}
		return inputPaths;
	}

	private Set<String> filterFolders(Set<String> untracked,
			Set<String> untrackedFolders) {
		Set<String> filtered = new TreeSet<String>(untracked);
		for (String file : untracked)
			for (String folder : untrackedFolders)
				if (file.startsWith(folder)) {
					filtered.remove(file);
					break;
				}


		return filtered;
	}

	/**
	 * If paths are set, only these paths are affected by the cleaning.
	 *
	 * @param paths
	 *            the paths to set
	 * @return {@code this}
	 */
	public CleanCommand setPaths(Set<String> paths) {
		this.paths = paths;
		return this;
	}

	/**
	 * If dryRun is set, the paths in question will not actually be deleted.
	 *
	 * @param dryRun
	 *            whether to do a dry run or not
	 * @return {@code this}
	 */
	public CleanCommand setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
		return this;
	}

	/**
	 * If dirs is set, in addition to files, also clean directories.
	 *
	 * @param dirs
	 *            whether to clean directories too, or only files.
	 * @return {@code this}
	 */
	public CleanCommand setCleanDirectories(boolean dirs) {
		directories = dirs;
		return this;
	}

	/**
	 * If ignore is set, don't report/clean files/directories that are ignored
	 * by a .gitignore. otherwise do handle them.
	 *
	 * @param ignore
	 *            whether to respect .gitignore or not.
	 * @return {@code this}
	 */
	public CleanCommand setIgnore(boolean ignore) {
		this.ignore = ignore;
		return this;
	}
}
