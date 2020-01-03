/*
 * Copyright (C) 2013 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;

/**
 * The interface of a factory returning
 * {@link org.eclipse.jgit.transport.http.HttpConnection}
 *
 * @since 3.3
 */
public interface HttpConnectionFactory {
	/**
	 * Creates a new connection to a destination defined by a
	 * {@link java.net.URL}
	 *
	 * @param url
	 *            a {@link java.net.URL} object.
	 * @return a {@link org.eclipse.jgit.transport.http.HttpConnection}
	 * @throws java.io.IOException
	 */
	HttpConnection create(URL url) throws IOException;

	/**
	 * Creates a new connection to a destination defined by a
	 * {@link java.net.URL} using a proxy
	 *
	 * @param url
	 *            a {@link java.net.URL} object.
	 * @param proxy
	 *            the proxy to be used
	 * @return a {@link org.eclipse.jgit.transport.http.HttpConnection}
	 * @throws java.io.IOException
	 */
	HttpConnection create(URL url, Proxy proxy)
			throws IOException;
}
