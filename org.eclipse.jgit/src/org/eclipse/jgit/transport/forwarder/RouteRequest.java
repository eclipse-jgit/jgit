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
 * @param requestId unique request id
 * @param commandInfo parsed command info, if available
 * @param sourceIp source IP address, if available
 * @param listenAddr listen address
 *
 * @since 7.7
 */
public record RouteRequest(
		@NonNull String requestId,
		@NonNull CommandInfo commandInfo,
		@NonNull String sourceIp,
		@NonNull InetSocketAddress listenAddr) {
	/**
	 *
	 * @return repository associated with this request
	 */
	public String repo() {
		return commandInfo().repo;
	}
}
