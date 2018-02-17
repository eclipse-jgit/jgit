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

/**
 * Matcher for simple patterns ending with an asterisk, e.g. "Makefile.*"
 */
public class TrailingAsteriskMatcher extends NameMatcher {

	TrailingAsteriskMatcher(String pattern, Character pathSeparator, boolean dirOnly) {
		super(pattern, pathSeparator, dirOnly, true);

		if (subPattern.charAt(subPattern.length() - 1) != '*')
			throw new IllegalArgumentException(
					"Pattern must have trailing asterisk: " + pattern); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public boolean matches(String segment, int startIncl, int endExcl) {
		// faster local access, same as in string.indexOf()
		String s = subPattern;
		// we don't need to count '*' character itself
		int subLenth = s.length() - 1;
		// simple /*/ pattern
		if (subLenth == 0)
			return true;

		if (subLenth > (endExcl - startIncl))
			return false;

		for (int i = 0; i < subLenth; i++) {
			char c1 = s.charAt(i);
			char c2 = segment.charAt(i + startIncl);
			if (c1 != c2)
				return false;
		}
		return true;
	}

}
