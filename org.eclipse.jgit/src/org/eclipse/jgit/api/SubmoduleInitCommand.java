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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule init command.
 *
 * This will copy the 'url' and 'update' fields from the working tree
 * .gitmodules file to a repository's config file for each submodule not
 * currently present in the repository's config file.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleInitCommand extends GitCommand<Collection<String>> {

	private final Collection<String> paths;

	/**
	 * Constructor for SubmoduleInitCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public SubmoduleInitCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<>();
	}

	/**
	 * Add repository-relative submodule path to initialize
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleInitCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<String> call() throws GitAPIException {
		checkCallable();

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repo)) {
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			StoredConfig config = repo.getConfig();
			List<String> initialized = new ArrayList<>();
			while (generator.next()) {
				// Ignore entry if URL is already present in config file
				if (generator.getConfigUrl() != null)
					continue;

				String path = generator.getPath();
				String name = generator.getModuleName();
				// Copy 'url' and 'update' fields from .gitmodules to config
				// file
				String url = generator.getRemoteUrl();
				String update = generator.getModulesUpdate();
				if (url != null)
					config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
							name, ConfigConstants.CONFIG_KEY_URL, url);
				if (update != null)
					config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
							name, ConfigConstants.CONFIG_KEY_UPDATE, update);
				if (url != null || update != null)
					initialized.add(path);
			}
			// Save repository config if any values were updated
			if (!initialized.isEmpty())
				config.save();
			return initialized;
		} catch (IOException | ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
