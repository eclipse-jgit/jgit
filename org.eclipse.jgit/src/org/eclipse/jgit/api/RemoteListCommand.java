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

import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Used to obtain the list of remotes.
 *
 * This class has setters for all supported options and arguments of this
 * command and a {@link #call()} method to finally execute the command.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-remote.html" > Git
 *      documentation about Remote</a>
 * @since 4.2
 */
public class RemoteListCommand extends GitCommand<List<RemoteConfig>> {

	/**
	 * <p>
	 * Constructor for RemoteListCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RemoteListCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code remote} command with all the options and parameters
	 * collected by the setter methods of this class.
	 */
	@Override
	public List<RemoteConfig> call() throws GitAPIException {
		checkCallable();

		try {
			return RemoteConfig.getAllRemoteConfigs(repo.getConfig());
		} catch (URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

}
