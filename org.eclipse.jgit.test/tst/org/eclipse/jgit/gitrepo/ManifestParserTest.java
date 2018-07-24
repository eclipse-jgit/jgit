/*
 * Copyright (C) 2015, Google Inc.
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
package org.eclipse.jgit.gitrepo;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.xml.sax.SAXException;

public class ManifestParserTest {

	@Test
	public void testManifestParser() throws Exception {
		String baseUrl = "https://git.google.com/";
		StringBuilder xmlContent = new StringBuilder();
		Set<String> results = new HashSet<>();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append("foo")
			.append("\" groups=\"a,test\" />")
			.append("<project path=\"bar\" name=\"")
			.append("bar")
			.append("\" groups=\"notdefault\" />")
			.append("<project path=\"foo/a\" name=\"")
			.append("a")
			.append("\" groups=\"a\" />")
			.append("<project path=\"b\" name=\"")
			.append("b")
			.append("\" groups=\"b\" />")
			.append("</manifest>");

		ManifestParser parser = new ManifestParser(
				null, null, "master", baseUrl, null, null);
		parser.read(new ByteArrayInputStream(xmlContent.toString().getBytes(CHARSET)));
		// Unfiltered projects should have them all.
		results.clear();
		results.add("foo");
		results.add("bar");
		results.add("foo/a");
		results.add("b");
		for (RepoProject proj : parser.getProjects()) {
			String msg = String.format(
					"project \"%s\" should be included in unfiltered projects",
					proj.getPath());
			assertTrue(msg, results.contains(proj.getPath()));
			results.remove(proj.getPath());
		}
		assertTrue(
				"Unfiltered projects shouldn't contain any unexpected results",
				results.isEmpty());
		// Filtered projects should have foo & b
		results.clear();
		results.add("foo");
		results.add("b");
		for (RepoProject proj : parser.getFilteredProjects()) {
			String msg = String.format(
					"project \"%s\" should be included in filtered projects",
					proj.getPath());
			assertTrue(msg, results.contains(proj.getPath()));
			results.remove(proj.getPath());
		}
		assertTrue(
				"Filtered projects shouldn't contain any unexpected results",
				results.isEmpty());
	}

	@Test
	public void testManifestParserWithMissingFetchOnRemote() throws Exception {
		String baseUrl = "https://git.google.com/";
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append("foo")
				.append("\" groups=\"a,test\" />")
				.append("<project path=\"bar\" name=\"").append("bar")
				.append("\" groups=\"notdefault\" />")
				.append("<project path=\"foo/a\" name=\"").append("a")
				.append("\" groups=\"a\" />")
				.append("<project path=\"b\" name=\"").append("b")
				.append("\" groups=\"b\" />").append("</manifest>");

		ManifestParser parser = new ManifestParser(null, null, "master",
				baseUrl, null, null);
		try {
			parser.read(new ByteArrayInputStream(
					xmlContent.toString().getBytes(CHARSET)));
			fail("ManifestParser did not throw exception for missing fetch");
		} catch (IOException e) {
			assertTrue(e.getCause() instanceof SAXException);
			assertTrue(e.getCause().getMessage()
					.contains("is missing fetch attribute"));
		}
	}

	@Test
	public void testRemoveProject() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"foo\" />")
				.append("<project path=\"bar\" name=\"bar\" />")
				.append("<remove-project name=\"foo\" />")
				.append("<project path=\"foo\" name=\"baz\" />")
				.append("</manifest>");

		ManifestParser parser = new ManifestParser(null, null, "master",
				"https://git.google.com/", null, null);
		parser.read(new ByteArrayInputStream(
				xmlContent.toString().getBytes(CHARSET)));

		assertEquals(Stream.of("bar", "baz").collect(Collectors.toSet()),
				parser.getProjects().stream().map(RepoProject::getName)
						.collect(Collectors.toSet()));
	}

	void testNormalize(String in, String want) {
		URI got = ManifestParser.normalizeEmptyPath(URI.create(in));
		if (!got.toString().equals(want)) {
			fail(String.format("normalize(%s) = %s want %s", in, got, want));
		}
	}

	@Test
	public void testNormalizeEmptyPath() {
		testNormalize("http://a.b", "http://a.b/");
		testNormalize("http://a.b/", "http://a.b/");
		testNormalize("", "");
		testNormalize("a/b", "a/b");
	}
}
