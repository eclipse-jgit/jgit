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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.http.HttpConnection;

/** Extra utilities to support usage of HTTP. */
public class HttpSupport {
	/** The {@code GET} HTTP method. */
	public static final String METHOD_GET = "GET"; //$NON-NLS-1$

	/** The {@code POST} HTTP method. */
	public static final String METHOD_POST = "POST"; //$NON-NLS-1$

	/** The {@code Cache-Control} header. */
	public static final String HDR_CACHE_CONTROL = "Cache-Control"; //$NON-NLS-1$

	/** The {@code Pragma} header. */
	public static final String HDR_PRAGMA = "Pragma"; //$NON-NLS-1$

	/** The {@code User-Agent} header. */
	public static final String HDR_USER_AGENT = "User-Agent"; //$NON-NLS-1$

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

	/** The {@code gzip} encoding value for {@link #HDR_ACCEPT_ENCODING}. */
	public static final String ENCODING_GZIP = "gzip"; //$NON-NLS-1$

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
	public static void encode(final StringBuilder urlstr, final String key) {
		if (key == null || key.length() == 0)
			return;
		try {
			urlstr.append(URLEncoder.encode(key, "UTF-8")); //$NON-NLS-1$
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
	 *         {@link HttpConnection} for other defined constants.
	 * @throws IOException
	 *             communications error prevented obtaining the response code.
	 */
	public static int response(final HttpConnection c) throws IOException {
		try {
			return c.getResponseCode();
		} catch (ConnectException ce) {
			final String host = c.getURL().getHost();
			// The standard J2SE error message is not very useful.
			//
			if ("Connection timed out: connect".equals(ce.getMessage()))
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
	 *         {@link HttpConnection} for other defined constants.
	 * @throws IOException
	 *             communications error prevented obtaining the response code.
	 */
	public static int response(final java.net.HttpURLConnection c)
			throws IOException {
		try {
			return c.getResponseCode();
		} catch (ConnectException ce) {
			final String host = c.getURL().getHost();
			// The standard J2SE error message is not very useful.
			//
			if ("Connection timed out: connect".equals(ce.getMessage()))
				throw new ConnectException(MessageFormat.format(
						JGitText.get().connectionTimeOut, host));
			throw new ConnectException(ce.getMessage() + " " + host); //$NON-NLS-1$
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
			err = new ConnectException(MessageFormat.format(JGitText.get().cannotDetermineProxyFor, u));
			err.initCause(e);
			throw err;
		}
	}

	private HttpSupport() {
		// Utility class only.
	}
}
