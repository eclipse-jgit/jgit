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
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.pgm.forwarder.ForwarderConfig.RepositoryLimit;
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
	private static final class LimitState {
		private final int maxConnections;

		private final Set<String> active = ConcurrentHashMap.newKeySet();

		private final Semaphore startSlots;

		/**
		 * Concurrency state for a scope (global or a project pattern).
		 *
		 * @param maxConnections
		 *            hard cap on concurrent connections, or &lt;= 0 for no
		 *            limit
		 * @param maxStart
		 *            soft cap; excess connections queue until a slot is free,
		 *            or &lt;= 0 for no limit
		 */
		LimitState(int maxConnections, int maxStart) {
			this.maxConnections = maxConnections;
			this.startSlots = maxStart > 0 ? new Semaphore(maxStart, true)
					: null;
		}

		/**
		 * Returns false if the hard cap is reached.
		 *
		 * @param id
		 *            request ID to track
		 * @return false if maxConnections is reached, true if the slot was
		 *         acquired
		 * @throws InterruptedException
		 *             if interrupted while waiting for a start slot; state is
		 *             left unchanged
		 */
		boolean tryAcquire(String id) throws InterruptedException {
			if (maxConnections > 0) {
				synchronized (active) {
					if (active.size() >= maxConnections) {
						return false;
					}
					active.add(id);
				}
			} else {
				active.add(id);
			}
			try {
				if (startSlots != null) {
					startSlots.acquire();
				}
			} catch (InterruptedException e) {
				active.remove(id);
				throw e;
			}
			return true;
		}

		void release(String id) {
			if (active.remove(id) && startSlots != null) {
				startSlots.release();
			}
		}
	}

	private static final Logger LOG = LoggerFactory
			.getLogger(FixedRouteListener.class);

	private final RouteResponse response;

	private final LimitState globalLimit;

	private final Map<RepositoryLimit, LimitState> projectLimits = new LinkedHashMap<>();

	private final ConcurrentMap<String, LimitState> projectStates = new ConcurrentHashMap<>();

	FixedRouteListener(@NonNull InetSocketAddress destination,
			int maxConnections, int maxStart, List<RepositoryLimit> limits) {
		this.response = new RouteResponse(destination);
		this.globalLimit = new LimitState(maxConnections, maxStart);
		for (RepositoryLimit limit : limits) {
			projectLimits.put(limit,
					new LimitState(limit.maxConnections(), limit.maxStart()));
		}
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		String id = request.requestId();
		LimitState project = null;
		try {
			if (!globalLimit.tryAcquire(id)) {
				LOG.warn(CLIText.get().forwarderMaxConnectionsExceeded);
				sendError(request,
						CLIText.get().forwarderMaxConnectionsExceeded);
				return null;
			}
			project = findProjectLimit(request.commandInfo().repo);
			if (project != null) {
				if (!project.tryAcquire(id)) {
					globalLimit.release(id);
					String msg = MessageFormat.format(CLIText
							.get().forwarderProjectMaxConnectionsExceeded,
							request.commandInfo().repo);
					LOG.warn(msg);
					sendError(request, msg);
					return null;
				}
				projectStates.put(id, project);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			globalLimit.release(id);
			if (project != null) {
				project.release(id);
			}
			LOG.error(CLIText.get().forwarderStartSlotWaitInterrupted, e);
			sendError(request, CLIText.get().forwarderStartSlotWaitInterrupted);
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

	@Nullable
	private LimitState findProjectLimit(@Nullable String repo) {
		if (repo == null) {
			return null;
		}
		for (Map.Entry<RepositoryLimit, LimitState> e : projectLimits
				.entrySet()) {
			if (e.getKey().matches(repo)) {
				return e.getValue();
			}
		}
		return null;
	}

	private void release(RouteRequest request) {
		globalLimit.release(request.requestId());
		LimitState project = projectStates.remove(request.requestId());
		if (project != null) {
			project.release(request.requestId());
		}
	}
}
