/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.submodule;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Generator that visits all submodule entries found in the index tree
 */
public class SubmoduleGenerator {

	private final Repository repository;

	private final TreeWalk walk;

	private FileBasedConfig config;

	private String path;

	private ObjectId id;

	/**
	 * Create submodule generator
	 *
	 * @param repository
	 * @throws IOException
	 */
	public SubmoduleGenerator(Repository repository) throws IOException {
		this.repository = repository;

		walk = new TreeWalk(repository);
		walk.setRecursive(true);
		walk.addTree(new DirCacheIterator(repository.readDirCache()));
	}

	private void loadConfig() throws IOException, ConfigInvalidException {
		if (config == null) {
			File modules = new File(repository.getWorkTree(),
					Constants.DOT_GIT_MODULES);
			FileBasedConfig modulesConfig = new FileBasedConfig(modules,
					repository.getFS());
			modulesConfig.load();
			config = modulesConfig;
		}
	}

	/**
	 * Get submodule Git directory file handle
	 *
	 * @return submodule Git directory
	 */
	private File getSubmoduleGitDir() {
		String normalized;
		if (File.separatorChar != '\\')
			normalized = path;
		else
			normalized = path.replace('/', '\\');
		String repoPath = normalized + File.separatorChar + Constants.DOT_GIT;
		return new File(repository.getWorkTree(), repoPath);
	}

	/**
	 * Advance to next submodule in the index tree.
	 *
	 * The object id and path of the next entry can be obtained by calling
	 * {@link #getObjectId()} and {@link #getPath()}.
	 *
	 * @return true if entry found, false otherwise
	 * @throws IOException
	 */
	public boolean next() throws IOException {
		while (walk.next()) {
			if (FileMode.GITLINK != walk.getFileMode(0))
				continue;

			path = walk.getPathString();
			id = walk.getObjectId(0);
			return true;
		}
		path = null;
		id = null;
		return false;
	}

	/**
	 * Get path of current submodule entry
	 *
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Get object id of current submodule entry
	 *
	 * @return object id
	 */
	public ObjectId getObjectId() {
		return id;
	}

	/**
	 * Get configured path for current entry. This will be the value from the
	 * .gitmodules file in the current repository's working tree.
	 *
	 * @return configured path
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getConfigPath() throws IOException, ConfigInvalidException {
		if (path == null)
			return null;

		loadConfig();
		return config.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH);
	}

	/**
	 * Get configured remote URL for current entry. This will be the value from
	 * the .gitmodules file in the current repository's working tree.
	 *
	 * @return configured URL
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getConfigUrl() throws IOException, ConfigInvalidException {
		if (path == null)
			return null;

		loadConfig();
		return config.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL);
	}

	/**
	 * Does the current submodule entry have a .git directory in the working
	 * tree?
	 *
	 * @return true if .git directory exists, false otherwise
	 */
	public boolean hasGitDirectory() {
		return path != null ? getSubmoduleGitDir().isDirectory() : false;
	}

	/**
	 * Get repository for current submodule entry
	 *
	 * @see #hasGitDirectory()
	 * @return repository or null if not found
	 * @throws IOException
	 */
	public Repository getRepository() throws IOException {
		if (path == null)
			return null;

		File directory = getSubmoduleGitDir();
		if (!directory.isDirectory())
			return null;
		try {
			return Git.open(directory).getRepository();
		} catch (RepositoryNotFoundException e) {
			return null;
		}
	}
}
