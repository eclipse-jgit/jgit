/*
 * Copyright (C) 2018, Sasa Zivkov <sasa.zivkov@sap.com>
 * Copyright (C) 2016, Mark Ingram <markdingram@gmail.com>
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ConfigRepository;
import com.jcraft.jsch.ConfigRepository.Config;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * The base session factory that loads known hosts and private keys from
 * <code>$HOME/.ssh</code>.
 * <p>
 * This is the default implementation used by JGit and provides most of the
 * compatibility necessary to match OpenSSH, a popular implementation of SSH
 * used by C Git.
 * <p>
 * The factory does not provide UI behavior. Override the method
 * {@link #configure(org.eclipse.jgit.transport.OpenSshConfig.Host, Session)} to
 * supply appropriate {@link com.jcraft.jsch.UserInfo} to the session.
 */
public abstract class JschConfigSessionFactory extends SshSessionFactory {

	private static final Logger LOG = LoggerFactory
			.getLogger(JschConfigSessionFactory.class);

	/**
	 * We use different Jsch instances for hosts that have an IdentityFile
	 * configured in ~/.ssh/config. Jsch by default would cache decrypted keys
	 * only per session, which results in repeated password prompts. Using
	 * different Jsch instances, we can cache the keys on these instances so
	 * that they will be re-used for successive sessions, and thus the user is
	 * prompted for a key password only once while Eclipse runs.
	 */
	private final Map<String, JSch> byIdentityFile = new HashMap<>();

	private JSch defaultJSch;

	private OpenSshConfig config;

	/** {@inheritDoc} */
	@Override
	public synchronized RemoteSession getSession(URIish uri,
			CredentialsProvider credentialsProvider, FS fs, int tms)
			throws TransportException {

		String user = uri.getUser();
		final String pass = uri.getPass();
		String host = uri.getHost();
		int port = uri.getPort();

		try {
			if (config == null)
				config = OpenSshConfig.get(fs);

			final OpenSshConfig.Host hc = config.lookup(host);
			if (port <= 0)
				port = hc.getPort();
			if (user == null)
				user = hc.getUser();

			Session session = createSession(credentialsProvider, fs, user,
					pass, host, port, hc);

			int retries = 0;
			while (!session.isConnected()) {
				try {
					retries++;
					session.connect(tms);
				} catch (JSchException e) {
					session.disconnect();
					session = null;
					// Make sure our known_hosts is not outdated
					knownHosts(getJSch(hc, fs), fs);

					if (isAuthenticationCanceled(e)) {
						throw e;
					} else if (isAuthenticationFailed(e)
							&& credentialsProvider != null) {
						// if authentication failed maybe credentials changed at
						// the remote end therefore reset credentials and retry
						if (retries < 3) {
							credentialsProvider.reset(uri);
							session = createSession(credentialsProvider, fs,
									user, pass, host, port, hc);
						} else
							throw e;
					} else if (retries >= hc.getConnectionAttempts()) {
						throw e;
					} else {
						try {
							Thread.sleep(1000);
							session = createSession(credentialsProvider, fs,
									user, pass, host, port, hc);
						} catch (InterruptedException e1) {
							throw new TransportException(
									JGitText.get().transportSSHRetryInterrupt,
									e1);
						}
					}
				}
			}

			return new JschSession(session, uri);

		} catch (JSchException je) {
			final Throwable c = je.getCause();
			if (c instanceof UnknownHostException) {
				throw new TransportException(uri, JGitText.get().unknownHost,
						je);
			}
			if (c instanceof ConnectException) {
				throw new TransportException(uri, c.getMessage(), je);
			}
			throw new TransportException(uri, je.getMessage(), je);
		}

	}

	private static boolean isAuthenticationFailed(JSchException e) {
		return e.getCause() == null && e.getMessage().equals("Auth fail"); //$NON-NLS-1$
	}

	private static boolean isAuthenticationCanceled(JSchException e) {
		return e.getCause() == null && e.getMessage().equals("Auth cancel"); //$NON-NLS-1$
	}

