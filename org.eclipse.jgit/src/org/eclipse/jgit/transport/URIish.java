/*
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;

/**
 * This URI like construct used for referencing Git archives over the net, as
 * well as locally stored archives. It is similar to RFC 2396 URI's, but also
 * support SCP and the malformed file://<path> syntax (as opposed to the correct
 * file:<path> syntax.
 */
public class URIish implements Serializable {
	/**
	 * Part of a pattern which matches the scheme part (git, http, ...) of an
	 * URI. Defines one capturing group containing the scheme without the
	 * trailing colon and slashes
	 */
	private static final String SCHEME_P = "([a-z][a-z0-9+-]+)://"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the optional user/password part (e.g.
	 * root:pwd@ in git://root:pwd@host.xyz/a.git) of URIs. Defines two
	 * capturing groups: the first containing the user and the second containing
	 * the password
	 */
	private static final String OPT_USER_PWD_P = "(?:([^/:@]+)(?::([^\\\\/]+))?@)?"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the host part of URIs. Defines one
	 * capturing group containing the host name.
	 */
	private static final String HOST_P = "([^\\\\/:]+)"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the optional port part of URIs. Defines
	 * one capturing group containing the port without the preceding colon.
	 */
	private static final String OPT_PORT_P = "(?::(\\d+))?"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the ~username part (e.g. /~root in
	 * git://host.xyz/~root/a.git) of URIs. Defines no capturing group.
	 */
	private static final String USER_HOME_P = "(?:/~(?:[^\\\\/]+))"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the optional drive letter in paths (e.g.
	 * D: in file:///D:/a.txt). Defines no capturing group.
	 */
	private static final String OPT_DRIVE_LETTER_P = "(?:[A-Za-z]:)?"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches a relative path. Relative paths don't
	 * start with slash or drive letters. Defines no capturing group.
	 */
	private static final String RELATIVE_PATH_P = "(?:(?:[^\\\\/]+[\\\\/])*[^\\\\/]+[\\\\/]?)"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches a relative or absolute path. Defines no
	 * capturing group.
	 */
	private static final String PATH_P = "(" + OPT_DRIVE_LETTER_P + "[\\\\/]?" //$NON-NLS-1$
			+ RELATIVE_PATH_P + ")"; //$NON-NLS-1$

	private static final long serialVersionUID = 1L;

	/**
	 * A pattern matching standard URI: </br>
	 * <code>scheme "://" user_password? hostname? portnumber? path</code>
	 */
	private static final Pattern FULL_URI = Pattern.compile("^" // //$NON-NLS-1$
			+ SCHEME_P //
			+ "(?:" // start a group containing hostname and all options only //$NON-NLS-1$
					// availabe when a hostname is there
			+ OPT_USER_PWD_P //
			+ HOST_P //
			+ OPT_PORT_P //
			+ "(" // open a catpuring group the the user-home-dir part //$NON-NLS-1$
			+ (USER_HOME_P + "?") // //$NON-NLS-1$
			+ "[\\\\/])" // //$NON-NLS-1$
			+ ")?" // close the optional group containing hostname //$NON-NLS-1$
			+ "(.+)?" // //$NON-NLS-1$
			+ "$"); //$NON-NLS-1$

	/**
	 * A pattern matching the reference to a local file. This may be an absolute
	 * path (maybe even containing windows drive-letters) or a relative path.
	 */
	private static final Pattern LOCAL_FILE = Pattern.compile("^" // //$NON-NLS-1$
			+ "([\\\\/]?" + PATH_P + ")" // //$NON-NLS-1$
			+ "$"); //$NON-NLS-1$

	/**
	 * A pattern matching a URI for the scheme 'file' which has only ':/' as
	 * separator between scheme and path. Standard file URIs have '://' as
	 * separator, but java.io.File.toURI() constructs those URIs.
	 */
	private static final Pattern SINGLE_SLASH_FILE_URI = Pattern.compile("^" // //$NON-NLS-1$
			+ "(file):([\\\\/](?![\\\\/])" // //$NON-NLS-1$
			+ PATH_P //
			+ ")$"); //$NON-NLS-1$

	/**
	 * A pattern matching a SCP URI's of the form user@host:path/to/repo.git
	 */
	private static final Pattern RELATIVE_SCP_URI = Pattern.compile("^" // //$NON-NLS-1$
			+ OPT_USER_PWD_P //
			+ HOST_P //
			+ ":(" // //$NON-NLS-1$
			+ ("(?:" + USER_HOME_P + "[\\\\/])?") // //$NON-NLS-1$
			+ RELATIVE_PATH_P //
			+ ")$"); //$NON-NLS-1$

	/**
	 * A pattern matching a SCP URI's of the form user@host:/path/to/repo.git
	 */
	private static final Pattern ABSOLUTE_SCP_URI = Pattern.compile("^" // //$NON-NLS-1$
			+ OPT_USER_PWD_P //
			+ "([^\\\\/:]{2,})" // //$NON-NLS-1$
			+ ":(" // //$NON-NLS-1$
			+ "[\\\\/]" + RELATIVE_PATH_P // //$NON-NLS-1$
			+ ")$"); //$NON-NLS-1$

	private String scheme;

	private String path;

	private String rawPath;

	private String user;

	private String pass;

	private int port = -1;

	private String host;

	/**
	 * Parse and construct an {@link URIish} from a string
	 *
	 * @param s
	 * @throws URISyntaxException
	 */
	public URIish(String s) throws URISyntaxException {
		if (StringUtils.isEmptyOrNull(s)) {
			throw new URISyntaxException("The uri was empty or null", //$NON-NLS-1$
					JGitText.get().cannotParseGitURIish);
		}
		Matcher matcher = SINGLE_SLASH_FILE_URI.matcher(s);
		if (matcher.matches()) {
			scheme = matcher.group(1);
			rawPath = cleanLeadingSlashes(matcher.group(2), scheme);
			path = unescape(rawPath);
			return;
		}
		matcher = FULL_URI.matcher(s);
		if (matcher.matches()) {
			scheme = matcher.group(1);
			user = unescape(matcher.group(2));
			pass = unescape(matcher.group(3));
			host = unescape(matcher.group(4));
			if (matcher.group(5) != null)
				port = Integer.parseInt(matcher.group(5));
			rawPath = cleanLeadingSlashes(
					n2e(matcher.group(6)) + n2e(matcher.group(7)), scheme);
			path = unescape(rawPath);
			return;
		}
		matcher = RELATIVE_SCP_URI.matcher(s);
		if (matcher.matches()) {
			user = matcher.group(1);
			pass = matcher.group(2);
			host = matcher.group(3);
			rawPath = matcher.group(4);
			path = rawPath;
			return;
		}
		matcher = ABSOLUTE_SCP_URI.matcher(s);
		if (matcher.matches()) {
			user = matcher.group(1);
			pass = matcher.group(2);
			host = matcher.group(3);
			rawPath = matcher.group(4);
			path = rawPath;
			return;
		}
		matcher = LOCAL_FILE.matcher(s);
		if (matcher.matches()) {
			rawPath = matcher.group(1);
			path = rawPath;
			return;
		}
		throw new URISyntaxException(s, JGitText.get().cannotParseGitURIish);
	}

	private static String unescape(String s) throws URISyntaxException {
		if (s == null)
			return null;
		if (s.indexOf('%') < 0)
			return s;

		byte[] bytes;
		try {
			bytes = s.getBytes(Constants.CHARACTER_ENCODING);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); // can't happen
		}

		byte[] os = new byte[bytes.length];
		int j = 0;
		for (int i = 0; i < bytes.length; ++i) {
			byte c = bytes[i];
			if (c == '%') {
				if (i + 2 >= bytes.length)
					throw new URISyntaxException(s, JGitText.get().cannotParseGitURIish);
				int val = (RawParseUtils.parseHexInt4(bytes[i + 1]) << 4)
						| RawParseUtils.parseHexInt4(bytes[i + 2]);
				os[j++] = (byte) val;
				i += 2;
			} else
				os[j++] = c;
		}
		return RawParseUtils.decode(os, 0, j);
	}

	private static final BitSet reservedChars = new BitSet(127);

	static {
		for (byte b : Constants.encodeASCII("!*'();:@&=+$,/?#[]")) //$NON-NLS-1$
			reservedChars.set(b);
	}

	/**
	 * Escape unprintable characters optionally URI-reserved characters
	 *
	 * @param s
	 *            The Java String to encode (may contain any character)
	 * @param escapeReservedChars
	 *            true to escape URI reserved characters
	 * @param encodeNonAscii
	 *            encode any non-ASCII characters
	 * @return a URI-encoded string
	 */
	private static String escape(String s, boolean escapeReservedChars,
			boolean encodeNonAscii) {
		if (s == null)
			return null;
		ByteArrayOutputStream os = new ByteArrayOutputStream(s.length());
		byte[] bytes;
		try {
			bytes = s.getBytes(Constants.CHARACTER_ENCODING);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); // cannot happen
		}
		for (int i = 0; i < bytes.length; ++i) {
			int b = bytes[i] & 0xFF;
			if (b <= 32 || (encodeNonAscii && b > 127) || b == '%'
					|| (escapeReservedChars && reservedChars.get(b))) {
				os.write('%');
				byte[] tmp = Constants.encodeASCII(String.format("%02x", //$NON-NLS-1$
						Integer.valueOf(b)));
				os.write(tmp[0]);
				os.write(tmp[1]);
			} else {
				os.write(b);
			}
		}
		byte[] buf = os.toByteArray();
		return RawParseUtils.decode(buf, 0, buf.length);
	}

	private String n2e(String s) {
		if (s == null)
			return ""; //$NON-NLS-1$
		else
			return s;
	}

	// takes care to cut of a leading slash if a windows drive letter or a
	// user-home-dir specifications are
	private String cleanLeadingSlashes(String p, String s) {
		if (p.length() >= 3
				&& p.charAt(0) == '/'
				&& p.charAt(2) == ':'
				&& (p.charAt(1) >= 'A' && p.charAt(1) <= 'Z' || p.charAt(1) >= 'a'
						&& p.charAt(1) <= 'z'))
			return p.substring(1);
		else if (s != null && p.length() >= 2 && p.charAt(0) == '/'
				&& p.charAt(1) == '~')
			return p.substring(1);
		else
			return p;
	}

	/**
	 * Construct a URIish from a standard URL.
	 *
	 * @param u
	 *            the source URL to convert from.
	 */
	public URIish(final URL u) {
		scheme = u.getProtocol();
		path = u.getPath();
		try {
			rawPath = u.toURI().getRawPath();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e); // Impossible
		}

		final String ui = u.getUserInfo();
		if (ui != null) {
			final int d = ui.indexOf(':');
			user = d < 0 ? ui : ui.substring(0, d);
			pass = d < 0 ? null : ui.substring(d + 1);
		}

		port = u.getPort();
		host = u.getHost();
	}

