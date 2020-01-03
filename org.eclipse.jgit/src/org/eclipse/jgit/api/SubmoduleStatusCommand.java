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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule status command.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleStatusCommand extends
		GitCommand<Map<String, SubmoduleStatus>> {

	private final Collection<String> paths;

	/**
	 * Constructor for SubmoduleStatusCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public SubmoduleStatusCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<>();
	}

	/**
	 * Add repository-relative submodule path to limit status reporting to
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleStatusCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, SubmoduleStatus> call() throws GitAPIException {
		checkCallable();

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repo)) {
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			Map<String, SubmoduleStatus> statuses = new HashMap<>();
			while (generator.next()) {
				SubmoduleStatus status = getStatus(generator);
				statuses.put(status.getPath(), status);
			}
			return statuses;
		} catch (IOException | ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private SubmoduleStatus getStatus(SubmoduleWalk generator)
			throws IOException, ConfigInvalidException {
		ObjectId id = generator.getObjectId();
		String path = generator.getPath();

		// Report missing if no path in .gitmodules file
		if (generator.getModulesPath() == null)
			return new SubmoduleStatus(SubmoduleStatusType.MISSING, path, id);

		// Report uninitialized if no URL in config file
		if (generator.getConfigUrl() == null)
			return new SubmoduleStatus(SubmoduleStatusType.UNINITIALIZED, path,
					id);

		// Report uninitialized if no submodule repository
		ObjectId headId = null;
		try (Repository subRepo = generator.getRepository()) {
			if (subRepo == null) {
				return new SubmoduleStatus(SubmoduleStatusType.UNINITIALIZED,
						path, id);
			}

			headId = subRepo.resolve(Constants.HEAD);
		}

		// Report uninitialized if no HEAD commit in submodule repository
		if (headId == null)
			return new SubmoduleStatus(SubmoduleStatusType.UNINITIALIZED, path,
					id, headId);

		// Report checked out if HEAD commit is different than index commit
		if (!headId.equals(id))
			return new SubmoduleStatus(SubmoduleStatusType.REV_CHECKED_OUT,
					path, id, headId);

		// Report initialized if HEAD commit is the same as the index commit
		return new SubmoduleStatus(SubmoduleStatusType.INITIALIZED, path, id,
				headId);
	}
}