	// Package visibility for tests
	Session createSession(CredentialsProvider credentialsProvider,
			FS fs, String user, final String pass, String host, int port,
			final OpenSshConfig.Host hc) throws JSchException {
		final Session session = createSession(hc, user, host, port, fs);
		// Jsch will have overridden the explicit user by the one from the SSH
		// config file...
		setUserName(session, user);
		// Jsch will also have overridden the port.
		if (port > 0 && port != session.getPort()) {
			session.setPort(port);
		}
		// We retry already in getSession() method. JSch must not retry
		// on its own.
		session.setConfig("MaxAuthTries", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		if (pass != null)
			session.setPassword(pass);
		final String strictHostKeyCheckingPolicy = hc
				.getStrictHostKeyChecking();
		if (strictHostKeyCheckingPolicy != null)
			session.setConfig("StrictHostKeyChecking", //$NON-NLS-1$
					strictHostKeyCheckingPolicy);
		final String pauth = hc.getPreferredAuthentications();
		if (pauth != null)
			session.setConfig("PreferredAuthentications", pauth); //$NON-NLS-1$
		if (credentialsProvider != null
				&& (!hc.isBatchMode() || !credentialsProvider.isInteractive())) {
			session.setUserInfo(new CredentialsProviderUserInfo(session,
					credentialsProvider));
		}
		safeConfig(session, hc.getConfig());
		if (hc.getConfig().getValue("HostKeyAlgorithms") == null) { //$NON-NLS-1$
			setPreferredKeyTypesOrder(session);
		}
		configure(hc, session);
		return session;
	}

	private void safeConfig(Session session, Config cfg) {
		// Ensure that Jsch checks all configured algorithms, not just its
		// built-in ones. Otherwise it may propose an algorithm for which it
		// doesn't have an implementation, and then run into an NPE if that
		// algorithm ends up being chosen.
		copyConfigValueToSession(session, cfg, "Ciphers", "CheckCiphers"); //$NON-NLS-1$ //$NON-NLS-2$
		copyConfigValueToSession(session, cfg, "KexAlgorithms", "CheckKexes"); //$NON-NLS-1$ //$NON-NLS-2$
		copyConfigValueToSession(session, cfg, "HostKeyAlgorithms", //$NON-NLS-1$
				"CheckSignatures"); //$NON-NLS-1$
	}

	private static void setPreferredKeyTypesOrder(Session session) {
		HostKeyRepository hkr = session.getHostKeyRepository();
		HostKey[] hostKeys = hkr.getHostKey(hostName(session), null);

		if (hostKeys == null) {
			return;
		}

		List<String> known = Stream.of(hostKeys)
				.map(HostKey::getType)
				.collect(toList());

		if (!known.isEmpty()) {
			String serverHostKey = "server_host_key"; //$NON-NLS-1$
			String current = session.getConfig(serverHostKey);
			if (current == null) {
				session.setConfig(serverHostKey, String.join(",", known)); //$NON-NLS-1$
				return;
			}

			String knownFirst = Stream.concat(
							known.stream(),
							Stream.of(current.split(",")) //$NON-NLS-1$
									.filter(s -> !known.contains(s)))
					.collect(joining(",")); //$NON-NLS-1$
			session.setConfig(serverHostKey, knownFirst);
		}
	}

	private static String hostName(Session s) {
		if (s.getPort() == SshConstants.SSH_DEFAULT_PORT) {
			return s.getHost();
		}
		return String.format("[%s]:%d", s.getHost(), //$NON-NLS-1$
				Integer.valueOf(s.getPort()));
	}

	private void copyConfigValueToSession(Session session, Config cfg,
			String from, String to) {
		String value = cfg.getValue(from);
		if (value != null) {
			session.setConfig(to, value);
		}
	}

	private void setUserName(Session session, String userName) {
		// Jsch 0.1.54 picks up the user name from the ssh config, even if an
		// explicit user name was given! We must correct that if ~/.ssh/config
		// has a different user name.
		if (userName == null || userName.isEmpty()
				|| userName.equals(session.getUserName())) {
			return;
		}
		try {
			Class<?>[] parameterTypes = { String.class };
			Method method = Session.class.getDeclaredMethod("setUserName", //$NON-NLS-1$
					parameterTypes);
			method.setAccessible(true);
			method.invoke(session, userName);
		} catch (NullPointerException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			LOG.error(MessageFormat.format(JGitText.get().sshUserNameError,
					userName, session.getUserName()), e);
		}
	}

	/**
	 * Create a new remote session for the requested address.
	 *
	 * @param hc
	 *            host configuration
	 * @param user
	 *            login to authenticate as.
	 * @param host
	 *            server name to connect to.
	 * @param port
	 *            port number of the SSH daemon (typically 22).
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @return new session instance, but otherwise unconfigured.
	 * @throws com.jcraft.jsch.JSchException
	 *             the session could not be created.
	 */
	protected Session createSession(final OpenSshConfig.Host hc,
			final String user, final String host, final int port, FS fs)
			throws JSchException {
		return getJSch(hc, fs).getSession(user, host, port);
	}

	/**
	 * Provide additional configuration for the JSch instance. This method could
	 * be overridden to supply a preferred
	 * {@link com.jcraft.jsch.IdentityRepository}.
	 *
	 * @param jsch
	 *            jsch instance
	 * @since 4.5
	 */
	protected void configureJSch(JSch jsch) {
		// No additional configuration required.
	}

	/**
	 * Provide additional configuration for the session based on the host
	 * information. This method could be used to supply
	 * {@link com.jcraft.jsch.UserInfo}.
	 *
	 * @param hc
	 *            host configuration
	 * @param session
	 *            session to configure
	 */
	protected abstract void configure(OpenSshConfig.Host hc, Session session);

	/**
	 * Obtain the JSch used to create new sessions.
	 *
	 * @param hc
	 *            host configuration
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @return the JSch instance to use.
	 * @throws com.jcraft.jsch.JSchException
	 *             the user configuration could not be created.
	 */
	protected JSch getJSch(OpenSshConfig.Host hc, FS fs) throws JSchException {
		if (defaultJSch == null) {
			defaultJSch = createDefaultJSch(fs);
			if (defaultJSch.getConfigRepository() == null) {
				defaultJSch.setConfigRepository(
						new JschBugFixingConfigRepository(config));
			}
			for (Object name : defaultJSch.getIdentityNames())
				byIdentityFile.put((String) name, defaultJSch);
		}

		final File identityFile = hc.getIdentityFile();
		if (identityFile == null)
			return defaultJSch;

		final String identityKey = identityFile.getAbsolutePath();
		JSch jsch = byIdentityFile.get(identityKey);
		if (jsch == null) {
			jsch = new JSch();
			configureJSch(jsch);
			if (jsch.getConfigRepository() == null) {
				jsch.setConfigRepository(defaultJSch.getConfigRepository());
			}
			jsch.setHostKeyRepository(defaultJSch.getHostKeyRepository());
			jsch.addIdentity(identityKey);
			byIdentityFile.put(identityKey, jsch);
		}
		return jsch;
	}

	/**
	 * Create default instance of jsch
	 *
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return the new default JSch implementation.
	 * @throws com.jcraft.jsch.JSchException
	 *             known host keys cannot be loaded.
	 */
	protected JSch createDefaultJSch(FS fs) throws JSchException {
		final JSch jsch = new JSch();
		JSch.setConfig("ssh-rsa", JSch.getConfig("signature.rsa")); //$NON-NLS-1$ //$NON-NLS-2$
		JSch.setConfig("ssh-dss", JSch.getConfig("signature.dss")); //$NON-NLS-1$ //$NON-NLS-2$
		configureJSch(jsch);
		knownHosts(jsch, fs);
		identities(jsch, fs);
		return jsch;
	}

	private static void knownHosts(JSch sch, FS fs) throws JSchException {
		final File home = fs.userHome();
		if (home == null)
			return;
		final File known_hosts = new File(new File(home, ".ssh"), "known_hosts"); //$NON-NLS-1$ //$NON-NLS-2$
		try (FileInputStream in = new FileInputStream(known_hosts)) {
			sch.setKnownHosts(in);
		} catch (FileNotFoundException none) {
			// Oh well. They don't have a known hosts in home.
		} catch (IOException err) {
			// Oh well. They don't have a known hosts in home.
		}
	}

	private static void identities(JSch sch, FS fs) {
		final File home = fs.userHome();
		if (home == null)
			return;
		final File sshdir = new File(home, ".ssh"); //$NON-NLS-1$
		if (sshdir.isDirectory()) {
			loadIdentity(sch, new File(sshdir, "identity")); //$NON-NLS-1$
			loadIdentity(sch, new File(sshdir, "id_rsa")); //$NON-NLS-1$
			loadIdentity(sch, new File(sshdir, "id_dsa")); //$NON-NLS-1$
		}
	}

	private static void loadIdentity(JSch sch, File priv) {
		if (priv.isFile()) {
			try {
				sch.addIdentity(priv.getAbsolutePath());
			} catch (JSchException e) {
				// Instead, pretend the key doesn't exist.
			}
		}
	}

	private static class JschBugFixingConfigRepository
			implements ConfigRepository {

		private final ConfigRepository base;

		public JschBugFixingConfigRepository(ConfigRepository base) {
			this.base = base;
		}

		@Override
		public Config getConfig(String host) {
			return new JschBugFixingConfig(base.getConfig(host));
		}

		/**
		 * A {@link com.jcraft.jsch.ConfigRepository.Config} that transforms
		 * some values from the config file into the format Jsch 0.1.54 expects.
		 * This is a work-around for bugs in Jsch.
		 * <p>
		 * Additionally, this config hides the IdentityFile config entries from
		 * Jsch; we manage those ourselves. Otherwise Jsch would cache passwords
		 * (or rather, decrypted keys) only for a single session, resulting in
		 * multiple password prompts for user operations that use several Jsch
		 * sessions.
		 */
		private static class JschBugFixingConfig implements Config {

			private static final String[] NO_IDENTITIES = {};

			private final Config real;

			public JschBugFixingConfig(Config delegate) {
				real = delegate;
			}

			@Override
			public String getHostname() {
				return real.getHostname();
			}

			@Override
			public String getUser() {
				return real.getUser();
			}

			@Override
			public int getPort() {
				return real.getPort();
			}

			@Override
			public String getValue(String key) {
				String k = key.toUpperCase(Locale.ROOT);
				if ("IDENTITYFILE".equals(k)) { //$NON-NLS-1$
					return null;
				}
				String result = real.getValue(key);
				if (result != null) {
					if ("SERVERALIVEINTERVAL".equals(k) //$NON-NLS-1$
							|| "CONNECTTIMEOUT".equals(k)) { //$NON-NLS-1$
						// These values are in seconds. Jsch 0.1.54 passes them
						// on as is to java.net.Socket.setSoTimeout(), which
						// expects milliseconds. So convert here to
						// milliseconds.
						try {
							int timeout = Integer.parseInt(result);
							result = Long.toString(
									TimeUnit.SECONDS.toMillis(timeout));
						} catch (NumberFormatException e) {
							// Ignore
						}
					}
				}
				return result;
			}

			@Override
			public String[] getValues(String key) {
				String k = key.toUpperCase(Locale.ROOT);
				if ("IDENTITYFILE".equals(k)) { //$NON-NLS-1$
					return NO_IDENTITIES;
				}
				return real.getValues(key);
			}
		}
	}

	/**
	 * Set the {@link OpenSshConfig} to use. Intended for use in tests.
	 *
	 * @param config
	 *            to use
	 */
	synchronized void setConfig(OpenSshConfig config) {
		this.config = config;
	}
}
