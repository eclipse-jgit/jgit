/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import java.io.IOException;

import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.LfsFactory.LfsInstallCommand;
import org.eclipse.jgit.util.SystemReader;

/**
 * Installs all required LFS properties for the current user, analogous to 'git
 * lfs install', but defaulting to using JGit builtin hooks.
 *
 * @since 4.11
 */
public class InstallBuiltinLfsCommand implements LfsInstallCommand {

	private static final String[] ARGS_USER = new String[] { "lfs", "install" }; //$NON-NLS-1$//$NON-NLS-2$

	private static final String[] ARGS_LOCAL = new String[] { "lfs", "install", //$NON-NLS-1$//$NON-NLS-2$
			"--local" }; //$NON-NLS-1$

	private Repository repository;

	/**
	 * {@inheritDoc}
	 *
	 * @throws IOException
	 *             if an I/O error occurs while accessing a git config or
	 *             executing {@code git lfs install} in an external process
	 * @throws InvalidConfigurationException
	 *             if a git configuration is invalid
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting for the
	 *             {@code git lfs install} executed in an external process
	 */
	@Override
	public Void call() throws IOException, InvalidConfigurationException,
			InterruptedException {
		StoredConfig cfg = null;
		if (repository == null) {
			try {
				cfg = SystemReader.getInstance().getUserConfig();
			} catch (ConfigInvalidException e) {
				throw new InvalidConfigurationException(e.getMessage(), e);
			}
		} else {
			cfg = repository.getConfig();
		}

		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, true);

		cfg.save();

		// try to run git lfs install, we really don't care if it is present
		// and/or works here (yet).
		ProcessBuilder builder = FS.DETECTED.runInShell("git", //$NON-NLS-1$
				repository == null ? ARGS_USER : ARGS_LOCAL);
		if (repository != null) {
			builder.directory(repository.isBare() ? repository.getDirectory()
					: repository.getWorkTree());
		}
		FS.DETECTED.runProcess(builder, null, null, (String) null);

		return null;
	}

	/**
	 * Set the repository to install LFS for
	 *
	 * @param repo
	 *            the repository to install LFS into locally instead of the user
	 *            configuration
	 * @return this command
	 */
	@Override
	public LfsInstallCommand setRepository(Repository repo) {
		this.repository = repo;
		return this;
	}

}
