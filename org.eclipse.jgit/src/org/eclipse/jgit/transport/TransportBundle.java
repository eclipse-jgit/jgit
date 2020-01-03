/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

/**
 * Marker interface for transports that supports fetching from a git bundle
 * (sneaker-net object transport).
 * <p>
 * Push support for a bundle is complex, as one does not have a peer to
 * communicate with to decide what the peer already knows. So push is not
 * supported by the bundle transport.
 */
public interface TransportBundle extends PackTransport {
	/**
	 * Bundle signature
	 */
	String V2_BUNDLE_SIGNATURE = "# v2 git bundle"; //$NON-NLS-1$
}
