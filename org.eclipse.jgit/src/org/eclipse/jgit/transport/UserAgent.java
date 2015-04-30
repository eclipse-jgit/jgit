/*
 * Copyright (C) 2015, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.transport;

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
			if (ver != null)
				return ver;
		}
		return "unknown"; //$NON-NLS-1$
	}

	private static String clean(String s) {
		s = s.trim();
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c <= 32 || c >= 127) {
				if (i > 0 && b.charAt(i - 1) == '.')
					continue;
				c = '.';
			}
			b.append(c);
		}
		return b.toString();
	}

	/**
	 * Get the user agent string advertised by JGit.
	 *
	 * @return a string similar to {@code "JGit/4.0"}.
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
	 *            new user agent string.
	 */
	public static void set(String agent) {
		userAgent = clean(agent);
	}

	private UserAgent() {
	}
}
