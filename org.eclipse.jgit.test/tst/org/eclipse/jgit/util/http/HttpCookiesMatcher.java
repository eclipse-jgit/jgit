/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.http;

import java.net.HttpCookie;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.collection.IsIterableContainingInOrder;

public final class HttpCookiesMatcher {
	public static Matcher<Iterable<? extends HttpCookie>> containsInOrder(
			Iterable<HttpCookie> expectedCookies) {
		return containsInOrder(expectedCookies, 0);
	}

	public static Matcher<Iterable<? extends HttpCookie>> containsInOrder(
			Iterable<HttpCookie> expectedCookies, int allowedMaxAgeDelta) {
		final List<Matcher<? super HttpCookie>> cookieMatchers = new LinkedList<>();
		for (HttpCookie cookie : expectedCookies) {
			cookieMatchers
					.add(new HttpCookieMatcher(cookie, allowedMaxAgeDelta));
		}
		return new IsIterableContainingInOrder<>(cookieMatchers);
	}

	/**
	 * The default {@link HttpCookie#equals(Object)} is not good enough for
	 * testing purposes. Also the {@link HttpCookie#toString()} only emits some
	 * of the cookie attributes. For testing a dedicated matcher is needed which
	 * takes into account all attributes.
	 */
	private static final class HttpCookieMatcher
			extends TypeSafeMatcher<HttpCookie> {

		private final HttpCookie cookie;

		private final int allowedMaxAgeDelta;

		public HttpCookieMatcher(HttpCookie cookie, int allowedMaxAgeDelta) {
			this.cookie = cookie;
			this.allowedMaxAgeDelta = allowedMaxAgeDelta;
		}

		@Override
		public void describeTo(Description description) {
			describeCookie(description, cookie);
		}

		@Override
		protected void describeMismatchSafely(HttpCookie item,
				Description mismatchDescription) {
			mismatchDescription.appendText("was ");
			describeCookie(mismatchDescription, item);
		}

		@Override
		protected boolean matchesSafely(HttpCookie otherCookie) {
			// the equals method in HttpCookie is not specific enough, we want
			// to consider all attributes!
			return (equals(cookie.getName(), otherCookie.getName())
					&& equals(cookie.getValue(), otherCookie.getValue())
					&& equals(cookie.getDomain(), otherCookie.getDomain())
					&& equals(cookie.getPath(), otherCookie.getPath())
					&& (cookie.getMaxAge() >= otherCookie.getMaxAge()
							- allowedMaxAgeDelta)
					&& (cookie.getMaxAge() <= otherCookie.getMaxAge()
							+ allowedMaxAgeDelta)
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
			description.appendText("HttpCookie[");
			description.appendText("name: ").appendValue(cookie.getName())
					.appendText(", ");
			description.appendText("value: ").appendValue(cookie.getValue())
					.appendText(", ");
			description.appendText("domain: ").appendValue(cookie.getDomain())
					.appendText(", ");
			description.appendText("path: ").appendValue(cookie.getPath())
					.appendText(", ");
			description.appendText("maxAge: ").appendValue(cookie.getMaxAge())
					.appendText(", ");
			description.appendText("httpOnly: ")
					.appendValue(cookie.isHttpOnly()).appendText(", ");
			description.appendText("secure: ").appendValue(cookie.getSecure())
					.appendText(", ");
			description.appendText("version: ").appendValue(cookie.getVersion())
					.appendText(", ");
			description.appendText("]");
		}
	}
}