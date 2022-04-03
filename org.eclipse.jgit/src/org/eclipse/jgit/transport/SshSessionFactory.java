/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2022 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Creates and destroys SSH connections to a remote system.
 * <p>
 * Different implementations of the session factory may be used to control
 * communicating with the end-user as well as reading their personal SSH
 * configuration settings, such as known hosts and private keys.
 * </p>
 * <p>
 * A {@link RemoteSession} must be returned to the factory that created it.
 * Callers are encouraged to retain the SshSessionFactory for the duration of
 * the period they are using the session.
 * </p>
 */
public abstract class SshSessionFactory {

	private static class DefaultFactory {

		private static volatile SshSessionFactory INSTANCE = loadSshSessionFactory();

		private static SshSessionFactory loadSshSessionFactory() {
			ServiceLoader<SshSessionFactory> loader = ServiceLoader
					.load(SshSessionFactory.class);
			Iterator<SshSessionFactory> iter = loader.iterator();
			if (iter.hasNext()) {
				return iter.next();
			}
			return null;
		}

		private DefaultFactory() {
			// No instantiation
		}

		public static SshSessionFactory getInstance() {
			return INSTANCE;
		}

		public static void setInstance(SshSessionFactory newFactory) {
			if (newFactory != null) {
				INSTANCE = newFactory;
			} else {
				INSTANCE = loadSshSessionFactory();
			}
		}
	}

	/**
	 * Gets the currently configured JVM-wide factory.
	 * <p>
	 * By default the factory will read from the user's {@code $HOME/.ssh} and
	 * assume OpenSSH compatibility.
	 * </p>
	 *
	 * @return factory the current factory for this JVM.
	 */
	public static SshSessionFactory getInstance() {
		return DefaultFactory.getInstance();
	}

	/**
	 * Changes the JVM-wide factory to a different implementation.
	 *
	 * @param newFactory
	 *            factory for future sessions to be created through; if
	 *            {@code null} the default factory will be restored.
	 */
	public static void setInstance(SshSessionFactory newFactory) {
		DefaultFactory.setInstance(newFactory);
	}

	/**
	 * Retrieves the local user name as defined by the system property
	 * "user.name".
	 *
	 * @return the user name
	 * @since 5.2
	 */
	public static String getLocalUserName() {
		return AccessController
				.doPrivileged((PrivilegedAction<String>) () -> SystemReader
						.getInstance().getProperty(Constants.OS_USER_NAME_KEY));
	}

	/**
	 * Opens (or reuses) a session to a host. The returned session is connected
	 * and authenticated and is ready for further use.
	 *
	 * @param uri
	 *            URI of the remote host to connect to
	 * @param credentialsProvider
	 *            provider to support authentication, may be {@code null} if no
	 *            user input for authentication is needed
	 * @param fs
	 *            the file system abstraction to use for certain file
	 *            operations, such as reading configuration files
	 * @param tms
	 *            connection timeout for creating the session, in milliseconds
	 * @return a connected and authenticated session for communicating with the
	 *         remote host given by the {@code uri}
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             if the session could not be created
	 */
	public abstract RemoteSession getSession(URIish uri,
			CredentialsProvider credentialsProvider, FS fs, int tms)
			throws TransportException;

	/**
	 * The name of the type of session factory.
	 *
	 * @return the name of the type of session factory.
	 *
	 * @since 5.8
	 */
	public abstract String getType();

	/**
	 * Closes (or recycles) a session to a host.
	 *
	 * @param session
	 *            a session previously obtained from this factory's
	 *            {@link #getSession(URIish, CredentialsProvider, FS, int)}
	 *            method.
	 */
	public void releaseSession(RemoteSession session) {
		session.disconnect();
	}
}
