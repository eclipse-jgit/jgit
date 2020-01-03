/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
