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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.eclipse.jgit.annotations.NonNull;

import java.net.InetSocketAddress;

/**
 * Route request metadata.
 *
 * @param commandInfo parsed command info, if available
 * @param sourceIp source IP address, if available
 * @param listenAddr listen address
 *
 * @since 7.7
 */
public record RouteRequest(
		@NonNull Socket socket,
		@NonNull CommandInfo commandInfo,
		@NonNull String sourceIp,
		@NonNull InetSocketAddress listenAddr) {
	public RouteRequest(Socket socket, String sourceIp, InetSocketAddress listenAddr) throws IOException {
		this(socket, new CommandInfo(socket), sourceIp, listenAddr);
	}
}
