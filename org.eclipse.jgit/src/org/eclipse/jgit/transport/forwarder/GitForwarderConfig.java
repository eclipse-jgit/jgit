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
import java.net.SocketOption;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jgit.annotations.NonNull;

/**
 * Configuration for a {@link GitForwarder}.
 *
 * @since 7.7
 */
public interface GitForwarderConfig {

	/**
	 * Address the forwarder binds to and accepts client connections on.
	 *
	 * @return the local socket address to bind the listening socket to
	 */
	@NonNull
	InetSocketAddress listenOn();

	/**
	 * Listener that decides where each incoming connection is routed and
	 * receives connection lifecycle callbacks.
	 *
	 * @return the routing listener to consult for every accepted connection
	 */
	@NonNull
	RoutingListener routingListener();

	/**
	 * Socket options applied to every proxied socket. Defaults to empty.
	 *
	 * @return socket options to apply to every proxied socket
	 */
	@NonNull
	default Map<SocketOption<?>, Object> socketOptions() {
		return Collections.emptyMap();
	}
}
