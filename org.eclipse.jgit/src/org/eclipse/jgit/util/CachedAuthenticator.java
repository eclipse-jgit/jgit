/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.util;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract authenticator which remembers prior authentications.
 */
public abstract class CachedAuthenticator extends Authenticator {
	private static final Collection<CachedAuthentication> cached = new CopyOnWriteArrayList<>();

	/**
	 * Add a cached authentication for future use.
	 *
	 * @param ca
	 *            the information we should remember.
	 */
	public static void add(CachedAuthentication ca) {
		cached.add(ca);
	}

	/** {@inheritDoc} */
	@Override
	protected final PasswordAuthentication getPasswordAuthentication() {
		final String host = getRequestingHost();
		final int port = getRequestingPort();
		for (final CachedAuthentication ca : cached) {
			if (ca.host.equals(host) && ca.port == port)
				return ca.toPasswordAuthentication();
		}
		PasswordAuthentication pa = promptPasswordAuthentication();
		if (pa != null) {
			CachedAuthentication ca = new CachedAuthentication(host, port, pa
					.getUserName(), new String(pa.getPassword()));
			add(ca);
			return ca.toPasswordAuthentication();
		}
		return null;
	}

	/**
	 * Prompt for and request authentication from the end-user.
	 *
	 * @return the authentication data; null if the user canceled the request
	 *         and does not want to continue.
	 */
	protected abstract PasswordAuthentication promptPasswordAuthentication();

	/** Authentication data to remember and reuse. */
	public static class CachedAuthentication {
		final String host;

		final int port;

		final String user;

		final String pass;

		/**
		 * Create a new cached authentication.
		 *
		 * @param aHost
		 *            system this is for.
		 * @param aPort
		 *            port number of the service.
		 * @param aUser
		 *            username at the service.
		 * @param aPass
		 *            password at the service.
		 */
		public CachedAuthentication(final String aHost, final int aPort,
				final String aUser, final String aPass) {
			host = aHost;
			port = aPort;
			user = aUser;
			pass = aPass;
		}

		PasswordAuthentication toPasswordAuthentication() {
			return new PasswordAuthentication(user, pass.toCharArray());
		}
	}
}
