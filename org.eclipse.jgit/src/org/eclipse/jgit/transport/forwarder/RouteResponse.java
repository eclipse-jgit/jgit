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

import org.eclipse.jgit.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * Route response from the listener.
 *
 * @param destination destination address
 * @param errorMessage error message when a route request isn't provided
 *
 * @since 7.7
 */
public record RouteResponse(InetSocketAddress destination, @Nullable String errorMessage) {
}
