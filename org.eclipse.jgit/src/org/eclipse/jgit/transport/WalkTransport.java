/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
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
 * Marker interface for an object transport walking transport.
 * <p>
 * Implementations of WalkTransport transfer individual objects one at a time
 * from the loose objects directory, or entire packs if the source side does not
 * have the object as a loose object.
 * <p>
 * WalkTransports are not as efficient as
 * {@link org.eclipse.jgit.transport.PackTransport} instances, but can be useful
 * in situations where a pack transport is not acceptable.
 *
 * @see WalkFetchConnection
 */
public interface WalkTransport {
	// no methods in marker interface
}
