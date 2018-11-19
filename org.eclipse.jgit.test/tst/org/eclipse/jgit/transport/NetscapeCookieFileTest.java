/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de>
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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NetscapeCookieFileTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path tmpFile;

	private URL baseUrl;

	@Before
	public void setUp() throws IOException {
		// this will not only return a new file name but also create new empty
		// file!
		tmpFile = folder.newFile().toPath();
		baseUrl = new URL("http://domain.com/my/path");
	}

	@Test
	public void testWrite() throws IOException {
		List<HttpCookie> cookies = new LinkedList<>();
		cookies.add(new HttpCookie("key1", "value"));
		HttpCookie cookie = new HttpCookie("key2", "value");
		cookie.setSecure(true);
		cookie.setDomain("mydomain.com");
		cookie.setPath("/");
		cookie.setMaxAge(1000);
		cookies.add(cookie);
		Date creationDate = new Date();
		NetscapeCookieFile.write(tmpFile, cookies, baseUrl, creationDate);

		String expectedExpiration = String
				.valueOf(creationDate.getTime() + (cookie.getMaxAge() * 1000));

		Assert.assertThat(
				Files.readAllLines(tmpFile, StandardCharsets.US_ASCII),
				CoreMatchers.equalTo(Arrays.asList(
						"domain.com\tTRUE\t/my/path\tFALSE\t0\tkey1\tvalue",
						"mydomain.com\tTRUE\t/\tTRUE\t" + expectedExpiration
								+ "\tkey2\tvalue")));
	}

	@Test
	public void testWriteToExistingFile() throws IOException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		List<HttpCookie> cookies = new LinkedList<>();
		cookies.add(new HttpCookie("key2", "value2"));
		Date creationDate = new Date();
		NetscapeCookieFile.write(tmpFile, cookies, baseUrl, creationDate);

		Assert.assertThat(
				Files.readAllLines(tmpFile, StandardCharsets.US_ASCII),
				CoreMatchers.equalTo(Arrays.asList(
						"domain.com\tTRUE\t/my/path\tFALSE\t0\tkey2\tvalue2")));
	}

	@Test
	public void testWriteAndReadCycle() throws IOException {
		List<HttpCookie> cookies = new LinkedList<>();

		HttpCookie cookie = new HttpCookie("key1", "value1");
		cookie.setPath("/some/path1");
		cookie.setDomain("some-domain1");
		cookies.add(cookie);
		cookie = new HttpCookie("key2", "value2");
		cookie.setSecure(true);
		cookie.setPath("/some/path2");
		cookie.setDomain("some-domain2");
		cookie.setMaxAge(1000);
		cookie.setHttpOnly(true);
		cookies.add(cookie);

		Date creationDate = new Date();

		NetscapeCookieFile.write(tmpFile, cookies, baseUrl, creationDate);
		Assert.assertThat(NetscapeCookieFile.read(tmpFile, creationDate),
				new HttpCookiesMatcher(cookies));
	}

	@Test
	public void testReadWithEmptyAndCommentLines() throws IOException {
		try (InputStream input = this.getClass().getResourceAsStream(
				"cookies-with-empty-and-comment-lines.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Date creationDate = new Date();
		List<HttpCookie> cookies = new LinkedList<>();
		HttpCookie cookie = new HttpCookie("key1", "value1");
		cookie.setDomain("some-domain1");
		cookie.setPath("/some/path1");
		cookie.setMaxAge(-1);
		cookies.add(cookie);

		cookie = new HttpCookie("key2", "value2");
		cookie.setDomain("some-domain2");
		cookie.setPath("/some/path2");
		cookie.setMaxAge((1893456000000L - creationDate.getTime()) / 1000);
		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		cookies.add(cookie);

		Assert.assertThat(NetscapeCookieFile.read(tmpFile, creationDate),
				new HttpCookiesMatcher(cookies));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testReadInvalidFile() throws IOException {
		try (InputStream input = this.getClass().getResourceAsStream(
				"cookies-invalid.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Date creationDate = new Date();
		NetscapeCookieFile.read(tmpFile, creationDate);
	}

	/**
	 * The default {@link HttpCookie#equals(Object)} is not good enough for
	 * testing purposes. Also the {@link HttpCookie#toString()} only emits some
	 * of the cookie attributes. For testing a dedicated matcher is needed which
	 * takes into account all attributes.
	 *
	 */
	private final static class HttpCookiesMatcher
			extends TypeSafeMatcher<Collection<HttpCookie>> {

		private final List<HttpCookieMatcher> cookieMatchers;

		public HttpCookiesMatcher(Collection<HttpCookie> cookies) {
			cookieMatchers = new ArrayList<>(cookies.size());
			for (HttpCookie cookie : cookies) {
				cookieMatchers.add(new HttpCookieMatcher(cookie));
			}
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("a List of the following cookies ");
			for (HttpCookieMatcher cookieMatcher : cookieMatchers) {
				description.appendText("\n");
				cookieMatcher.describeTo(description);
			}
		}

		@Override
		protected boolean matchesSafely(Collection<HttpCookie> cookies) {
			// first check size
			if (cookies.size() != cookieMatchers.size()) {
				return false;
			}
			int index = 0;
			for (HttpCookie cookie : cookies) {
				if (!cookieMatchers.get(index++).matchesSafely(cookie)) {
					return false;
				}
			}
			return true;
		}

		@Override
		protected void describeMismatchSafely(Collection<HttpCookie> cookies,
				Description mismatchDescription) {
			mismatchDescription
					.appendValue("a List of the following cookies: ");
			for (HttpCookie cookie : cookies) {
				mismatchDescription.appendText("\n");
				HttpCookieMatcher.describeCookie(mismatchDescription, cookie);
			}
		}

	}

	private final static class HttpCookieMatcher
			extends TypeSafeMatcher<HttpCookie> {

		private final HttpCookie cookie;

		public HttpCookieMatcher(HttpCookie cookie) {
			this.cookie = cookie;
		}

		@Override
		public void describeTo(Description description) {
			describeCookie(description, cookie);
		}

		@Override
		protected boolean matchesSafely(HttpCookie otherCookie) {
			// the equals method in HttpCookie is not specific enough, we want
			// to consider all attributes!
			return (equals(cookie.getName(), otherCookie.getName())
					&& equals(cookie.getValue(), otherCookie.getValue())
					&& equals(cookie.getDomain(), otherCookie.getDomain())
					&& equals(cookie.getPath(), otherCookie.getPath())
					&& cookie.getMaxAge() == otherCookie.getMaxAge()
					&& cookie.isHttpOnly() == otherCookie.isHttpOnly()
					&& cookie.getSecure() == otherCookie.getSecure()
					&& cookie.getVersion() == otherCookie.getVersion());
		}

		private static boolean equals(String value1, String value2) {
			if (value1 == null && value2 == null) {
				return true;
			}
			if (value1 == null || value2 == null) {
				return false;
			}
			return value1.equals(value2);
		}

		@SuppressWarnings("boxing")
		protected static void describeCookie(Description description,
				HttpCookie cookie) {
			description.appendText(
					"HttpCookie[");
			description.appendText("name: ").appendValue(cookie.getName()).appendText(", ");
			description.appendText("value: ").appendValue(cookie.getValue()).appendText(", ");
			description.appendText("domain: ").appendValue(cookie.getDomain()).appendText(", ");
			description.appendText("path: ").appendValue(cookie.getPath()).appendText(", ");
			description.appendText("maxAge: ").appendValue(cookie.getMaxAge()).appendText(", ");
			description.appendText("httpOnly: ").appendValue(cookie.isHttpOnly()).appendText(", ");
			description.appendText("secure: ").appendValue(cookie.getSecure()).appendText(", ");
			description.appendText("version: ").appendValue(cookie.getVersion()).appendText(", ");
			description.appendText("]");
		}
	}
}
