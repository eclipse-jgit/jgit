/*******************************************************************************
 * Copyright (C) 2014, Obeo
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
 *******************************************************************************/
package org.eclipse.jgit.util;

import java.io.File;
import java.util.regex.Pattern;

/**
 * This path matcher will check whether a given path matches a glob pattern.
 * <p>
 * For example, <code>*c</code> matches the path "abc", but does not match
 * "t/abc"; whereas <code>*&#47;*c</code> will match "t/abc" but not "abc".
 * <code>*.*</code> matches file names containing a dot and <code>a?c</code>
 * will match any file which names starts with 'a', finishes with 'c' and has
 * one character (any character) in between : it would match "abc" but not
 * "ac" or "abbc".
 * </p>
 * <p>
 * Java did not support the glob syntax until Java 7; this will convert glob
 * patterns into a system-dependent regex in order to support a subset of that
 * syntax : "**" to match over a whole hierarchy, "*" to match any sequence of
 * characters, "?" to match any single character.
 * </p>
 */
public class PathMatcher_Java5 implements PathMatcher {
	/** The pattern against which we'll match paths. */
	private final Pattern regex;

	/**
	 * @param globPattern
	 *            The pattern this instance should match against.
	 */
	public PathMatcher_Java5(String globPattern) {
		// converts a glob pattern to regex
		// though this only supports "**", "*" and "?"
		final boolean dosStyleSeparator = File.separatorChar == '\\';

		StringBuilder regexPattern = new StringBuilder("^"); //$NON-NLS-1$
		int i = 0;
		while (i < globPattern.length()) {
			char c = globPattern.charAt(i);
			switch (c) {
			case '/':
				if (dosStyleSeparator)
					regexPattern.append("\\\\"); //$NON-NLS-1$
				else
					regexPattern.append(c);
				break;
			case '*':
				if (i + 1 < globPattern.length()
						&& globPattern.charAt(i + 1) == '*') {
					regexPattern.append(".*"); //$NON-NLS-1$
					i++;
				} else if (dosStyleSeparator)
					regexPattern.append("[^\\\\]*?"); //$NON-NLS-1$
				else
					regexPattern.append("[^/]*?"); //$NON-NLS-1$
				break;
			case '?':
				if (dosStyleSeparator)
					regexPattern.append("[^\\\\]"); //$NON-NLS-1$
				else
					regexPattern.append("[^/]"); //$NON-NLS-1$
				break;
			default:
					regexPattern.append(c);
				break;
			}
			i++;
		}
		regexPattern.append('$');
		regex = Pattern.compile(regexPattern.toString());
	}

	public boolean matches(String path) {
		final String osString = new File(path).getPath();
		return regex.matcher(osString).matches();
	}
}
