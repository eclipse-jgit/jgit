/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008-2009, Shawn O. Pearce <spearce@spearce.org>
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

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * The base class for transports that use SSH protocol. This class allows
 * customizing SSH connection settings.
 */
public abstract class SshTransport extends TcpTransport {

	private SshSessionFactory sch;

	/**
	 * The open SSH session
	 */
	protected Session sock;

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
	 * Set SSH session factory instead of the default one for this instance of
	 * the transport.
	 *
	 * @param factory
	 *            a factory to set, must not be null
	 * @throws IllegalStateException
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
	 * @return the SSH session factory that will be used for creating SSH sessions
	 */
	public SshSessionFactory getSshSessionFactory() {
		return sch;
	}


	/**
	 * Initialize SSH session
	 *
	 * @throws TransportException
	 *             in case of error with opening SSH session
	 */
	protected void initSession() throws TransportException {
		if (sock != null)
			return;

		final int tms = getTimeout() > 0 ? getTimeout() * 1000 : 0;
		final String user = uri.getUser();
		final String pass = uri.getPass();
		final String host = uri.getHost();
		final int port = uri.getPort();
		try {
			sock = sch.getSession(user, pass, host, port, local.getFS());
			if (!sock.isConnected())
				sock.connect(tms);
		} catch (JSchException je) {
			final Throwable c = je.getCause();
			if (c instanceof UnknownHostException)
				throw new TransportException(uri, JGitText.get().unknownHost);
			if (c instanceof ConnectException)
				throw new TransportException(uri, c.getMessage());
			throw new TransportException(uri, je.getMessage(), je);
		}
	}

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
