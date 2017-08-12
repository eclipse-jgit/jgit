/*
 * Copyright (C) 2014, 2017 Andrey Loskutov <loskutov@gmx.de>
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
 * Generic string matcher
 */
public interface IMatcher {

	/**
	 * Matcher that does not match any pattern.
	 */
	public static final IMatcher NO_MATCH = new IMatcher() {
		@Override
		public boolean matches(String path, boolean assumeDirectory,
				boolean pathMatch) {
			return false;
		}

		@Override
		public boolean matches(String segment, int startIncl, int endExcl,
				boolean assumeDirectory) {
			return false;
		}
	};

	/**
	 * Matches entire given string
	 *
	 * @param path
	 *            string which is not null, but might be empty
	 * @param assumeDirectory
	 *            true to assume this path as directory (even if it doesn't end
	 *            with a slash)
	 * @param pathMatch
	 *            {@code true} if the match is for the full path: prefix-only
	 *            matches are not allowed, and {@link NameMatcher}s must match
	 *            only the last component (if they can -- they may not, if they
	 *            are anchored at the beginning)
	 * @return true if this matcher pattern matches given string
	 */
	boolean matches(String path, boolean assumeDirectory, boolean pathMatch);

	/**
	 * Matches only part of given string
	 *
	 * @param segment
	 *            string which is not null, but might be empty
	 * @param startIncl
	 *            start index, inclusive
	 * @param endExcl
	 *            end index, exclusive
	 * @param assumeDirectory
	 *            true to assume this path as directory (even if it doesn't end
	 *            with a slash)
	 * @return true if this matcher pattern matches given string
	 */
	boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory);
}
