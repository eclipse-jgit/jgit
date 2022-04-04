/*
 * Copyright (C) 2022, Matthias Fromme <mfromme@dspace.de>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.internal;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;

import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * Encapsulate access to the .lfsconfig.
 *
 * According to the document
 * https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-config.5.ronn
 * the order to find the .lfsconfig file is:
 *
 * <pre>
 *   1. in the root of the working tree
 *   2. in the index
 *   3. in the HEAD, for bare repositories this is the only place
 *      that is searched
 * </pre>
 *
 * Values from the .lfsconfig are used only if not specified in another git
 * config file to allow local override without modifiction of a committed file.
 */
public class LfsConfig {
	private Repository db;
	private Config delegate;

	/**
	 * Create a new instance of the LfsConfig.
	 *
	 * @param db
	 *            the associated repo
	 */
	public LfsConfig(Repository db) {
		this.db = db;
	}

	/**
	 * Getter for the delegate to allow lazy initialization.
	 *
	 * @return the delegate {@link Config}
	 * @throws IOException
	 */
	private Config getDelegate() throws IOException {
		if (delegate == null) {
			delegate = this.load();
		}
		return delegate;
	}

	/**
	 * Read the .lfsconfig file from the repository
	 *
	 * An empty config is returned be empty if no lfs config exists.
	 *
	 * @return The loaded lfs config
	 *
	 * @throws IOException
	 */
	private Config load() throws IOException {
		Config result = null;

		if (!db.isBare()) {
			result = loadFromWorkingTree();
			if (result == null) {
				result = loadFromIndex();
			}
		}

		if (result == null) {
			result = loadFromHead();
		}

		if (result == null) {
			result = emptyConfig();
		}

		return result;
	}

	/**
	 * Try to read the lfs config from a file called .lfsconfig at the top level
	 * of the working tree.
	 *
	 * @return the config, or <code>null</code>
	 * @throws IOException
	 */
	@Nullable
	private Config loadFromWorkingTree()
			throws IOException {
		File lfsConfig = db.getFS().resolve(db.getWorkTree(),
				Constants.DOT_LFS_CONFIG);
		if (lfsConfig.exists() && lfsConfig.isFile()) {
			FileBasedConfig config = new FileBasedConfig(lfsConfig, db.getFS());
			try {
				config.load();
				return config;
			} catch (ConfigInvalidException e) {
				throw new LfsConfigInvalidException(
						LfsText.get().dotLfsConfigReadFailed, e);
			}
		}
		return null;
	}

	/**
	 * Try to read the lfs config from an entry called .lfsconfig contained in
	 * the index.
	 *
	 * @return the config, or <code>null</code> if the entry does not exist
	 * @throws IOException
	 */
	@Nullable
	private Config loadFromIndex()
			throws IOException {
		try {
			DirCacheEntry entry = db.readDirCache()
					.getEntry(Constants.DOT_LFS_CONFIG);
			if (entry != null) {
				return new BlobBasedConfig(null, db, entry.getObjectId());
			}
		} catch (ConfigInvalidException e) {
			throw new LfsConfigInvalidException(
					LfsText.get().dotLfsConfigReadFailed, e);
		}
		return null;
	}

	/**
	 * Try to read the lfs config from an entry called .lfsconfig contained in
	 * the head revision.
	 *
	 * @return the config, or <code>null</code> if the file does not exist
	 * @throws IOException
	 */
	@Nullable
	private Config loadFromHead() throws IOException {
		try (RevWalk revWalk = new RevWalk(db)) {
			ObjectId headCommitId = db.resolve(HEAD);
			if (headCommitId == null) {
				return null;
			}
			RevCommit commit = revWalk.parseCommit(headCommitId);
			RevTree tree = commit.getTree();
			TreeWalk treewalk = TreeWalk.forPath(db, Constants.DOT_LFS_CONFIG,
					tree);
			if (treewalk != null) {
				return new BlobBasedConfig(null, db, treewalk.getObjectId(0));
			}
		} catch (ConfigInvalidException e) {
			throw new LfsConfigInvalidException(
					LfsText.get().dotLfsConfigReadFailed, e);
		}
		return null;
	}

	/**
	 * Create an empty config as fallback to avoid null pointer checks.
	 *
	 * @return an empty config
	 */
	private Config emptyConfig() {
		return new Config();
	}

	/**
	 * Get string value or null if not found.
	 *
	 * First tries to find the value in the git config files. If not found tries
	 * to find data in .lfsconfig.
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection for the value
	 * @param name
	 *            the key name
	 * @return a String value from the config, <code>null</code> if not found
	 * @throws IOException
	 */
	@Nullable
	public String getString(final String section, final String subsection,
			final String name) throws IOException {
		String result = db.getConfig().getString(section, subsection, name);
		if (result == null) {
			result = getDelegate().getString(section, subsection, name);
		}
		return result;
	}
}
