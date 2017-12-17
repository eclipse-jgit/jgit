/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com>
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
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Used to to change the URL of a remote.
 *
 * This class has setters for all supported options and arguments of this
 * command and a {@link #call()} method to finally execute the command.
 *
 * @see <a href=
 *      "http://www.kernel.org/pub/software/scm/git/docs/git-remote.html" > Git
 *      documentation about Remote</a>
 * @since 4.2
 */
public class RemoteSetUrlCommand extends GitCommand<RemoteConfig> {

	private String name;

	private URIish uri;

	private boolean push;

	/**
	 * <p>
	 * Constructor for RemoteSetUrlCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected RemoteSetUrlCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The name of the remote to change the URL for.
	 *
	 * @param name
	 *            a remote name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * The new URL for the remote.
	 *
	 * @param uri
	 *            an URL for the remote
	 */
	public void setUri(URIish uri) {
		this.uri = uri;
	}

	/**
	 * Whether to change the push URL of the remote instead of the fetch URL.
	 *
	 * @param push
	 *            <code>true</code> to set the push url, <code>false</code> to
	 *            set the fetch url
	 */
	public void setPush(boolean push) {
		this.push = push;
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
			RemoteConfig remote = new RemoteConfig(config, name);
			if (push) {
				List<URIish> uris = remote.getPushURIs();
				if (uris.size() > 1) {
					throw new JGitInternalException(
							"remote.newtest.pushurl has multiple values"); //$NON-NLS-1$
				} else if (uris.size() == 1) {
					remote.removePushURI(uris.get(0));
				}
				remote.addPushURI(uri);
			} else {
				List<URIish> uris = remote.getURIs();
				if (uris.size() > 1) {
					throw new JGitInternalException(
							"remote.newtest.url has multiple values"); //$NON-NLS-1$
				} else if (uris.size() == 1) {
					remote.removeURI(uris.get(0));
				}
				remote.addURI(uri);
			}

			remote.update(config);
			config.save();
			return remote;
		} catch (IOException | URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

}
