/*
 * Copyright (C) 2008, 2010, Google Inc.
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A representation of the "http.*" config values in a git
 * {@link org.eclipse.jgit.lib.Config}. git provides for setting values for
 * specific URLs through "http.&lt;url&gt;.*" subsections. git always considers
 * only the initial original URL for such settings, not any redirected URL.
 *
 * @since 4.9
 */
public class HttpConfig {

	private static final Logger LOG = LoggerFactory.getLogger(HttpConfig.class);

	private static final String FTP = "ftp"; //$NON-NLS-1$

	/** git config section key for http settings. */
	public static final String HTTP = "http"; //$NON-NLS-1$

	/** git config key for the "followRedirects" setting. */
	public static final String FOLLOW_REDIRECTS_KEY = "followRedirects"; //$NON-NLS-1$

	/** git config key for the "maxRedirects" setting. */
	public static final String MAX_REDIRECTS_KEY = "maxRedirects"; //$NON-NLS-1$

	/** git config key for the "postBuffer" setting. */
	public static final String POST_BUFFER_KEY = "postBuffer"; //$NON-NLS-1$

	/** git config key for the "sslVerify" setting. */
	public static final String SSL_VERIFY_KEY = "sslVerify"; //$NON-NLS-1$

	/**
	 * git config key for the "cookieFile" setting.
	 *
	 * @since 5.4
	 */
	public static final String COOKIE_FILE_KEY = "cookieFile"; //$NON-NLS-1$

	/**
	 * git config key for the "saveCookies" setting.
	 *
	 * @since 5.4
	 */
	public static final String SAVE_COOKIES_KEY = "saveCookies"; //$NON-NLS-1$

	/**
	 * Custom JGit config key which holds the maximum number of cookie files to
	 * keep in the cache.
	 *
	 * @since 5.4
	 */
	public static final String COOKIE_FILE_CACHE_LIMIT_KEY = "cookieFileCacheLimit"; //$NON-NLS-1$

	private static final int DEFAULT_COOKIE_FILE_CACHE_LIMIT = 10;

	private static final String MAX_REDIRECT_SYSTEM_PROPERTY = "http.maxRedirects"; //$NON-NLS-1$

	private static final int DEFAULT_MAX_REDIRECTS = 5;

	private static final int MAX_REDIRECTS = (new Supplier<Integer>() {

		@Override
		public Integer get() {
			String rawValue = SystemReader.getInstance()
					.getProperty(MAX_REDIRECT_SYSTEM_PROPERTY);
			Integer value = Integer.valueOf(DEFAULT_MAX_REDIRECTS);
			if (rawValue != null) {
				try {
					value = Integer.valueOf(Integer.parseUnsignedInt(rawValue));
				} catch (NumberFormatException e) {
					LOG.warn(MessageFormat.format(
							JGitText.get().invalidSystemProperty,
							MAX_REDIRECT_SYSTEM_PROPERTY, rawValue, value));
				}
			}
			return value;
		}
	}).get().intValue();

	/**
	 * Config values for http.followRedirect.
	 */
	public enum HttpRedirectMode implements Config.ConfigEnum {

		/** Always follow redirects (up to the http.maxRedirects limit). */
		TRUE("true"), //$NON-NLS-1$
		/**
		 * Only follow redirects on the initial GET request. This is the
		 * default.
		 */
		INITIAL("initial"), //$NON-NLS-1$
		/** Never follow redirects. */
		FALSE("false"); //$NON-NLS-1$

		private final String configValue;

		private HttpRedirectMode(String configValue) {
			this.configValue = configValue;
		}

		@Override
		public String toConfigValue() {
			return configValue;
		}

		@Override
		public boolean matchConfigValue(String s) {
			return configValue.equals(s);
		}
	}

	private int postBuffer;

	private boolean sslVerify;

	private HttpRedirectMode followRedirects;

	private int maxRedirects;

	private String cookieFile;

	private boolean saveCookies;

	private int cookieFileCacheLimit;

	/**
	 * Get the "http.postBuffer" setting
	 *
	 * @return the value of the "http.postBuffer" setting
	 */
	public int getPostBuffer() {
		return postBuffer;
	}

	/**
	 * Get the "http.sslVerify" setting
	 *
	 * @return the value of the "http.sslVerify" setting
	 */
	public boolean isSslVerify() {
		return sslVerify;
	}

	/**
	 * Get the "http.followRedirects" setting
	 *
	 * @return the value of the "http.followRedirects" setting
	 */
	public HttpRedirectMode getFollowRedirects() {
		return followRedirects;
	}

