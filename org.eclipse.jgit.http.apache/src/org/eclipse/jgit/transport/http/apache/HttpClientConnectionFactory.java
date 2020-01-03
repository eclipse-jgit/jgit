/*
 * Copyright (C) 2013 Christian Halstrick <christian.halstrick@sap.com> and others
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

import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;

/**
 * A factory returning instances of
 * {@link org.eclipse.jgit.transport.http.apache.HttpClientConnection}
 *
 * @since 3.3
 */
public class HttpClientConnectionFactory implements HttpConnectionFactory {
	/** {@inheritDoc} */
	@Override
	public HttpConnection create(URL url) throws IOException {
		return new HttpClientConnection(url.toString());
	}

	/** {@inheritDoc} */
	@Override
	public HttpConnection create(URL url, Proxy proxy)
			throws IOException {
		return new HttpClientConnection(url.toString(), proxy);
	}
}
