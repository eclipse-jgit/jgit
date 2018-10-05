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

import static java.text.MessageFormat.format;
import static org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile.positive;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.DefaultConnectFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.client.session.SessionFactory;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoConnectFuture;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.common.util.ValidateUtils;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.sshd.KeyCache;

/**
 * Customized {@link SshClient} for JGit. It creates specialized
 * {@link JGitClientSession}s that know about the {@link HostConfigEntry} they
 * were created for, and it loads all KeyPair identities lazily.
 */
public class JGitSshClient extends SshClient {

	/**
	 * We need access to this during the constructor of the ClientSession,
	 * before setConnectAddress() can have been called. So we have to remember
	 * it in an attribute on the SshClient, from where we can then retrieve it.
	 */
	static final AttributeKey<HostConfigEntry> HOST_CONFIG_ENTRY = new AttributeKey<>();

	/**
	 * An attribute key for the comma-separated list of default preferred
	 * authentication mechanisms.
	 */
	public static final AttributeKey<String> PREFERRED_AUTHENTICATIONS = new AttributeKey<>();

	private KeyCache keyCache;

	private CredentialsProvider credentialsProvider;

	@Override
	protected SessionFactory createSessionFactory() {
		// Override the parent's default
		return new JGitSessionFactory(this);
	}

	@Override
	public ConnectFuture connect(HostConfigEntry hostConfig)
			throws IOException {
		if (connector == null) {
			throw new IllegalStateException("SshClient not started."); //$NON-NLS-1$
		}
		Objects.requireNonNull(hostConfig, "No host configuration"); //$NON-NLS-1$
		String host = ValidateUtils.checkNotNullAndNotEmpty(
				hostConfig.getHostName(), "No target host"); //$NON-NLS-1$
		int port = hostConfig.getPort();
		ValidateUtils.checkTrue(port > 0, "Invalid port: %d", port); //$NON-NLS-1$
		String userName = hostConfig.getUsername();
		InetSocketAddress address = new InetSocketAddress(host, port);
		ConnectFuture connectFuture = new DefaultConnectFuture(
				userName + '@' + address, null);
		SshFutureListener<IoConnectFuture> listener = createConnectCompletionListener(
				connectFuture, userName, address, hostConfig);
		// sshd needs some entries from the host config already in the
		// constructor of the session. Put those as properties on this client,
		// where it will find them. We can set the host config only once the
		// session object has been created.
		copyProperty(
				hostConfig.getProperty(SshConstants.PREFERRED_AUTHENTICATIONS,
						getAttribute(PREFERRED_AUTHENTICATIONS)),
				PREFERRED_AUTHS);
		setAttribute(HOST_CONFIG_ENTRY, hostConfig);
		connector.connect(address).addListener(listener);
		return connectFuture;
	}

	private void copyProperty(String value, String key) {
		if (value != null && !value.isEmpty()) {
			getProperties().put(key, value);
		}
	}

	private SshFutureListener<IoConnectFuture> createConnectCompletionListener(
			ConnectFuture connectFuture, String username,
			InetSocketAddress address, HostConfigEntry hostConfig) {
		return new SshFutureListener<IoConnectFuture>() {

			@Override
			public void operationComplete(IoConnectFuture future) {
				if (future.isCanceled()) {
					connectFuture.cancel();
					return;
				}
				Throwable t = future.getException();
				if (t != null) {
					connectFuture.setException(t);
					return;
				}
				IoSession ioSession = future.getSession();
				try {
					JGitClientSession session = createSession(ioSession,
							username, address, hostConfig);
					connectFuture.setSession(session);
				} catch (RuntimeException e) {
					connectFuture.setException(e);
					ioSession.close(true);
				}
			}

			@Override
			public String toString() {
				return "JGitSshClient$ConnectCompletionListener[" + username //$NON-NLS-1$
						+ '@' + address + ']';
			}
		};
	}

