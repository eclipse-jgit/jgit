/*
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2015, Patrick Steinhardt <ps@pks.im> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.References;
import org.eclipse.jgit.util.StringUtils;

/**
 * This URI like construct used for referencing Git archives over the net, as
 * well as locally stored archives. It is similar to RFC 2396 URI's, but also
 * support SCP and the malformed file://&lt;path&gt; syntax (as opposed to the correct
 * file:&lt;path&gt; syntax.
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
	private static final String OPT_USER_PWD_P = "(?:([^/:]+)(?::([^\\\\/]+))?@)?"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the host part of URIs. Defines one
	 * capturing group containing the host name.
	 */
	private static final String HOST_P = "((?:[^\\\\/:]+)|(?:\\[[0-9a-f:]+\\]))"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches the optional port part of URIs. Defines
	 * one capturing group containing the port without the preceding colon.
	 */
	private static final String OPT_PORT_P = "(?::(\\d*))?"; //$NON-NLS-1$

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
	private static final String RELATIVE_PATH_P = "(?:(?:[^\\\\/]+[\\\\/]+)*[^\\\\/]+[\\\\/]*)"; //$NON-NLS-1$

	/**
	 * Part of a pattern which matches a relative or absolute path. Defines no
	 * capturing group.
	 */
	private static final String PATH_P = "(" + OPT_DRIVE_LETTER_P + "[\\\\/]?" //$NON-NLS-1$ //$NON-NLS-2$
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
			+ "(" // open a group capturing the user-home-dir-part //$NON-NLS-1$
			+ (USER_HOME_P + "?") //$NON-NLS-1$
			+ "(?:" // start non capturing group for host //$NON-NLS-1$
					// separator or end of line
			+ "[\\\\/])|$" //$NON-NLS-1$
			+ ")" // close non capturing group for the host//$NON-NLS-1$
					// separator or end of line
			+ ")?" // close the optional group containing hostname //$NON-NLS-1$
			+ "(.+)?" //$NON-NLS-1$
			+ "$"); //$NON-NLS-1$

	/**
	 * A pattern matching the reference to a local file. This may be an absolute
	 * path (maybe even containing windows drive-letters) or a relative path.
	 */
	private static final Pattern LOCAL_FILE = Pattern.compile("^" // //$NON-NLS-1$
			+ "([\\\\/]?" + PATH_P + ")" // //$NON-NLS-1$ //$NON-NLS-2$
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
			+ ("(?:" + USER_HOME_P + "[\\\\/])?") // //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Parse and construct an {@link org.eclipse.jgit.transport.URIish} from a
	 * string
	 *
	 * @param s
	 *            a {@link java.lang.String} object.
	 * @throws java.net.URISyntaxException
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
			// empty ports are in general allowed, except for URLs like
			// file://D:/path for which it is more desirable to parse with
			// host=null and path=D:/path
			String portString = matcher.group(5);
			if ("file".equals(scheme) && "".equals(portString)) { //$NON-NLS-1$ //$NON-NLS-2$
				rawPath = cleanLeadingSlashes(
						n2e(matcher.group(4)) + ":" + portString //$NON-NLS-1$
								+ n2e(matcher.group(6)) + n2e(matcher.group(7)),
						scheme);
			} else {
				host = unescape(matcher.group(4));
				if (portString != null && portString.length() > 0) {
					port = Integer.parseInt(portString);
				}
				rawPath = cleanLeadingSlashes(
						n2e(matcher.group(6)) + n2e(matcher.group(7)), scheme);
			}
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

	private static int parseHexByte(byte c1, byte c2) {
			return ((RawParseUtils.parseHexInt4(c1) << 4)
					| RawParseUtils.parseHexInt4(c2));
	}

	private static String unescape(String s) throws URISyntaxException {
		if (s == null)
			return null;
		if (s.indexOf('%') < 0)
			return s;

		byte[] bytes = s.getBytes(UTF_8);

		byte[] os = new byte[bytes.length];
		int j = 0;
		for (int i = 0; i < bytes.length; ++i) {
			byte c = bytes[i];
			if (c == '%') {
				if (i + 2 >= bytes.length)
					throw new URISyntaxException(s, JGitText.get().cannotParseGitURIish);
				byte c1 = bytes[i + 1];
				byte c2 = bytes[i + 2];
				int val;
				try {
					val = parseHexByte(c1, c2);
				} catch (ArrayIndexOutOfBoundsException e) {
					URISyntaxException use = new URISyntaxException(s,
							JGitText.get().cannotParseGitURIish);
					use.initCause(e);
					throw use;
				}
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
		byte[] bytes = s.getBytes(UTF_8);
		for (byte c : bytes) {
			int b = c & 0xFF;
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
		return s == null ? "" : s; //$NON-NLS-1$
	}

	// takes care to cut of a leading slash if a windows drive letter or a
	// user-home-dir specifications are
	private String cleanLeadingSlashes(String p, String s) {
		if (p.length() >= 3
				&& p.charAt(0) == '/'
				&& p.charAt(2) == ':'
				&& ((p.charAt(1) >= 'A' && p.charAt(1) <= 'Z')
						|| (p.charAt(1) >= 'a' && p.charAt(1) <= 'z')))
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
	public URIish(URL u) {
		scheme = u.getProtocol();
		path = u.getPath();
		path = cleanLeadingSlashes(path, scheme);
		try {
			rawPath = u.toURI().getRawPath();
			rawPath = cleanLeadingSlashes(rawPath, scheme);
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

	/**
	 * Create an empty, non-configured URI.
	 */
	public URIish() {
		// Configure nothing.
	}

	private URIish(URIish u) {
		this.scheme = u.scheme;
		this.rawPath = u.rawPath;
		this.path = u.path;
		this.user = u.user;
		this.pass = u.pass;
		this.port = u.port;
		this.host = u.host;
	}

	/**
	 * Whether this URI references a repository on another system.
	 *
	 * @return true if this URI references a repository on another system.
	 */
	public boolean isRemote() {
		return getHost() != null;
	}

	/**
	 * Get host name part.
	 *
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
	public URIish setHost(String n) {
		final URIish r = new URIish(this);
		r.host = n;
		return r;
	}

	/**
	 * Get protocol name
	 *
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
	public URIish setScheme(String n) {
		final URIish r = new URIish(this);
		r.scheme = n;
		return r;
	}

	/**
	 * Get path name component
	 *
	 * @return path name component
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Get path name component
	 *
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
	public URIish setPath(String n) {
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
	 * @throws java.net.URISyntaxException
	 */
	public URIish setRawPath(String n) throws URISyntaxException {
		final URIish r = new URIish(this);
		r.path = unescape(n);
		r.rawPath = n;
		return r;
	}

	/**
	 * Get user name requested for transfer
	 *
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
	public URIish setUser(String n) {
		final URIish r = new URIish(this);
		r.user = n;
		return r;
	}

	/**
	 * Get password requested for transfer
	 *
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
	public URIish setPass(String n) {
		final URIish r = new URIish(this);
		r.pass = n;
		return r;
	}

	/**
	 * Get port number requested for transfer or -1 if not explicit
	 *
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
	public URIish setPort(int n) {
		final URIish r = new URIish(this);
		r.port = n > 0 ? n : -1;
		return r;
	}

	/** {@inheritDoc} */
	@Override
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

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
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

	private static boolean eq(String a, String b) {
		if (References.isSameObject(a, b)) {
			return true;
		}
		if (StringUtils.isEmptyOrNull(a) && StringUtils.isEmptyOrNull(b))
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

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return format(false, false);
	}

	private String format(boolean includePassword, boolean escapeNonAscii) {
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
			if (getUser() != null && getUser().length() > 0)
				r.append('@');
			r.append(escape(getHost(), false, escapeNonAscii));
			if (getScheme() != null && getPort() > 0) {
				r.append(':');
				r.append(getPort());
			}
		}

		if (getPath() != null) {
			if (getScheme() != null) {
				if (!getPath().startsWith("/") && !getPath().isEmpty()) //$NON-NLS-1$
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
	 * Get the URI as an ASCII string.
	 *
	 * @return the URI as an ASCII string. Password is not included.
	 */
	public String toASCIIString() {
		return format(false, true);
	}

	/**
	 * Convert the URI including password, formatted with only ASCII characters
	 * such that it will be valid for use over the network.
	 *
	 * @return the URI including password, formatted with only ASCII characters
	 *         such that it will be valid for use over the network.
	 */
	public String toPrivateASCIIString() {
		return format(true, true);
	}

	/**
	 * Get the "humanish" part of the path. Some examples of a 'humanish' part
	 * for a full path:
	 * <table summary="path vs humanish path" border="1">
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
	 * <td><code>localhost</code></td>
	 * <td><code>ssh://localhost/</code></td>
	 * </tr>
	 * <tr>
	 * <td><code>/path//to</code></td>
	 * <td>an empty string</td>
	 * </tr>
	 * </table>
	 *
	 * @return the "humanish" part of the path. May be an empty string. Never
	 *         {@code null}.
	 * @throws java.lang.IllegalArgumentException
	 *             if it's impossible to determine a humanish part, or path is
	 *             {@code null} or empty
	 * @see #getPath
	 */
	public String getHumanishName() throws IllegalArgumentException {
		String s = getPath();
		if ("/".equals(s) || "".equals(s)) //$NON-NLS-1$ //$NON-NLS-2$
			s = getHost();
		if (s == null) // $NON-NLS-1$
			throw new IllegalArgumentException();

		String[] elements;
		if ("file".equals(scheme) || LOCAL_FILE.matcher(s).matches()) //$NON-NLS-1$
			elements = s.split("[\\" + File.separatorChar + "/]"); //$NON-NLS-1$ //$NON-NLS-2$
		else
			elements = s.split("/+"); //$NON-NLS-1$
		if (elements.length == 0)
			throw new IllegalArgumentException();
		String result = elements[elements.length - 1];
		if (Constants.DOT_GIT.equals(result))
			result = elements[elements.length - 2];
		else if (result.endsWith(Constants.DOT_GIT_EXT))
			result = result.substring(0, result.length()
					- Constants.DOT_GIT_EXT.length());
		if (("file".equals(scheme) || LOCAL_FILE.matcher(s).matches())
				&& result.endsWith(Constants.DOT_BUNDLE_EXT)) {
			result = result.substring(0,
					result.length() - Constants.DOT_BUNDLE_EXT.length());
		}
		return result;
	}

}
