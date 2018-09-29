/*
 * Copyright (C) 2010, Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.http.HttpConnection;

/**
 * Extra utilities to support usage of HTTP.
 */
public class HttpSupport {
	/** The {@code GET} HTTP method. */
	public static final String METHOD_GET = "GET"; //$NON-NLS-1$

	/** The {@code HEAD} HTTP method.
	 * @since 4.3 */
	public static final String METHOD_HEAD = "HEAD"; //$NON-NLS-1$

	/** The {@code POST} HTTP method.
	 * @since 4.3 */
	public static final String METHOD_PUT = "PUT"; //$NON-NLS-1$

	/** The {@code POST} HTTP method. */
	public static final String METHOD_POST = "POST"; //$NON-NLS-1$

	/** The {@code Cache-Control} header. */
	public static final String HDR_CACHE_CONTROL = "Cache-Control"; //$NON-NLS-1$

	/** The {@code Pragma} header. */
	public static final String HDR_PRAGMA = "Pragma"; //$NON-NLS-1$

	/** The {@code User-Agent} header. */
	public static final String HDR_USER_AGENT = "User-Agent"; //$NON-NLS-1$

	/**
	 * The {@code Server} header.
	 * @since 4.0
	 */
	public static final String HDR_SERVER = "Server"; //$NON-NLS-1$

	/** The {@code Date} header. */
	public static final String HDR_DATE = "Date"; //$NON-NLS-1$

	/** The {@code Expires} header. */
	public static final String HDR_EXPIRES = "Expires"; //$NON-NLS-1$

	/** The {@code ETag} header. */
	public static final String HDR_ETAG = "ETag"; //$NON-NLS-1$

	/** The {@code If-None-Match} header. */
	public static final String HDR_IF_NONE_MATCH = "If-None-Match"; //$NON-NLS-1$

	/** The {@code Last-Modified} header. */
	public static final String HDR_LAST_MODIFIED = "Last-Modified"; //$NON-NLS-1$

	/** The {@code If-Modified-Since} header. */
	public static final String HDR_IF_MODIFIED_SINCE = "If-Modified-Since"; //$NON-NLS-1$

	/** The {@code Accept} header. */
	public static final String HDR_ACCEPT = "Accept"; //$NON-NLS-1$

	/** The {@code Content-Type} header. */
	public static final String HDR_CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$

	/** The {@code Content-Length} header. */
	public static final String HDR_CONTENT_LENGTH = "Content-Length"; //$NON-NLS-1$

	/** The {@code Content-Encoding} header. */
	public static final String HDR_CONTENT_ENCODING = "Content-Encoding"; //$NON-NLS-1$

	/** The {@code Content-Range} header. */
	public static final String HDR_CONTENT_RANGE = "Content-Range"; //$NON-NLS-1$

	/** The {@code Accept-Ranges} header. */
	public static final String HDR_ACCEPT_RANGES = "Accept-Ranges"; //$NON-NLS-1$

	/** The {@code If-Range} header. */
	public static final String HDR_IF_RANGE = "If-Range"; //$NON-NLS-1$

	/** The {@code Range} header. */
	public static final String HDR_RANGE = "Range"; //$NON-NLS-1$

	/** The {@code Accept-Encoding} header. */
	public static final String HDR_ACCEPT_ENCODING = "Accept-Encoding"; //$NON-NLS-1$

	/**
	 * The {@code Location} header.
	 * @since 4.7
	 */
	public static final String HDR_LOCATION = "Location"; //$NON-NLS-1$

	/** The {@code gzip} encoding value for {@link #HDR_ACCEPT_ENCODING}. */
	public static final String ENCODING_GZIP = "gzip"; //$NON-NLS-1$

	/**
	 * The {@code x-gzip} encoding value for {@link #HDR_ACCEPT_ENCODING}.
	 * @since 4.6
	 */
	public static final String ENCODING_X_GZIP = "x-gzip"; //$NON-NLS-1$

	/** The standard {@code text/plain} MIME type. */
	public static final String TEXT_PLAIN = "text/plain"; //$NON-NLS-1$

	/** The {@code Authorization} header. */
	public static final String HDR_AUTHORIZATION = "Authorization"; //$NON-NLS-1$

	/** The {@code WWW-Authenticate} header. */
	public static final String HDR_WWW_AUTHENTICATE = "WWW-Authenticate"; //$NON-NLS-1$

