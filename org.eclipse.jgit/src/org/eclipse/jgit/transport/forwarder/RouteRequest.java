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

import org.eclipse.jgit.annotations.NonNull;

/**
 * Route request metadata.
 *
 * @param requestId
 *            unique id for this request
 * @param commandInfo
 *            parsed command info
 * @param sourceIp
 *            source IP address
 * @param listenAddr
 *            listen address
 *
 * @since 7.7
 */
public record RouteRequest(@NonNull String requestId,
		@NonNull CommandInfo commandInfo, @NonNull String sourceIp,
		@NonNull InetSocketAddress listenAddr) {
}