	/** Create an empty, non-configured URI. */
	public URIish() {
		// Configure nothing.
	}

	private URIish(final URIish u) {
		this.scheme = u.scheme;
		this.rawPath = u.rawPath;
		this.path = u.path;
		this.user = u.user;
		this.pass = u.pass;
		this.port = u.port;
		this.host = u.host;
	}

	/**
	 * @return true if this URI references a repository on another system.
	 */
	public boolean isRemote() {
		return getHost() != null;
	}

	/**
	 * @return host name part or null
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Return a new URI matching this one, but with a different host.
	 *
	 * @param n
	 *            the new value for host.
	 * @return a new URI with the updated value.
	 */
	public URIish setHost(final String n) {
		final URIish r = new URIish(this);
		r.host = n;
		return r;
	}

	/**
	 * @return protocol name or null for local references
	 */
	public String getScheme() {
		return scheme;
	}

	/**
	 * Return a new URI matching this one, but with a different scheme.
	 *
	 * @param n
	 *            the new value for scheme.
	 * @return a new URI with the updated value.
	 */
	public URIish setScheme(final String n) {
		final URIish r = new URIish(this);
		r.scheme = n;
		return r;
	}

	/**
	 * @return path name component
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return path name component
	 */
	public String getRawPath() {
		return rawPath;
	}

