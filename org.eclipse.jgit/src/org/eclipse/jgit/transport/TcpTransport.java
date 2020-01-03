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

/**
 * The base class for transports based on TCP sockets. This class
 * holds settings common for all TCP based transports.
 */
public abstract class TcpTransport extends Transport {
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
	protected TcpTransport(Repository local, URIish uri) {
		super(local, uri);
	}

	/**
	 * Create a new transport instance without a local repository.
	 *
	 * @param uri the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(URIish)}.
	 * @since 3.5
	 */
	protected TcpTransport(URIish uri) {
		super(uri);
	}
}
