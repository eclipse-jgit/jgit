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

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.pgm.Die;
import org.eclipse.jgit.transport.Daemon;

import java.net.InetSocketAddress;

/**
 * Class to parse the forwarder configuration.
 *
 * <pre>
 * [global]
 *  # Required. Any of the following forms are accepted:
 *  # "0.0.0.0:9418"
 *  #	"localhost" -> localhost:9418 (default port)
 *  listen = 127.0.0.1:9418
 *
 *  # Required. Same parsing rules as listen.
 *  remote = 127.0.0.1:9419
 * </pre>
 */
class ForwarderConfig {
	private static final String GLOBAL = "global";

	private static final String LISTEN = "listen";

	private static final String REMOTE = "remote";

	private final InetSocketAddress listen;

	private final InetSocketAddress remote;

	/**
	 * Build forwarder config from a JGit config.
	 *
	 * @param cfg
	 *            config containing [global] listen, remote.
	 * @throws Die
	 *             if required keys are missing or invalid
	 */
	public ForwarderConfig(@NonNull Config cfg) throws Die {

		String listenValue = cfg.getString(GLOBAL, null, LISTEN);
		String remoteValue = cfg.getString(GLOBAL, null, REMOTE);

		if (listenValue == null) {
			throw new Die("Missing global." + LISTEN);
		}
		if (remoteValue == null) {
			throw new Die("Missing global." + REMOTE);
		}

		this.listen = parseAddress(listenValue);
		this.remote = parseAddress(remoteValue);
	}

	/**
	 * Listen address (host and port) for the forwarder.
	 *
	 * @return listen HostPort from config
	 */
	public InetSocketAddress getListen() {
		return listen;
	}

	/**
	 * Remote (upstream) address to forward connections to.
	 *
	 * @return remote HostPort from config
	 */
	public InetSocketAddress getRemote() {
		return remote;
	}

	private InetSocketAddress parseAddress(String in) {
		if (in == null) {
			throw new IllegalArgumentException("host/port must not be null");
		}

		String trimmed = in.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Empty host/port combination");
		}

		int colon = trimmed.lastIndexOf(':');
		if (colon > 0 && colon < trimmed.length() - 1) {
			String portPart = trimmed.substring(colon + 1);
			if (portPart.matches("\\d+")) {
				return new InetSocketAddress(trimmed.substring(0, colon),
						Integer.parseInt(portPart));
			}
		}
		return new InetSocketAddress(trimmed, Daemon.DEFAULT_PORT);
	}
}
