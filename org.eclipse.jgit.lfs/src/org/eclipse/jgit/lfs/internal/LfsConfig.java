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

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
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
	 * @throws LfsConfigInvalidException
	 */
	public LfsConfig(Repository db) throws LfsConfigInvalidException {
		this.db = db;
		delegate = this.createDelegateConfig();
	}

	/**
	 * Read the .lfsconfig file from the repository
	 *
	 * @return The loaded lfs config or null if it does not exist
	 *
	 * @throws LfsConfigInvalidException
	 */
	private Config createDelegateConfig()
			throws LfsConfigInvalidException {
		Config result = null;

		if (!db.isBare()) {
			result = createLfsConfigFromWorkingTree();
			if (result == null) {
				result = createLfsConfigFromIndex();
			}
		}

		if (result == null) {
			result = createLfsConfigFromHeadRevision();
		}

		if (result == null) {
			result = createEmptyConfig();
		}

		return result;
	}

	/**
	 * Try to read the lfs config from a file called .lfsconfig at the top level
	 * of the working tree.
	 *
	 * @return the config, or <code>null</code>
	 * @throws LfsConfigInvalidException
	 */
	private FileBasedConfig createLfsConfigFromWorkingTree()
			throws LfsConfigInvalidException {
		File lfsConfig = db.getFS().resolve(db.getWorkTree(),
				Constants.DOT_LFS_CONFIG);
		/* If config file exists, create a file based config for it */
		if (lfsConfig.exists() && lfsConfig.isFile()) {
			FileBasedConfig config = new FileBasedConfig(lfsConfig, db.getFS());
			try {
				config.load();
				return config;
			} catch (ConfigInvalidException | IOException e) {
				/*
				 * .lfsonfig file is present, but reading failed anyway. Seems
				 * to be a real error, e.g. invalid config file syntax.
				 */
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
	 * @throws LfsConfigInvalidException
	 */
	private Config createLfsConfigFromIndex()
			throws LfsConfigInvalidException {
		/* Search in Index */
		try {
			DirCacheEntry entry = db.readDirCache()
					.getEntry(Constants.DOT_LFS_CONFIG);
			if (entry != null) {
				return new BlobBasedConfig(null, db, entry.getObjectId());
			}
		} catch (NoWorkTreeException | IOException | ConfigInvalidException e) {
			/*
			 * Entry for .lfsonfig file is exists, but reading failed anyway.
			 * Seems to be a real error, e.g. invalid config file syntax.
			 */
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
	 * @throws LfsConfigInvalidException
	 */
	private Config createLfsConfigFromHeadRevision()
			throws LfsConfigInvalidException {
		try (RevWalk revWalk = new RevWalk(db)) {
			ObjectId headCommitId = db
					.resolve(org.eclipse.jgit.lib.Constants.HEAD);
			RevCommit commit = revWalk.parseCommit(headCommitId);
			RevTree tree = commit.getTree();
			TreeWalk treewalk = TreeWalk.forPath(db, Constants.DOT_LFS_CONFIG,
					tree);
			if (treewalk != null) {
				return new BlobBasedConfig(null, db, treewalk.getObjectId(0));
			}
		} catch (RevisionSyntaxException | IOException
				| ConfigInvalidException e) {
			/*
			 * Entry for .lfsonfig file is exists, but reading failed anyway.
			 * Seems to be a real error, e.g. invalid config file syntax.
			 */
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
	private Config createEmptyConfig() {
		return new Config();
	}

	/**
	 * Get string value or null if not found.
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection for the value
	 * @param name
	 *            the key name
	 * @return a String value from the config, <code>null</code> if not found
	 */
	public String getString(final String section, final String subsection,
			final String name) {
		return delegate.getString(section, subsection, name);
	}
}
