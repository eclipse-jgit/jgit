/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import org.eclipse.jgit.awtui.AwtAuthenticator;

/** Extra utilities to support usage of HTTP. */
public class HttpSupport {
	/**
	 * Configure the JRE's standard HTTP based on <code>http_proxy</code>.
	 * <p>
	 * The popular libcurl library honors the <code>http_proxy</code>
	 * environment variable as a means of specifying an HTTP proxy for requests
	 * made behind a firewall. This is not natively recognized by the JRE, so
	 * this method can be used by command line utilities to configure the JRE
	 * before the first request is sent.
	 *
	 * @throws MalformedURLException
	 *             the value in <code>http_proxy</code> is unsupportable.
	 */
	public static void configureHttpProxy() throws MalformedURLException {
		final String s = System.getenv("http_proxy");
		if (s == null || s.equals(""))
			return;

		final URL u = new URL((s.indexOf("://") == -1) ? "http://" + s : s);
		if (!"http".equals(u.getProtocol()))
			throw new MalformedURLException("Invalid http_proxy: " + s
					+ ": Only http supported.");

		final String proxyHost = u.getHost();
		final int proxyPort = u.getPort();

		System.setProperty("http.proxyHost", proxyHost);
		if (proxyPort > 0)
			System.setProperty("http.proxyPort", String.valueOf(proxyPort));

		final String userpass = u.getUserInfo();
		if (userpass != null && userpass.contains(":")) {
			final int c = userpass.indexOf(':');
			final String user = userpass.substring(0, c);
			final String pass = userpass.substring(c + 1);
			AwtAuthenticator.add(new AwtAuthenticator.CachedAuthentication(
					proxyHost, proxyPort, user, pass));
		}
	}

	/**
	 * URL encode a value string into an output buffer.
	 *
	 * @param urlstr
	 *            the output buffer.
	 * @param key
	 *            value which must be encoded to protected special characters.
	 */
	public static void encode(final StringBuilder urlstr, final String key) {
		if (key == null || key.length() == 0)
			return;
		try {
			urlstr.append(URLEncoder.encode(key, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Could not URL encode to UTF-8", e);
		}
	}

	/**
	 * Get the HTTP response code from the request.
	 * <p>
	 * Roughly the same as <code>c.getResponseCode()</code> but the
	 * ConnectException is translated to be more understandable.
	 *
	 * @param c
	 *            connection the code should be obtained from.
	 * @return r HTTP status code, usually 200 to indicate success. See
	 *         {@link HttpURLConnection} for other defined constants.
	 * @throws IOException
	 *             communications error prevented obtaining the response code.
	 */
	public static int response(final HttpURLConnection c) throws IOException {
		try {
			return c.getResponseCode();
		} catch (ConnectException ce) {
			final String host = c.getURL().getHost();
			// The standard J2SE error message is not very useful.
			//
			if ("Connection timed out: connect".equals(ce.getMessage()))
				throw new ConnectException("Connection time out: " + host);
			throw new ConnectException(ce.getMessage() + " " + host);
		}
	}

	/**
	 * Determine the proxy server (if any) needed to obtain a URL.
	 *
	 * @param proxySelector
	 *            proxy support for the caller.
	 * @param u
	 *            location of the server caller wants to talk to.
	 * @return proxy to communicate with the supplied URL.
	 * @throws ConnectException
	 *             the proxy could not be computed as the supplied URL could not
	 *             be read. This failure should never occur.
	 */
	public static Proxy proxyFor(final ProxySelector proxySelector, final URL u)
			throws ConnectException {
		try {
			return proxySelector.select(u.toURI()).get(0);
		} catch (URISyntaxException e) {
			final ConnectException err;
			err = new ConnectException("Cannot determine proxy for " + u);
			err.initCause(e);
			throw err;
		}
	}

	private HttpSupport() {
		// Utility class only.
	}
}