	/**
	 * Get the "http.maxRedirects" setting
	 *
	 * @return the value of the "http.maxRedirects" setting
	 */
	public int getMaxRedirects() {
		return maxRedirects;
	}

	/**
	 * Get the "http.cookieFile" setting
	 *
	 * @return the value of the "http.cookieFile" setting
	 *
	 * @since 5.4
	 */
	public String getCookieFile() {
		return cookieFile;
	}

	/**
	 * Get the "http.saveCookies" setting
	 *
	 * @return the value of the "http.saveCookies" setting
	 *
	 * @since 5.4
	 */
	public boolean getSaveCookies() {
		return saveCookies;
	}

	/**
	 * Get the "http.cookieFileCacheLimit" setting (gives the maximum number of
	 * cookie files to keep in the LRU cache)
	 *
	 * @return the value of the "http.cookieFileCacheLimit" setting
	 *
	 * @since 5.4
	 */
	public int getCookieFileCacheLimit() {
		return cookieFileCacheLimit;
	}

	/**
	 * Creates a new {@link org.eclipse.jgit.transport.HttpConfig} tailored to
	 * the given {@link org.eclipse.jgit.transport.URIish}.
	 *
	 * @param config
	 *            to read the {@link org.eclipse.jgit.transport.HttpConfig} from
	 * @param uri
	 *            to get the configuration values for
	 */
	public HttpConfig(Config config, URIish uri) {
		init(config, uri);
	}

	/**
	 * Creates a {@link org.eclipse.jgit.transport.HttpConfig} that reads values
	 * solely from the user config.
	 *
	 * @param uri
	 *            to get the configuration values for
	 */
	public HttpConfig(URIish uri) {
		StoredConfig userConfig = null;
		try {
			userConfig = SystemReader.getInstance().getUserConfig();
		} catch (IOException | ConfigInvalidException e) {
			// Log it and then work with default values.
			LOG.error(e.getMessage(), e);
			init(new Config(), uri);
			return;
		}
		init(userConfig, uri);
	}

	private void init(Config config, URIish uri) {
		// Set defaults from the section first
		int postBufferSize = config.getInt(HTTP, POST_BUFFER_KEY,
				1 * 1024 * 1024);
		boolean sslVerifyFlag = config.getBoolean(HTTP, SSL_VERIFY_KEY, true);
		HttpRedirectMode followRedirectsMode = config.getEnum(
				HttpRedirectMode.values(), HTTP, null,
				FOLLOW_REDIRECTS_KEY, HttpRedirectMode.INITIAL);
		int redirectLimit = config.getInt(HTTP, MAX_REDIRECTS_KEY,
				MAX_REDIRECTS);
		if (redirectLimit < 0) {
			redirectLimit = MAX_REDIRECTS;
		}
		cookieFile = config.getString(HTTP, null, COOKIE_FILE_KEY);
		saveCookies = config.getBoolean(HTTP, SAVE_COOKIES_KEY, false);
		cookieFileCacheLimit = config.getInt(HTTP, COOKIE_FILE_CACHE_LIMIT_KEY,
				DEFAULT_COOKIE_FILE_CACHE_LIMIT);
		String match = findMatch(config.getSubsections(HTTP), uri);
		if (match != null) {
			// Override with more specific items
			postBufferSize = config.getInt(HTTP, match, POST_BUFFER_KEY,
					postBufferSize);
			sslVerifyFlag = config.getBoolean(HTTP, match, SSL_VERIFY_KEY,
					sslVerifyFlag);
			followRedirectsMode = config.getEnum(HttpRedirectMode.values(),
					HTTP, match, FOLLOW_REDIRECTS_KEY, followRedirectsMode);
			int newMaxRedirects = config.getInt(HTTP, match, MAX_REDIRECTS_KEY,
					redirectLimit);
			if (newMaxRedirects >= 0) {
				redirectLimit = newMaxRedirects;
			}
			String urlSpecificCookieFile = config.getString(HTTP, match,
					COOKIE_FILE_KEY);
			if (urlSpecificCookieFile != null) {
				cookieFile = urlSpecificCookieFile;
			}
			saveCookies = config.getBoolean(HTTP, match, SAVE_COOKIES_KEY,
					saveCookies);
		}
		postBuffer = postBufferSize;
		sslVerify = sslVerifyFlag;
		followRedirects = followRedirectsMode;
		maxRedirects = redirectLimit;
	}

