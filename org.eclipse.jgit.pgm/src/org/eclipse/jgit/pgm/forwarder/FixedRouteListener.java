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
import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a fixed destination used by Forwarder with configurable global
 * limit.
 */
final class FixedRouteListener implements RoutingListener {
	private static final Logger LOG = LoggerFactory
			.getLogger(FixedRouteListener.class);

	private final RouteResponse response;

	private final int globalLimit;

	private final AtomicInteger globalInFlight;

	private final Set<String> admitted = ConcurrentHashMap.newKeySet();

	FixedRouteListener(@NonNull InetSocketAddress destination,
			int globalLimit) {
		this.response = new RouteResponse(destination, null);
		this.globalLimit = globalLimit;
		this.globalInFlight = globalLimit > 0 ? new AtomicInteger() : null;
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		if (!tryAcquire()) {
			LOG.warn(MessageFormat.format(
					CLIText.get().forwarderRejectingGlobalMaxStart, request));
			return new RouteResponse(null,
					CLIText.get().forwarderGlobalMaxStartExceeded);
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

	private boolean tryAcquire() {
		if (globalInFlight == null) {
			return true;
		}
		int prev = globalInFlight
				.getAndUpdate(cur -> cur < globalLimit ? cur + 1 : cur);
		return prev < globalLimit;
	}

	private void release(RouteRequest request) {
		if (admitted.remove(request.requestId())) {
			globalInFlight.decrementAndGet();
		}
	}
}
