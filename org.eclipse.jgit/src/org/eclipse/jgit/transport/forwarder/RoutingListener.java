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

import org.eclipse.jgit.annotations.Nullable;

/**
 * Listener invoked for connection lifecycle events.
 *
 * @since 7.7
 */
public interface RoutingListener {
	/**
	 * Called when a client connects.
	 *
	 * @param request
	 *            request information
	 * @return routing response
	 */
	@Nullable
	RouteResponse onConnect(RouteRequest request);

	/**
	 * Called when a connection closes.
	 *
	 * @param request
	 *            request information
	 * @param response
	 *            routing response
	 */
	void onClose(RouteRequest request, @Nullable RouteResponse response);

	/**
	 * Called when the connection could not be established.
	 *
	 * @param request
	 *            request information
	 * @param error
	 *            exception encountered while handling the connection
	 */
	void onException(RouteRequest request, Exception error);

	/**
	 * Called when an exception occurs after a route was opened.
	 *
	 * @param request
	 *            request information
	 * @param response
	 *            routing response
	 * @param error
	 *            exception encountered while proxying
	 */
	void onOpenException(RouteRequest request,
			@Nullable RouteResponse response, Exception error);
}
