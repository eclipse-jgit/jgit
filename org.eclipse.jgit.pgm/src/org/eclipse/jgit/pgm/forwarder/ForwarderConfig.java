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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class to parse the forwarder configuration.
 *
 * <pre>
 * [global]
 *     # Required. Any of the following forms are accepted:
 *     #   "0.0.0.0:9418"
 *     #   "localhost"   -> localhost:9418 (default port)
 *     listen = 127.0.0.1:9418
 *
 *     # Required. Same parsing rules as listen.
 *     remote = 127.0.0.1:9419
 *
 *     # Optional. Enable TCP keep-alives on client and upstream sockets.
 *     # Defaults to false.
 *     keepAlive = true
 *
 *     # Optional. If > 0, limits total concurrent forward connects.
 *     maxStart = 100
 *
 * # Optional per-project limits
 * # Only considers the first applicable match
 * # .git suffix not needed
 *
 * [project "&lt;java-style-regex&gt;"]
 *     maxStart = 20
 * </pre>
 */
class ForwarderConfig {
	/**
	 * Per-repo concurrency limit identified by a regex pattern.
	 *
	 * @param pattern
	 *            Java regex matched against the project path
	 * @param maxStart
	 *            maximum concurrent forward connections for matching projects
	 */
	public record RepositoryLimit(Pattern pattern, int maxStart) {
		/**
		 * Validates pattern and maxStart.
		 */
		public RepositoryLimit {
			if (pattern == null) {
				throw new IllegalArgumentException("pattern must not be null"); //$NON-NLS-1$
			}
			if (maxStart <= 0) {
				throw new IllegalArgumentException("maxStart must be > 0"); //$NON-NLS-1$
			}
		}

		/**
		 * Check whether the given project path matches this limit's pattern.
		 *
		 * @param project
		 *            project path to match
		 * @return true if the pattern matches
		 */
		public boolean matches(String project) {
			return pattern.matcher(project).matches();
		}
	}

	private static final String GLOBAL = "global"; //$NON-NLS-1$

	private static final String LISTEN = "listen"; //$NON-NLS-1$

	private static final String REMOTE = "remote"; //$NON-NLS-1$

	private static final String KEEP_ALIVE = "keepAlive"; //$NON-NLS-1$

	private static final String MAX_START = "maxStart"; //$NON-NLS-1$

	private static final String REPO = "project"; //$NON-NLS-1$

	private final InetSocketAddress listen;

	private final InetSocketAddress remote;

	private final boolean keepAlive;

	private final int globalLimit;

	private final List<RepositoryLimit> repositoryLimits;

	/**
	 * Build forwarder config from a JGit config.
	 *
	 * @param cfg
	 *            config containing required keys.
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
		this.keepAlive = cfg.getBoolean(GLOBAL, null, KEEP_ALIVE, false);
		this.globalLimit = cfg.getInt(GLOBAL, null, MAX_START, -1);
		this.repositoryLimits = loadRepoLimits(cfg);
	}

	/**
	 * Listen address (host and port) for the forwarder.
	 *
	 * @return listen HostPort from config
	 */
	InetSocketAddress getListen() {
		return listen;
	}

	/**
	 * Remote (upstream) address to forward connections to.
	 *
	 * @return remote HostPort from config
	 */
	InetSocketAddress getRemote() {
		return remote;
	}

	/**
	 * Whether TCP keep-alives should be enabled on sockets.
	 *
	 * @return true if keep-alives are enabled
	 */
	public boolean isKeepAlive() {
		return keepAlive;
	}

	/**
	 * Global maximum concurrent forward connections, or -1 if unset.
	 *
	 * @return global maxStart from config
	 */
	public int getGlobalLimit() {
		return globalLimit;
	}

	/**
	 * Per-project limits; first matching pattern applies.
	 *
	 * @return list of RepositoryLimit entries from config
	 */
	public List<RepositoryLimit> getProjectLimits() {
		return repositoryLimits;
	}

	private static List<RepositoryLimit> loadRepoLimits(Config cfg) throws Die {
		Set<String> patterns = cfg.getSubsections(REPO);
		List<RepositoryLimit> limits = new ArrayList<>();

		for (String pattern : patterns) {
			int max = cfg.getInt(REPO, pattern, MAX_START, -1);
			if (max <= 0) {
				continue;
			}
			try {
				limits.add(new RepositoryLimit(Pattern.compile(pattern), max));
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
