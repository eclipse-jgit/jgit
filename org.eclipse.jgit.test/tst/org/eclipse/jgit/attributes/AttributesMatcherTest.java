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
package org.eclipse.jgit.attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests git attributes pattern matches
 */
public class AttributesMatcherTest {

	@Test
	public void testBasic() {
		String pattern = "/test.stp";
		assertMatched(pattern, "/test.stp");

		pattern = "#/test.stp";
		assertNotMatched(pattern, "/test.stp");
	}

	@Test
	public void testFileNameWildcards() {
		//Test basic * and ? for any pattern + any character
		String pattern = "*.st?";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/anothertest.stg");
		assertMatched(pattern, "/anothertest.st0");
		assertNotMatched(pattern, "/anothertest.sta1");
		//Check that asterisk does not expand to "/"
		assertNotMatched(pattern, "/another/test.sta1");

		//Same as above, with a leading slash to ensure that doesn't cause problems
		pattern = "/*.st?";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/anothertest.stg");
		assertMatched(pattern, "/anothertest.st0");
		assertNotMatched(pattern, "/anothertest.sta1");
		//Check that asterisk does not expand to "/"
		assertNotMatched(pattern, "/another/test.sta1");

		//Test for numbers
		pattern = "*.sta[0-5]";
		assertMatched(pattern,  "/test.sta5");
		assertMatched(pattern, "/test.sta4");
		assertMatched(pattern, "/test.sta3");
		assertMatched(pattern, "/test.sta2");
		assertMatched(pattern, "/test.sta1");
		assertMatched(pattern, "/test.sta0");
		assertMatched(pattern, "/anothertest.sta2");
		assertNotMatched(pattern, "test.stag");
		assertNotMatched(pattern, "test.sta6");

		//Test for letters
		pattern = "/[tv]est.sta[a-d]";
		assertMatched(pattern,  "/test.staa");
		assertMatched(pattern, "/test.stab");
		assertMatched(pattern, "/test.stac");
		assertMatched(pattern, "/test.stad");
		assertMatched(pattern, "/vest.stac");
		assertNotMatched(pattern, "test.stae");
		assertNotMatched(pattern, "test.sta9");

		//Test child directory/file is matched
		pattern = "/src/ne?";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new.c");

		//Test name-only fnmatcher matches
		pattern = "ne?";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/new/a/a.c");
		assertMatched(pattern, "/neb");
		assertNotMatched(pattern, "/src/new.c");
	}

	@Test
	public void testTargetWithoutLeadingSlash() {
		//Test basic * and ? for any pattern + any character
		String pattern = "/*.st?";
		assertMatched(pattern, "test.stp");
		assertMatched(pattern, "anothertest.stg");
		assertMatched(pattern, "anothertest.st0");
		assertNotMatched(pattern, "anothertest.sta1");
		//Check that asterisk does not expand to ""
		assertNotMatched(pattern, "another/test.sta1");

		//Same as above, with a leading slash to ensure that doesn't cause problems
		pattern = "/*.st?";
		assertMatched(pattern, "test.stp");
		assertMatched(pattern, "anothertest.stg");
		assertMatched(pattern, "anothertest.st0");
		assertNotMatched(pattern, "anothertest.sta1");
		//Check that asterisk does not expand to ""
		assertNotMatched(pattern, "another/test.sta1");

		//Test for numbers
		pattern = "/*.sta[0-5]";
		assertMatched(pattern,  "test.sta5");
		assertMatched(pattern, "test.sta4");
		assertMatched(pattern, "test.sta3");
		assertMatched(pattern, "test.sta2");
		assertMatched(pattern, "test.sta1");
		assertMatched(pattern, "test.sta0");
		assertMatched(pattern, "anothertest.sta2");
		assertNotMatched(pattern, "test.stag");
		assertNotMatched(pattern, "test.sta6");

		//Test for letters
		pattern = "/[tv]est.sta[a-d]";
		assertMatched(pattern,  "test.staa");
		assertMatched(pattern, "test.stab");
		assertMatched(pattern, "test.stac");
		assertMatched(pattern, "test.stad");
		assertMatched(pattern, "vest.stac");
		assertNotMatched(pattern, "test.stae");
		assertNotMatched(pattern, "test.sta9");

		//Test child directory/file is matched
		pattern = "/src/ne?";
		assertMatched(pattern, "src/new/");
		assertMatched(pattern, "src/new");
		assertNotMatched(pattern, "src/new/a.c");
		assertNotMatched(pattern, "src/new/a/a.c");
		assertNotMatched(pattern, "src/new.c");

		//Test name-only fnmatcher matches
		pattern = "ne?";
		assertMatched(pattern, "src/new/");
		assertMatched(pattern, "src/new");
		assertNotMatched(pattern, "src/new/a.c");
		assertNotMatched(pattern, "src/new/a/a.c");
		assertMatched(pattern, "neb");
		assertNotMatched(pattern, "src/new.c");
	}

