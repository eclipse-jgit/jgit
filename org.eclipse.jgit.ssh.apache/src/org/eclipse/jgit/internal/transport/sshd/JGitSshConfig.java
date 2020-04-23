/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.flag;
import static org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.positive;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.TreeMap;

import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.SshSessionFactory;

/**
 * A bridge between a JGit {@link SshConfigStore} and the Apache MINA sshd
 * {@link HostConfigEntryResolver}.
 */
public class JGitSshConfig implements HostConfigEntryResolver {

	private final SshConfigStore configFile;

	/**
	 * Creates a new {@link JGitSshConfig} that will read the config from the
	 * given {@link SshConfigStore}.
	 *
	 * @param store
	 *            to use
	 */
	public JGitSshConfig(SshConfigStore store) {
		configFile = store;
	}

	@Override
	public HostConfigEntry resolveEffectiveHost(String host, int port,
			SocketAddress localAddress, String username,
			AttributeRepository attributes) throws IOException {
		SshConfigStore.HostConfig entry = configFile == null
				? SshConfigStore.EMPTY_CONFIG
				: configFile.lookup(host, port, username);
		JGitHostConfigEntry config = new JGitHostConfigEntry();
		// Apache MINA conflates all keys, even multi-valued ones, in one map
		// and puts multiple values separated by commas in one string. See
		// the javadoc on HostConfigEntry.
		Map<String, String> allOptions = new TreeMap<>(
				String.CASE_INSENSITIVE_ORDER);
		allOptions.putAll(entry.getOptions());
		// And what if a value contains a comma??
		entry.getMultiValuedOptions().entrySet().stream()
				.forEach(e -> allOptions.put(e.getKey(),
						String.join(",", e.getValue()))); //$NON-NLS-1$
		config.setProperties(allOptions);
		// The following is an extension from JGitHostConfigEntry
		config.setMultiValuedOptions(entry.getMultiValuedOptions());
		// Also make sure the underlying properties are set
		String hostName = entry.getValue(SshConstants.HOST_NAME);
		if (hostName == null || hostName.isEmpty()) {
			hostName = host;
		}
		config.setHostName(hostName);
		config.setProperty(SshConstants.HOST_NAME, hostName);
		config.setHost(SshdSocketAddress.isIPv6Address(hostName) ? "" : hostName); //$NON-NLS-1$
		String user = username != null && !username.isEmpty() ? username
				: entry.getValue(SshConstants.USER);
		if (user == null || user.isEmpty()) {
			user = SshSessionFactory.getLocalUserName();
		}
		config.setUsername(user);
		config.setProperty(SshConstants.USER, user);
		int p = port >= 0 ? port : positive(entry.getValue(SshConstants.PORT));
		config.setPort(p >= 0 ? p : SshConstants.SSH_DEFAULT_PORT);
		config.setProperty(SshConstants.PORT,
				Integer.toString(config.getPort()));
		config.setIdentities(entry.getValues(SshConstants.IDENTITY_FILE));
		config.setIdentitiesOnly(
				flag(entry.getValue(SshConstants.IDENTITIES_ONLY)));
		return config;
	}

}
