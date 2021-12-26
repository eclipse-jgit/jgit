/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.agent.SshAgentServer;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.JGitClientSession;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;

/**
 * A factory for creating {@link SshAgentClient}s.
 */
public class JGitSshAgentFactory implements SshAgentFactory {

	private final @NonNull ConnectorFactory factory;

	private final File homeDir;

	/**
	 * Creates a new {@link JGitSshAgentFactory}.
	 *
	 * @param factory
	 *            {@link JGitSshAgentFactory} to wrap
	 * @param homeDir
	 *            for obtaining the current local user's home directory
	 */
	public JGitSshAgentFactory(@NonNull ConnectorFactory factory,
			File homeDir) {
		this.factory = factory;
		this.homeDir = homeDir;
	}

	@Override
	public List<ChannelFactory> getChannelForwardingFactories(
			FactoryManager manager) {
		// No agent forwarding supported.
		return Collections.emptyList();
	}

	@Override
	public SshAgent createClient(Session session, FactoryManager manager)
			throws IOException {
		String identityAgent = null;
		if (session instanceof JGitClientSession) {
			HostConfigEntry hostConfig = ((JGitClientSession) session)
					.getHostConfigEntry();
			identityAgent = hostConfig.getProperty(SshConstants.IDENTITY_AGENT,
					null);
		}
		if (SshConstants.NONE.equals(identityAgent)) {
			return null;
		}
		return new SshAgentClient(factory.create(identityAgent, homeDir));
	}

	@Override
	public SshAgentServer createServer(ConnectionService service)
			throws IOException {
		// This should be called in a server only.
		return null;
	}
}
