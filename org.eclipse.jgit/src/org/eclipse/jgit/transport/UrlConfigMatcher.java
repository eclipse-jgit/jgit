/*
 * Copyright (C) 2026, JGit contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the best matching URL subsection from a config section.
 */
final class UrlConfigMatcher {

	private static final Logger LOG = LoggerFactory
			.getLogger(UrlConfigMatcher.class);

	private static final String FTP = "ftp"; //$NON-NLS-1$

	private UrlConfigMatcher() {
		// Utility class; do not instantiate.
	}

	static String findMatch(Set<String> names, URIish uri) {
		String bestMatch = null;
		int bestMatchLength = -1;
		boolean withUser = false;
		String uriPath = uri.getPath();
		boolean hasPath = !StringUtils.isEmptyOrNull(uriPath);
		if (hasPath) {
			uriPath = normalize(uriPath);
			if (uriPath == null) {
				return null;
			}
		}
		for (String name : names) {
			try {
				URIish candidate = new URIish(name);
				if (!compare(uri.getScheme(), candidate.getScheme())
						|| !compare(uri.getHost(), candidate.getHost())) {
					continue;
				}
				if (defaultedPort(uri.getPort(),
						uri.getScheme()) != defaultedPort(candidate.getPort(),
								candidate.getScheme())) {
					continue;
				}
				boolean hasUser = false;
				if (candidate.getUser() != null) {
					if (!candidate.getUser().equals(uri.getUser())) {
						continue;
					}
					hasUser = true;
				}
				String candidatePath = candidate.getPath();
				int matchLength = -1;
				if (StringUtils.isEmptyOrNull(candidatePath)) {
					matchLength = 0;
				} else {
					if (!hasPath) {
						continue;
					}
					matchLength = segmentCompare(uriPath, candidatePath);
					if (matchLength < 0) {
						continue;
					}
				}
				if (matchLength > bestMatchLength
						|| (!withUser && hasUser && matchLength >= 0
								&& matchLength == bestMatchLength)) {
					bestMatch = name;
					bestMatchLength = matchLength;
					withUser = hasUser;
				}
			} catch (URISyntaxException e) {
				LOG.warn(MessageFormat
						.format(JGitText.get().httpConfigInvalidURL, name));
			}
		}
		return bestMatch;
	}

	static int segmentCompare(String uriPath, String match) {
		String matchPath = normalize(match);
		if (matchPath == null || !uriPath.startsWith(matchPath)) {
			return -1;
		}
		int uriLength = uriPath.length();
		int matchLength = matchPath.length();
		if (matchLength == uriLength || matchPath.charAt(matchLength - 1) == '/'
				|| (matchLength < uriLength
						&& uriPath.charAt(matchLength) == '/')) {
			return matchLength;
		}
		return -1;
	}

	static String normalize(String path) {
		int index = 0;
		int length = path.length();
		StringBuilder builder = new StringBuilder(length);
		builder.append('/');
		if (length > 0 && path.charAt(0) == '/') {
			index = 1;
		}
		while (index < length) {
			int slash = path.indexOf('/', index);
			if (slash < 0) {
				slash = length;
			}
			if (slash == index
					|| (slash == index + 1 && path.charAt(index) == '.')) {
				// Skip /. or also double slashes.
			} else if (slash == index + 2 && path.charAt(index) == '.'
					&& path.charAt(index + 1) == '.') {
				int previous = builder.length() - 2;
				while (previous >= 0 && builder.charAt(previous) != '/') {
					previous--;
				}
				if (previous < 0) {
					LOG.warn(MessageFormat.format(
							JGitText.get().httpConfigCannotNormalizeURL, path));
					return null;
				}
				builder.setLength(previous + 1);
			} else {
				builder.append(path, index, Math.min(length, slash + 1));
			}
			index = slash + 1;
		}
		if (builder.length() > 1 && builder.charAt(builder.length() - 1) == '/'
				&& length > 0 && path.charAt(length - 1) != '/') {
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
	}

	private static boolean compare(String first, String second) {
		if (first == null) {
			return second == null;
		}
		return first.equalsIgnoreCase(second);
	}

	private static int defaultedPort(int port, String scheme) {
		if (port >= 0) {
			return port;
		}
		if (FTP.equalsIgnoreCase(scheme)) {
			return 21;
		} else if (HttpConfig.HTTP.equalsIgnoreCase(scheme)) {
			return 80;
		}
		return 443;
	}
}