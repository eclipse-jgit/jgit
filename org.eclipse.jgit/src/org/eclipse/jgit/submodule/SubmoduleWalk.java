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
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Walker that visits all submodule entries found in a tree
 */
public class SubmoduleWalk {

	/**
	 * Create a generator to walk over the submodule entries currently in the
	 * index
	 *
	 * @param repository
	 * @return generator over submodule index entries
	 * @throws IOException
	 */
	public static SubmoduleWalk forIndex(Repository repository)
			throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		generator.setTree(new DirCacheIterator(repository.readDirCache()));
		return generator;
	}

	/**
	 * Create a generator and advance it to the submodule entry at the given
	 * path
	 *
	 * @param repository
	 * @param treeId
	 * @param path
	 * @return generator at given path, null if no submodule at given path
	 * @throws IOException
	 */
	public static SubmoduleWalk forPath(Repository repository,
			AnyObjectId treeId, String path) throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		generator.setTree(treeId);
		PathFilter filter = PathFilter.create(path);
		generator.setFilter(filter);
		while (generator.next())
			if (filter.isDone(generator.walk))
				return generator;
		return null;
	}

	/**
	 * Create a generator and advance it to the submodule entry at the given
	 * path
	 *
	 * @param repository
	 * @param iterator
	 * @param path
	 * @return generator at given path, null if no submodule at given path
	 * @throws IOException
	 */
	public static SubmoduleWalk forPath(Repository repository,
			AbstractTreeIterator iterator, String path) throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		generator.setTree(iterator);
		PathFilter filter = PathFilter.create(path);
		generator.setFilter(filter);
		while (generator.next())
			if (filter.isDone(generator.walk))
				return generator;
		return null;
	}

	/**
	 * Get submodule directory
	 *
	 * @param parent
	 * @param path
	 * @return directory
	 */
	public static File getSubmoduleDirectory(final Repository parent,
			final String path) {
		return new File(parent.getWorkTree(), path);
	}

	/**
	 * Get submodule repository
	 *
	 * @param parent
	 * @param path
	 * @return repository or null if repository doesn't exist
	 * @throws IOException
	 */
	public static Repository getSubmoduleRepository(final Repository parent,
			final String path) throws IOException {
		File gitDir = getSubmoduleGitDirectory(parent, path);
		if (!gitDir.isDirectory())
			return null;
		File workTree = getSubmoduleDirectory(parent, path);
		try {
			return new RepositoryBuilder().setMustExist(true)
					.setFS(FS.DETECTED).setGitDir(gitDir).setWorkTree(workTree)
					.build();
		} catch (RepositoryNotFoundException e) {
			return null;
		}
	}

	/**
	 * Get the .git directory for a repository submodule path
	 *
	 * @param parent
	 * @param path
	 * @return .git for submodule repository
	 * @throws IOException
	 *             if locating the directory failed
	 */
	public static File getSubmoduleGitDirectory(final Repository parent,
			final String path) throws IOException {
		File gitDir = new File(getSubmoduleDirectory(parent, path),
				Constants.DOT_GIT);
		if (gitDir.isFile()) {
			byte[] content = IO.readFully(gitDir);
			if (isSymRef(content)) {
				int pathStart = 8;
				int lineEnd = RawParseUtils.nextLF(content, pathStart);
				if (content[lineEnd - 1] == '\n')
					lineEnd--;
				if (lineEnd == pathStart)
					throw new IOException(MessageFormat.format(
							JGitText.get().submoduleInvalidGitdirRef, path));
				gitDir = new File(RawParseUtils.decode(content, pathStart,
						lineEnd));
			} else
				throw new IOException(MessageFormat.format(
						JGitText.get().submoduleInvalidGitdirRef, path));
		}
		return gitDir;
	}

	private static boolean isSymRef(byte[] ref) {
		if (ref.length < 9)
			return false;
		return /**/ref[0] == 'g' //
				&& ref[1] == 'i' //
				&& ref[2] == 't' //
				&& ref[3] == 'd' //
				&& ref[4] == 'i' //
				&& ref[5] == 'r' //
				&& ref[6] == ':' //
				&& ref[7] == ' ';
	}

	private final Repository repository;

	private final TreeWalk walk;

	private StoredConfig repoConfig;

	private FileBasedConfig modulesConfig;

	private String path;

	/**
	 * Create submodule generator
	 *
	 * @param repository
	 * @throws IOException
	 */
	public SubmoduleWalk(final Repository repository) throws IOException {
		this.repository = repository;
		repoConfig = repository.getConfig();
		walk = new TreeWalk(repository);
		walk.setRecursive(true);
	}

	private void loadModulesConfig() throws IOException, ConfigInvalidException {
		if (modulesConfig == null) {
			File modulesFile = new File(repository.getWorkTree(),
					Constants.DOT_GIT_MODULES);
			FileBasedConfig config = new FileBasedConfig(modulesFile,
					repository.getFS());
			config.load();
			modulesConfig = config;
		}
	}

	/**
	 * Set tree filter
	 *
	 * @param filter
	 * @return this generator
	 */
	public SubmoduleWalk setFilter(TreeFilter filter) {
		walk.setFilter(filter);
		return this;
	}

	/**
	 * Set the tree iterator used for finding submodule entries
	 *
	 * @param iterator
	 * @return this generator
	 * @throws CorruptObjectException
	 */
	public SubmoduleWalk setTree(final AbstractTreeIterator iterator)
			throws CorruptObjectException {
		walk.addTree(iterator);
		return this;
	}

	/**
	 * Set the tree used for finding submodule entries
	 *
	 * @param treeId
	 * @return this generator
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	public SubmoduleWalk setTree(final AnyObjectId treeId) throws IOException {
		walk.addTree(treeId);
		return this;
	}

	/**
	 * Reset generator and start new submodule walk
	 *
	 * @return this generator
	 */
	public SubmoduleWalk reset() {
		repoConfig = repository.getConfig();
		modulesConfig = null;
		walk.reset();
		return this;
	}

	/**
	 * Get directory that will be the root of the submodule's local repository
	 *
	 * @return submodule repository directory
	 */
	public File getDirectory() {
		return getSubmoduleDirectory(repository, path);
	}

	/**
	 * Get the .git directory for the current submodule entry
	 *
	 * @return .git for submodule repository
	 * @throws IOException
	 *             if locating the directory failed
	 */
	public File getGitDirectory() throws IOException {
		return getSubmoduleGitDirectory(repository, path);
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
			return true;
		}
		path = null;
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
		return walk.getObjectId(0);
	}

	/**
	 * Get the configured path for current entry. This will be the value from
	 * the .gitmodules file in the current repository's working tree.
	 *
	 * @return configured path
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getModulesPath() throws IOException, ConfigInvalidException {
		loadModulesConfig();
		return modulesConfig.getString(
				ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH);
	}

	/**
	 * Get the configured remote URL for current entry. This will be the value
	 * from the repository's config.
	 *
	 * @return configured URL
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getConfigUrl() throws IOException, ConfigInvalidException {
		return repoConfig.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
				path, ConfigConstants.CONFIG_KEY_URL);
	}

	/**
	 * Get the configured remote URL for current entry. This will be the value
	 * from the .gitmodules file in the current repository's working tree.
	 *
	 * @return configured URL
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getModulesUrl() throws IOException, ConfigInvalidException {
		loadModulesConfig();
		return modulesConfig.getString(
				ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL);
	}

	/**
	 * Get the configured update field for current entry. This will be the value
	 * from the repository's config.
	 *
	 * @return update value
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getConfigUpdate() throws IOException, ConfigInvalidException {
		return repoConfig.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
				path, ConfigConstants.CONFIG_KEY_UPDATE);
	}

	/**
	 * Get the configured update field for current entry. This will be the value
	 * from the .gitmodules file in the current repository's working tree.
	 *
	 * @return update value
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public String getModulesUpdate() throws IOException, ConfigInvalidException {
		loadModulesConfig();
		return modulesConfig.getString(
				ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE);
	}

	/**
	 * Does the current submodule entry have a .git directory?
	 *
	 * @return true if .git directory exists, false otherwise
	 */
	public boolean hasGitDirectory() {
		try {
			return getGitDirectory().isDirectory();
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Get repository for current submodule entry
	 *
	 * @see #hasGitDirectory()
	 * @return repository or null if non-existent
	 * @throws IOException
	 */
	public Repository getRepository() throws IOException {
		return getSubmoduleRepository(repository, path);
	}

	/**
	 * Get commit id that HEAD points to in the current submodule's repository
	 *
	 * @return object id of HEAD reference
	 * @throws IOException
	 */
	public ObjectId getHead() throws IOException {
		Repository subRepo = getRepository();
		return subRepo != null ? subRepo.resolve(Constants.HEAD) : null;
	}

	/**
	 * Get ref that HEAD points to in the current submodule's repository
	 *
	 * @return ref name, null on failures
	 * @throws IOException
	 */
	public String getHeadRef() throws IOException {
		Repository subRepo = getRepository();
		if (subRepo == null)
			return null;
		Ref head = subRepo.getRef(Constants.HEAD);
		return head != null ? head.getLeaf().getName() : null;
	}
}
