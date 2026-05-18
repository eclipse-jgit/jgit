/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.forwarder;

import java.net.InetSocketAddress;

import org.eclipse.jgit.annotations.Nullable;

/**
 * Route response from the listener.
 *
 * @param destination
 *            destination address; null when the request is rejected
 * @param errorMessage
 *            non-null when the request is rejected; the message is delivered
 *            to the client as a pkt-line error
 *
 * @since 7.7
 */
public record RouteResponse(@Nullable InetSocketAddress destination,
		@Nullable String errorMessage) {
}
