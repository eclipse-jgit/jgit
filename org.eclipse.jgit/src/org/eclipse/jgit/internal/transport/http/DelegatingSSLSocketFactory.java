/*
 * Copyright (c) 2020 Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * An {@link SSLSocketFactory} that delegates to another factory and allows
 * configuring the created socket via {@link #configure(SSLSocket)} before it is
 * returned.
 */
public abstract class DelegatingSSLSocketFactory extends SSLSocketFactory {

	private final SSLSocketFactory delegate;

	/**
	 * Creates a new {@link DelegatingSSLSocketFactory} based on the given
	 * delegate.
	 *
	 * @param delegate
	 *            {@link SSLSocketFactory} to delegate to
	 */
	public DelegatingSSLSocketFactory(SSLSocketFactory delegate) {
		this.delegate = delegate;
	}

	@Override
	public SSLSocket createSocket() throws IOException {
		return prepare(delegate.createSocket());
	}

	@Override
	public SSLSocket createSocket(String host, int port) throws IOException {
		return prepare(delegate.createSocket(host, port));
	}

	@Override
	public SSLSocket createSocket(String host, int port,
			InetAddress localAddress, int localPort) throws IOException {
		return prepare(
				delegate.createSocket(host, port, localAddress, localPort));
	}

	@Override
	public SSLSocket createSocket(InetAddress host, int port)
			throws IOException {
		return prepare(delegate.createSocket(host, port));
	}

	@Override
	public SSLSocket createSocket(InetAddress host, int port,
			InetAddress localAddress, int localPort) throws IOException {
		return prepare(
				delegate.createSocket(host, port, localAddress, localPort));
	}

	@Override
	public SSLSocket createSocket(Socket socket, String host, int port,
			boolean autoClose) throws IOException {
		return prepare(delegate.createSocket(socket, host, port, autoClose));
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return delegate.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return delegate.getSupportedCipherSuites();
	}

	private SSLSocket prepare(Socket socket) throws IOException {
		SSLSocket sslSocket = (SSLSocket) socket;
		configure(sslSocket);
		return sslSocket;
	}

	/**
	 * Configure the newly created socket.
	 *
	 * @param socket
	 *            to configure
	 * @throws IOException
	 *             if the socket cannot be configured
	 */
	protected abstract void configure(SSLSocket socket) throws IOException;

}
