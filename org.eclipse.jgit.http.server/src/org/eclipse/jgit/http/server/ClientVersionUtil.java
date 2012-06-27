/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.http.server;

import static org.eclipse.jgit.http.server.ServletUtils.isChunked;

import javax.servlet.http.HttpServletRequest;

/** Parses Git client User-Agent strings. */
public class ClientVersionUtil {
	private static final int[] v1_7_5 = { 1, 7, 5 };
	private static final int[] v1_7_8_6 = { 1, 7, 8, 6 };
	private static final int[] v1_7_9 = { 1, 7, 9 };

	/** @return maximum version array, indicating an invalid version of Git. */
	public static int[] invalidVersion() {
		return new int[] { Integer.MAX_VALUE };
	}

	/**
	 * Parse a Git client User-Agent header value.
	 *
	 * @param version
	 *            git client version string, of the form "git/1.7.9".
	 * @return components of the version string. {@link #invalidVersion()} if
	 *         the version string cannot be parsed.
	 */
	public static int[] parseVersion(String version) {
		if (version != null && version.startsWith("git/"))
			return splitVersion(version.substring("git/".length()));
		return invalidVersion();
	}

	private static int[] splitVersion(String versionString) {
		char[] str = versionString.toCharArray();
		int[] ver = new int[4];
		int end = 0;
		int acc = 0;
		for (int i = 0; i < str.length; i++) {
			char c = str[i];
			if ('0' <= c && c <= '9') {
				acc *= 10;
				acc += c - '0';
			} else if (c == '.') {
				if (end == ver.length)
					ver = grow(ver);
				ver[end++] = acc;
				acc = 0;
			} else if (c == 'g' && 0 < i && str[i - 1] == '.' && 0 < end) {
				// Non-tagged builds may contain a mangled git describe output.
				// "1.7.6.1.45.gbe0cc". The 45 isn't a valid component. Drop it.
				ver[end - 1] = 0;
				acc = 0;
				break;
			} else if (c == '-' && (i + 2) < str.length
					&& str[i + 1] == 'r' && str[i + 2] == 'c') {
				// Release candidates aren't the same as a final release.
				if (acc > 0)
					acc--;
				break;
			} else
				break;
		}
		if (acc != 0) {
			if (end == ver.length)
				ver = grow(ver);
			ver[end++] = acc;
		} else {
			while (0 < end && ver[end - 1] == 0)
				end--;
		}
		if (end < ver.length) {
			int[] n = new int[end];
			System.arraycopy(ver, 0, n, 0, end);
			ver = n;
		}
		return ver;
	}

	private static int[] grow(int[] tmp) {
		int[] n = new int[tmp.length + 1];
		System.arraycopy(tmp, 0, n, 0, tmp.length);
		return n;
	}

	/**
	 * Compare two version strings for natural ordering.
	 *
	 * @param a
	 *            first parsed version string.
	 * @param b
	 *            second parsed version string.
	 * @return <0 if a is before b; 0 if a equals b; >0 if a is after b.
	 */
	public static int compare(int[] a, int[] b) {
		for (int i = 0; i < a.length && i < b.length; i++) {
			int cmp = a[i] - b[i];
			if (cmp != 0)
				return cmp;
		}
		return a.length - b.length;
	}

	/**
	 * Convert a parsed version back to a string.
	 *
	 * @param ver
	 *            the parsed version array.
	 * @return a string, e.g. "1.6.6.0".
	 */
	public static String toString(int[] ver) {
		StringBuilder b = new StringBuilder();
		for (int v : ver) {
			if (b.length() > 0)
				b.append('.');
			b.append(v);
		}
		return b.toString();
	}

	/**
	 * Check if a Git client has the known push status bug.
	 * <p>
	 * These buggy clients do not display the status report from a failed push
	 * over HTTP.
	 *
	 * @param version
	 *            parsed version of the Git client software.
	 * @return true if the bug is present.
	 */
	public static boolean hasPushStatusBug(int[] version) {
		int cmp = compare(version, v1_7_8_6);
		if (cmp < 0)
			return true; // Everything before 1.7.8.6 is known broken.
		else if (cmp == 0)
			return false; // 1.7.8.6 contained the bug fix.

		if (compare(version, v1_7_9) <= 0)
			return true; // 1.7.9 shipped before 1.7.8.6 and has the bug.
		return false; // 1.7.9.1 and later are fixed.
	}

	/**
	 * Check if a Git client has the known chunked request body encoding bug.
	 * <p>
	 * Git 1.7.5 contains a unique bug where chunked requests are malformed.
	 * This applies to both fetch and push.
	 *
	 * @param version
	 *            parsed version of the Git client software.
	 * @param request
	 *            incoming HTTP request.
	 * @return true if the client has the chunked encoding bug.
	 */
	public static boolean hasChunkedEncodingRequestBug(
			int[] version, HttpServletRequest request) {
		return compare(version, v1_7_5) == 0 && isChunked(request);
	}

	private ClientVersionUtil() {
	}
}
