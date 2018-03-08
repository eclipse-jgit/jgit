/*
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
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
package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class RawSubStringPatternTest extends RepositoryTestCase {

	@Test
	public void testBoundary() {
		assertMatchResult("a", "a", 0);
		assertMatchResult("a", "abcd", 0);
		assertMatchResult("ab", "abcd", 0);
		assertMatchResult("abcd", "abcd", 0);
		assertMatchResult("bc", "abcd", 1);
		assertMatchResult("bcd", "abcd", 1);
		assertMatchResult("cd", "abcd", 2);
		assertMatchResult("d", "abcd", 3);
		assertMatchResult("abab", "abaabab", 3);
	}

	@Test
	public void testNoMatches() {
		assertMatchResult("a", "", -1);
		assertMatchResult("a", "b", -1);
		assertMatchResult("abab", "abaaba", -1);
		assertMatchResult("ab", "ddda", -1);
	}

	@Test
	public void testCaseInsensitive() {
		assertMatchResult("a", "A", 0);
		assertMatchResult("A", "a", 0);
		assertMatchResult("Ab", "aB", 0);
		assertMatchResult("aB", "Ab", 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyPattern() {
		assertNotNull(new RawSubStringPattern(""));
	}

	private static void assertMatchResult(String pattern, String input, int position) {
		RawSubStringPattern p = new RawSubStringPattern(pattern);
		assertEquals("Expected match result " + position + " with input "
				+ input + " for pattern " + pattern,
				position, p.match(raw(input)));
	}

	private static RawCharSequence raw(String text) {
		byte[] bytes = text.getBytes(CHARSET);
		return new RawCharSequence(bytes, 0, bytes.length);
	}
}
