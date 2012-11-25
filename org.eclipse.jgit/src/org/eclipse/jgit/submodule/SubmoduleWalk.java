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

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
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
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;

/**
 * Walker that visits all submodule entries found in a tree
 */
public class SubmoduleWalk {

	/**
	 * Create a generator to walk over the submodule entries currently in the
	 * index
	 *
	 * The {@code .gitmodules} file is read from the index.
	 *
	 * @param repository
	 * @return generator over submodule index entries
	 * @throws IOException
	 */
	public static SubmoduleWalk forIndex(Repository repository)
			throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		try {
			DirCache index = repository.readDirCache();
			generator.setTree(new DirCacheIterator(index));
		} catch (IOException e) {
			generator.release();
			throw e;
		}
		return generator;
	}

	/**
	 * Create a generator and advance it to the submodule entry at the given
	 * path
	 *
	 * @param repository
	 * @param treeId
	 *            the root of a tree containing both a submodule at the given path
	 *            and .gitmodules at the root.
	 * @param path
	 * @return generator at given path, null if no submodule at given path
	 * @throws IOException
	 */
	public static SubmoduleWalk forPath(Repository repository,
			AnyObjectId treeId, String path) throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		try {
			generator.setTree(treeId);
			PathFilter filter = PathFilter.create(path);
			generator.setFilter(filter);
			generator.setRootTree(treeId);
			while (generator.next())
				if (filter.isDone(generator.walk))
					return generator;
		} catch (IOException e) {
			generator.release();
			throw e;
		}
		generator.release();
		return null;
	}

	/**
	 * Create a generator and advance it to the submodule entry at the given
	 * path
	 *
	 * @param repository
	 * @param iterator
	 *            the root of a tree containing both a submodule at the given path
	 *            and .gitmodules at the root.
	 * @param path
	 * @return generator at given path, null if no submodule at given path
	 * @throws IOException
	 */
	public static SubmoduleWalk forPath(Repository repository,
			AbstractTreeIterator iterator, String path) throws IOException {
		SubmoduleWalk generator = new SubmoduleWalk(repository);
		try {
			generator.setTree(iterator);
			PathFilter filter = PathFilter.create(path);
			generator.setFilter(filter);
			generator.setRootTree(iterator);
			while (generator.next())
				if (filter.isDone(generator.walk))
					return generator;
		} catch (IOException e) {
			generator.release();
			throw e;
		}
		generator.release();
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
		return getSubmoduleRepository(parent.getWorkTree(), path);
	}

	/**
	 * Get submodule repository at path
	 *
	 * @param parent
	 * @param path
	 * @return repository or null if repository doesn't exist
	 * @throws IOException
	 */
	public static Repository getSubmoduleRepository(final File parent,
			final String path) throws IOException {
		File subWorkTree = new File(parent, path);
		if (!subWorkTree.isDirectory())
			return null;
		File workTree = new File(parent, path);
		try {
			return new RepositoryBuilder() //
					.setMustExist(true) //
					.setFS(FS.DETECTED) //
					.setWorkTree(workTree) //
					.build();
		} catch (RepositoryNotFoundException e) {
			return null;
		}
	}

	/**
	 * Resolve submodule repository URL.
	 * <p>
	 * This handles relative URLs that are typically specified in the
	 * '.gitmodules' file by resolving them against the remote URL of the parent
	 * repository.
	 * <p>
	 * Relative URLs will be resolved against the parent repository's working
	 * directory if the parent repository has no configured remote URL.
	 *
	 * @param parent
	 *            parent repository
	 * @param url
	 *            absolute or relative URL of the submodule repository
	 * @return resolved URL
	 * @throws IOException
	 */
	public static String getSubmoduleRemoteUrl(final Repository parent,
			final String url) throws IOException {
		if (!url.startsWith("./") && !url.startsWith("../")) //$NON-NLS-1$ //$NON-NLS-2$
			return url;

		String remoteName = null;
		// Look up remote URL associated wit HEAD ref
		Ref ref = parent.getRef(Constants.HEAD);
		if (ref != null) {
			if (ref.isSymbolic())
				ref = ref.getLeaf();
			remoteName = parent.getConfig().getString(
					ConfigConstants.CONFIG_BRANCH_SECTION,
					Repository.shortenRefName(ref.getName()),
					ConfigConstants.CONFIG_KEY_REMOTE);
		}

		// Fall back to 'origin' if current HEAD ref has no remote URL
		if (remoteName == null)
			remoteName = Constants.DEFAULT_REMOTE_NAME;

		String remoteUrl = parent.getConfig().getString(
				ConfigConstants.CONFIG_REMOTE_SECTION, remoteName,
				ConfigConstants.CONFIG_KEY_URL);

		// Fall back to parent repository's working directory if no remote URL
		if (remoteUrl == null) {
			remoteUrl = parent.getWorkTree().getAbsolutePath();
			// Normalize slashes to '/'
			if ('\\' == File.separatorChar)
				remoteUrl = remoteUrl.replace('\\', '/');
		}

		// Remove trailing '/'
		if (remoteUrl.charAt(remoteUrl.length() - 1) == '/')
			remoteUrl = remoteUrl.substring(0, remoteUrl.length() - 1);

		char separator = '/';
		String submoduleUrl = url;
		while (submoduleUrl.length() > 0) {
			if (submoduleUrl.startsWith("./")) //$NON-NLS-1$
				submoduleUrl = submoduleUrl.substring(2);
			else if (submoduleUrl.startsWith("../")) { //$NON-NLS-1$
				int lastSeparator = remoteUrl.lastIndexOf('/');
				if (lastSeparator < 1) {
					lastSeparator = remoteUrl.lastIndexOf(':');
					separator = ':';
				}
				if (lastSeparator < 1)
					throw new IOException(MessageFormat.format(
							JGitText.get().submoduleParentRemoteUrlInvalid,
							remoteUrl));
				remoteUrl = remoteUrl.substring(0, lastSeparator);
				submoduleUrl = submoduleUrl.substring(3);
			} else
				break;
		}
		return remoteUrl + separator + submoduleUrl;
	}

	private final Repository repository;

	private final TreeWalk walk;

	private StoredConfig repoConfig;

	private AbstractTreeIterator rootTree;

	private Config modulesConfig;

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

	/**
	 * Set the config used by this walk.
	 *
	 * This method need only be called if constructing a walk manually instead of
	 * with one of the static factory methods above.
	 *
	 * @param config
	 *            .gitmodules config object
	 * @return this generator
	 */
	public SubmoduleWalk setModulesConfig(final Config config) {
		modulesConfig = config;
		return this;
	}

	/**
	 * Set the tree used by this walk for finding {@code .gitmodules}.
	 * <p>
	 * The root tree is not read until the first submodule is encountered by the
	 * walk.
	 * <p>
	 * This method need only be called if constructing a walk manually instead of
	 * with one of the static factory methods above.
	 *
	 * @param tree
	 *            tree containing .gitmodules
	 * @return this generator
	 */
	public SubmoduleWalk setRootTree(final AbstractTreeIterator tree) {
		rootTree = tree;
		modulesConfig = null;
		return this;
	}

	/**
	 * Set the tree used by this walk for finding {@code .gitmodules}.
	 * <p>
	 * The root tree is not read until the first submodule is encountered by the
	 * walk.
	 * <p>
	 * This method need only be called if constructing a walk manually instead of
	 * with one of the static factory methods above.
	 *
	 * @param id
	 *            ID of a tree containing .gitmodules
	 * @return this generator
	 * @throws IOException
	 */
	public SubmoduleWalk setRootTree(final AnyObjectId id) throws IOException {
		final CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(walk.getObjectReader(), id);
		rootTree = p;
		modulesConfig = null;
		return this;
	}

	/**
	 * Load the config for this walk from {@code .gitmodules}.
	 * <p>
	 * Uses the root tree if {@link #setRootTree(AbstractTreeIterator)} was
	 * previously called, otherwise uses the working tree.
	 * <p>
	 * If no submodule config is found, loads an empty config.
	 *
	 * @return this generator
	 * @throws IOException if an error occurred, or if the repository is bare
	 * @throws ConfigInvalidException
	 */
	public SubmoduleWalk loadModulesConfig() throws IOException, ConfigInvalidException {
		if (rootTree == null) {
			File modulesFile = new File(repository.getWorkTree(),
					Constants.DOT_GIT_MODULES);
			FileBasedConfig config = new FileBasedConfig(modulesFile,
					repository.getFS());
			config.load();
			modulesConfig = config;
		} else {
			TreeWalk configWalk = new TreeWalk(repository);
			try {
				configWalk.addTree(rootTree);

				// The root tree may be part of the submodule walk, so we need to revert
				// it after this walk.
				int idx;
				for (idx = 0; !rootTree.first(); idx++) {
					rootTree.back(1);
				}

				try {
					configWalk.setRecursive(false);
					PathFilter filter = PathFilter.create(Constants.DOT_GIT_MODULES);
					configWalk.setFilter(filter);
					while (configWalk.next()) {
						if (filter.isDone(configWalk)) {
							modulesConfig = new BlobBasedConfig(null, repository,
									configWalk.getObjectId(0));
							return this;
						}
					}
					modulesConfig = new Config();
				} finally {
					if (idx > 0)
						rootTree.next(idx);
				}
			} finally {
				configWalk.release();
			}
		}
		return this;
	}

	private void lazyLoadModulesConfig() throws IOException, ConfigInvalidException {
		if (modulesConfig == null)
			loadModulesConfig();
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
		lazyLoadModulesConfig();
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
		lazyLoadModulesConfig();
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
		lazyLoadModulesConfig();
		return modulesConfig.getString(
				ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE);
	}

	/**
	 * Get repository for current submodule entry
	 *
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
		if (subRepo == null)
			return null;
		try {
			return subRepo.resolve(Constants.HEAD);
		} finally {
			subRepo.close();
		}
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
		try {
			Ref head = subRepo.getRef(Constants.HEAD);
			return head != null ? head.getLeaf().getName() : null;
		} finally {
			subRepo.close();
		}
	}

	/**
	 * Get the resolved remote URL for the current submodule.
	 * <p>
	 * This method resolves the value of {@link #getModulesUrl()} to an absolute
	 * URL
	 *
	 * @return resolved remote URL
	 * @throws IOException
	 * @throws ConfigInvalidException
	 */
	public String getRemoteUrl() throws IOException, ConfigInvalidException {
		String url = getModulesUrl();
		return url != null ? getSubmoduleRemoteUrl(repository, url) : null;
	}

	/** Release any resources used by this walker's reader. */
	public void release() {
		walk.release();
	}
}