	/**
	 * URL encode a value string into an output buffer.
	 *
	 * @param urlstr
	 *            the output buffer.
	 * @param key
	 *            value which must be encoded to protected special characters.
	 */
	public static void encode(StringBuilder urlstr, String key) {
		if (key == null || key.length() == 0)
			return;
		try {
			urlstr.append(URLEncoder.encode(key, UTF_8.name()));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(JGitText.get().couldNotURLEncodeToUTF8, e);
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
	 *         {@link org.eclipse.jgit.transport.http.HttpConnection} for other
	 *         defined constants.
	 * @throws java.io.IOException
	 *             communications error prevented obtaining the response code.
	 * @since 3.3
	 */
	public static int response(HttpConnection c) throws IOException {
		try {
			return c.getResponseCode();
		} catch (ConnectException ce) {
			final URL url = c.getURL();
			final String host = (url == null) ? "<null>" : url.getHost(); //$NON-NLS-1$
			// The standard J2SE error message is not very useful.
			//
			if ("Connection timed out: connect".equals(ce.getMessage())) //$NON-NLS-1$
				throw new ConnectException(MessageFormat.format(JGitText.get().connectionTimeOut, host));
			throw new ConnectException(ce.getMessage() + " " + host); //$NON-NLS-1$
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
	 *         {@link org.eclipse.jgit.transport.http.HttpConnection} for other
	 *         defined constants.
	 * @throws java.io.IOException
	 *             communications error prevented obtaining the response code.
	 */
	public static int response(java.net.HttpURLConnection c)
			throws IOException {
		try {
			return c.getResponseCode();
		} catch (ConnectException ce) {
			final URL url = c.getURL();
			final String host = (url == null) ? "<null>" : url.getHost(); //$NON-NLS-1$
			// The standard J2SE error message is not very useful.
			//
			if ("Connection timed out: connect".equals(ce.getMessage())) //$NON-NLS-1$
				throw new ConnectException(MessageFormat.format(
						JGitText.get().connectionTimeOut, host));
			throw new ConnectException(ce.getMessage() + " " + host); //$NON-NLS-1$
		}
	}

	/**
	 * Extract a HTTP header from the response.
	 *
	 * @param c
	 *            connection the header should be obtained from.
	 * @param headerName
	 *            the header name
	 * @return the header value
	 * @throws java.io.IOException
	 *             communications error prevented obtaining the header.
	 * @since 4.7
	 */
	public static String responseHeader(final HttpConnection c,
			final String headerName) throws IOException {
		return c.getHeaderField(headerName);
	}

	/**
	 * Determine the proxy server (if any) needed to obtain a URL.
	 *
	 * @param proxySelector
	 *            proxy support for the caller.
	 * @param u
	 *            location of the server caller wants to talk to.
	 * @return proxy to communicate with the supplied URL.
	 * @throws java.net.ConnectException
	 *             the proxy could not be computed as the supplied URL could not
	 *             be read. This failure should never occur.
	 */
	public static Proxy proxyFor(ProxySelector proxySelector, URL u)
			throws ConnectException {
		try {
			return proxySelector.select(u.toURI()).get(0);
		} catch (URISyntaxException e) {
			final ConnectException err;
			err = new ConnectException(MessageFormat.format(JGitText.get().cannotDetermineProxyFor, u));
			err.initCause(e);
			throw err;
		}
	}

	/**
	 * Disable SSL and hostname verification for given HTTP connection
	 *
	 * @param conn
	 *            a {@link org.eclipse.jgit.transport.http.HttpConnection}
	 *            object.
	 * @throws java.io.IOException
	 * @since 4.3
	 */
	public static void disableSslVerify(HttpConnection conn)
			throws IOException {
		final TrustManager[] trustAllCerts = new TrustManager[] {
				new DummyX509TrustManager() };
		try {
			conn.configure(null, trustAllCerts, null);
			conn.setHostnameVerifier(new DummyHostnameVerifier());
		} catch (KeyManagementException e) {
			throw new IOException(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e.getMessage());
		}
	}

	private static class DummyX509TrustManager implements X509TrustManager {
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		@Override
		public void checkClientTrusted(X509Certificate[] certs,
				String authType) {
			// no check
		}

		@Override
		public void checkServerTrusted(X509Certificate[] certs,
				String authType) {
			// no check
		}
	}

	private static class DummyHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			// always accept
			return true;
		}
	}

	private HttpSupport() {
		// Utility class only.
	}
}
