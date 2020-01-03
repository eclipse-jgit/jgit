/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Used to remove an existing remote.
 *
 * This class has setters for all supported options and arguments of this
 * command and a {@link #call()} method to finally execute the command.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-remote.html" > Git
 *      documentation about Remote</a>
 * @since 4.2
 */
public class RemoteRemoveCommand extends GitCommand<RemoteConfig> {

	private String remoteName;

	/**
	 * <p>
	 * Constructor for RemoteRemoveCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RemoteRemoveCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The name of the remote to remove.
	 *
	 * @param name
	 *            a remote name
	 * @deprecated use {@link #setRemoteName} instead
	 */
	@Deprecated
	public void setName(String name) {
		this.remoteName = name;
	}

	/**
	 * The name of the remote to remove.
	 *
	 * @param remoteName
	 *            a remote name
	 * @return {@code this}
	 * @since 5.3
	 */
	public RemoteRemoveCommand setRemoteName(String remoteName) {
		this.remoteName = remoteName;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code remote} command with all the options and parameters
	 * collected by the setter methods of this class.
	 */
	@Override
	public RemoteConfig call() throws GitAPIException {
		checkCallable();

		try {
			StoredConfig config = repo.getConfig();
			RemoteConfig remote = new RemoteConfig(config, remoteName);
			config.unsetSection(ConfigConstants.CONFIG_KEY_REMOTE, remoteName);
			config.save();
			return remote;
		} catch (IOException | URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

	}

}
