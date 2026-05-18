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

import static org.eclipse.jgit.transport.GitProtocolConstants.PACKET_ERR;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a fixed destination used by Forwarder.
 */
final class FixedRouteListener implements RoutingListener {
	private static final Logger LOG = LoggerFactory
			.getLogger(FixedRouteListener.class);

	private final RouteResponse response;

	private final int maxConnections;

	private final Set<String> connections = ConcurrentHashMap.newKeySet();

	FixedRouteListener(@NonNull InetSocketAddress destination,
			int maxConnections) {
		this.response = new RouteResponse(destination);
		this.maxConnections = maxConnections;
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		if (!tryAcquire(request)) {
			LOG.warn(CLIText.get().forwarderMaxConnectionsExceeded);
			sendError(request, CLIText.get().forwarderMaxConnectionsExceeded);
			return null;
		}

		return response;
	}

	@Override
	public void onClose(RouteRequest request,
			@Nullable RouteResponse routeResponse) {
		release(request);
	}

	@Override
	public void onConnectException(RouteRequest request, Exception error) {
		release(request);
	}

	@Override
	public void afterOpenException(RouteRequest request,
			@Nullable RouteResponse routeResponse, Exception error) {
		release(request);
	}

	private void sendError(RouteRequest request, String message) {
		try {
			new PacketLineOut(request.clientSocket().getOutputStream())
					.writeString(request.requestId() + ": " + PACKET_ERR //$NON-NLS-1$
							+ message + '\n');
		} catch (IOException e) {
			LOG.debug(CLIText.get().forwarderFailedToWriteErrorToClient, e);
		}
	}

	private boolean tryAcquire(RouteRequest request) {
		if (maxConnections <= 0) {
			return true;
		}
		synchronized (connections) {
			if (connections.size() < maxConnections) {
				connections.add(request.requestId());
				return true;
			}
		}

		return false;
	}

	private void release(RouteRequest request) {
		connections.remove(request.requestId());
	}
}
