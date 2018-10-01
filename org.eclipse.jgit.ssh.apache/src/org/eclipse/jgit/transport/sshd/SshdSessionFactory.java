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
package org.eclipse.jgit.transport.sshd;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.UserAuth;
import org.apache.sshd.client.auth.keyboard.UserAuthKeyboardInteractiveFactory;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.transport.sshd.CachingKeyPairProvider;
import org.eclipse.jgit.internal.transport.sshd.JGitPublicKeyAuthFactory;
import org.eclipse.jgit.internal.transport.sshd.JGitSshClient;
import org.eclipse.jgit.internal.transport.sshd.JGitUserInteraction;
import org.eclipse.jgit.internal.transport.sshd.OpenSshServerKeyVerifier;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

/**
 * A {@link SshSessionFactory} that uses Apache MINA sshd.
 *
 * @since 5.2
 */
public class SshdSessionFactory extends SshSessionFactory implements Closeable {

	private final AtomicBoolean closing = new AtomicBoolean();

	private final Set<SshdSession> sessions = new HashSet<>();

	private final Map<Tuple, HostConfigEntryResolver> defaultHostConfigEntryResolver = new ConcurrentHashMap<>();

	private final Map<Tuple, ServerKeyVerifier> defaultServerKeyVerifier = new ConcurrentHashMap<>();

	private final Map<Tuple, FileKeyPairProvider> defaultKeys = new ConcurrentHashMap<>();

	private final KeyCache keyCache;

	private File sshDirectory;

	private File homeDirectory;

	/**
	 * Creates a new {@link SshdSessionFactory} without {@link KeyCache}.
	 */
	public SshdSessionFactory() {
		this(null);
	}

	/**
	 * Creates a new {@link SshdSessionFactory} using the given
	 * {@link KeyCache}. The {@code keyCache} is used for all sessions created
	 * through this session factory; cached keys are destroyed when the session
	 * factory is {@link #close() closed}.
	 * <p>
	 * Caching ssh keys in memory for an extended period of time is generally
	 * considered bad practice, but there may be circumstances where using a
	 * {@link KeyCache} is still the right choice, for instance to avoid that a
	 * user gets prompted several times for the same password for the same key.
	 * In general, however, it is preferable <em>not</em> to use a key cache but
	 * to use a {@link #createFilePasswordProvider(CredentialsProvider)
	 * FilePasswordProvider} that has access to some secure storage and can save
	 * and retrieve passwords from there without user interaction. Another
	 * approach is to use an ssh agent.
	 * </p>
	 * <p>
	 * Note that the underlying ssh library (Apache MINA sshd) may or may not
	 * keep ssh keys in memory for unspecified periods of time irrespective of
	 * the use of a {@link KeyCache}.
	 * </p>
	 *
	 * @param keyCache
	 *            {@link KeyCache} to use for caching ssh keys, or {@code null}
	 *            to not use a key cache
	 */
	public SshdSessionFactory(KeyCache keyCache) {
		super();
		this.keyCache = keyCache;
	}

	/** A simple general map key. */
	private static final class Tuple {
		private Object[] objects;

		public Tuple(Object... objects) {
			this.objects = objects;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj != null && obj.getClass() == Tuple.class) {
				Tuple other = (Tuple) obj;
				return Arrays.equals(objects, other.objects);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(objects);
		}
	}

	// We can't really use a single client. Clients need to be stopped
	// properly, and we don't really know when to do that. Instead we use
	// a dedicated SshClient instance per session. We need a bit of caching to
	// avoid re-loading the ssh config and keys repeatedly.

