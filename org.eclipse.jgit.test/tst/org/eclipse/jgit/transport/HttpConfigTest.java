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
