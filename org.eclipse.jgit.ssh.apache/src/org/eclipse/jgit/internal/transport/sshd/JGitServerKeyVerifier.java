/*
 * Copyright (C) 2019 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.flag;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bridge between the {@link ServerKeyVerifier} from Apache MINA sshd and our
 * {@link ServerKeyDatabase}.
 */
public class JGitServerKeyVerifier
		implements ServerKeyVerifier, ServerKeyLookup {

	private static final Logger LOG = LoggerFactory
			.getLogger(JGitServerKeyVerifier.class);

	private final @NonNull ServerKeyDatabase database;

	/**
	 * Creates a new {@link JGitServerKeyVerifier} using the given
	 * {@link ServerKeyDatabase}.
	 *
	 * @param database
	 *            to use
	 */
	public JGitServerKeyVerifier(@NonNull ServerKeyDatabase database) {
		this.database = database;
	}

	@Override
	public List<PublicKey> lookup(ClientSession session,
			SocketAddress remoteAddress) {
		if (!(session instanceof JGitClientSession)) {
			LOG.warn("Internal error: wrong session kind: " //$NON-NLS-1$
					+ session.getClass().getName());
			return Collections.emptyList();
		}
		if (!(remoteAddress instanceof InetSocketAddress)) {
			return Collections.emptyList();
		}
		SessionConfig config = new SessionConfig((JGitClientSession) session);
		SshdSocketAddress connectAddress = SshdSocketAddress
				.toSshdSocketAddress(session.getConnectAddress());
		String connect = KnownHostHashValue.createHostPattern(
				connectAddress.getHostName(), connectAddress.getPort());
		return database.lookup(connect, (InetSocketAddress) remoteAddress,
				config);
	}

	@Override
	public boolean verifyServerKey(ClientSession session,
			SocketAddress remoteAddress, PublicKey serverKey) {
		if (!(session instanceof JGitClientSession)) {
			LOG.warn("Internal error: wrong session kind: " //$NON-NLS-1$
					+ session.getClass().getName());
			return false;
		}
		if (!(remoteAddress instanceof InetSocketAddress)) {
			return false;
		}
		SessionConfig config = new SessionConfig((JGitClientSession) session);
		SshdSocketAddress connectAddress = SshdSocketAddress
				.toSshdSocketAddress(session.getConnectAddress());
		String connect = KnownHostHashValue.createHostPattern(
				connectAddress.getHostName(), connectAddress.getPort());
		CredentialsProvider provider = ((JGitClientSession) session)
				.getCredentialsProvider();
		return database.accept(connect, (InetSocketAddress) remoteAddress,
				serverKey, config, provider);
	}

	private static class SessionConfig
			implements ServerKeyDatabase.Configuration {

		private final JGitClientSession session;

		public SessionConfig(JGitClientSession session) {
			this.session = session;
		}

		private List<String> get(String key) {
			HostConfigEntry entry = session.getHostConfigEntry();
			if (entry instanceof JGitHostConfigEntry) {
				// Always true!
				return ((JGitHostConfigEntry) entry).getMultiValuedOptions()
						.get(key);
			}
			return Collections.emptyList();
		}

		@Override
		public List<String> getUserKnownHostsFiles() {
			return get(SshConstants.USER_KNOWN_HOSTS_FILE);
		}

		@Override
		public List<String> getGlobalKnownHostsFiles() {
			return get(SshConstants.GLOBAL_KNOWN_HOSTS_FILE);
		}

		@Override
		public StrictHostKeyChecking getStrictHostKeyChecking() {
			HostConfigEntry entry = session.getHostConfigEntry();
			String value = entry
					.getProperty(SshConstants.STRICT_HOST_KEY_CHECKING, "ask"); //$NON-NLS-1$
			switch (value.toLowerCase(Locale.ROOT)) {
			case SshConstants.YES:
			case SshConstants.ON:
				return StrictHostKeyChecking.REQUIRE_MATCH;
			case SshConstants.NO:
			case SshConstants.OFF:
				return StrictHostKeyChecking.ACCEPT_ANY;
			case "accept-new": //$NON-NLS-1$
				return StrictHostKeyChecking.ACCEPT_NEW;
			default:
				return StrictHostKeyChecking.ASK;
			}
		}

		@Override
		public boolean getHashKnownHosts() {
			HostConfigEntry entry = session.getHostConfigEntry();
			return flag(entry.getProperty(SshConstants.HASH_KNOWN_HOSTS));
		}

		@Override
		public String getUsername() {
			return session.getUsername();
		}
	}
}
