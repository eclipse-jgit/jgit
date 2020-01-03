/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		for (CachedAuthentication ca : cached) {
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
