/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.EnumSet;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal API to assist JGit HTTP clients outside this package.
 * <p>
 * <b>Do not call.</b>
 *
 * @since 7.8
 */
public final class InternalHttpClientGlue {
	private static final Logger LOG = LoggerFactory
			.getLogger(InternalHttpClientGlue.class);

	/**
	 * Stateful HTTP authenticator for replayable requests.
	 * <p>
	 * Instances are not thread-safe and are scoped to one request replay
	 * sequence.
	 *
	 * @since 7.8
	 */
	public static final class Authenticator {
		private final URIish uri;

		private final CredentialsProvider credentialsProvider;

		private final Collection<HttpAuthMethod.Type> ignoreTypes = EnumSet
				.noneOf(HttpAuthMethod.Type.class);

		private HttpAuthMethod authMethod;

		private HttpAuthMethod challengedAuthMethod;

		private int authAttempts = 1;

		private Authenticator(URIish uri,
				CredentialsProvider credentialsProvider) {
			this.uri = uri;
			this.credentialsProvider = credentialsProvider;
			this.authMethod = authFromUri(uri);
		}

		/**
		 * Apply the current authentication method to a request.
		 *
		 * @param conn
		 *            connection to configure
		 * @throws IOException
		 *             if applying the authentication method fails
		 */
		public void configureRequest(HttpConnection conn) throws IOException {
			authMethod.configureRequest(conn);
		}

		/**
		 * Handle a HTTP 401 response.
		 *
		 * @param conn
		 *            connection that received a 401 response
		 * @throws TransportException
		 *             if authentication cannot continue
		 */
		public void retryAfterUnauthorized(HttpConnection conn)
				throws TransportException {
			HttpAuthMethod nextMethod = HttpAuthMethod.scanResponse(conn,
					ignoreTypes);
			switch (nextMethod.getType()) {
			case NONE:
				throw new TransportException(uri, MessageFormat.format(
						JGitText.get().authenticationNotSupported,
						conn.getURL()));
			case NEGOTIATE:
				ignoreTypes.add(HttpAuthMethod.Type.NEGOTIATE);
				if (challengedAuthMethod != null) {
					ignoreTypes.add(challengedAuthMethod.getType());
				}
				authAttempts = 1;
				break;
			default:
				ignoreTypes.add(HttpAuthMethod.Type.NEGOTIATE);
				if (challengedAuthMethod == null || challengedAuthMethod
						.getType() != nextMethod.getType()) {
					if (challengedAuthMethod != null) {
						ignoreTypes.add(challengedAuthMethod.getType());
					}
					authAttempts = 1;
				}
				break;
			}
			authMethod = nextMethod;
			challengedAuthMethod = nextMethod;
			if (credentialsProvider == null) {
				throw new TransportException(uri,
						JGitText.get().noCredentialsProvider);
			}
			if (authAttempts > 1) {
				credentialsProvider.reset(uri);
			}
			if (3 < authAttempts
					|| !authMethod.authorize(uri, credentialsProvider)) {
				throw new TransportException(uri, JGitText.get().notAuthorized);
			}
			authAttempts++;
		}

		/**
		 * Handle an I/O failure while an authentication method is active.
		 *
		 * @return {@code true} if another authentication method may be tried
		 */
		public boolean retryOnAuthFailure() {
			if (authMethod.getType() == HttpAuthMethod.Type.NONE) {
				return false;
			}
			ignoreTypes.add(authMethod.getType());
			authMethod = HttpAuthMethod.Type.NONE.method(null);
			authAttempts = 1;
			return true;
		}
	}

	/**
	 * Create an HTTP authenticator for a replayable request.
	 *
	 * @param uri
	 *            URI used to scope credential lookup
	 * @param credentialsProvider
	 *            provider for credentials, or {@code null}
	 * @return a stateful HTTP authenticator
	 */
	public static Authenticator newAuthenticator(URIish uri,
			CredentialsProvider credentialsProvider) {
		return new Authenticator(uri, credentialsProvider);
	}

	static HttpAuthMethod authFromUri(URIish uri) {
		String user = uri.getUser();
		String pass = uri.getPass();
		if (user != null && pass != null) {
			try {
				// User/password are _not_ application/x-www-form-urlencoded. In
				// particular the "+" sign would be replaced by a space.
				user = URLDecoder.decode(user.replace("+", "%2B"), //$NON-NLS-1$ //$NON-NLS-2$
						StandardCharsets.UTF_8.name());
				pass = URLDecoder.decode(pass.replace("+", "%2B"), //$NON-NLS-1$ //$NON-NLS-2$
						StandardCharsets.UTF_8.name());
				HttpAuthMethod basic = HttpAuthMethod.Type.BASIC.method(null);
				basic.authorize(user, pass);
				return basic;
			} catch (IllegalArgumentException
					| UnsupportedEncodingException e) {
				LOG.warn(JGitText.get().httpUserInfoDecodeError, uri);
			}
		}
		return HttpAuthMethod.Type.NONE.method(null);
	}

	private InternalHttpClientGlue() {
	}
}
