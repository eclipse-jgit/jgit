/*
 * Copyright (C) 2010, Red Hat Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import org.eclipse.jgit.junit.Assert;
import org.junit.Test;

/**
 * Tests ignore pattern matches
 */
public class IgnoreMatcherParametrizedTest {

	@Test
	public void testBasic() {
		String pattern = "/test.stp";
		assertMatched(pattern, "/test.stp");

		pattern = "#/test.stp";
		assertNotMatched(pattern, "/test.stp");
	}

	@Test
	public void testFileNameWildcards() {
		// Test basic * and ? for any pattern + any character
		String pattern = "*.st?";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/anothertest.stg");
		assertMatched(pattern, "/anothertest.st0");
		assertNotMatched(pattern, "/anothertest.sta1");
		// Check that asterisk does not expand to "/"
		assertNotMatched(pattern, "/another/test.sta1");

		// Same as above, with a leading slash to ensure that doesn't cause
		// problems
		pattern = "/*.st?";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/anothertest.stg");
		assertMatched(pattern, "/anothertest.st0");
		assertNotMatched(pattern, "/anothertest.sta1");
		// Check that asterisk does not expand to "/"
		assertNotMatched(pattern, "/another/test.sta1");

		// Test for numbers
		pattern = "*.sta[0-5]";
		assertMatched(pattern, "/test.sta5");
		assertMatched(pattern, "/test.sta4");
		assertMatched(pattern, "/test.sta3");
		assertMatched(pattern, "/test.sta2");
		assertMatched(pattern, "/test.sta1");
		assertMatched(pattern, "/test.sta0");
		assertMatched(pattern, "/anothertest.sta2");
		assertNotMatched(pattern, "test.stag");
		assertNotMatched(pattern, "test.sta6");

		// Test for letters
		pattern = "/[tv]est.sta[a-d]";
		assertMatched(pattern, "/test.staa");
		assertMatched(pattern, "/test.stab");
		assertMatched(pattern, "/test.stac");
		assertMatched(pattern, "/test.stad");
		assertMatched(pattern, "/vest.stac");
		assertNotMatched(pattern, "test.stae");
		assertNotMatched(pattern, "test.sta9");

		// Test child directory/file is matched
		pattern = "/src/ne?";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new.c");

		// Test name-only fnmatcher matches
		pattern = "ne?";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/new/a/a.c");
		assertMatched(pattern, "/neb");
		assertNotMatched(pattern, "/src/new.c");
	}

	@Test
	public void testTargetWithoutLeadingSlash() {
		// Test basic * and ? for any pattern + any character
		String pattern = "/*.st?";
		assertMatched(pattern, "test.stp");
		assertMatched(pattern, "anothertest.stg");
		assertMatched(pattern, "anothertest.st0");
		assertNotMatched(pattern, "anothertest.sta1");
		// Check that asterisk does not expand to ""
		assertNotMatched(pattern, "another/test.sta1");

		// Same as above, with a leading slash to ensure that doesn't cause
		// problems
		pattern = "/*.st?";
		assertMatched(pattern, "test.stp");
		assertMatched(pattern, "anothertest.stg");
		assertMatched(pattern, "anothertest.st0");
		assertNotMatched(pattern, "anothertest.sta1");
		// Check that asterisk does not expand to ""
		assertNotMatched(pattern, "another/test.sta1");

		// Test for numbers
		pattern = "/*.sta[0-5]";
		assertMatched(pattern, "test.sta5");
		assertMatched(pattern, "test.sta4");
		assertMatched(pattern, "test.sta3");
		assertMatched(pattern, "test.sta2");
		assertMatched(pattern, "test.sta1");
		assertMatched(pattern, "test.sta0");
		assertMatched(pattern, "anothertest.sta2");
		assertNotMatched(pattern, "test.stag");
		assertNotMatched(pattern, "test.sta6");

		// Test for letters
		pattern = "/[tv]est.sta[a-d]";
		assertMatched(pattern, "test.staa");
		assertMatched(pattern, "test.stab");
		assertMatched(pattern, "test.stac");
		assertMatched(pattern, "test.stad");
		assertMatched(pattern, "vest.stac");
		assertNotMatched(pattern, "test.stae");
		assertNotMatched(pattern, "test.sta9");

		// Test child directory/file is matched
		pattern = "/src/ne?";
		assertMatched(pattern, "src/new/");
		assertMatched(pattern, "src/new");
		assertMatched(pattern, "src/new/a.c");
		assertMatched(pattern, "src/new/a/a.c");
		assertNotMatched(pattern, "src/new.c");

		// Test name-only fnmatcher matches
		pattern = "ne?";
		assertMatched(pattern, "src/new/");
		assertMatched(pattern, "src/new");
		assertMatched(pattern, "src/new/a.c");
		assertMatched(pattern, "src/new/a/a.c");
		assertMatched(pattern, "neb");
		assertNotMatched(pattern, "src/new.c");
	}

	@Test
	public void testParentDirectoryGitIgnores() {
		// Contains git ignore patterns such as might be seen in a parent
		// directory

		// Test for wildcards
		String pattern = "/*/*.c";
		assertMatched(pattern, "/file/a.c");
		assertMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");

		// Test child directory/file is matched
		pattern = "/src/new";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new.c");

		// Test child directory is matched, slash after name
		pattern = "/src/new/";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new.c");

		// Test directory is matched by name only
		pattern = "b1";
		assertMatched(pattern, "/src/new/a/b1/a.c");
		assertNotMatched(pattern, "/src/new/a/b2/file.c");
		assertNotMatched(pattern, "/src/new/a/bb1/file.c");
		assertNotMatched(pattern, "/src/new/a/file.c");
	}

