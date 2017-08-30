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
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for correctly resolving URIs when reading http.* values from a
 * {@link Config}.
 */
public class HttpConfigTest {

	private static final String DEFAULT = "[http]\n" + "\tpostBuffer = 1\n"
			+ "\tsslVerify= true\n" + "\tfollowRedirects = true\n"
			+ "\tmaxRedirects = 5\n\n";

	private Config config;

	@Before
	public void setUp() {
		config = new Config();
	}

	@Test
	public void testDefault() throws Exception {
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024 * 1024, http.getPostBuffer());
		assertTrue(http.isSslVerify());
		assertEquals(HttpConfig.HttpRedirectMode.INITIAL,
				http.getFollowRedirects());
	}

	@Test
	public void testMatchSuccess() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("https://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.org/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.com:80/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.com:8080/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
	}

	@Test
	public void testMatchWithOnlySchemeInConfig() throws Exception {
		config.fromText(
				DEFAULT + "[http \"http://\"]\n" + "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
	}

	@Test
	public void testMatchWithPrefixUriInConfig() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example\"]\n"
				+ "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
	}

	@Test
	public void testMatchCaseSensitivity() throws Exception {
		config.fromText(DEFAULT + "[http \"http://exAMPle.com\"]\n"
				+ "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
	}

	@Test
	public void testMatchWithInvalidUriInConfig() throws Exception {
		config.fromText(
				DEFAULT + "[http \"///\"]\n" + "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
	}

	@Test
	public void testMatchWithInvalidAndValidUriInConfig() throws Exception {
		config.fromText(DEFAULT + "[http \"///\"]\n" + "\tpostBuffer = 1024\n"
				+ "[http \"http://example.com\"]\n" + "\tpostBuffer = 2048\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(2048, http.getPostBuffer());
	}

	@Test
	public void testMatchWithHostEndingInSlash() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com/\"]\n"
				+ "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
	}

	@Test
	public void testMatchWithUser() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com/path\"]\n"
				+ "\tpostBuffer = 1024\n"
				+ "[http \"http://example.com/path/repo\"]\n"
				+ "\tpostBuffer = 2048\n"
				+ "[http \"http://user@example.com/path\"]\n"
				+ "\tpostBuffer = 4096\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://user@example.com/path/repo.git"));
		assertEquals(4096, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://user@example.com/path/repo/foo.git"));
		assertEquals(2048, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://user@example.com/path/foo.git"));
		assertEquals(4096, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.com/path/foo.git"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://User@example.com/path/repo/foo.git"));
		assertEquals(2048, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://User@example.com/path/foo.git"));
		assertEquals(1024, http.getPostBuffer());
	}

	@Test
	public void testMatchLonger() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com/path\"]\n"
				+ "\tpostBuffer = 1024\n"
				+ "[http \"http://example.com/path/repo\"]\n"
				+ "\tpostBuffer = 2048\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.com/foo/repo.git"));
		assertEquals(1, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("https://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://example.com/path/repo/.git"));
		assertEquals(2048, http.getPostBuffer());
		http = new HttpConfig(config, new URIish("http://example.com/path"));
		assertEquals(1024, http.getPostBuffer());
		http = new HttpConfig(config,
				new URIish("http://user@example.com/path"));
		assertEquals(1024, http.getPostBuffer());
	}
}
