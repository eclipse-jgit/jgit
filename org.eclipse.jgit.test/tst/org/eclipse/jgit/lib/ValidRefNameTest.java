/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.junit.Assert.assertEquals;

import org.junit.Test;

public class ValidRefNameTest {
	private static void assertValid(final boolean exp, final String name) {
		assertEquals("\"" + name + "\"", exp, Repository.isValidRefName(name));
	}

	@Test
	public void testEmptyString() {
		assertValid(false, "");
		assertValid(false, "/");
	}

	@Test
	public void testMustHaveTwoComponents() {
		assertValid(false, "master");
		assertValid(true, "heads/master");
	}

	@Test
	public void testValidHead() {
		assertValid(true, "refs/heads/master");
		assertValid(true, "refs/heads/pu");
		assertValid(true, "refs/heads/z");
		assertValid(true, "refs/heads/FoO");
	}

	@Test
	public void testValidTag() {
		assertValid(true, "refs/tags/v1.0");
	}

	@Test
	public void testNoLockSuffix() {
		assertValid(false, "refs/heads/master.lock");
	}

	@Test
	public void testNoDirectorySuffix() {
		assertValid(false, "refs/heads/master/");
	}

	@Test
	public void testNoSpace() {
		assertValid(false, "refs/heads/i haz space");
	}

	@Test
	public void testNoAsciiControlCharacters() {
		for (char c = '\0'; c < ' '; c++)
			assertValid(false, "refs/heads/mast" + c + "er");
	}

	@Test
	public void testNoBareDot() {
		assertValid(false, "refs/heads/.");
		assertValid(false, "refs/heads/..");
		assertValid(false, "refs/heads/./master");
		assertValid(false, "refs/heads/../master");
	}

	@Test
	public void testNoLeadingOrTrailingDot() {
		assertValid(false, ".");
		assertValid(false, "refs/heads/.bar");
		assertValid(false, "refs/heads/..bar");
		assertValid(false, "refs/heads/bar.");
	}

	@Test
	public void testContainsDot() {
		assertValid(true, "refs/heads/m.a.s.t.e.r");
		assertValid(false, "refs/heads/master..pu");
	}

	@Test
	public void testNoMagicRefCharacters() {
		assertValid(false, "refs/heads/master^");
		assertValid(false, "refs/heads/^master");
		assertValid(false, "^refs/heads/master");

		assertValid(false, "refs/heads/master~");
		assertValid(false, "refs/heads/~master");
		assertValid(false, "~refs/heads/master");

		assertValid(false, "refs/heads/master:");
		assertValid(false, "refs/heads/:master");
		assertValid(false, ":refs/heads/master");
	}

	@Test
	public void testShellGlob() {
		assertValid(false, "refs/heads/master?");
		assertValid(false, "refs/heads/?master");
		assertValid(false, "?refs/heads/master");

		assertValid(false, "refs/heads/master[");
		assertValid(false, "refs/heads/[master");
		assertValid(false, "[refs/heads/master");

		assertValid(false, "refs/heads/master*");
		assertValid(false, "refs/heads/*master");
		assertValid(false, "*refs/heads/master");
	}

	@Test
	public void testValidSpecialCharacters() {
		assertValid(true, "refs/heads/!");
		assertValid(true, "refs/heads/\"");
		assertValid(true, "refs/heads/#");
		assertValid(true, "refs/heads/$");
		assertValid(true, "refs/heads/%");
		assertValid(true, "refs/heads/&");
		assertValid(true, "refs/heads/'");
		assertValid(true, "refs/heads/(");
		assertValid(true, "refs/heads/)");
		assertValid(true, "refs/heads/+");
		assertValid(true, "refs/heads/,");
		assertValid(true, "refs/heads/-");
		assertValid(true, "refs/heads/;");
		assertValid(true, "refs/heads/<");
		assertValid(true, "refs/heads/=");
		assertValid(true, "refs/heads/>");
		assertValid(true, "refs/heads/@");
		assertValid(true, "refs/heads/]");
		assertValid(true, "refs/heads/_");
		assertValid(true, "refs/heads/`");
		assertValid(true, "refs/heads/{");
		assertValid(true, "refs/heads/|");
		assertValid(true, "refs/heads/}");

		// This is valid on UNIX, but not on Windows
		// hence we make in invalid due to non-portability
		//
		assertValid(false, "refs/heads/\\");
	}

	@Test
	public void testUnicodeNames() {
		assertValid(true, "refs/heads/\u00e5ngstr\u00f6m");
	}

	@Test
	public void testRefLogQueryIsValidRef() {
		assertValid(false, "refs/heads/master@{1}");
		assertValid(false, "refs/heads/master@{1.hour.ago}");
	}
}
