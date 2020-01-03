/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.internal.transport.http.NetscapeCookieFile;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.http.HttpCookiesMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TransportHttpTest extends SampleDataRepositoryTestCase {
	private URIish uri;
	private File cookieFile;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		uri = new URIish("https://everyones.loves.git/u/2");

		final Config config = db.getConfig();
		config.setBoolean("http", null, "saveCookies", true);
		cookieFile = createTempFile();
		config.setString("http", null, "cookieFile",
				cookieFile.getAbsolutePath());
	}

	@Test
	public void testMatchesCookieDomain() {
		Assert.assertTrue(TransportHttp.matchesCookieDomain("example.com",
				"example.com"));
		Assert.assertTrue(TransportHttp.matchesCookieDomain("Example.Com",
				"example.cOM"));
		Assert.assertTrue(TransportHttp.matchesCookieDomain(
				"some.subdomain.example.com", "example.com"));
		Assert.assertFalse(TransportHttp
				.matchesCookieDomain("someotherexample.com", "example.com"));
		Assert.assertFalse(TransportHttp.matchesCookieDomain("example.com",
				"example1.com"));
		Assert.assertFalse(TransportHttp
				.matchesCookieDomain("sub.sub.example.com", ".example.com"));
		Assert.assertTrue(TransportHttp.matchesCookieDomain("host.example.com",
				"example.com"));
		Assert.assertTrue(TransportHttp.matchesCookieDomain(
				"something.example.com", "something.example.com"));
		Assert.assertTrue(TransportHttp.matchesCookieDomain(
				"host.something.example.com", "something.example.com"));
	}

	@Test
	public void testMatchesCookiePath() {
		Assert.assertTrue(
				TransportHttp.matchesCookiePath("/some/path", "/some/path"));
		Assert.assertTrue(TransportHttp.matchesCookiePath("/some/path/child",
				"/some/path"));
		Assert.assertTrue(TransportHttp.matchesCookiePath("/some/path/child",
				"/some/path/"));
		Assert.assertFalse(TransportHttp.matchesCookiePath("/some/pathother",
				"/some/path"));
		Assert.assertFalse(
				TransportHttp.matchesCookiePath("otherpath", "/some/path"));
	}

	@Test
	public void testProcessResponseCookies() throws IOException {
		HttpConnection connection = Mockito.mock(HttpConnection.class);
		Mockito.when(
				connection.getHeaderFields(ArgumentMatchers.eq("Set-Cookie")))
				.thenReturn(Arrays.asList(
						"id=a3fWa; Expires=Fri, 01 Jan 2100 11:00:00 GMT; Secure; HttpOnly",
						"sessionid=38afes7a8; HttpOnly; Path=/"));
		Mockito.when(
				connection.getHeaderFields(ArgumentMatchers.eq("Set-Cookie2")))
				.thenReturn(Collections
						.singletonList("cookie2=some value; Max-Age=1234; Path=/"));

		try (TransportHttp transportHttp = new TransportHttp(db, uri)) {
			Date creationDate = new Date();
			transportHttp.processResponseCookies(connection);

			// evaluate written cookie file
			Set<HttpCookie> expectedCookies = new LinkedHashSet<>();

			HttpCookie cookie = new HttpCookie("id", "a3fWa");
			cookie.setDomain("everyones.loves.git");
			cookie.setPath("/u/2/");

			cookie.setMaxAge(
					(Instant.parse("2100-01-01T11:00:00.000Z").toEpochMilli()
							- creationDate.getTime()) / 1000);
			cookie.setSecure(true);
			cookie.setHttpOnly(true);
			expectedCookies.add(cookie);

			cookie = new HttpCookie("cookie2", "some value");
			cookie.setDomain("everyones.loves.git");
			cookie.setPath("/");
			cookie.setMaxAge(1234);
			expectedCookies.add(cookie);

			Assert.assertThat(
					new NetscapeCookieFile(cookieFile.toPath())
							.getCookies(true),
					HttpCookiesMatcher.containsInOrder(expectedCookies, 5));
		}
	}

	@Test
	public void testProcessResponseCookiesNotPersistingWithSaveCookiesFalse()
			throws IOException {
		HttpConnection connection = Mockito.mock(HttpConnection.class);
		Mockito.when(
				connection.getHeaderFields(ArgumentMatchers.eq("Set-Cookie")))
				.thenReturn(Arrays.asList(
						"id=a3fWa; Expires=Thu, 21 Oct 2100 11:00:00 GMT; Secure; HttpOnly",
						"sessionid=38afes7a8; HttpOnly; Path=/"));
		Mockito.when(
				connection.getHeaderFields(ArgumentMatchers.eq("Set-Cookie2")))
				.thenReturn(Collections.singletonList(
						"cookie2=some value; Max-Age=1234; Path=/"));

		// tweak config
		final Config config = db.getConfig();
		config.setBoolean("http", null, "saveCookies", false);

		try (TransportHttp transportHttp = new TransportHttp(db, uri)) {
			transportHttp.processResponseCookies(connection);

			// evaluate written cookie file
			Assert.assertFalse("Cookie file was not supposed to be written!",
					cookieFile.exists());
		}
	}
}
