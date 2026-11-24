/*
 * Copyright (C) 2026 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.jgit.transport.http.HttpConnectionFactory2.GitSession;
import org.junit.Test;

/**
 * Tests for {@link JDKHttpConnectionFactory}.
 */
public class JDKHttpConnectionFactoryTest {

	@Test
	public void testConfigureVerifiedHttpsUsesCustomSSLSocketFactory()
			throws Exception {
		SSLSocketFactory customFactory = new StubSSLSocketFactory();
		JDKHttpConnectionFactory factory = new JDKHttpConnectionFactory(
				customFactory);
		GitSession session = factory.newSession();
		try {
			JDKHttpConnection conn = (JDKHttpConnection) factory
					.create(URI.create("https://example.com/repo.git").toURL());
			session.configure(conn, true);
			assertSame(customFactory,
					((HttpsURLConnection) conn.wrappedUrlConnection)
							.getSSLSocketFactory());
		} finally {
			session.close();
		}
	}

	@Test
	public void testConfigureVerifiedHttpsWithDefaultConstructorUsesJdkDefault()
			throws Exception {
		JDKHttpConnectionFactory factory = new JDKHttpConnectionFactory();
		GitSession session = factory.newSession();
		try {
			JDKHttpConnection conn = (JDKHttpConnection) factory
					.create(URI.create("https://example.com/repo.git").toURL());
			SSLSocketFactory defaultFactory = ((HttpsURLConnection) conn.wrappedUrlConnection)
					.getSSLSocketFactory();
			session.configure(conn, true);
			assertSame(defaultFactory,
					((HttpsURLConnection) conn.wrappedUrlConnection)
							.getSSLSocketFactory());
		} finally {
			session.close();
		}
	}

	@Test
	public void testConfigureUnverifiedHttpsDoesNotUseCustomFactory()
			throws Exception {
		SSLSocketFactory customFactory = new StubSSLSocketFactory();
		JDKHttpConnectionFactory factory = new JDKHttpConnectionFactory(
				customFactory);
		GitSession session = factory.newSession();
		try {
			JDKHttpConnection conn = (JDKHttpConnection) factory
					.create(URI.create("https://example.com/repo.git").toURL());
			session.configure(conn, false);
			assertNotSame(customFactory,
					((HttpsURLConnection) conn.wrappedUrlConnection)
							.getSSLSocketFactory());
		} finally {
			session.close();
		}
	}

	@Test
	public void testConfigurePlainHttpLeavesConnectionUntouched()
			throws Exception {
		SSLSocketFactory customFactory = new StubSSLSocketFactory();
		JDKHttpConnectionFactory factory = new JDKHttpConnectionFactory(
				customFactory);
		GitSession session = factory.newSession();
		try {
			JDKHttpConnection conn = (JDKHttpConnection) factory
					.create(URI.create("http://example.com/repo.git").toURL());
			assertSame(conn, session.configure(conn, true));
		} finally {
			session.close();
		}
	}

	private static final class StubSSLSocketFactory extends SSLSocketFactory {
		@Override
		public String[] getDefaultCipherSuites() {
			return new String[0];
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return new String[0];
		}

		@Override
		public Socket createSocket(Socket s, String host, int port,
				boolean autoClose) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost,
				int localPort) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Socket createSocket(InetAddress host, int port)
				throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Socket createSocket(InetAddress address, int port,
				InetAddress localAddress, int localPort) throws IOException {
			throw new UnsupportedOperationException();
		}
	}
}
