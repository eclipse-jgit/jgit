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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.pgm.forwarder.ForwarderConfig.RepositoryLimit;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a fixed destination used by Forwarder with limits.
 */
final class FixedRouteListener implements RoutingListener {
	private record RouteState(AtomicInteger globalLimit,
			AtomicInteger projectLimit) {
		void release() {
			if (projectLimit != null) {
				projectLimit.decrementAndGet();
			}
			if (globalLimit != null) {
				globalLimit.decrementAndGet();
			}
		}
	}

	private final RouteResponse response;

	private final ForwarderConfig forwarderConfig;

	private final AtomicInteger globalInflight;

	private final Map<RepositoryLimit, AtomicInteger> inFlightByRepo = new HashMap<>();

	private final ConcurrentMap<String, RouteState> routeStates = new ConcurrentHashMap<>();

	private static final Logger LOG = LoggerFactory
			.getLogger(FixedRouteListener.class);

	FixedRouteListener(InetSocketAddress destination, ForwarderConfig fc) {
		this.response = new RouteResponse(destination, null);
		this.forwarderConfig = fc;
		this.globalInflight = fc.getGlobalLimit() <= 0 ? null
				: new AtomicInteger(0);
		for (ForwarderConfig.RepositoryLimit lim : fc.getProjectLimits()) {
			this.inFlightByRepo.put(lim, new AtomicInteger(0));
		}
	}

	@Override
	public RouteResponse onConnect(RouteRequest request) {
		if (!tryAcquire(globalInflight, forwarderConfig.getGlobalLimit())) {
			LOG.warn(MessageFormat.format(
					CLIText.get().forwarderRejectingGlobalMaxStart, request));
			return new RouteResponse(null,
					CLIText.get().forwarderGlobalMaxStartExceeded);
		}

		String repoName = request.repo();
		AtomicInteger repoInFlight = null;
		if (repoName != null) {
			ForwarderConfig.RepositoryLimit repositoryLimit = findFirstMatch(
					repoName);
			if (repositoryLimit != null) {
				repoInFlight = inFlightByRepo.get(repositoryLimit);
				if (!tryAcquire(repoInFlight, repositoryLimit.maxStart())) {
					globalInflight.decrementAndGet();
					LOG.warn(MessageFormat.format(
							CLIText.get().forwarderRejectingRepoMaxStart,
							request));
					return new RouteResponse(null,
							MessageFormat.format(
									CLIText.get().forwarderRepoMaxStartExceeded,
									repositoryLimit.pattern()));
				}
			}
		}

		routeStates.put(request.requestId(),
				new RouteState(globalInflight, repoInFlight));
		return response;
	}

	@Override
	public void onClose(RouteRequest request,
			@Nullable RouteResponse routeResponse) {
		releaseState(request);
	}

	@Override
	public void onConnectException(RouteRequest request, Exception error) {
		releaseState(request);
	}

	@Override
	public void afterOpenException(RouteRequest request,
			@Nullable RouteResponse routeResponse, Exception error) {
		releaseState(request);
	}

	private void releaseState(RouteRequest request) {
		RouteState state = routeStates.remove(request.requestId());
		if (state != null) {
			state.release();
		}
	}

	private ForwarderConfig.RepositoryLimit findFirstMatch(String project) {
		for (ForwarderConfig.RepositoryLimit limit : forwarderConfig
				.getProjectLimits()) {
			if (limit.matches(project)) {
				return limit;
			}
		}
		return null;
	}

	private static boolean tryAcquire(AtomicInteger inFlight, int max) {
		if (inFlight == null) {
			return true;
		}
		int previousValue = inFlight
				.getAndUpdate(cur -> (cur < max) ? cur + 1 : cur);
		return previousValue < max;
	}
}
