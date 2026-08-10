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
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.pgm.Die;
import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.forwarder.GitForwarderConfig;
import org.eclipse.jgit.transport.forwarder.RoutingListener;

/**
 * Parses forwarder configuration.
 *
 * <pre>
 * [global]
 *  # Required. Any of the following forms are accepted:
 *  # "0.0.0.0:9418"
 *  # "localhost" -> localhost:9418 (default port)
 *  listen = 127.0.0.1:9418
 *
 *  # Required. Same parsing rules as listen.
 *  remote = 127.0.0.1:9419

 *  # Optional. Enable TCP keep-alives on client and upstream sockets.
 *  # Defaults to false.
 *  keepAlive = true
 *
 *  # Optional. If &gt; 0, hard cap on total concurrent connections; excess
 *  # is rejected immediately. It helps limit memory and socket resources.
 *  maxConnections = 100
 *
 *  # Optional. If &gt; 0, queues connections beyond this limit until a slot
 *  # is free. Unlike maxConnections, this helps limit networking bandwidth
 *  # and CPU.
 *  maxStart = 10
 *
 * # Optional per-project limits; first matching section applies.
 * # Pattern is a Java regex matched against the repo path (no .git suffix).
 * [project "some/repo.*"]
 *  maxConnections = 5
 *  maxStart = 2
 * </pre>
 */
class ForwarderConfig implements GitForwarderConfig {

	/**
	 * Per-project concurrency limit identified by a Java regex.
	 *
	 * @param pattern
	 *            Java regex matched against the repo path (no .git suffix)
	 * @param maxConnections
	 *            hard cap; excess rejected immediately, or &lt;= 0 for no limit
	 * @param maxStart
	 *            soft cap; excess queued until a slot is free, or &lt;= 0 for
	 *            no limit
	 */
	record RepositoryLimit(Pattern pattern, int maxConnections, int maxStart) {
		boolean matches(String repo) {
			return pattern.matcher(repo).matches();
		}
	}

	private static final String GLOBAL = "global"; //$NON-NLS-1$

	private static final String LISTEN = "listen"; //$NON-NLS-1$

	private static final String REMOTE = "remote"; //$NON-NLS-1$

	private static final String KEEP_ALIVE = "keepAlive"; //$NON-NLS-1$

	private static final String MAX_CONNECTIONS = "maxConnections"; //$NON-NLS-1$

	private static final String MAX_START = "maxStart"; //$NON-NLS-1$

	private static final String PROJECT = "project"; //$NON-NLS-1$

	private final InetSocketAddress listen;

	private final InetSocketAddress remote;

	private final RoutingListener routingListener;

	private final boolean keepAlive;

	/**
	 * Build forwarder config from a config file.
	 *
	 * @param cfg
	 *            config containing required keys
	 * @throws Die
	 *             if required keys are missing or invalid
	 */
	ForwarderConfig(@NonNull Config cfg) throws Die {
		String listenValue = cfg.getString(GLOBAL, null, LISTEN);
		String remoteValue = cfg.getString(GLOBAL, null, REMOTE);

		if (listenValue == null) {
			throw new Die("Missing global." + LISTEN); //$NON-NLS-1$
		}
		if (remoteValue == null) {
			throw new Die("Missing global." + REMOTE); //$NON-NLS-1$
		}

		this.listen = parseAddress(listenValue);
		this.remote = parseAddress(remoteValue);
		this.routingListener = new FixedRouteListener(this.remote,
				cfg.getInt(GLOBAL, null, MAX_CONNECTIONS, -1),
				cfg.getInt(GLOBAL, null, MAX_START, -1),
				loadProjectLimits(cfg));
		this.keepAlive = cfg.getBoolean(GLOBAL, null, KEEP_ALIVE, false);
	}

	@Override
	@NonNull
	public InetSocketAddress listenOn() {
		return listen;
	}

	@Override
	@NonNull
	public RoutingListener routingListener() {
		return routingListener;
	}

	@Override
	@NonNull
	public Map<SocketOption<?>, Object> socketOptions() {
		if (!keepAlive) {
			return Map.of();
		}
		return Map.of(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);
	}

	/**
	 * Remote (upstream) address to forward connections to.
	 *
	 * @return remote HostPort from config
	 */
	InetSocketAddress getRemote() {
		return remote;
	}

	private static List<RepositoryLimit> loadProjectLimits(Config cfg)
			throws Die {
		Set<String> subsections = cfg.getSubsections(PROJECT);
		List<RepositoryLimit> limits = new ArrayList<>();
		for (String pattern : subsections) {
			int maxConn = cfg.getInt(PROJECT, pattern, MAX_CONNECTIONS, -1);
			int maxStart = cfg.getInt(PROJECT, pattern, MAX_START, -1);
			if (maxConn <= 0 && maxStart <= 0) {
				continue;
			}
			try {
				limits.add(new RepositoryLimit(Pattern.compile(pattern),
						maxConn, maxStart));
			} catch (IllegalArgumentException e) {
				throw new Die("Invalid project regex: " + pattern, e); //$NON-NLS-1$
			}
		}
		return limits;
	}

	private InetSocketAddress parseAddress(String in) {
		if (in == null) {
			throw new IllegalArgumentException("host/port must not be null"); //$NON-NLS-1$
		}

		String trimmed = in.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Empty host/port combination"); //$NON-NLS-1$
		}

		int colon = trimmed.lastIndexOf(':');
		if (colon > 0 && colon < trimmed.length() - 1) {
			String portPart = trimmed.substring(colon + 1);
			if (portPart.matches("\\d+")) { //$NON-NLS-1$
				return new InetSocketAddress(trimmed.substring(0, colon),
						Integer.parseInt(portPart));
			}
		}
		return new InetSocketAddress(trimmed, Daemon.DEFAULT_PORT);
	}
}
