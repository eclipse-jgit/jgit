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
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * A class used to execute a submodule add command.
 *
 * This will clone the configured submodule, register the submodule in the
 * .gitmodules file and the repository config file, and also add the submodule
 * and .gitmodules file to the index.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleAddCommand extends
		TransportCommand<SubmoduleAddCommand, Repository> {

	private String path;

	private String uri;

	private ProgressMonitor monitor;

	/**
	 * Constructor for SubmoduleAddCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public SubmoduleAddCommand(final Repository repo) {
		super(repo);
	}

	/**
	 * Set repository-relative path of submodule
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleAddCommand setPath(final String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set URI to clone submodule from
	 *
	 * @param uri
	 *            a {@link java.lang.String} object.
	 * @return this command
	 */
	public SubmoduleAddCommand setURI(final String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor} object.
	 * @return this command
	 */
	public SubmoduleAddCommand setProgressMonitor(final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Is the configured already a submodule in the index?
	 *
	 * @return true if submodule exists in index, false otherwise
	 * @throws java.io.IOException
	 */
	protected boolean submoduleExists() throws IOException {
		TreeFilter filter = PathFilter.create(path);
		try (SubmoduleWalk w = SubmoduleWalk.forIndex(repo)) {
			return w.setFilter(filter).next();
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code SubmoduleAddCommand}
	 *
	 * The {@code Repository} instance returned by this command needs to be
	 * closed by the caller to free resources held by the {@code Repository}
	 * instance. It is recommended to call this method as soon as you don't need
	 * a reference to this {@code Repository} instance anymore.
	 */
	@Override
	public Repository call() throws GitAPIException {
		checkCallable();
		if (path == null || path.length() == 0)
			throw new IllegalArgumentException(JGitText.get().pathNotConfigured);
		if (uri == null || uri.length() == 0)
			throw new IllegalArgumentException(JGitText.get().uriNotConfigured);

		try {
			SubmoduleValidator.assertValidSubmoduleName(path);
			SubmoduleValidator.assertValidSubmodulePath(path);
			SubmoduleValidator.assertValidSubmoduleUri(uri);
		} catch (SubmoduleValidator.SubmoduleValidationException e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		try {
			if (submoduleExists())
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().submoduleExists, path));
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		final String resolvedUri;
		try {
			resolvedUri = SubmoduleWalk.getSubmoduleRemoteUrl(repo, uri);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		// Clone submodule repository
		File moduleDirectory = SubmoduleWalk.getSubmoduleDirectory(repo, path);
		CloneCommand clone = Git.cloneRepository();
		configure(clone);
		clone.setDirectory(moduleDirectory);
		clone.setGitDir(new File(new File(repo.getDirectory(),
				Constants.MODULES), path));
		clone.setURI(resolvedUri);
		if (monitor != null)
			clone.setProgressMonitor(monitor);
		Repository subRepo = null;
		try (Git git = clone.call()) {
			subRepo = git.getRepository();
			subRepo.incrementOpen();
		}

		// Save submodule URL to parent repository's config
		StoredConfig config = repo.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, resolvedUri);
		try {
			config.save();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		// Save path and URL to parent repository's .gitmodules file
		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		try {
			modulesConfig.load();
			modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
					path, ConfigConstants.CONFIG_KEY_PATH, path);
			modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
					path, ConfigConstants.CONFIG_KEY_URL, uri);
			modulesConfig.save();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		AddCommand add = new AddCommand(repo);
		// Add .gitmodules file to parent repository's index
		add.addFilepattern(Constants.DOT_GIT_MODULES);
		// Add submodule directory to parent repository's index
		add.addFilepattern(path);
		try {
			add.call();
		} catch (NoFilepatternException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		return subRepo;
	}
}
