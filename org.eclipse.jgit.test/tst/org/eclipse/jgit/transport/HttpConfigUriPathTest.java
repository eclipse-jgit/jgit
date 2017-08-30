/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch>
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Basic URI path prefix match tests for {@link HttpConfig}.
 */
public class HttpConfigUriPathTest {

	@Test
	public void testNormalizationEmptyPaths() {
		assertEquals("/", HttpConfig.normalize(""));
		assertEquals("/", HttpConfig.normalize("/"));
	}

	@Test
	public void testNormalization() {
		assertEquals("/f", HttpConfig.normalize("f"));
		assertEquals("/f", HttpConfig.normalize("/f"));
		assertEquals("/f/", HttpConfig.normalize("/f/"));
		assertEquals("/foo", HttpConfig.normalize("foo"));
		assertEquals("/foo", HttpConfig.normalize("/foo"));
		assertEquals("/foo/", HttpConfig.normalize("/foo/"));
		assertEquals("/foo/bar", HttpConfig.normalize("foo/bar"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/bar"));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo/bar/"));
	}

	@Test
	public void testNormalizationWithDot() {
		assertEquals("/", HttpConfig.normalize("."));
		assertEquals("/", HttpConfig.normalize("/."));
		assertEquals("/", HttpConfig.normalize("/./"));
		assertEquals("/foo", HttpConfig.normalize("foo/."));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/./bar"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/bar/."));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo/bar/./"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/./././bar"));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo/./././bar/"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/bar/././."));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo/bar/./././"));
		assertEquals("/foo/bar/.baz/bam",
				HttpConfig.normalize("/foo/bar/.baz/bam"));
		assertEquals("/foo/bar/.baz/bam/",
				HttpConfig.normalize("/foo/bar/.baz/bam/"));
	}

	@Test
	public void testNormalizationWithDotDot() {
		assertEquals("/", HttpConfig.normalize("foo/.."));
		assertEquals("/", HttpConfig.normalize("/foo/.."));
		assertEquals("/", HttpConfig.normalize("/foo/../bar/.."));
		assertEquals("/", HttpConfig.normalize("/foo/.././bar/.."));
		assertEquals("/bar", HttpConfig.normalize("foo/../bar"));
		assertEquals("/bar", HttpConfig.normalize("/foo/../bar"));
		assertEquals("/bar", HttpConfig.normalize("/foo/./.././bar"));
		assertEquals("/bar/", HttpConfig.normalize("/foo/../bar/"));
		assertEquals("/bar/", HttpConfig.normalize("/foo/./.././bar/"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo/bar/baz/.."));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo/bar/baz/../"));
		assertEquals("/foo", HttpConfig.normalize("/foo/bar/baz/../.."));
		assertEquals("/foo", HttpConfig.normalize("/foo/bar/baz/../.."));
		assertEquals("/foo", HttpConfig.normalize("/foo/bar/baz/.././.."));
		assertEquals("/foo", HttpConfig.normalize("/foo/bar/baz/../././.."));
		assertEquals("/foo/baz", HttpConfig.normalize("/foo/bar/../baz"));
		assertEquals("/foo/baz/", HttpConfig.normalize("/foo/bar/../baz/"));
		assertEquals("/foo/baz", HttpConfig.normalize("/foo/bar/../baz/."));
		assertEquals("/foo/baz/", HttpConfig.normalize("/foo/bar/../baz/./"));
		assertEquals("/foo", HttpConfig.normalize("/foo/bar/../baz/.."));
		assertEquals("/foo/", HttpConfig.normalize("/foo/bar/../baz/../"));
		assertEquals("/baz", HttpConfig.normalize("/foo/bar/../../baz"));
		assertEquals("/baz/", HttpConfig.normalize("/foo/bar/../../baz/"));
		assertEquals("/foo/.b/bar", HttpConfig.normalize("/foo/.b/bar"));
		assertEquals("/.f/foo/.b/bar/", HttpConfig.normalize(".f/foo/.b/bar/"));
		assertEquals("/foo/bar/..baz/bam",
				HttpConfig.normalize("/foo/bar/..baz/bam"));
		assertEquals("/foo/bar/..baz/bam/",
				HttpConfig.normalize("/foo/bar/..baz/bam/"));
		assertEquals("/foo/bar/.../baz/bam",
				HttpConfig.normalize("/foo/bar/.../baz/bam"));
		assertEquals("/foo/bar/.../baz/bam/",
				HttpConfig.normalize("/foo/bar/.../baz/bam/"));
	}

	@Test
	public void testNormalizationWithDoubleSlash() {
		assertEquals("/", HttpConfig.normalize("//"));
		assertEquals("/foo/", HttpConfig.normalize("///foo//"));
		assertEquals("/foo", HttpConfig.normalize("///foo//."));
		assertEquals("/foo/", HttpConfig.normalize("///foo//.////"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo//bar"));
		assertEquals("/foo/bar", HttpConfig.normalize("/foo//bar//."));
		assertEquals("/foo/bar/", HttpConfig.normalize("/foo//bar//./"));
	}

	@Test
	public void testNormalizationWithDotDotFailing() {
		assertNull(HttpConfig.normalize(".."));
		assertNull(HttpConfig.normalize("/.."));
		assertNull(HttpConfig.normalize("/../"));
		assertNull(HttpConfig.normalize("/../foo"));
		assertNull(HttpConfig.normalize("./../foo"));
		assertNull(HttpConfig.normalize("/./../foo"));
		assertNull(HttpConfig.normalize("/foo/./.././.."));
		assertNull(HttpConfig.normalize("/foo/../bar/../.."));
		assertNull(HttpConfig.normalize("/foo/../bar/../../baz"));
	}

	@Test
	public void testSegmentCompare() {
		// 2nd parameter is the match, will be normalized
		assertSuccess("/foo", "");
		assertSuccess("/foo", "/");
		assertSuccess("/foo", "//");
		assertSuccess("/foo", "foo");
		assertSuccess("/foo", "/foo");
		assertSuccess("/foo/", "foo");
		assertSuccess("/foo/", "/foo");
		assertSuccess("/foo/", "foo/");
		assertSuccess("/foo/", "/foo/");
		assertSuccess("/foo/bar", "foo");
		assertSuccess("/foo/bar", "foo/");
		assertSuccess("/foo/bar", "foo/bar");
		assertSuccess("/foo/bar/", "foo/bar");
		assertSuccess("/foo/bar/", "foo/bar/");
		assertSuccess("/foo/bar", "/foo/bar");
		assertSuccess("/foo/bar/", "/foo/bar");
		assertSuccess("/foo/bar/", "/foo/bar/");
		assertSuccess("/foo/bar", "/foo/bar/..");
		assertSuccess("/foo/bar/", "/foo/bar/..");
		assertSuccess("/foo/bar/", "/foo/bar/../");
		assertSuccess("/foo/bar", "/foo/./bar");
		assertSuccess("/foo/bar/", "/foo/./bar/");
		assertSuccess("/some/repo/.git", "/some/repo");
		assertSuccess("/some/repo/bare.git", "/some/repo");
		assertSuccess("/some/repo/.git", "/some/repo/.git");
		assertSuccess("/some/repo/bare.git", "/some/repo/bare.git");
	}

	@Test
	public void testSegmentCompareFailing() {
		// 2nd parameter is the match, will be normalized
		assertEquals(-1, HttpConfig.segmentCompare("/foo", "foo/"));
		assertEquals(-1, HttpConfig.segmentCompare("/foo", "/foo/"));
		assertEquals(-1, HttpConfig.segmentCompare("/foobar", "foo"));
		assertEquals(-1, HttpConfig.segmentCompare("/foobar", "/foo"));
		assertEquals(-1,
				HttpConfig.segmentCompare("/foo/barbar/baz", "foo/bar"));
		assertEquals(-1, HttpConfig.segmentCompare("/foo/barbar", "/foo/bar"));
		assertEquals(-1,
				HttpConfig.segmentCompare("/some/repo.git", "/some/repo"));
		assertEquals(-1,
				HttpConfig.segmentCompare("/some/repo.git", "/some/repo.g"));
		assertEquals(-1, HttpConfig.segmentCompare("/some/repo/bare.git",
				"/some/repo/bar"));
		assertSuccess("/some/repo/bare.git", "/some/repo");
		// Just to make sure we don't use the PathMatchers...
		assertEquals(-1, HttpConfig.segmentCompare("/foo/barbar/baz", "**"));
		assertEquals(-1,
				HttpConfig.segmentCompare("/foo/barbar/baz", "**/foo"));
		assertEquals(-1,
				HttpConfig.segmentCompare("/foo/barbar/baz", "/*/barbar/**"));
		assertEquals(-1, HttpConfig.segmentCompare("/foo", "/*"));
		assertEquals(-1, HttpConfig.segmentCompare("/foo", "/???"));
		assertEquals(-1, HttpConfig.segmentCompare("/foo/bar/baz", "bar"));
		// Failing to normalize
		assertEquals(-1,
				HttpConfig.segmentCompare("/foo/bar/baz", "bar/../.."));
	}

	private void assertSuccess(String uri, String match) {
		String normalized = HttpConfig.normalize(match);
		assertEquals(normalized.length(),
				HttpConfig.segmentCompare(uri, match));
	}
}