	@Test
	public void testParentDirectoryGitAttributes() {
		//Contains git attribute patterns such as might be seen in a parent directory

		//Test for wildcards
		String pattern = "/*/*.c";
		assertMatched(pattern, "/file/a.c");
		assertMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");

		//Test child directory/file is matched
		pattern = "/src/new";
		assertMatched(pattern, "/src/new/");
		assertMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new.c");

		//Test child directory is matched, slash after name
		pattern = "/src/new/";
		assertMatched(pattern, "/src/new/");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/new/a/a.c");
		assertNotMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new.c");

		//Test directory is matched by name only
		pattern = "b1";
		assertNotMatched(pattern, "/src/new/a/b1/a.c");
		assertNotMatched(pattern, "/src/new/a/b2/file.c");
		assertNotMatched(pattern, "/src/new/a/bb1/file.c");
		assertNotMatched(pattern, "/src/new/a/file.c");
		assertNotMatched(pattern, "/src/new/a/bb1");
		assertMatched(pattern, "/src/new/a/b1");
	}

	@Test
	public void testTrailingSlash() {
		String pattern = "/src/";
		assertMatched(pattern, "/src/");
		assertNotMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src");
		assertNotMatched(pattern, "/srcA/");

		pattern = "src/";
		assertMatched(pattern, "src/");
		assertMatched(pattern, "/src/");
		assertNotMatched(pattern, "src");
		assertNotMatched(pattern, "/src/new");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "foo/src/a.c");
		assertNotMatched(pattern, "foo/src/bar/a.c");
		assertNotMatched(pattern, "foo/src/bar/src");
		assertMatched(pattern, "foo/src/");
		assertMatched(pattern, "foo/src/bar/src/");
	}

	@Test
	public void testNameOnlyMatches() {
		/*
		 * Name-only matches do not contain any path separators
		 */
		//Test matches for file extension
		String pattern = "*.stp";
		assertMatched(pattern, "/test.stp");
		assertMatched(pattern, "/src/test.stp");
		assertNotMatched(pattern, "/test.stp1");
		assertNotMatched(pattern, "/test.astp");
		assertNotMatched(pattern, "test.stp/foo.bar");
		assertMatched(pattern, "test.stp");
		assertMatched(pattern, "test.stp/");
		assertMatched(pattern, "test.stp/test.stp");

		//Test matches for name-only, applies to file name or folder name
		pattern = "src";
		assertMatched(pattern, "/src");
		assertMatched(pattern, "/src/");
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/file/src");

		//Test matches for name-only, applies only to folder names
		pattern = "src/";
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/new/src/a.c");
		assertNotMatched(pattern, "/src");
		assertNotMatched(pattern, "/file/src");
		assertMatched(pattern, "/file/src/");

		//Test matches for name-only, applies to file name or folder name
		//With a small wildcard
		pattern = "?rc";
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/new/src/");
		assertMatched(pattern, "/file/src");
		assertMatched(pattern, "/src/");

		//Test matches for name-only, applies to file name or folder name
		//With a small wildcard
		pattern = "?r[a-c]";
		assertNotMatched(pattern, "/src/a.c");
		assertNotMatched(pattern, "/src/new/a.c");
		assertNotMatched(pattern, "/new/src/a.c");
		assertMatched(pattern, "/file/src");
		assertMatched(pattern, "/src/");
		assertNotMatched(pattern, "/srb/a.c");
		assertNotMatched(pattern, "/grb/new/a.c");
		assertNotMatched(pattern, "/new/crb/a.c");
		assertMatched(pattern, "/file/3rb");
		assertMatched(pattern, "/xrb/");
		assertNotMatched(pattern, "/3ra/a.c");
		assertNotMatched(pattern, "/5ra/new/a.c");
		assertNotMatched(pattern, "/new/1ra/a.c");
		assertNotMatched(pattern, "/new/1ra/a.c/");
		assertMatched(pattern, "/file/dra");
		assertMatched(pattern, "/file/dra/");
		assertMatched(pattern, "/era/");
		assertNotMatched(pattern, "/crg");
		assertNotMatched(pattern, "/cr3");
	}

	@Test
	public void testGetters() {
		AttributesRule r = new AttributesRule("/pattern/", "");
		assertFalse(r.isNameOnly());
		assertTrue(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertTrue(r.getAttributes().isEmpty());
		assertEquals(r.getPattern(), "/pattern");

		r = new AttributesRule("/patter?/", "");
		assertFalse(r.isNameOnly());
		assertTrue(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertTrue(r.getAttributes().isEmpty());
		assertEquals(r.getPattern(), "/patter?");

		r = new AttributesRule("patt*", "");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertTrue(r.getAttributes().isEmpty());
		assertEquals(r.getPattern(), "patt*");

		r = new AttributesRule("pattern", "attribute1");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertFalse(r.getAttributes().isEmpty());
		assertEquals(r.getAttributes().size(), 1);
		assertEquals(r.getPattern(), "pattern");

		r = new AttributesRule("pattern", "attribute1 -attribute2");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertEquals(r.getAttributes().size(), 2);
		assertEquals(r.getPattern(), "pattern");

		r = new AttributesRule("pattern", "attribute1 \t-attribute2 \t");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertEquals(r.getAttributes().size(), 2);
		assertEquals(r.getPattern(), "pattern");

		r = new AttributesRule("pattern", "attribute1\t-attribute2\t");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertEquals(r.getAttributes().size(), 2);
		assertEquals(r.getPattern(), "pattern");

		r = new AttributesRule("pattern", "attribute1\t -attribute2\t ");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertEquals(r.getAttributes().size(), 2);
		assertEquals(r.getPattern(), "pattern");

		r = new AttributesRule("pattern",
				"attribute1 -attribute2  attribute3=value ");
		assertTrue(r.isNameOnly());
		assertFalse(r.isDirOnly());
		assertNotNull(r.getAttributes());
		assertEquals(r.getAttributes().size(), 3);
		assertEquals(r.getPattern(), "pattern");
		assertEquals(r.getAttributes().get(0).toString(), "attribute1");
		assertEquals(r.getAttributes().get(1).toString(), "-attribute2");
		assertEquals(r.getAttributes().get(2).toString(), "attribute3=value");
	}

	@Test
	public void testBracketsInGroup() {
		//combinations of brackets in brackets, escaped and not

		String[] patterns = new String[]{"[[\\]]", "[\\[\\]]"};
		for (String pattern : patterns) {
			assertNotMatched(pattern, "");
			assertNotMatched(pattern, "[]");
			assertNotMatched(pattern, "][");
			assertNotMatched(pattern, "[\\[]");
			assertNotMatched(pattern, "[[]");
			assertNotMatched(pattern, "[[]]");
			assertNotMatched(pattern, "[\\[\\]]");

			assertMatched(pattern, "[");
			assertMatched(pattern, "]");
		}

		patterns = new String[]{"[[]]", "[\\[]]"};
		for (String pattern : patterns) {
			assertNotMatched(pattern, "");
			assertMatched(pattern, "[]");
			assertNotMatched(pattern, "][");
			assertNotMatched(pattern, "[\\[]");
			assertNotMatched(pattern, "[[]");
			assertNotMatched(pattern, "[[]]");
			assertNotMatched(pattern, "[\\[\\]]");

			assertNotMatched(pattern, "[");
			assertNotMatched(pattern, "]");
		}
	}

	@Test
	public void testFileNameWithLineTerminator() {
		assertMatched("a?", "a\r");
		assertMatched("a?", "dir/a\r");
		assertMatched("*a", "\ra");
		assertMatched("dir/*a*", "dir/\ra\r");
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitattributes file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 */
	private void assertMatched(String pattern, String target) {
		boolean value = match(pattern, target);
		assertTrue("Expected a match for: " + pattern + " with: " + target,
				value);
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitattributes file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 */
	private void assertNotMatched(String pattern, String target) {
		boolean value = match(pattern, target);
		assertFalse("Expected no match for: " + pattern + " with: " + target,
				value);
	}

	/**
	 * Check for a match. If target ends with "/", match will assume that the
	 * target is meant to be a directory.
	 *
	 * @param pattern
	 *            Pattern as it would appear in a .gitattributes file
	 * @param target
	 *            Target file path relative to repository's GIT_DIR
	 * @return Result of {@link AttributesRule#isMatch(String, boolean)}
	 */
	private static boolean match(String pattern, String target) {
		AttributesRule r = new AttributesRule(pattern, "");
		//If speed of this test is ever an issue, we can use a presetRule field
		//to avoid recompiling a pattern each time.
		return r.isMatch(target, target.endsWith("/"));
	}
}
