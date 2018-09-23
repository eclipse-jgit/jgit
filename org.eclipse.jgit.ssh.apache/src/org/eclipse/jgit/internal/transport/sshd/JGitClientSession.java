/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.io.IoSession;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * A {@link org.apache.sshd.client.session.ClientSession ClientSession} that can
 * be associated with the {@link HostConfigEntry} the session was created for.
 * The {@link JGitSshClient} creates such sessions and sets this association.
 * <p>
 * Also provides for associating a JGit {@link CredentialsProvider} with a
 * session.
 * </p>
 */
public class JGitClientSession extends ClientSessionImpl {

	private HostConfigEntry hostConfig;

	private CredentialsProvider credentialsProvider;

	/**
	 * @param manager
	 * @param session
	 * @throws Exception
	 */
	public JGitClientSession(ClientFactoryManager manager, IoSession session)
			throws Exception {
		super(manager, session);
	}

	/**
	 * Retrieves the {@link HostConfigEntry} this session was created for.
	 *
	 * @return the {@link HostConfigEntry}, or {@code null} if none set
	 */
	public HostConfigEntry getHostConfigEntry() {
		return hostConfig;
	}

	/**
	 * Sets the {@link HostConfigEntry} this session was created for.
	 *
	 * @param hostConfig
	 *            the {@link HostConfigEntry}
	 */
	public void setHostConfigEntry(HostConfigEntry hostConfig) {
		this.hostConfig = hostConfig;
	}

	/**
	 * Sets the {@link CredentialsProvider} for this session.
	 *
	 * @param provider
	 *            to set
	 */
	public void setCredentialsProvider(CredentialsProvider provider) {
		credentialsProvider = provider;
	}

	/**
	 * Retrieves the {@link CredentialsProvider} set for this session.
	 *
	 * @return the provider, or {@code null} if none is set.
	 */
	public CredentialsProvider getCredentialsProvider() {
		return credentialsProvider;
	}

}
