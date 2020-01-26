/*
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
 * <p>
 * A {@link org.eclipse.jgit.transport.RemoteSession} must be returned to the
 * factory that created it. Callers are encouraged to retain the
 * SshSessionFactory for the duration of the period they are using the Session.
 */
public abstract class SshSessionFactory {
	private static SshSessionFactory INSTANCE = loadSshSessionFactory();

	private static SshSessionFactory loadSshSessionFactory() {
		ServiceLoader<SshSessionFactory> loader = ServiceLoader.load(SshSessionFactory.class);
		Iterator<SshSessionFactory> iter = loader.iterator();
		if(iter.hasNext()) {
			return iter.next();
		}
		return null;
	}
	/**
	 * Get the currently configured JVM-wide factory.
	 * <p>
	 * A factory is always available. By default the factory will read from the
	 * user's <code>$HOME/.ssh</code> and assume OpenSSH compatibility.
	 *
	 * @return factory the current factory for this JVM.
	 */
	public static SshSessionFactory getInstance() {
		return INSTANCE;
	}

	/**
	 * Change the JVM-wide factory to a different implementation.
	 *
	 * @param newFactory
	 *            factory for future sessions to be created through. If null the
	 *            default factory will be restored.s
	 */
	public static void setInstance(SshSessionFactory newFactory) {
		if (newFactory != null) {
			INSTANCE = newFactory;
		} else {
			INSTANCE = loadSshSessionFactory();
		}
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
	 * Open (or reuse) a session to a host.
	 * <p>
	 * A reasonable UserInfo that can interact with the end-user (if necessary)
	 * is installed on the returned session by this method.
	 * <p>
	 * The caller must connect the session by invoking <code>connect()</code> if
	 * it has not already been connected.
	 *
	 * @param uri
	 *            URI information about the remote host
	 * @param credentialsProvider
	 *            provider to support authentication, may be null.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param tms
	 *            Timeout value, in milliseconds.
	 * @return a session that can contact the remote host.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the session could not be created.
	 */
	public abstract RemoteSession getSession(URIish uri,
			CredentialsProvider credentialsProvider, FS fs, int tms)
			throws TransportException;

	/**
	 * Close (or recycle) a session to a host.
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
