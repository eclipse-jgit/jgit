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

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for correctly resolving URIs when reading http.* values from a
 * {@link Config}.
 */
public class HttpConfigTest {

	private static final String DEFAULT = "[http]\n" + "\tpostBuffer = 1\n"
			+ "\tsslVerify= true\n" + "\tfollowRedirects = true\n"
			+ "\textraHeader = x: y\n" + "\tuserAgent = Test/0.1\n"
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
				DEFAULT + "[http \"///#expectedWarning\"]\n"
						+ "\tpostBuffer = 1024\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/path/repo.git"));
		assertEquals(1, http.getPostBuffer());
	}

	@Test
	public void testMatchWithInvalidAndValidUriInConfig() throws Exception {
		config.fromText(DEFAULT + "[http \"///#expectedWarning\"]\n"
				+ "\tpostBuffer = 1024\n"
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

	@Test
	public void testExtraHeaders() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\textraHeader=foo: bar\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertEquals(1, http.getExtraHeaders().size());
		assertEquals("foo: bar", http.getExtraHeaders().get(0));
	}

	@Test
	public void testExtraHeadersMultiple() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\textraHeader=foo: bar\n" //
				+ "\textraHeader=bar: foo\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertEquals(2, http.getExtraHeaders().size());
		assertEquals("foo: bar", http.getExtraHeaders().get(0));
		assertEquals("bar: foo", http.getExtraHeaders().get(1));
	}

	@Test
	public void testExtraHeadersReset() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\textraHeader=foo: bar\n" //
				+ "\textraHeader=bar: foo\n" //
				+ "\textraHeader=\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertTrue(http.getExtraHeaders().isEmpty());
	}

	@Test
	public void testExtraHeadersResetAndMore() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\textraHeader=foo: bar\n" //
				+ "\textraHeader=bar: foo\n" //
				+ "\textraHeader=\n" //
				+ "\textraHeader=baz: something\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertEquals(1, http.getExtraHeaders().size());
		assertEquals("baz: something", http.getExtraHeaders().get(0));
	}

	@Test
	public void testUserAgent() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\tuserAgent=DummyAgent/4.0\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertEquals("DummyAgent/4.0", http.getUserAgent());
	}

	@Test
	public void testUserAgentEnvOverride() throws Exception {
		String mockAgent = "jgit-test/5.10.0";
		SystemReader originalReader = SystemReader.getInstance();
		SystemReader.setInstance(new MockSystemReader() {

			@Override
			public String getenv(String variable) {
				if ("GIT_HTTP_USER_AGENT".equals(variable)) {
					return mockAgent;
				}
				return super.getenv(variable);
			}
		});
		try {
			config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
					+ "\tuserAgent=DummyAgent/4.0\n");
			HttpConfig http = new HttpConfig(config,
					new URIish("http://example.com/"));
			assertEquals(mockAgent, http.getUserAgent());
		} finally {
			SystemReader.setInstance(originalReader);
		}
	}

	@Test
	public void testUserAgentNonAscii() throws Exception {
		config.fromText(DEFAULT + "[http \"http://example.com\"]\n"
				+ "\tuserAgent= d ümmy Agent -5.10\n");
		HttpConfig http = new HttpConfig(config,
				new URIish("http://example.com/"));
		assertEquals("d.mmy.Agent.-5.10", http.getUserAgent());
	}
}