	/**
	 * Return a new URI matching this one, but with a different path.
	 *
	 * @param n
	 *            the new value for path.
	 * @return a new URI with the updated value.
	 */
	public URIish setPath(final String n) {
		final URIish r = new URIish(this);
		r.path = n;
		r.rawPath = n;
		return r;
	}

	/**
	 * Return a new URI matching this one, but with a different (raw) path.
	 *
	 * @param n
	 *            the new value for path.
	 * @return a new URI with the updated value.
	 * @throws URISyntaxException
	 */
	public URIish setRawPath(final String n) throws URISyntaxException {
		final URIish r = new URIish(this);
		r.path = unescape(n);
		r.rawPath = n;
		return r;
	}

	/**
	 * @return user name requested for transfer or null
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Return a new URI matching this one, but with a different user.
	 *
	 * @param n
	 *            the new value for user.
	 * @return a new URI with the updated value.
	 */
	public URIish setUser(final String n) {
		final URIish r = new URIish(this);
		r.user = n;
		return r;
	}

	/**
	 * @return password requested for transfer or null
	 */
	public String getPass() {
		return pass;
	}

	/**
	 * Return a new URI matching this one, but with a different password.
	 *
	 * @param n
	 *            the new value for password.
	 * @return a new URI with the updated value.
	 */
	public URIish setPass(final String n) {
		final URIish r = new URIish(this);
		r.pass = n;
		return r;
	}

	/**
	 * @return port number requested for transfer or -1 if not explicit
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Return a new URI matching this one, but with a different port.
	 *
	 * @param n
	 *            the new value for port.
	 * @return a new URI with the updated value.
	 */
	public URIish setPort(final int n) {
		final URIish r = new URIish(this);
		r.port = n > 0 ? n : -1;
		return r;
	}

