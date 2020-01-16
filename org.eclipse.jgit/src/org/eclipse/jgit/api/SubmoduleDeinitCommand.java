/*
 * Copyright (C) 2017, Two Sigma Open Source and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.util.FileUtils.RECURSIVE;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a submodule deinit command.
 * <p>
 * This will remove the module(s) from the working tree, but won't affect
 * .git/modules.
 *
 * @since 4.10
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleDeinitCommand
		extends GitCommand<Collection<SubmoduleDeinitResult>> {

	private final Collection<String> paths;

	private boolean force;

	/**
	 * Constructor of SubmoduleDeinitCommand
	 *
	 * @param repo
	 */
	public SubmoduleDeinitCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<>();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * @return the set of repositories successfully deinitialized.
	 * @throws NoSuchSubmoduleException
	 *             if any of the submodules which we might want to deinitialize
	 *             don't exist
	 */
	@Override
	public Collection<SubmoduleDeinitResult> call() throws GitAPIException {
		checkCallable();
		try {
			if (paths.isEmpty()) {
				return Collections.emptyList();
			}
			for (String path : paths) {
				if (!submoduleExists(path)) {
					throw new NoSuchSubmoduleException(path);
				}
			}
			List<SubmoduleDeinitResult> results = new ArrayList<>(paths.size());
			try (RevWalk revWalk = new RevWalk(repo);
					SubmoduleWalk generator = SubmoduleWalk.forIndex(repo)) {
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
				StoredConfig config = repo.getConfig();
				while (generator.next()) {
					String path = generator.getPath();
					String name = generator.getModuleName();
					SubmoduleDeinitStatus status = checkDirty(revWalk, path);
					switch (status) {
					case SUCCESS:
						deinit(path);
						break;
					case ALREADY_DEINITIALIZED:
						break;
					case DIRTY:
						if (force) {
							deinit(path);
							status = SubmoduleDeinitStatus.FORCED;
						}
						break;
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().unexpectedSubmoduleStatus,
								status));
					}

					config.unsetSection(
							ConfigConstants.CONFIG_SUBMODULE_SECTION, name);
					results.add(new SubmoduleDeinitResult(path, status));
				}
			}
			return results;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Recursively delete the *contents* of path, but leave path as an empty
	 * directory
	 *
	 * @param path
	 *            the path to clean
	 * @throws IOException
	 */
	private void deinit(String path) throws IOException {
		File dir = new File(repo.getWorkTree(), path);
		if (!dir.isDirectory()) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().expectedDirectoryNotSubmodule, path));
		}
		final File[] ls = dir.listFiles();
		if (ls != null) {
			for (File f : ls) {
				FileUtils.delete(f, RECURSIVE);
			}
		}
	}

	/**
	 * Check if a submodule is dirty. A submodule is dirty if there are local
	 * changes to the submodule relative to its HEAD, including untracked files.
	 * It is also dirty if the HEAD of the submodule does not match the value in
	 * the parent repo's index or HEAD.
	 *
	 * @param revWalk
	 * @param path
	 * @return status of the command
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private SubmoduleDeinitStatus checkDirty(RevWalk revWalk, String path)
			throws GitAPIException, IOException {
		Ref head = repo.exactRef("HEAD"); //$NON-NLS-1$
		if (head == null) {
			throw new NoHeadException(
					JGitText.get().invalidRepositoryStateNoHead);
		}
		RevCommit headCommit = revWalk.parseCommit(head.getObjectId());
		RevTree tree = headCommit.getTree();

		ObjectId submoduleHead;
		try (SubmoduleWalk w = SubmoduleWalk.forPath(repo, tree, path)) {
			submoduleHead = w.getHead();
			if (submoduleHead == null) {
				// The submodule is not checked out.
				return SubmoduleDeinitStatus.ALREADY_DEINITIALIZED;
			}
			if (!submoduleHead.equals(w.getObjectId())) {
				// The submodule's current HEAD doesn't match the value in the
				// outer repo's HEAD.
				return SubmoduleDeinitStatus.DIRTY;
			}
		}

		try (SubmoduleWalk w = SubmoduleWalk.forIndex(repo)) {
			if (!w.next()) {
				// The submodule does not exist in the index (shouldn't happen
				// since we check this earlier)
				return SubmoduleDeinitStatus.DIRTY;
			}
			if (!submoduleHead.equals(w.getObjectId())) {
				// The submodule's current HEAD doesn't match the value in the
				// outer repo's index.
				return SubmoduleDeinitStatus.DIRTY;
			}

			try (Repository submoduleRepo = w.getRepository()) {
				Status status = Git.wrap(submoduleRepo).status().call();
				return status.isClean() ? SubmoduleDeinitStatus.SUCCESS
						: SubmoduleDeinitStatus.DIRTY;
			}
		}
	}

	/**
	 * Check if this path is a submodule by checking the index, which is what
	 * git submodule deinit checks.
	 *
	 * @param path
	 *            path of the submodule
	 *
	 * @return {@code true} if path exists and is a submodule in index,
	 *         {@code false} otherwise
	 * @throws IOException
	 */
	private boolean submoduleExists(String path) throws IOException {
		TreeFilter filter = PathFilter.create(path);
		try (SubmoduleWalk w = SubmoduleWalk.forIndex(repo)) {
			return w.setFilter(filter).next();
		}
	}

	/**
	 * Add repository-relative submodule path to deinitialize
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleDeinitCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	/**
	 * If {@code true}, call() will deinitialize modules with local changes;
	 * else it will refuse to do so.
	 *
	 * @param force
	 * @return {@code this}
	 */
	public SubmoduleDeinitCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * The user tried to deinitialize a submodule that doesn't exist in the
	 * index.
	 */
	public static class NoSuchSubmoduleException extends GitAPIException {
		private static final long serialVersionUID = 1L;

		/**
		 * Constructor of NoSuchSubmoduleException
		 *
		 * @param path
		 *            path of non-existing submodule
		 */
		public NoSuchSubmoduleException(String path) {
			super(MessageFormat.format(JGitText.get().noSuchSubmodule, path));
		}
	}

	/**
	 * The effect of a submodule deinit command for a given path
	 */
	public enum SubmoduleDeinitStatus {
		/**
		 * The submodule was not initialized in the first place
		 */
		ALREADY_DEINITIALIZED,
		/**
		 * The submodule was deinitialized
		 */
		SUCCESS,
		/**
		 * The submodule had local changes, but was deinitialized successfully
		 */
		FORCED,
		/**
		 * The submodule had local changes and force was false
		 */
		DIRTY,
	}
}
