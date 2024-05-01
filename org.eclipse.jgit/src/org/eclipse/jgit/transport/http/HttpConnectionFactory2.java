/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A {@link HttpConnectionFactory} that supports client-side sessions that can
 * maintain state and configure connections.
 *
 * @since 5.11
 */
public interface HttpConnectionFactory2 extends HttpConnectionFactory {

	/**
	 * Creates a new {@link GitSession} instance that can be used with
	 * connections created by this {@link HttpConnectionFactory} instance.
	 *
	 * @return a new {@link GitSession}
	 */
	@NonNull
	GitSession newSession();

	/**
	 * A {@code GitSession} groups the multiple HTTP connections
	 * {@link org.eclipse.jgit.transport.TransportHttp TransportHttp} uses for
	 * the requests it makes during a git fetch or push. A {@code GitSession}
	 * can maintain client-side HTTPS state and can configure individual
	 * connections.
	 */
	interface GitSession {

		/**
		 * Configure a just created {@link HttpConnection}.
		 *
		 * @param connection
		 *            to configure; created by the same
		 *            {@link HttpConnectionFactory} instance
		 * @param sslVerify
		 *            whether SSL is to be verified
		 * @return the configured {@code connection}
		 * @throws IOException
		 *             if the connection cannot be configured
		 * @throws GeneralSecurityException
		 *             if the connection cannot be configured
		 */
		@NonNull
		HttpConnection configure(@NonNull HttpConnection connection,
				boolean sslVerify) throws IOException, GeneralSecurityException;

		/**
		 * Closes the {@link GitSession}, releasing any internal state.
		 */
		void close();
	}
}
