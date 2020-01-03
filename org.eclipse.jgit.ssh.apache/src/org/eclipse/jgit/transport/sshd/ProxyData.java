/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A DTO encapsulating the data needed to connect through a proxy server.
 *
 * @since 5.2
 */
public class ProxyData {

	private final @NonNull Proxy proxy;

	private final String proxyUser;

	private final char[] proxyPassword;

	/**
	 * Creates a new {@link ProxyData} instance without user name or password.
	 *
	 * @param proxy
	 *            to connect to; must not be {@link java.net.Proxy.Type#DIRECT}
	 *            and must have an {@link InetSocketAddress}.
	 */
	public ProxyData(@NonNull Proxy proxy) {
		this(proxy, null, null);
	}

	/**
	 * Creates a new {@link ProxyData} instance.
	 *
	 * @param proxy
	 *            to connect to; must not be {@link java.net.Proxy.Type#DIRECT}
	 *            and must have an {@link InetSocketAddress}.
	 * @param proxyUser
	 *            to use for log-in to the proxy, may be {@code null}
	 * @param proxyPassword
	 *            to use for log-in to the proxy, may be {@code null}
	 */
	public ProxyData(@NonNull Proxy proxy, String proxyUser,
			char[] proxyPassword) {
		this.proxy = proxy;
		if (!(proxy.address() instanceof InetSocketAddress)) {
			// Internal error not translated
			throw new IllegalArgumentException(
					"Proxy does not have an InetSocketAddress"); //$NON-NLS-1$
		}
		this.proxyUser = proxyUser;
		this.proxyPassword = proxyPassword == null ? null
				: proxyPassword.clone();
	}

	/**
	 * Obtains the remote {@link InetSocketAddress} of the proxy to connect to.
	 *
	 * @return the remote address of the proxy
	 */
	@NonNull
	public Proxy getProxy() {
		return proxy;
	}

	/**
	 * Obtains the user to log in at the proxy with.
	 *
	 * @return the user name, or {@code null} if none
	 */
	public String getUser() {
		return proxyUser;
	}

	/**
	 * Obtains a copy of the internally stored password.
	 *
	 * @return the password or {@code null} if none
	 */
	public char[] getPassword() {
		return proxyPassword == null ? null : proxyPassword.clone();
	}

	/**
	 * Clears the stored password, if any.
	 */
	public void clearPassword() {
		if (proxyPassword != null) {
			Arrays.fill(proxyPassword, '\000');
		}
	}

}