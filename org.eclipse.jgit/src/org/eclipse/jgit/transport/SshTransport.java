/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008-2009, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

/**
 * The base class for transports that use SSH protocol. This class allows
 * customizing SSH connection settings.
 */
public abstract class SshTransport extends TcpTransport {

	private SshSessionFactory sch;

	/**
	 * The open SSH session
	 */
	private RemoteSession sock;

	/**
	 * Create a new transport instance.
	 *
	 * @param local
	 *            the repository this instance will fetch into, or push out of.
	 *            This must be the repository passed to
	 *            {@link #open(Repository, URIish)}.
	 * @param uri
	 *            the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(Repository, URIish)}.
	 */
	protected SshTransport(Repository local, URIish uri) {
		super(local, uri);
		sch = SshSessionFactory.getInstance();
	}

	/**
	 * Create a new transport instance without a local repository.
	 *
	 * @param uri the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(URIish)}.
	 * @since 3.5
	 */
	protected SshTransport(URIish uri) {
		super(uri);
		sch = SshSessionFactory.getInstance();
	}

	/**
	 * Set SSH session factory instead of the default one for this instance of
	 * the transport.
	 *
	 * @param factory
	 *            a factory to set, must not be null
	 * @throws java.lang.IllegalStateException
	 *             if session has been already created.
	 */
	public void setSshSessionFactory(SshSessionFactory factory) {
		if (factory == null)
			throw new NullPointerException(JGitText.get().theFactoryMustNotBeNull);
		if (sock != null)
			throw new IllegalStateException(
					JGitText.get().anSSHSessionHasBeenAlreadyCreated);
		sch = factory;
	}

	/**
	 * Get the SSH session factory
	 *
	 * @return the SSH session factory that will be used for creating SSH
	 *         sessions
	 */
	public SshSessionFactory getSshSessionFactory() {
		return sch;
	}

	/**
	 * Get the default SSH session
	 *
	 * @return a remote session
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             in case of error with opening SSH session
	 */
	protected RemoteSession getSession() throws TransportException {
		if (sock != null)
			return sock;

		final int tms = getTimeout() > 0 ? getTimeout() * 1000 : 0;

		final FS fs = local == null ? FS.detect() : local.getFS();

		sock = sch
				.getSession(uri, getCredentialsProvider(), fs, tms);
		return sock;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (sock != null) {
			try {
				sch.releaseSession(sock);
			} finally {
				sock = null;
			}
		}
	}
}
