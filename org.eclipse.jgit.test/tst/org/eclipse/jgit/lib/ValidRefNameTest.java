/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class ValidRefNameTest {
	private static void assertValid(boolean exp, String name) {
		SystemReader instance = SystemReader.getInstance();
		try {
			setUnixSystemReader();
			assertEquals("\"" + name + "\"", exp,
					Repository.isValidRefName(name));
			setWindowsSystemReader();
			assertEquals("\"" + name + "\"", exp,
					Repository.isValidRefName(name));
		} finally {
			SystemReader.setInstance(instance);
		}
	}

	private static void setWindowsSystemReader() {
		SystemReader.setInstance(new MockSystemReader() {
			{
				setWindows();
			}
		});
	}

	private static void setUnixSystemReader() {
		SystemReader.setInstance(new MockSystemReader() {
			{
				setUnix();
			}
		});
	}

	private static void assertInvalidOnWindows(String name) {
		SystemReader instance = SystemReader.getInstance();
		try {
			setUnixSystemReader();
			assertEquals("\"" + name + "\"", true,
					Repository.isValidRefName(name));
			setWindowsSystemReader();
			assertEquals("\"" + name + "\"", false,
					Repository.isValidRefName(name));
		} finally {
			SystemReader.setInstance(instance);
		}
	}

	private static void assertNormalized(final String name,
			final String expected) {
		SystemReader instance = SystemReader.getInstance();
		try {
			setUnixSystemReader();
			String normalized = Repository.normalizeBranchName(name);
			assertEquals("Normalization of " + name, expected, normalized);
			assertEquals("\"" + normalized + "\"", true,
					Repository.isValidRefName(Constants.R_HEADS + normalized));
			setWindowsSystemReader();
			normalized = Repository.normalizeBranchName(name);
			assertEquals("Normalization of " + name, expected, normalized);
			assertEquals("\"" + normalized + "\"", true,
					Repository.isValidRefName(Constants.R_HEADS + normalized));
		} finally {
			SystemReader.setInstance(instance);
		}
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
	public void testValidSpecialCharacterUnixs() {
		assertValid(true, "refs/heads/!");
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
		assertValid(true, "refs/heads/=");
		assertValid(true, "refs/heads/@");
		assertValid(true, "refs/heads/]");
		assertValid(true, "refs/heads/_");
		assertValid(true, "refs/heads/`");
		assertValid(true, "refs/heads/{");
		assertValid(true, "refs/heads/}");

		// This is valid on UNIX, but not on Windows
		// hence we make in invalid due to non-portability
		//
		assertValid(false, "refs/heads/\\");

		// More invalid characters on Windows, but we allow them
		assertInvalidOnWindows("refs/heads/\"");
		assertInvalidOnWindows("refs/heads/<");
		assertInvalidOnWindows("refs/heads/>");
		assertInvalidOnWindows("refs/heads/|");
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

	@Test
	public void testWindowsReservedNames() {
		// re-using code from DirCacheCheckoutTest, hence
		// only testing for one of the special names.
		assertInvalidOnWindows("refs/heads/con");
		assertInvalidOnWindows("refs/con/x");
		assertInvalidOnWindows("con/heads/x");
		assertValid(true, "refs/heads/conx");
		assertValid(true, "refs/heads/xcon");
	}

	@Test
	public void testNormalizeBranchName() {
		assertEquals("", Repository.normalizeBranchName(null));
		assertEquals("", Repository.normalizeBranchName(""));
		assertNormalized("Bug 12345::::Hello World", "Bug_12345-Hello_World");
		assertNormalized("Bug 12345 :::: Hello World", "Bug_12345_Hello_World");
		assertNormalized("Bug 12345 :::: Hello::: World",
				"Bug_12345_Hello-World");
		assertNormalized(":::Bug 12345 - Hello World", "Bug_12345_Hello_World");
		assertNormalized("---Bug 12345 - Hello World", "Bug_12345_Hello_World");
		assertNormalized("Bug 12345 ---- Hello --- World",
				"Bug_12345_Hello_World");
		assertNormalized("Bug 12345 - Hello World!", "Bug_12345_Hello_World!");
		assertNormalized("Bug 12345 : Hello World!", "Bug_12345_Hello_World!");
		assertNormalized("Bug 12345 _ Hello World!", "Bug_12345_Hello_World!");
		assertNormalized("Bug 12345   -       Hello World!",
				"Bug_12345_Hello_World!");
		assertNormalized(" Bug 12345   -   Hello World! ",
				"Bug_12345_Hello_World!");
		assertNormalized(" Bug 12345   -   Hello World!   ",
				"Bug_12345_Hello_World!");
		assertNormalized("Bug 12345   -   Hello______ World!",
				"Bug_12345_Hello_World!");
		assertNormalized("_Bug 12345 - Hello World!", "Bug_12345_Hello_World!");
	}

	@Test
	public void testNormalizeWithSlashes() {
		assertNormalized("foo/bar/baz", "foo/bar/baz");
		assertNormalized("foo/bar.lock/baz.lock", "foo/bar_lock/baz_lock");
		assertNormalized("foo/.git/.git~1/bar", "foo/git/git-1/bar");
		assertNormalized(".foo/aux/con/com3.txt/com0/prn/lpt1",
				"foo/+aux/+con/+com3.txt/com0/+prn/+lpt1");
		assertNormalized("foo/../bar///.--ba...z", "foo/bar/ba.z");
	}

	@Test
	public void testNormalizeWithUnicode() {
		assertNormalized("f\u00f6\u00f6/.b\u00e0r/*[<>|^~/b\u00e9\\z",
				"f\u00f6\u00f6/b\u00e0r/b\u00e9-z");
		assertNormalized("\u5165\u53e3 entrance;/.\u51fa\u53e3_*ex*it*/",
				"\u5165\u53e3_entrance;/\u51fa\u53e3_ex-it");
	}

	@Test
	public void testNormalizeAlreadyValidRefName() {
		assertNormalized("refs/heads/m.a.s.t.e.r", "refs/heads/m.a.s.t.e.r");
		assertNormalized("refs/tags/v1.0-20170223", "refs/tags/v1.0-20170223");
	}

	@Test
	public void testNormalizeTrimmedUnicodeAlreadyValidRefName() {
		assertNormalized(" \u00e5ngstr\u00f6m\t", "\u00e5ngstr\u00f6m");
	}
}
