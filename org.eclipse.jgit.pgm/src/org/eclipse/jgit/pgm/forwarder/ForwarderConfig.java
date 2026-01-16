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
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.forwarder.GitForwarderConfig;
import org.eclipse.jgit.transport.forwarder.RoutingListener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;

/**
 * Parses forwarder configuration.
 *
 * <p>
 * The active node is selected by matching the local hostname to a
 * {@code [node "<hostname>"]} subsection, falling back to {@code [node "*"]}.
 *
 * <pre>
 * [node "git-us.corp"]
 *   # Required. Any of the following forms are accepted:
 *   # "0.0.0.0:9418"
 *   # "localhost" -> localhost:9418 (default port)
 *   listen = 0.0.0.0:9418
 *   # Required. Same parsing rules as listen
 *   remote = 127.0.0.1:9419
 * </pre>
 */
class ForwarderConfig implements GitForwarderConfig {
	private static final String NODE = "node"; //$NON-NLS-1$

	private static final String LISTEN = "listen"; //$NON-NLS-1$

	private static final String REMOTE = "remote"; //$NON-NLS-1$

	private static final String WILDCARD = "*"; //$NON-NLS-1$

	private final InetSocketAddress listen;

	private final InetSocketAddress remote;

	private final RoutingListener routingListener;

	/**
	 * Build forwarder config from a config file.
	 *
	 * @param cfg
	 *            config containing required keys
	 * @throws Die
	 *             if the hostname cannot be resolved or required keys are
	 *             missing
	 */
	ForwarderConfig(@NonNull Config cfg) throws Die {
		String hostname;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw new Die(MessageFormat.format(
					CLIText.get().forwarderHostnameUnresolvable,
					e.getMessage()));
		}

		String resolvedNode = cfg.getSubsections(NODE).contains(hostname)
				? hostname
				: WILDCARD;

		String listenValue = cfg.getString(NODE, resolvedNode, LISTEN);
		String remoteValue = cfg.getString(NODE, resolvedNode, REMOTE);

		if (listenValue == null) {
			throw new Die(
					MessageFormat.format(CLIText.get().forwarderMissingNodeKey,
							resolvedNode, LISTEN));
		}
		if (remoteValue == null) {
			throw new Die(
					MessageFormat.format(CLIText.get().forwarderMissingNodeKey,
							resolvedNode, REMOTE));
		}

		this.listen = parseAddress(listenValue);
		this.remote = parseAddress(remoteValue);
		this.routingListener = new FixedRouteListener(this.remote);
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

	/**
	 * Remote (upstream) address to forward connections to.
	 *
	 * @return remote HostPort from config
	 */
	InetSocketAddress getRemote() {
		return remote;
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
