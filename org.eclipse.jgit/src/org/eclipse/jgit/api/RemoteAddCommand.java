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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Used to add a new remote.
 *
 * This class has setters for all supported options and arguments of this
 * command and a {@link #call()} method to finally execute the command.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-remote.html" > Git
 *      documentation about Remote</a>
 * @since 4.2
 */
public class RemoteAddCommand extends GitCommand<RemoteConfig> {

	private String name;

	private URIish uri;

	/**
	 * Constructor for RemoteAddCommand.
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RemoteAddCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The name of the remote to add.
	 *
	 * @param name
	 *            a remote name
	 * @return this instance
	 * @since 5.0
	 */
	public RemoteAddCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * The URL of the repository for the new remote.
	 *
	 * @param uri
	 *            an URL for the remote
	 * @return this instance
	 * @since 5.0
	 */
	public RemoteAddCommand setUri(URIish uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code remote add} command with all the options and
	 * parameters collected by the setter methods of this class.
	 */
	@Override
	public RemoteConfig call() throws GitAPIException {
		checkCallable();

		try {
			StoredConfig config = repo.getConfig();
			RemoteConfig remote = new RemoteConfig(config, name);

			RefSpec refSpec = new RefSpec();
			refSpec = refSpec.setForceUpdate(true);
			refSpec = refSpec.setSourceDestination(Constants.R_HEADS + "*", //$NON-NLS-1$
					Constants.R_REMOTES + name + "/*"); //$NON-NLS-1$
			remote.addFetchRefSpec(refSpec);

			remote.addURI(uri);

			remote.update(config);
			config.save();
			return remote;
		} catch (IOException | URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

	}

}
