/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2009, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

/**
 * The base class for transports that use HTTP as underlying protocol. This class
 * allows customizing HTTP connection settings.
 */
public abstract class HttpTransport extends Transport {
	/**
	 * factory for creating HTTP connections
	 *
	 * @since 3.3
	 */
	protected static HttpConnectionFactory connectionFactory = new JDKHttpConnectionFactory();

	/**
	 * Get the {@link org.eclipse.jgit.transport.http.HttpConnectionFactory}
	 * used to create new connections
	 *
	 * @return the {@link org.eclipse.jgit.transport.http.HttpConnectionFactory}
	 *         used to create new connections
	 * @since 3.3
	 */
	public static HttpConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	/**
	 * Set the {@link org.eclipse.jgit.transport.http.HttpConnectionFactory} to
	 * be used to create new connections
	 *
	 * @param cf
	 *            connection factory
	 * @since 3.3
	 */
	public static void setConnectionFactory(HttpConnectionFactory cf) {
		connectionFactory = cf;
	}

	/**
	 * Create a new transport instance.
	 *
	 * @param local
	 *            the repository this instance will fetch into, or push out of.
	 *            This must be the repository passed to
	 *            {@link #open(Repository, URIish)}.
	 * @param uri
	 *            the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(Repository, URIish)}.
	 */
	protected HttpTransport(Repository local, URIish uri) {
		super(local, uri);
	}

	/**
	 * Create a minimal HTTP transport instance not tied to a single repository.
	 *
	 * @param uri a {@link org.eclipse.jgit.transport.URIish} object.
	 */
	protected HttpTransport(URIish uri) {
		super(uri);
	}
}
