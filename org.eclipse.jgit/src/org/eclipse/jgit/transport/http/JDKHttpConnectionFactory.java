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
 * A factory returning instances of
 * {@link org.eclipse.jgit.transport.http.JDKHttpConnection}
 *
 * @since 3.3
 */
public class JDKHttpConnectionFactory implements HttpConnectionFactory {
	/** {@inheritDoc} */
	@Override
	public HttpConnection create(URL url) throws IOException {
		return new JDKHttpConnection(url);
	}

	/** {@inheritDoc} */
	@Override
	public HttpConnection create(URL url, Proxy proxy)
			throws IOException {
		return new JDKHttpConnection(url, proxy);
	}
}
