/*
 * Copyright (C) 2019 Thomas Wolf <thomas.wolf@paranor.ch>
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
		public String getUsername() {
			return session.getUsername();
		}
	}
}