	private JGitClientSession createSession(IoSession ioSession,
			String username, InetSocketAddress address,
			HostConfigEntry hostConfig) {
		AbstractSession rawSession = AbstractSession.getSession(ioSession);
		if (!(rawSession instanceof JGitClientSession)) {
			throw new IllegalStateException("Wrong session type: " //$NON-NLS-1$
					+ rawSession.getClass().getCanonicalName());
		}
		JGitClientSession session = (JGitClientSession) rawSession;
		session.setUsername(username);
		session.setConnectAddress(address);
		session.setHostConfigEntry(hostConfig);
		if (session.getCredentialsProvider() == null) {
			session.setCredentialsProvider(getCredentialsProvider());
		}
		int numberOfPasswordPrompts = getNumberOfPasswordPrompts(hostConfig);
		session.getProperties().put(PASSWORD_PROMPTS,
				Integer.valueOf(numberOfPasswordPrompts));
		FileKeyPairProvider ourConfiguredKeysProvider = null;
		List<Path> identities = hostConfig.getIdentities().stream()
				.map(s -> {
					try {
						return Paths.get(s);
					} catch (InvalidPathException e) {
						log.warn(format(SshdText.get().configInvalidPath,
								SshConstants.IDENTITY_FILE, s), e);
						return null;
					}
				}).filter(p -> p != null && Files.exists(p))
				.collect(Collectors.toList());
		ourConfiguredKeysProvider = new CachingKeyPairProvider(identities,
				keyCache);
		ourConfiguredKeysProvider.setPasswordFinder(getFilePasswordProvider());
		if (hostConfig.isIdentitiesOnly()) {
			session.setKeyPairProvider(ourConfiguredKeysProvider);
		} else {
			KeyPairProvider defaultKeysProvider = getKeyPairProvider();
			if (defaultKeysProvider instanceof FileKeyPairProvider) {
				((FileKeyPairProvider) defaultKeysProvider)
						.setPasswordFinder(getFilePasswordProvider());
			}
			KeyPairProvider combinedProvider = new CombinedKeyPairProvider(
					ourConfiguredKeysProvider, defaultKeysProvider);
			session.setKeyPairProvider(combinedProvider);
		}
		return session;
	}

	private int getNumberOfPasswordPrompts(HostConfigEntry hostConfig) {
		String prompts = hostConfig
				.getProperty(SshConstants.NUMBER_OF_PASSWORD_PROMPTS);
		if (prompts != null) {
			prompts = prompts.trim();
			int value = positive(prompts);
			if (value > 0) {
				return value;
			}
			log.warn(format(SshdText.get().configInvalidPositive,
					SshConstants.NUMBER_OF_PASSWORD_PROMPTS, prompts));
		}
		// Default for NumberOfPasswordPrompts according to
		// https://man.openbsd.org/ssh_config
		return 3;
	}

	/**
	 * Set a cache for loaded keys. Newly discovered keys will be added when
	 * IdentityFile host entries from the ssh config file are used during
	 * session authentication.
	 *
	 * @param cache
	 *            to use
	 */
	public void setKeyCache(KeyCache cache) {
		keyCache = cache;
	}

	/**
	 * Sets the {@link CredentialsProvider} for this client.
	 *
	 * @param provider
	 *            to set
	 */
	public void setCredentialsProvider(CredentialsProvider provider) {
		credentialsProvider = provider;
	}

	/**
	 * Retrieves the {@link CredentialsProvider} set for this client.
	 *
	 * @return the provider, or {@code null} if none is set.
	 */
	public CredentialsProvider getCredentialsProvider() {
		return credentialsProvider;
	}

	/**
	 * A {@link SessionFactory} to create our own specialized
	 * {@link JGitClientSession}s.
	 */
	private static class JGitSessionFactory extends SessionFactory {

		public JGitSessionFactory(JGitSshClient client) {
			super(client);
		}

		@Override
		protected ClientSessionImpl doCreateSession(IoSession ioSession)
				throws Exception {
			return new JGitClientSession(getClient(), ioSession);
		}
	}

	/**
	 * A {@link KeyPairProvider} that iterates over the {@link Iterable}s
	 * returned by other {@link KeyPairProvider}s.
	 */
	private static class CombinedKeyPairProvider implements KeyPairProvider {

		private final List<KeyPairProvider> providers;

		public CombinedKeyPairProvider(KeyPairProvider... providers) {
			this(Arrays.stream(providers).filter(Objects::nonNull)
					.collect(Collectors.toList()));
		}

		public CombinedKeyPairProvider(List<KeyPairProvider> providers) {
			this.providers = providers;
		}

		@Override
		public Iterable<String> getKeyTypes() {
			throw new UnsupportedOperationException(
					"Should not have been called in a ssh client"); //$NON-NLS-1$
		}

		@Override
		public KeyPair loadKey(String type) {
			throw new UnsupportedOperationException(
					"Should not have been called in a ssh client"); //$NON-NLS-1$
		}

		@Override
		public Iterable<KeyPair> loadKeys() {
			return () -> new Iterator<KeyPair>() {

				private Iterator<KeyPairProvider> factories = providers.iterator();
				private Iterator<KeyPair> current;

				private Boolean hasElement;

				@Override
				public boolean hasNext() {
					if (hasElement != null) {
						return hasElement.booleanValue();
					}
					while (current == null || !current.hasNext()) {
						if (factories.hasNext()) {
							current = factories.next().loadKeys().iterator();
						} else {
							current = null;
							hasElement = Boolean.FALSE;
							return false;
						}
					}
					hasElement = Boolean.TRUE;
					return true;
				}

				@Override
				public KeyPair next() {
					if (hasElement == null && !hasNext()
							|| !hasElement.booleanValue()) {
						throw new NoSuchElementException();
					}
					hasElement = null;
					KeyPair result;
					try {
						result = current.next();
					} catch (NoSuchElementException e) {
						result = null;
					}
					return result;
				}

			};
		}

	}
}