	@Test
	public void testDirModeAndNoRegex() {
		String pattern = "/src/";
		assertMatched(pattern, "/src/");
		assertMatched(pattern, "/src/new");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/a.c");
		// no match as a "file" pattern, because rule is for directories only
		assertNotMatched(pattern, "/src");
		assertNotMatched(pattern, "/srcA/");
	}

	@Test
	public void testDirModeAndRegex1() {
		String pattern = "a/*/src/";
		assertMatched(pattern, "a/b/src/");
		assertMatched(pattern, "a/b/src/new");
		assertMatched(pattern, "a/b/src/new/a.c");
		assertMatched(pattern, "a/b/src/a.c");
		// no match as a "file" pattern, because rule is for directories only
		assertNotMatched(pattern, "a/b/src");
		assertNotMatched(pattern, "a/b/srcA/");
	}

	@Test
	public void testDirModeAndRegex2() {
		String pattern = "a/[a-b]/src/";
		assertMatched(pattern, "a/b/src/");
		assertMatched(pattern, "a/b/src/new");
		assertMatched(pattern, "a/b/src/new/a.c");
		assertMatched(pattern, "a/b/src/a.c");
		// no match as a "file" pattern, because rule is for directories only
		assertNotMatched(pattern, "a/b/src");
		assertNotMatched(pattern, "a/b/srcA/");
	}

	@Test
	public void testDirModeAndRegex3() {
		String pattern = "**/src/";
		assertMatched(pattern, "a/b/src/");
		assertMatched(pattern, "a/b/src/new");
		assertMatched(pattern, "a/b/src/new/a.c");
		assertMatched(pattern, "a/b/src/a.c");
		// no match as a "file" pattern, because rule is for directories only
		assertNotMatched(pattern, "a/b/src");
		assertNotMatched(pattern, "a/b/srcA/");
	}

	@Test
	public void testNameOnlyMatches() {
		/*
		 * Name-only matches do not contain any path separators
		 */
		// Test matches for file extension
		String pattern = "*.stp";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/src/test.stp");
		assertNotMatched(pattern, "/test.stp1");
		assertNotMatched(pattern, "/test.astp");

		// Test matches for name-only, applies to file name or folder name
		pattern = "src";
		assertMatched(pattern, "/src");
		assertMatched(pattern, "/src/");
		assertMatched(pattern, "/src/a.c");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/file/src");

		// Test matches for name-only, applies only to folder names
		pattern = "src/";
		assertMatched(pattern, "/src/");
		assertMatched(pattern, "/src/a.c");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/new/src/a.c");
		assertNotMatched(pattern, "/src");
		assertNotMatched(pattern, "/file/src");

		// Test matches for name-only, applies to file name or folder name
		// With a small wildcard
		pattern = "?rc";
		assertMatched(pattern, "/src/a.c");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/file/src");
		assertMatched(pattern, "/src/");

		// Test matches for name-only, applies to file name or folder name
		// With a small wildcard
		pattern = "?r[a-c]";
		assertMatched(pattern, "/src/a.c");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/file/src");
		assertMatched(pattern, "/src/");
		assertMatched(pattern, "/srb/a.c");
		assertMatched(pattern, "/grb/new/a.c");
		assertMatched(pattern, "/new/crb/a.c");
		assertMatched(pattern, "/file/3rb");
		assertMatched(pattern, "/xrb/");
		assertMatched(pattern, "/3ra/a.c");
		assertMatched(pattern, "/5ra/new/a.c");
		assertMatched(pattern, "/new/1ra/a.c");
		assertMatched(pattern, "/file/dra");
		assertMatched(pattern, "/era/");
		assertNotMatched(pattern, "/crg");
		assertNotMatched(pattern, "/cr3");
	}

	@Test
	public void testNegation() {
		String pattern = "!/test.stp";
		assertMatched(pattern, "/test.stp");
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitignore file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 * @param assume
	 */
	private void assertMatched(String pattern, String target, Boolean... assume) {
		boolean value = match(pattern, target);
		if (assume.length == 0 || !assume[0].booleanValue())
			assertTrue("Expected a match for: " + pattern + " with: " + target,
					value);
		else
			assumeTrue("Expected a match for: " + pattern + " with: " + target,
					value);
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitignore file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 * @param assume
	 */
	private void assertNotMatched(String pattern, String target,
			Boolean... assume) {
		boolean value = match(pattern, target);
		if (assume.length == 0 || !assume[0].booleanValue())
			assertFalse("Expected no match for: " + pattern + " with: "
					+ target, value);
		else
			assumeFalse("Expected no match for: " + pattern + " with: "
					+ target, value);
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitignore file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 * @return Result of {@link FastIgnoreRule#isMatch(String, boolean)}
	 */
	private boolean match(String pattern, String target) {
		boolean isDirectory = target.endsWith("/");
		boolean match;
		FastIgnoreRule r = new FastIgnoreRule(pattern);
		match = r.isMatch(target, isDirectory);

		if (isDirectory) {
			boolean noTrailingSlash = matchAsDir(pattern,
					target.substring(0, target.length() - 1));
			if (match != noTrailingSlash) {
				String message = "Difference in result for directory pattern: "
						+ pattern + " with: " + target
						+ " if target is given without trailing slash";
				Assert.assertEquals(message, match, noTrailingSlash);
			}
		}
		return match;
	}

	/**
	 *
	 * @param target
	 *            must not ends with a slash!
	 * @param pattern
	 *            same as {@link #match(String, String)}
	 * @return same as {@link #match(String, String)}
	 */
	private boolean matchAsDir(String pattern, String target) {
		assertFalse(target.endsWith("/"));
		FastIgnoreRule r = new FastIgnoreRule(pattern);
		return r.isMatch(target, true);
	}
}
