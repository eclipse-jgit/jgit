/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.forwarder;

import java.net.InetSocketAddress;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;

/**
 * Represents a fixed destination used by Forwarder.
 *
 * @since 7.7
 */
final class FixedRouteListener implements RoutingListener {
	final RouteResponse response;

	/**
	 * @param destination destination address
	 */
	public FixedRouteListener(@NonNull InetSocketAddress destination) {
		this.response = new RouteResponse(destination);
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		return response;
	}

	@Override
	public void onClose(RouteRequest request, @Nullable RouteResponse routeResponse) {
		// No-op for fixed routing.
	}

	@Override
	public void onConnectException(RouteRequest request, Exception error) {
		// No-op for fixed routing.
	}

	@Override
	public void afterOpenException(RouteRequest request,
			@Nullable RouteResponse routeResponse, Exception error) {
		// No-op for fixed routing.
	}
}