	/**
	 * Determines the best match from a set of subsection names (representing
	 * prefix URLs) for the given {@link URIish}.
	 *
	 * @param names
	 *            to match against the {@code uri}
	 * @param uri
	 *            to find a match for
	 * @return the best matching subsection name, or {@code null} if no
	 *         subsection matches
	 */
	private String findMatch(Set<String> names, URIish uri) {
		String bestMatch = null;
		int bestMatchLength = -1;
		boolean withUser = false;
		String uPath = uri.getPath();
		boolean hasPath = !StringUtils.isEmptyOrNull(uPath);
		if (hasPath) {
			uPath = normalize(uPath);
			if (uPath == null) {
				// Normalization failed; warning was logged.
				return null;
			}
		}
		for (String s : names) {
			try {
				URIish candidate = new URIish(s);
				// Scheme and host must match case-insensitively
				if (!compare(uri.getScheme(), candidate.getScheme())
						|| !compare(uri.getHost(), candidate.getHost())) {
					continue;
				}
				// Ports must match after default ports have been substituted
				if (defaultedPort(uri.getPort(),
						uri.getScheme()) != defaultedPort(candidate.getPort(),
								candidate.getScheme())) {
					continue;
				}
				// User: if present in candidate, must match
				boolean hasUser = false;
				if (candidate.getUser() != null) {
					if (!candidate.getUser().equals(uri.getUser())) {
						continue;
					}
					hasUser = true;
				}
				// Path: prefix match, longer is better
				String cPath = candidate.getPath();
				int matchLength = -1;
				if (StringUtils.isEmptyOrNull(cPath)) {
					matchLength = 0;
				} else {
					if (!hasPath) {
						continue;
					}
					// Paths can match only on segments
					matchLength = segmentCompare(uPath, cPath);
					if (matchLength < 0) {
						continue;
					}
				}
				// A longer path match is always preferred even over a user
				// match. If the path matches are equal, a match with user wins
				// over a match without user.
				if (matchLength > bestMatchLength
						|| (!withUser && hasUser && matchLength >= 0
								&& matchLength == bestMatchLength)) {
					bestMatch = s;
					bestMatchLength = matchLength;
					withUser = hasUser;
				}
			} catch (URISyntaxException e) {
				LOG.warn(MessageFormat
						.format(JGitText.get().httpConfigInvalidURL, s));
			}
		}
		return bestMatch;
	}

	private boolean compare(String a, String b) {
		if (a == null) {
			return b == null;
		}
		return a.equalsIgnoreCase(b);
	}

	private int defaultedPort(int port, String scheme) {
		if (port >= 0) {
			return port;
		}
		if (FTP.equalsIgnoreCase(scheme)) {
			return 21;
		} else if (HTTP.equalsIgnoreCase(scheme)) {
			return 80;
		} else {
			return 443; // https
		}
	}

	static int segmentCompare(String uriPath, String m) {
		// Precondition: !uriPath.isEmpty() && !m.isEmpty(),and u must already
		// be normalized
		String matchPath = normalize(m);
		if (matchPath == null || !uriPath.startsWith(matchPath)) {
			return -1;
		}
		// We can match only on a segment boundary: either both paths are equal,
		// or if matchPath does not end in '/', there is a '/' in uriPath right
		// after the match.
		int uLength = uriPath.length();
		int mLength = matchPath.length();
		if (mLength == uLength || matchPath.charAt(mLength - 1) == '/'
				|| (mLength < uLength && uriPath.charAt(mLength) == '/')) {
			return mLength;
		}
		return -1;
	}

	static String normalize(String path) {
		// C-git resolves . and .. segments
		int i = 0;
		int length = path.length();
		StringBuilder builder = new StringBuilder(length);
		builder.append('/');
		if (length > 0 && path.charAt(0) == '/') {
			i = 1;
		}
		while (i < length) {
			int slash = path.indexOf('/', i);
			if (slash < 0) {
				slash = length;
			}
			if (slash == i || (slash == i + 1 && path.charAt(i) == '.')) {
				// Skip /. or also double slashes
			} else if (slash == i + 2 && path.charAt(i) == '.'
					&& path.charAt(i + 1) == '.') {
				// Remove previous segment if we have "/.."
				int l = builder.length() - 2; // Skip terminating slash.
				while (l >= 0 && builder.charAt(l) != '/') {
					l--;
				}
				if (l < 0) {
					LOG.warn(MessageFormat.format(
							JGitText.get().httpConfigCannotNormalizeURL, path));
					return null;
				}
				builder.setLength(l + 1);
			} else {
				// Include the slash, if any
				builder.append(path, i, Math.min(length, slash + 1));
			}
			i = slash + 1;
		}
		if (builder.length() > 1 && builder.charAt(builder.length() - 1) == '/'
				&& length > 0 && path.charAt(length - 1) != '/') {
			// . or .. normalization left a trailing slash when the original
			// path had none at the end
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
	}
}
