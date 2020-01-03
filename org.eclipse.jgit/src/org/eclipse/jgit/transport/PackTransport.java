/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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
 * Marker interface an object transport using Git pack transfers.
 * <p>
 * Implementations of PackTransport setup connections and move objects back and
 * forth by creating pack files on the source side and indexing them on the
 * receiving side.
 *
 * @see BasePackFetchConnection
 * @see BasePackPushConnection
 */
public interface PackTransport {
	// no methods in marker interface
}
