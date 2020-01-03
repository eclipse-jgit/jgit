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

/**
 * Matcher for simple regex patterns starting with an asterisk, e.g. "*.tmp"
 */
public class LeadingAsteriskMatcher extends NameMatcher {

	LeadingAsteriskMatcher(String pattern, Character pathSeparator, boolean dirOnly) {
		super(pattern, pathSeparator, dirOnly, true);

		if (subPattern.charAt(0) != '*')
			throw new IllegalArgumentException(
					"Pattern must have leading asterisk: " + pattern); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public boolean matches(String segment, int startIncl, int endExcl) {
		// faster local access, same as in string.indexOf()
		String s = subPattern;

		// we don't need to count '*' character itself
		int subLength = s.length() - 1;
		// simple /*/ pattern
		if (subLength == 0)
			return true;

		if (subLength > (endExcl - startIncl))
			return false;

		for (int i = subLength, j = endExcl - 1; i > 0; i--, j--) {
			char c1 = s.charAt(i);
			char c2 = segment.charAt(j);
			if (c1 != c2)
				return false;
		}
		return true;
	}

}
