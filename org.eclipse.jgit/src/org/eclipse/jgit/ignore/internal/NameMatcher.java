/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de>
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
package org.eclipse.jgit.ignore.internal;

import static org.eclipse.jgit.ignore.internal.Strings.getPathSeparator;

/**
 * Matcher built from patterns for file names (single path segments). This class
 * is immutable and thread safe.
 */
public class NameMatcher extends AbstractMatcher {

	final boolean beginning;

	final char slash;

	final String subPattern;

	NameMatcher(String pattern, Character pathSeparator, boolean dirOnly,
			boolean deleteBackslash) {
		super(pattern, dirOnly);
		slash = getPathSeparator(pathSeparator);
		if (deleteBackslash) {
			pattern = Strings.deleteBackslash(pattern);
		}
		beginning = pattern.length() == 0 ? false : pattern.charAt(0) == slash;
		if (!beginning) {
			this.subPattern = pattern;
		} else {
			this.subPattern = pattern.substring(1);
		}
	}

	@Override
	public boolean matches(String path, boolean assumeDirectory,
			boolean pathMatch) {
		// A NameMatcher's pattern does not contain a slash.
		int start = 0;
		int stop = path.length();
		if (stop > 0 && path.charAt(0) == slash) {
			start++;
		}
		if (pathMatch) {
			// Can match only after the last slash
			int lastSlash = path.lastIndexOf(slash, stop - 1);
			if (lastSlash == stop - 1) {
				// Skip trailing slash
				lastSlash = path.lastIndexOf(slash, lastSlash - 1);
				stop--;
			}
			boolean match;
			if (lastSlash < start) {
				match = matches(path, start, stop, assumeDirectory);
			} else {
				// Can't match if the path contains a slash if the pattern is
				// anchored at the beginning
				match = !beginning
						&& matches(path, lastSlash + 1, stop, assumeDirectory);
			}
			if (match && dirOnly) {
				match = assumeDirectory;
			}
			return match;
		}
		while (start < stop) {
			int end = path.indexOf(slash, start);
			if (end < 0) {
				end = stop;
			}
			if (end > start && matches(path, start, end, assumeDirectory)) {
				// make sure the directory matches: either if we are done with
				// segment and there is next one, or if the directory is assumed
				return !dirOnly || assumeDirectory || end < stop;
			}
			if (beginning) {
				break;
			}
			start = end + 1;
		}
		return false;
	}

	@Override
	public boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory) {
		// faster local access, same as in string.indexOf()
		String s = subPattern;
		int length = s.length();
		if (length != (endExcl - startIncl)) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			char c1 = s.charAt(i);
			char c2 = segment.charAt(i + startIncl);
			if (c1 != c2) {
				return false;
			}
		}
		return true;
	}

}