	@Override
	public SshdSession getSession(URIish uri,
			CredentialsProvider credentialsProvider, FS fs, int tms)
			throws TransportException {
		SshdSession session = null;
		try {
			session = new SshdSession(uri, () -> {
				File home = getHomeDirectory();
				if (home == null) {
					// Always use the detected filesystem for the user home!
					// It makes no sense to have different "user home"
					// directories depending on what file system a repository
					// is.
					home = FS.DETECTED.userHome();
				}
				File sshDir = getSshDirectory();
				if (sshDir == null) {
					sshDir = new File(home, SshConstants.SSH_DIR);
				}
				HostConfigEntryResolver configFile = getHostConfigEntryResolver(
						home, sshDir);
				KeyPairProvider defaultKeysProvider = getDefaultKeysProvider(
						sshDir);
				SshClient client = ClientBuilder.builder()
						.factory(JGitSshClient::new)
						.filePasswordProvider(
								createFilePasswordProvider(credentialsProvider))
						.hostConfigEntryResolver(configFile)
						.serverKeyVerifier(getServerKeyVerifier(home, sshDir))
						.compressionFactories(
								new ArrayList<>(BuiltinCompressions.VALUES))
						.build();
				client.setUserInteraction(
						new JGitUserInteraction(credentialsProvider));
				client.setUserAuthFactories(getUserAuthFactories());
				client.setKeyPairProvider(defaultKeysProvider);
				// JGit-specific things:
				JGitSshClient jgitClient = (JGitSshClient) client;
				jgitClient.setKeyCache(getKeyCache());
				jgitClient.setCredentialsProvider(credentialsProvider);
				String defaultAuths = getDefaultPreferredAuthentications();
				if (defaultAuths != null) {
					jgitClient.setAttribute(
							JGitSshClient.PREFERRED_AUTHENTICATIONS,
							defaultAuths);
				}
				// Other things?
				return client;
			});
			session.addCloseListener(s -> unregister(s));
			register(session);
			session.connect(Duration.ofMillis(tms));
			return session;
		} catch (Exception e) {
			unregister(session);
			throw new TransportException(uri, e.getMessage(), e);
		}
	}

	@Override
	public void close() {
		closing.set(true);
		boolean cleanKeys = false;
		synchronized (this) {
			cleanKeys = sessions.isEmpty();
		}
		if (cleanKeys) {
			KeyCache cache = getKeyCache();
			if (cache != null) {
				cache.close();
			}
		}
	}

	private void register(SshdSession newSession) throws IOException {
		if (newSession == null) {
			return;
		}
		if (closing.get()) {
			throw new IOException(SshdText.get().sshClosingDown);
		}
		synchronized (this) {
			sessions.add(newSession);
		}
	}

	private void unregister(SshdSession oldSession) {
		boolean cleanKeys = false;
		synchronized (this) {
			sessions.remove(oldSession);
			cleanKeys = closing.get() && sessions.isEmpty();
		}
		if (cleanKeys) {
			KeyCache cache = getKeyCache();
			if (cache != null) {
				cache.close();
			}
		}
	}

	/**
	 * Set a global directory to use as the user's home directory
	 *
	 * @param homeDir
	 *            to use
	 */
	public void setHomeDirectory(@NonNull File homeDir) {
		if (homeDir.isAbsolute()) {
			homeDirectory = homeDir;
		} else {
			homeDirectory = homeDir.getAbsoluteFile();
		}
	}

	/**
	 * Retrieves the global user home directory
	 *
	 * @return the directory, or {@code null} if not set
	 */
	public File getHomeDirectory() {
		return homeDirectory;
	}

	/**
	 * Set a global directory to use as the .ssh directory
	 *
	 * @param sshDir
	 *            to use
	 */
	public void setSshDirectory(@NonNull File sshDir) {
		if (sshDir.isAbsolute()) {
			sshDirectory = sshDir;
		} else {
			sshDirectory = sshDir.getAbsoluteFile();
		}
	}

	/**
	 * Retrieves the global .ssh directory
	 *
	 * @return the directory, or {@code null} if not set
	 */
	public File getSshDirectory() {
		return sshDirectory;
	}

	/**
	 * Obtain a {@link HostConfigEntryResolver} to read the ssh config file and
	 * to determine host entries for connections.
	 *
	 * @param homeDir
	 *            home directory to use for ~ replacement
	 * @param sshDir
	 *            to use for looking for the config file
	 * @return the resolver
	 */
	@NonNull
	protected HostConfigEntryResolver getHostConfigEntryResolver(
			@NonNull File homeDir, @NonNull File sshDir) {
		return defaultHostConfigEntryResolver.computeIfAbsent(
				new Tuple(homeDir, sshDir),
				t -> new JGitSshConfig(homeDir,
						new File(sshDir, SshConstants.CONFIG),
						getLocalUserName()));
	}

