/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2009, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
