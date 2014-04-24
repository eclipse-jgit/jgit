/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * The base session factory that loads known hosts and private keys from
 * <code>$HOME/.ssh</code>.
 * <p>
 * This is the default implementation used by JGit and provides most of the
 * compatibility necessary to match OpenSSH, a popular implementation of SSH
 * used by C Git.
 * <p>
 * The factory does not provide UI behavior. Override the method
 * {@link #configure(org.eclipse.jgit.transport.OpenSshConfig.Host, Session)}
 * to supply appropriate {@link UserInfo} to the session.
 */
public abstract class JschConfigSessionFactory extends SshSessionFactory {
	private final Map<String, JSch> byIdentityFile = new HashMap<String, JSch>();

	private JSch defaultJSch;

	private OpenSshConfig config;

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
			host = hc.getHostName();
			if (port <= 0)
				port = hc.getPort();
			if (user == null)
				user = hc.getUser();

			Session session = createSession(credentialsProvider, fs, user,
					pass, host, port, hc);

			int retries = 0;
			while (!session.isConnected() && retries < 3) {
				try {
					retries++;
					session.connect(tms);
				} catch (JSchException e) {
					session.disconnect();
					session = null;
					// Make sure our known_hosts is not outdated
					knownHosts(getJSch(hc, fs), fs);

					// if authentication failed maybe credentials changed at the
					// remote end therefore reset credentials and retry
					if (credentialsProvider != null && e.getCause() == null
							&& e.getMessage().equals("Auth fail") //$NON-NLS-1$
							&& retries < 3) {
						credentialsProvider.reset(uri);
						session = createSession(credentialsProvider, fs, user,
								pass, host, port, hc);
					} else {
						throw e;
					}
				}
			}

			return new JschSession(session, uri);

		} catch (JSchException je) {
			final Throwable c = je.getCause();
			if (c instanceof UnknownHostException)
				throw new TransportException(uri, JGitText.get().unknownHost);
			if (c instanceof ConnectException)
				throw new TransportException(uri, c.getMessage());
			throw new TransportException(uri, je.getMessage(), je);
		}

	}

	private Session createSession(CredentialsProvider credentialsProvider,
			FS fs, String user, final String pass, String host, int port,
			final OpenSshConfig.Host hc) throws JSchException {
		final Session session = createSession(hc, user, host, port, fs);
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
		configure(hc, session);
		return session;
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
	 * @throws JSchException
	 *             the session could not be created.
	 */
	protected Session createSession(final OpenSshConfig.Host hc,
			final String user, final String host, final int port, FS fs)
			throws JSchException {
		return getJSch(hc, fs).getSession(user, host, port);
	}

	/**
	 * Provide additional configuration for the session based on the host
	 * information. This method could be used to supply {@link UserInfo}.
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
	 * @throws JSchException
	 *             the user configuration could not be created.
	 */
	protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
		if (defaultJSch == null) {
			defaultJSch = createDefaultJSch(fs);
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
			jsch.setHostKeyRepository(defaultJSch.getHostKeyRepository());
			jsch.addIdentity(identityKey);
			byIdentityFile.put(identityKey, jsch);
		}
		return jsch;
	}

	/**
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @return the new default JSch implementation.
	 * @throws JSchException
	 *             known host keys cannot be loaded.
	 */
	protected JSch createDefaultJSch(FS fs) throws JSchException {
		final JSch jsch = new JSch();
		knownHosts(jsch, fs);
		identities(jsch, fs);
		return jsch;
	}

	private static void knownHosts(final JSch sch, FS fs) throws JSchException {
		final File home = fs.userHome();
		if (home == null)
			return;
		final File known_hosts = new File(new File(home, ".ssh"), "known_hosts"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			final FileInputStream in = new FileInputStream(known_hosts);
			try {
				sch.setKnownHosts(in);
			} finally {
				in.close();
			}
		} catch (FileNotFoundException none) {
			// Oh well. They don't have a known hosts in home.
		} catch (IOException err) {
			// Oh well. They don't have a known hosts in home.
		}
	}

	private static void identities(final JSch sch, FS fs) {
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

	private static void loadIdentity(final JSch sch, final File priv) {
		if (priv.isFile()) {
			try {
				sch.addIdentity(priv.getAbsolutePath());
			} catch (JSchException e) {
				// Instead, pretend the key doesn't exist.
			}
		}
	}
}
