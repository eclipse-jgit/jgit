/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	/** {@inheritDoc} */
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
				match = matches(path, start, stop);
			} else {
				// Can't match if the path contains a slash if the pattern is
				// anchored at the beginning
				match = !beginning
						&& matches(path, lastSlash + 1, stop);
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
			if (end > start && matches(path, start, end)) {
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

	/** {@inheritDoc} */
	@Override
	public boolean matches(String segment, int startIncl, int endExcl) {
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
