/*
 * Copyright (C) 2010, Google Inc.
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

import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;
import static org.eclipse.jgit.util.HttpSupport.HDR_WWW_AUTHENTICATE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.jgit.util.Base64;

/**
 * Support class to populate user authentication data on a connection.
 * <p>
 * Instances of an HttpAuthMethod are not thread-safe, as some implementations
 * may need to maintain per-connection state information.
 */
abstract class HttpAuthMethod {
	/** No authentication is configured. */
	static final HttpAuthMethod NONE = new None();

	/**
	 * Handle an authentication failure and possibly return a new response.
	 *
	 * @param conn
	 *            the connection that failed.
	 * @return new authentication method to try.
	 */
	static HttpAuthMethod scanResponse(HttpURLConnection conn) {
		String hdr = conn.getHeaderField(HDR_WWW_AUTHENTICATE);
		if (hdr == null || hdr.length() == 0)
			return NONE;

		int sp = hdr.indexOf(' ');
		if (sp < 0)
			return NONE;

		String type = hdr.substring(0, sp);
		if (Basic.NAME.equalsIgnoreCase(type))
			return new Basic();
		else if (Digest.NAME.equalsIgnoreCase(type))
			return new Digest(hdr.substring(sp + 1));
		else
			return NONE;
	}

	/**
	 * Update this method with the credentials from the URIish.
	 *
	 * @param uri
	 *            the URI used to create the connection.
	 * @param credentialsProvider
	 *            the credentials provider, or null. If provided,
	 *            {@link URIish#getPass() credentials in the URI} are ignored.
	 *
	 * @return true if the authentication method is able to provide
	 *         authorization for the given URI
	 */
	boolean authorize(URIish uri, CredentialsProvider credentialsProvider) {
		String username;
		String password;

		if (credentialsProvider != null) {
			CredentialItem.Username u = new CredentialItem.Username();
			CredentialItem.Password p = new CredentialItem.Password();

			if (credentialsProvider.supports(u, p)
					&& credentialsProvider.get(uri, u, p)) {
				username = u.getValue();
				password = new String(p.getValue());
				p.clear();
			} else
				return false;
		} else {
			username = uri.getUser();
			password = uri.getPass();
		}
		if (username != null) {
			authorize(username, password);
			return true;
		}
		return false;
	}

	/**
	 * Update this method with the given username and password pair.
	 *
	 * @param user
	 * @param pass
	 */
	abstract void authorize(String user, String pass);

	/**
	 * Update connection properties based on this authentication method.
	 *
	 * @param conn
	 * @throws IOException
	 */
	abstract void configureRequest(HttpURLConnection conn) throws IOException;

	/** Performs no user authentication. */
	private static class None extends HttpAuthMethod {
		@Override
		void authorize(String user, String pass) {
			// Do nothing when no authentication is enabled.
		}

		@Override
		void configureRequest(HttpURLConnection conn) throws IOException {
			// Do nothing when no authentication is enabled.
		}
	}

	/** Performs HTTP basic authentication (plaintext username/password). */
	private static class Basic extends HttpAuthMethod {
		static final String NAME = "Basic"; //$NON-NLS-1$

		private String user;

		private String pass;

		@Override
		void authorize(final String username, final String password) {
			this.user = username;
			this.pass = password;
		}

		@Override
		void configureRequest(final HttpURLConnection conn) throws IOException {
			String ident = user + ":" + pass; //$NON-NLS-1$
			String enc = Base64.encodeBytes(ident.getBytes("UTF-8")); //$NON-NLS-1$
			conn.setRequestProperty(HDR_AUTHORIZATION, NAME + " " + enc); //$NON-NLS-1$
		}
	}

	/** Performs HTTP digest authentication. */
	private static class Digest extends HttpAuthMethod {
		static final String NAME = "Digest"; //$NON-NLS-1$

		private static final Random PRNG = new Random();

		private final Map<String, String> params;

		private int requestCount;

		private String user;

		private String pass;

		Digest(String hdr) {
			params = parse(hdr);

			final String qop = params.get("qop"); //$NON-NLS-1$
			if ("auth".equals(qop)) { //$NON-NLS-1$
				final byte[] bin = new byte[8];
				PRNG.nextBytes(bin);
				params.put("cnonce", Base64.encodeBytes(bin)); //$NON-NLS-1$
			}
		}

		@Override
		void authorize(final String username, final String password) {
			this.user = username;
			this.pass = password;
		}

