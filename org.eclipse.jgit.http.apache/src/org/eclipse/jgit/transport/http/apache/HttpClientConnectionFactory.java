/*
 * Copyright (C) 2013, 2020 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http.apache;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory2;
import org.eclipse.jgit.transport.http.NoCheckX509TrustManager;
import org.eclipse.jgit.transport.http.apache.internal.HttpApacheText;
import org.eclipse.jgit.util.HttpSupport;

/**
 * A factory returning instances of {@link HttpClientConnection}.
 *
 * @since 3.3
 */
public class HttpClientConnectionFactory implements HttpConnectionFactory2 {

	@Override
	public HttpConnection create(URL url) throws IOException {
		return new HttpClientConnection(url.toString());
	}

	@Override
	public HttpConnection create(URL url, Proxy proxy) throws IOException {
		return new HttpClientConnection(url.toString(), proxy);
	}

	@Override
	public GitSession newSession() {
		return new HttpClientSession();
	}

	private static class HttpClientSession implements GitSession {

		private SSLContext securityContext;

		private SSLConnectionSocketFactory socketFactory;

		private boolean isDefault;

		@Override
		public HttpClientConnection configure(HttpConnection connection,
				boolean sslVerify)
				throws IOException, GeneralSecurityException {
			if (!(connection instanceof HttpClientConnection)) {
				throw new IllegalArgumentException(MessageFormat.format(
						HttpApacheText.get().httpWrongConnectionType,
						HttpClientConnection.class.getName(),
						connection.getClass().getName()));
			}
			HttpClientConnection conn = (HttpClientConnection) connection;
			String scheme = conn.getURL().getProtocol();
			if (!"https".equals(scheme)) { //$NON-NLS-1$
				return conn;
			}
			if (securityContext == null || isDefault != sslVerify) {
				isDefault = sslVerify;
				HostnameVerifier verifier;
				if (sslVerify) {
					securityContext = SSLContext.getDefault();
					verifier = SSLConnectionSocketFactory
							.getDefaultHostnameVerifier();
				} else {
					securityContext = SSLContext.getInstance("TLS");
					TrustManager[] trustAllCerts = {
							new NoCheckX509TrustManager() };
					securityContext.init(null, trustAllCerts, null);
					verifier = (name, session) -> true;
				}
				socketFactory = new SSLConnectionSocketFactory(securityContext,
						verifier) {

					@Override
					protected void prepareSocket(SSLSocket socket)
							throws IOException {
						super.prepareSocket(socket);
						HttpSupport.configureTLS(socket);
					}
				};
			}
			conn.setSSLSocketFactory(socketFactory, isDefault);
			return conn;
		}

		@Override
		public void close() {
			securityContext = null;
			socketFactory = null;
		}

	}
}