	public int hashCode() {
		int hc = 0;
		if (getScheme() != null)
			hc = hc * 31 + getScheme().hashCode();
		if (getUser() != null)
			hc = hc * 31 + getUser().hashCode();
		if (getPass() != null)
			hc = hc * 31 + getPass().hashCode();
		if (getHost() != null)
			hc = hc * 31 + getHost().hashCode();
		if (getPort() > 0)
			hc = hc * 31 + getPort();
		if (getPath() != null)
			hc = hc * 31 + getPath().hashCode();
		return hc;
	}

	public boolean equals(final Object obj) {
		if (!(obj instanceof URIish))
			return false;
		final URIish b = (URIish) obj;
		if (!eq(getScheme(), b.getScheme()))
			return false;
		if (!eq(getUser(), b.getUser()))
			return false;
		if (!eq(getPass(), b.getPass()))
			return false;
		if (!eq(getHost(), b.getHost()))
			return false;
		if (getPort() != b.getPort())
			return false;
		if (!eq(getPath(), b.getPath()))
			return false;
		return true;
	}

	private static boolean eq(final String a, final String b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	/**
	 * Obtain the string form of the URI, with the password included.
	 *
	 * @return the URI, including its password field, if any.
	 */
	public String toPrivateString() {
		return format(true, false);
	}

	public String toString() {
		return format(false, false);
	}

	private String format(final boolean includePassword, boolean escapeNonAscii) {
		final StringBuilder r = new StringBuilder();
		if (getScheme() != null) {
			r.append(getScheme());
			r.append("://"); //$NON-NLS-1$
		}

		if (getUser() != null) {
			r.append(escape(getUser(), true, escapeNonAscii));
			if (includePassword && getPass() != null) {
				r.append(':');
				r.append(escape(getPass(), true, escapeNonAscii));
			}
		}

		if (getHost() != null) {
			if (getUser() != null)
				r.append('@');
			r.append(escape(getHost(), false, escapeNonAscii));
			if (getScheme() != null && getPort() > 0) {
				r.append(':');
				r.append(getPort());
			}
		}

		if (getPath() != null) {
			if (getScheme() != null) {
				if (!getPath().startsWith("/")) //$NON-NLS-1$
					r.append('/');
			} else if (getHost() != null)
				r.append(':');
			if (getScheme() != null)
				if (escapeNonAscii)
					r.append(escape(getPath(), false, escapeNonAscii));
				else
					r.append(getRawPath());
			else
				r.append(getPath());
		}

		return r.toString();
	}

	/**
	 * @return the URI as an ASCII string. Password is not included.
	 */
	public String toASCIIString() {
		return format(false, true);
	}

	/**
	 * @return the URI including password, formatted with only ASCII characters
	 *         such that it will be valid for use over the network.
	 */
	public String toPrivateASCIIString() {
		return format(true, true);
	}

	/**
	 * Get the "humanish" part of the path. Some examples of a 'humanish' part
	 * for a full path:
	 * <table>
	 * <tr>
	 * <th>Path</th>
	 * <th>Humanish part</th>
	 * </tr>
	 * <tr>
	 * <td><code>/path/to/repo.git</code></td>
	 * <td rowspan="4"><code>repo</code></td>
	 * </tr>
	 * <tr>
	 * <td><code>/path/to/repo.git/</code></td>
	 * </tr>
	 * <tr>
	 * <td><code>/path/to/repo/.git</code></td>
	 * </tr>
	 * <tr>
	 * <td><code>/path/to/repo/</code></td>
	 * </tr>
	 * <tr>
	 * <td><code>/path//to</code></td>
	 * <td>an empty string</td>
	 * </tr>
	 * </table>
	 *
	 * @return the "humanish" part of the path. May be an empty string. Never
	 *         {@code null}.
	 * @throws IllegalArgumentException
	 *             if it's impossible to determine a humanish part, or path is
	 *             {@code null} or empty
	 * @see #getPath
	 */
	public String getHumanishName() throws IllegalArgumentException {
		if ("".equals(getPath()) || getPath() == null) //$NON-NLS-1$
			throw new IllegalArgumentException();
		String s = getPath();
		String[] elements;
		if ("file".equals(scheme) || LOCAL_FILE.matcher(s).matches()) //$NON-NLS-1$
			elements = s.split("[\\" + File.separatorChar + "/]"); //$NON-NLS-1$
		else
			elements = s.split("/"); //$NON-NLS-1$
		if (elements.length == 0)
			throw new IllegalArgumentException();
		String result = elements[elements.length - 1];
		if (Constants.DOT_GIT.equals(result))
			result = elements[elements.length - 2];
		else if (result.endsWith(Constants.DOT_GIT_EXT))
			result = result.substring(0, result.length()
					- Constants.DOT_GIT_EXT.length());
		return result;
	}

}
