/*
 * Copyright (C) 2010, Red Hat Inc.
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
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests ignore pattern matches
 */
@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class IgnoreMatcherParametrizedTest {

	@Parameters(name = "OldRule? {0}")
	public static Iterable<Boolean[]> data() {
		return Arrays.asList(new Boolean[][] { { Boolean.FALSE },
				{ Boolean.TRUE } });
	}

	@Parameter
	public Boolean useOldRule;

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
	public void testTrailingSlash() {
		String pattern = "/src/";
		assertMatched(pattern, "/src/");
		assertMatched(pattern, "/src/new");
		assertMatched(pattern, "/src/new/a.c");
		assertMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src");
		assertNotMatched(pattern, "/srcA/");
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
	 */
	public void assertMatched(String pattern, String target) {
		boolean value = match(pattern, target);
		assertTrue("Expected a match for: " + pattern + " with: " + target,
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
	 */
	public void assertNotMatched(String pattern, String target) {
		boolean value = match(pattern, target);
		assertFalse("Expected no match for: " + pattern + " with: " + target,
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
	 * @return Result of {@link FastIgnoreRule#isMatch(String, boolean)}
	 */
	private boolean match(String pattern, String target) {
		boolean isDirectory = target.endsWith("/");
		if (useOldRule.booleanValue()) {
			IgnoreRule r = new IgnoreRule(pattern);
			// If speed of this test is ever an issue, we can use a presetRule
			// field
			// to avoid recompiling a pattern each time.
			return r.isMatch(target, isDirectory);
		}
		FastIgnoreRule r = new FastIgnoreRule(pattern);
		// If speed of this test is ever an issue, we can use a presetRule field
		// to avoid recompiling a pattern each time.
		return r.isMatch(target, isDirectory);
	}
}
