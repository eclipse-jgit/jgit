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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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

	private final AtomicInteger numConnections;

	private final Semaphore maxStart;

	private final Set<String> admitted = ConcurrentHashMap.newKeySet();

	FixedRouteListener(@NonNull InetSocketAddress destination,
			int maxConnections, int maxStart) {
		this.response = new RouteResponse(destination);
		this.maxConnections = maxConnections;
		this.numConnections = maxConnections > 0 ? new AtomicInteger() : null;
		this.maxStart = maxStart > 0 ? new Semaphore(maxStart, true) : null;
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		if (!tryAcquireConnection()) {
			LOG.warn(CLIText.get().forwarderMaxConnectionsExceeded);
			sendError(request, CLIText.get().forwarderMaxConnectionsExceeded);
			return null;
		}

		if (maxStart != null) {
			try {
				maxStart.acquire();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				releaseConnection();
				LOG.error(CLIText.get().forwarderStartSlotWaitInterrupted, e);
				sendError(request,
						CLIText.get().forwarderStartSlotWaitInterrupted);
				return null;
			}
		}

		admitted.add(request.requestId());
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

	private boolean tryAcquireConnection() {
		if (numConnections == null) {
			return true;
		}
		int prev = numConnections
				.getAndUpdate(cur -> cur < maxConnections ? cur + 1 : cur);
		return prev < maxConnections;
	}

	private void releaseConnection() {
		if (numConnections != null) {
			numConnections.decrementAndGet();
		}
	}

	private void release(RouteRequest request) {
		if (admitted.remove(request.requestId())) {
			if (maxStart != null) {
				maxStart.release();
			}
			releaseConnection();
		}
	}
}
