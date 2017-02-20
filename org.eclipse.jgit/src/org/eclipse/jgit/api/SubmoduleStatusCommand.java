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
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleStatusCommand extends
		GitCommand<Map<String, SubmoduleStatus>> {

	private final Collection<String> paths;

	/**
	 * @param repo
	 */
	public SubmoduleStatusCommand(final Repository repo) {
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
	public SubmoduleStatusCommand addPath(final String path) {
		paths.add(path);
		return this;
	}

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
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
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
		Repository subRepo = generator.getRepository();
		if (subRepo == null)
			return new SubmoduleStatus(SubmoduleStatusType.UNINITIALIZED, path,
					id);

		ObjectId headId;
		try {
			headId = subRepo.resolve(Constants.HEAD);
		} finally {
			subRepo.close();
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
