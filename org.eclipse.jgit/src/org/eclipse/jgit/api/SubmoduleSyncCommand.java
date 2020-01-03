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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule sync command.
 *
 * This will set the remote URL in a submodule's repository to the current value
 * in the .gitmodules file.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleSyncCommand extends GitCommand<Map<String, String>> {

	private final Collection<String> paths;

	/**
	 * Constructor for SubmoduleSyncCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public SubmoduleSyncCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<>();
	}

	/**
	 * Add repository-relative submodule path to synchronize
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleSyncCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	/**
	 * Get branch that HEAD currently points to
	 *
	 * @param subRepo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @return shortened branch name, null on failures
	 * @throws java.io.IOException
	 */
	protected String getHeadBranch(Repository subRepo) throws IOException {
		Ref head = subRepo.exactRef(Constants.HEAD);
		if (head != null && head.isSymbolic()) {
			return Repository.shortenRefName(head.getLeaf().getName());
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, String> call() throws GitAPIException {
		checkCallable();

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repo)) {
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			Map<String, String> synced = new HashMap<>();
			StoredConfig config = repo.getConfig();
			while (generator.next()) {
				String remoteUrl = generator.getRemoteUrl();
				if (remoteUrl == null)
					continue;

				String path = generator.getPath();
				config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						path, ConfigConstants.CONFIG_KEY_URL, remoteUrl);
				synced.put(path, remoteUrl);

				try (Repository subRepo = generator.getRepository()) {
					if (subRepo == null) {
						continue;
					}

					StoredConfig subConfig;
					String branch;

					subConfig = subRepo.getConfig();
					// Get name of remote associated with current branch and
					// fall back to default remote name as last resort
					branch = getHeadBranch(subRepo);
					String remote = null;
					if (branch != null) {
						remote = subConfig.getString(
								ConfigConstants.CONFIG_BRANCH_SECTION, branch,
								ConfigConstants.CONFIG_KEY_REMOTE);
					}
					if (remote == null) {
						remote = Constants.DEFAULT_REMOTE_NAME;
					}

					subConfig.setString(ConfigConstants.CONFIG_REMOTE_SECTION,
							remote, ConfigConstants.CONFIG_KEY_URL, remoteUrl);
					subConfig.save();
				}
			}
			if (!synced.isEmpty())
				config.save();
			return synced;
		} catch (IOException | ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
