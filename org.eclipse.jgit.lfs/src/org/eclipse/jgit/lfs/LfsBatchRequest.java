/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.lfs.internal.LfsConnectionFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.InternalHttpClientGlue;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;

/** Executes LFS batch requests that may need HTTP authentication replay. */
final class LfsBatchRequest {
	/**
	 * Execute a replayable LFS batch request.
	 * <p>
	 * This method owns request serialization, HTTP authentication, and request
	 * replay. The returned connection has a successful response status and an
	 * unread response body for the caller to parse.
	 *
	 * @param db
	 *            repository the batch request is for
	 * @param operation
	 *            LFS batch operation
	 * @param resources
	 *            LFS objects to include in the batch request
	 * @return successful batch response connection with an unread body
	 * @throws IOException
	 *             if the batch request fails
	 */
	static HttpConnection execute(Repository db, String operation,
			LfsPointer... resources) throws IOException {
		byte[] body = Protocol.gson()
				.toJson(LfsConnectionFactory.toRequest(operation, resources))
				.getBytes(UTF_8);
		CredentialsProvider credentialsProvider = CredentialsProvider
				.getDefault();
		InternalHttpClientGlue.Authenticator authenticator = null;
		for (;;) {
			HttpConnection connection = LfsConnectionFactory.getLfsConnection(db,
					METHOD_POST, operation);
			if (authenticator == null) {
				authenticator = InternalHttpClientGlue.newAuthenticator(
						toURIish(connection), credentialsProvider);
			}
			int status;
			try {
				authenticator.configureRequest(connection);
				connection.getOutputStream().write(body);
				status = HttpSupport.response(connection);
			} catch (IOException e) {
				if (authenticator.retryOnAuthFailure()) {
					continue;
				}
				throw e;
			}
			if (status == HttpConnection.HTTP_UNAUTHORIZED) {
				authenticator.retryAfterUnauthorized(connection);
				continue;
			}
			return connection;
		}
	}

	private static URIish toURIish(HttpConnection connection)
			throws IOException {
		try {
			return new URIish(connection.getURL().toString());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private LfsBatchRequest() {
	}
}