		@SuppressWarnings("boxing")
		@Override
		void configureRequest(final HttpURLConnection conn) throws IOException {
			final Map<String, String> r = new LinkedHashMap<String, String>();

			final String realm = params.get("realm"); //$NON-NLS-1$
			final String nonce = params.get("nonce"); //$NON-NLS-1$
			final String cnonce = params.get("cnonce"); //$NON-NLS-1$
			final String uri = uri(conn.getURL());
			final String qop = params.get("qop"); //$NON-NLS-1$
			final String method = conn.getRequestMethod();

			final String A1 = user + ":" + realm + ":" + pass; //$NON-NLS-1$ //$NON-NLS-2$
			final String A2 = method + ":" + uri; //$NON-NLS-1$

			r.put("username", user); //$NON-NLS-1$
			r.put("realm", realm); //$NON-NLS-1$
			r.put("nonce", nonce); //$NON-NLS-1$
			r.put("uri", uri); //$NON-NLS-1$

			final String response, nc;
			if ("auth".equals(qop)) { //$NON-NLS-1$
				nc = String.format("%08x", ++requestCount); //$NON-NLS-1$
				response = KD(H(A1), nonce + ":" + nc + ":" + cnonce + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ qop + ":" //$NON-NLS-1$
						+ H(A2));
			} else {
				nc = null;
				response = KD(H(A1), nonce + ":" + H(A2)); //$NON-NLS-1$
			}
			r.put("response", response); //$NON-NLS-1$
			if (params.containsKey("algorithm")) //$NON-NLS-1$
				r.put("algorithm", "MD5"); //$NON-NLS-1$ //$NON-NLS-2$
			if (cnonce != null && qop != null)
				r.put("cnonce", cnonce); //$NON-NLS-1$
			if (params.containsKey("opaque")) //$NON-NLS-1$
				r.put("opaque", params.get("opaque")); //$NON-NLS-1$ //$NON-NLS-2$
			if (qop != null)
				r.put("qop", qop); //$NON-NLS-1$
			if (nc != null)
				r.put("nc", nc); //$NON-NLS-1$

			StringBuilder v = new StringBuilder();
			for (Map.Entry<String, String> e : r.entrySet()) {
				if (v.length() > 0)
					v.append(", "); //$NON-NLS-1$
				v.append(e.getKey());
				v.append('=');
				v.append('"');
				v.append(e.getValue());
				v.append('"');
			}
			conn.setRequestProperty(HDR_AUTHORIZATION, NAME + " " + v); //$NON-NLS-1$
		}

		private static String uri(URL u) {
			StringBuilder r = new StringBuilder();
			r.append(u.getProtocol());
			r.append("://"); //$NON-NLS-1$
			r.append(u.getHost());
			if (0 < u.getPort()) {
				if (u.getPort() == 80 && "http".equals(u.getProtocol())) { //$NON-NLS-1$
					/* nothing */
				} else if (u.getPort() == 443
						&& "https".equals(u.getProtocol())) { //$NON-NLS-1$
					/* nothing */
				} else {
					r.append(':').append(u.getPort());
				}
			}
			r.append(u.getPath());
			if (u.getQuery() != null)
				r.append('?').append(u.getQuery());
			return r.toString();
		}

		private static String H(String data) {
			try {
				MessageDigest md = newMD5();
				md.update(data.getBytes("UTF-8")); //$NON-NLS-1$
				return LHEX(md.digest());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("UTF-8 encoding not available", e); //$NON-NLS-1$
			}
		}

		private static String KD(String secret, String data) {
			try {
				MessageDigest md = newMD5();
				md.update(secret.getBytes("UTF-8")); //$NON-NLS-1$
				md.update((byte) ':');
				md.update(data.getBytes("UTF-8")); //$NON-NLS-1$
				return LHEX(md.digest());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("UTF-8 encoding not available", e); //$NON-NLS-1$
			}
		}

		private static MessageDigest newMD5() {
			try {
				return MessageDigest.getInstance("MD5"); //$NON-NLS-1$
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("No MD5 available", e); //$NON-NLS-1$
			}
		}

		private static final char[] LHEX = { '0', '1', '2', '3', '4', '5', '6',
				'7', '8', '9', //
				'a', 'b', 'c', 'd', 'e', 'f' };

		private static String LHEX(byte[] bin) {
			StringBuilder r = new StringBuilder(bin.length * 2);
			for (int i = 0; i < bin.length; i++) {
				byte b = bin[i];
				r.append(LHEX[(b >>> 4) & 0x0f]);
				r.append(LHEX[b & 0x0f]);
			}
			return r.toString();
		}

		private static Map<String, String> parse(String auth) {
			Map<String, String> p = new HashMap<String, String>();
			int next = 0;
			while (next < auth.length()) {
				if (next < auth.length() && auth.charAt(next) == ',') {
					next++;
				}
				while (next < auth.length()
						&& Character.isWhitespace(auth.charAt(next))) {
					next++;
				}

				int eq = auth.indexOf('=', next);
				if (eq < 0 || eq + 1 == auth.length()) {
					return Collections.emptyMap();
				}

				final String name = auth.substring(next, eq);
				final String value;
				if (auth.charAt(eq + 1) == '"') {
					int dq = auth.indexOf('"', eq + 2);
					if (dq < 0) {
						return Collections.emptyMap();
					}
					value = auth.substring(eq + 2, dq);
					next = dq + 1;

				} else {
					int space = auth.indexOf(' ', eq + 1);
					int comma = auth.indexOf(',', eq + 1);
					if (space < 0)
						space = auth.length();
					if (comma < 0)
						comma = auth.length();

					final int e = Math.min(space, comma);
					value = auth.substring(eq + 1, e);
					next = e + 1;
				}
				p.put(name, value);
			}
			return p;
		}
	}
}
