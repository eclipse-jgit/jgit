/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;

import java.util.Set;

import org.eclipse.jgit.util.StringUtils;

/**
 * User agent to be reported by this JGit client and server on the network.
 * <p>
 * On HTTP transports this user agent string is always supplied by the JGit
 * client in the {@code User-Agent} HTTP header.
 * <p>
 * On native transports this user agent string is always sent when JGit is a
 * server. When JGit is a client the user agent string will be supplied to the
 * remote server only if the remote server advertises its own agent identity.
 *
 * @since 4.0
 */
public class UserAgent {
	private static volatile String userAgent = computeUserAgent();

	private static String computeUserAgent() {
		return clean("JGit/" + computeVersion()); //$NON-NLS-1$
	}

	private static String computeVersion() {
		Package pkg = UserAgent.class.getPackage();
		if (pkg != null) {
			String ver = pkg.getImplementationVersion();
			if (!StringUtils.isEmptyOrNull(ver)) {
				return ver;
			}
		}
		return "unknown"; //$NON-NLS-1$
	}

	static String clean(String s) {
		s = s.trim();
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c <= 32 || c >= 127) {
				if (b.length() > 0 && b.charAt(b.length() - 1) == '.')
					continue;
				c = '.';
			}
			b.append(c);
		}
		return b.length() > 0 ? b.toString() : null;
	}

	/**
	 * Get the user agent string advertised by JGit.
	 *
	 * @return a string similar to {@code "JGit/4.0"}; null if the agent has
	 *         been cleared and should not be shared with a peer.
	 */
	public static String get() {
		return userAgent;
	}

	/**
	 * Change the user agent string advertised by JGit.
	 * <p>
	 * The new string should start with {@code "JGit/"} (for example
	 * {@code "JGit/4.0"}) to advertise the implementation as JGit based.
	 * <p>
	 * Spaces and other whitespace should be avoided as these will be
	 * automatically converted to {@code "."}.
	 * <p>
	 * User agent strings are restricted to printable ASCII.
	 *
	 * @param agent
	 *            new user agent string for this running JGit library. Setting
	 *            to null or empty string will avoid sending any identification
	 *            to the peer.
	 */
	public static void set(String agent) {
		userAgent = StringUtils.isEmptyOrNull(agent) ? null : clean(agent);
	}

	static String getAgent(Set<String> options, String transportAgent) {
		if (options == null || options.isEmpty()) {
			return transportAgent;
		}
		for (String o : options) {
			if (o.startsWith(OPTION_AGENT)
					&& o.length() > OPTION_AGENT.length()
					&& o.charAt(OPTION_AGENT.length()) == '=') {
				return o.substring(OPTION_AGENT.length() + 1);
			}
		}
		return transportAgent;
	}

	static boolean hasAgent(Set<String> options) {
		return getAgent(options, null) != null;
	}

	private UserAgent() {
	}
}
