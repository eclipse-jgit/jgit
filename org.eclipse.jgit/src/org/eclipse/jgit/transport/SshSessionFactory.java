/*
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

import java.security.AccessController;
import java.security.PrivilegedAction;

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
	private static SshSessionFactory INSTANCE = new DefaultSshSessionFactory();

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
		if (newFactory != null)
			INSTANCE = newFactory;
		else
			INSTANCE = new DefaultSshSessionFactory();
	}

	/**
	 * Retrieves the local user name as defined by the system property
	 * "user.name".
	 *
	 * @return the user name
	 * @since 5.2
	 */
	public static String getLocalUserName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			@Override
			public String run() {
				return SystemReader.getInstance()
						.getProperty(Constants.OS_USER_NAME_KEY);
			}
		});
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
