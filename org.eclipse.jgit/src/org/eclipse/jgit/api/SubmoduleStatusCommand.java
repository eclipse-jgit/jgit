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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.SubmoduleStatusCommand.SubmoduleStatus;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A class used to execute a submodule status command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about sobmodule</a>
 */
public class SubmoduleStatusCommand extends
		GitCommand<Map<String, SubmoduleStatus>> {

	private static final String CONFIG_SECTION_SUBMODULE = "submodule"; //$NON-NLS-1$

	private static final String CONFIG_KEY_PATH = "path"; //$NON-NLS-N$

	/**
	 * Status type for a submodule
	 */
	public static enum StatusType {

		/** Submodule's configuration is missing */
		MISSING,

		/** Submodule's Git repository is not initialized */
		NOT_INITIALIZED,

		/** Submodule's Git repository is initialized */
		INITIALIZED,

		/**
		 * Submodule commit checked out is different than the commit referenced
		 * in the index tree
		 */
		REV_CHECKED_OUT,
	}

	/**
	 * Status class containing the type, path, and commit id of the submodule.
	 */
	public static class SubmoduleStatus {

		private final StatusType type;

		private final String path;

		private final ObjectId id;

		/**
		 * Create submodule status
		 *
		 * @param type
		 * @param path
		 * @param id
		 */
		protected SubmoduleStatus(StatusType type, String path, ObjectId id) {
			this.type = type;
			this.path = path;
			this.id = id;
		}

		/**
		 * @return type
		 */
		public StatusType getType() {
			return type;
		}

		/**
		 * @return path
		 */
		public String getPath() {
			return path;
		}

		/**
		 * @return id
		 */
		public ObjectId getId() {
			return id;
		}

		public String toString() {
			StringBuilder buffer = new StringBuilder();
			if (type == StatusType.NOT_INITIALIZED)
				buffer.append('-');
			else if (type == StatusType.REV_CHECKED_OUT)
				buffer.append('+');
			else
				buffer.append(' ');
			buffer.append(id.name());
			buffer.append(' ');
			buffer.append(path);
			if (type == StatusType.MISSING)
				buffer.append(" (missing)");
			return buffer.toString();
		}
	}

	/**
	 * @param repo
	 */
	public SubmoduleStatusCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Resolve HEAD commit from submodule's repository
	 *
	 * @param directory
	 * @return HEAD commit, null if repository does not exist
	 * @throws IOException
	 */
	protected ObjectId resolveSubmoduleHead(File directory) throws IOException {
		try {
			return Git.open(directory).getRepository().resolve(Constants.HEAD);
		} catch (RepositoryNotFoundException e) {
			return null;
		}
	}

	/**
	 * Get submodule Git directory file handle
	 *
	 * @param configPath
	 * @return submodule Git dir
	 */
	protected File getSubmoduleGitDir(String configPath) {
		String repoPath = configPath + "/" + Constants.DOT_GIT;
		if (File.separatorChar == '\\')
			repoPath = repoPath.replace('/', '\\');
		return new File(repo.getWorkTree(), repoPath);
	}

	/**
	 *
	 */
	public Map<String, SubmoduleStatus> call() throws JGitInternalException {
		checkCallable();

		File modules = new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES);
		FileBasedConfig config = new FileBasedConfig(modules, repo.getFS());

		try {
			config.load();

			TreeWalk walk = new TreeWalk(repo);
			walk.setRecursive(true);
			walk.addTree(new DirCacheIterator(repo.readDirCache()));

			Map<String, SubmoduleStatus> status = new HashMap<String, SubmoduleStatus>();
			while (walk.next()) {
				if (FileMode.GITLINK != walk.getFileMode(0))
					continue;

				ObjectId id = walk.getObjectId(0);
				String path = walk.getPathString();
				String configPath = config.getString(CONFIG_SECTION_SUBMODULE,
						path, CONFIG_KEY_PATH);
				if (configPath != null && configPath.length() > 0) {
					File repoDir = getSubmoduleGitDir(configPath);
					if (repoDir.isDirectory()) {
						ObjectId headId = resolveSubmoduleHead(repoDir);
						if (headId == null)
							status.put(path, new SubmoduleStatus(
									StatusType.NOT_INITIALIZED, path, id));
						else if (!headId.equals(id))
							status.put(path, new SubmoduleStatus(
									StatusType.REV_CHECKED_OUT, path, headId));
						else
							status.put(path, new SubmoduleStatus(
									StatusType.INITIALIZED, path, id));
					} else
						status.put(path, new SubmoduleStatus(
								StatusType.NOT_INITIALIZED, path, id));
				} else
					status.put(path, new SubmoduleStatus(StatusType.MISSING,
							path, id));
			}
			return status;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