	/**
	 * Obtain a {@link ServerKeyVerifier} to read known_hosts files and to
	 * verify server host keys. The default implementation returns a
	 * {@link ServerKeyVerifier} that recognizes the two openssh standard files
	 * {@code ~/.ssh/known_hosts} and {@code ~/.ssh/known_hosts2} as well as any
	 * files configured via the {@code UserKnownHostsFile} option in the ssh
	 * config file.
	 *
	 * @param homeDir
	 *            home directory to use for ~ replacement
	 * @param sshDir
	 *            representing ~/.ssh/
	 * @return the resolver
	 */
	@NonNull
	protected ServerKeyVerifier getServerKeyVerifier(@NonNull File homeDir,
			@NonNull File sshDir) {
		return defaultServerKeyVerifier.computeIfAbsent(
				new Tuple(homeDir, sshDir),
				t -> new OpenSshServerKeyVerifier(true,
						Arrays.asList(
								new File(sshDir, SshConstants.KNOWN_HOSTS),
								new File(sshDir,
										SshConstants.KNOWN_HOSTS + '2'))));
	}

	/**
	 * Determines a {@link KeyPairProvider} to use to load the default keys.
	 *
	 * @param sshDir
	 *            to look in for keys
	 * @return the {@link KeyPairProvider}
	 */
	@NonNull
	protected KeyPairProvider getDefaultKeysProvider(@NonNull File sshDir) {
		return defaultKeys.computeIfAbsent(new Tuple(sshDir),
				t -> new CachingKeyPairProvider(getDefaultIdentities(sshDir),
						getKeyCache()));
	}

	/**
	 * Gets a list of default identities, i.e., private key files that shall
	 * always be tried for public key authentication. Typically those are
	 * ~/.ssh/id_dsa, ~/.ssh/id_rsa, and so on. The default implementation
	 * returns the files defined in {@link SshConstants#DEFAULT_IDENTITIES}.
	 *
	 * @param sshDir
	 *            the directory that represents ~/.ssh/
	 * @return a possibly empty list of paths containing default identities
	 *         (private keys)
	 */
	@NonNull
	protected List<Path> getDefaultIdentities(@NonNull File sshDir) {
		return Arrays
				.asList(SshConstants.DEFAULT_IDENTITIES).stream()
				.map(s -> new File(sshDir, s).toPath()).filter(Files::exists)
				.collect(Collectors.toList());
	}

	/**
	 * Obtains the {@link KeyCache} to use to cache loaded keys.
	 *
	 * @return the {@link KeyCache}, or {@code null} if none.
	 */
	protected final KeyCache getKeyCache() {
		return keyCache;
	}

	/**
	 * Creates a {@link FilePasswordProvider} for a new session.
	 *
	 * @param provider
	 *            the {@link CredentialsProvider} to delegate for for user
	 *            interactions
	 * @return a new {@link FilePasswordProvider}
	 */
	@NonNull
	protected FilePasswordProvider createFilePasswordProvider(
			CredentialsProvider provider) {
		return new IdentityPasswordProvider(provider);
	}

	/**
	 * Gets the user authentication mechanisms (or rather, factories for them).
	 * By default this returns public-key, keyboard-interactive, and password,
	 * in that order. (I.e., we don't do gssapi-with-mic or hostbased (yet)).
	 *
	 * @return the non-empty list of factories.
	 */
	@NonNull
	protected List<NamedFactory<UserAuth>> getUserAuthFactories() {
		return Collections.unmodifiableList(
				Arrays.asList(JGitPublicKeyAuthFactory.INSTANCE,
						UserAuthKeyboardInteractiveFactory.INSTANCE,
						UserAuthPasswordFactory.INSTANCE));
	}

	/**
	 * Gets the list of default preferred authentication mechanisms. If
	 * {@code null} is returned the openssh default list will be in effect. If
	 * the ssh config defines {@code PreferredAuthentications} the value from
	 * the ssh config takes precedence.
	 *
	 * @return a comma-separated list of algorithm names, or {@code null} if
	 *         none
	 */
	protected String getDefaultPreferredAuthentications() {
		return null;
	}
}
