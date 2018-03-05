/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com>
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
package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Callable;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Installs all required LFS properties for the current user, analogous to 'git
 * lfs install', but defaulting to using JGit builtin hooks.
 *
 * @since 4.11
 */
public class InstallLfsCommand implements Callable<Void>{

	private static final String[] ARGS_USER = new String[] { "lfs", "install" }; //$NON-NLS-1$//$NON-NLS-2$

	private static final String[] ARGS_LOCAL = new String[] { "lfs", "install", //$NON-NLS-1$//$NON-NLS-2$
			"--local" }; //$NON-NLS-1$

	private Repository repository;

	/** {@inheritDoc} */
	@Override
	public Void call() throws Exception {
		StoredConfig cfg = null;
		if (repository == null) {
			cfg = loadUserConfig();
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
	public InstallLfsCommand setRepository(Repository repo) {
		this.repository = repo;
		return this;
	}

	private StoredConfig loadUserConfig() throws IOException {
		FileBasedConfig c = SystemReader.getInstance().openUserConfig(null,
				FS.DETECTED);
		try {
			c.load();
		} catch (ConfigInvalidException e1) {
			throw new IOException(MessageFormat
					.format(LfsText.get().userConfigInvalid, c.getFile()
							.getAbsolutePath(), e1),
					e1);
		}

		return c;
	}

}
