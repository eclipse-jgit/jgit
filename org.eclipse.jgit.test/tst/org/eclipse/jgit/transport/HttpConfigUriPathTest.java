/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
