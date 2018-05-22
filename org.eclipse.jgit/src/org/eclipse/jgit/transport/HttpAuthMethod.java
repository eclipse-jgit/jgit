/*
 * Copyright (C) 2010, 2013, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;
import static org.eclipse.jgit.util.HttpSupport.HDR_WWW_AUTHENTICATE;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.GSSManagerFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

/**
 * Support class to populate user authentication data on a connection.
 * <p>
 * Instances of an HttpAuthMethod are not thread-safe, as some implementations
 * may need to maintain per-connection state information.
 */
abstract class HttpAuthMethod {
	/**
	 * Enum listing the http authentication method types supported by jgit. They
	 * are sorted by priority order!!!
	 */
	public enum Type {
		NONE {
			@Override
			public HttpAuthMethod method(String hdr) {
				return None.INSTANCE;
			}

			@Override
			public String getSchemeName() {
				return "None"; //$NON-NLS-1$
			}
		},
		BASIC {
			@Override
			public HttpAuthMethod method(String hdr) {
				return new Basic();
			}

			@Override
			public String getSchemeName() {
				return "Basic"; //$NON-NLS-1$
			}
		},
		DIGEST {
			@Override
			public HttpAuthMethod method(String hdr) {
				return new Digest(hdr);
			}

			@Override
			public String getSchemeName() {
				return "Digest"; //$NON-NLS-1$
			}
		},
		NEGOTIATE {
			@Override
			public HttpAuthMethod method(String hdr) {
				return new Negotiate(hdr);
			}

			@Override
			public String getSchemeName() {
				return "Negotiate"; //$NON-NLS-1$
			}
		};
		/**
		 * Creates a HttpAuthMethod instance configured with the provided HTTP
		 * WWW-Authenticate header.
		 *
		 * @param hdr the http header
		 * @return a configured HttpAuthMethod instance
		 */
		public abstract HttpAuthMethod method(String hdr);

		/**
		 * @return the name of the authentication scheme in the form to be used
		 *         in HTTP authentication headers as specified in RFC2617 and
		 *         RFC4559
		 */
		public abstract String getSchemeName();
	}

	static final String EMPTY_STRING = ""; //$NON-NLS-1$
	static final String SCHEMA_NAME_SEPARATOR = " "; //$NON-NLS-1$

	/**
	 * Handle an authentication failure and possibly return a new response.
	 *
	 * @param conn
	 *            the connection that failed.
	 * @param ignoreTypes
	 *            authentication types to be ignored.
	 * @return new authentication method to try.
	 */
	static HttpAuthMethod scanResponse(final HttpConnection conn,
			Collection<Type> ignoreTypes) {
		final Map<String, List<String>> headers = conn.getHeaderFields();
		HttpAuthMethod authentication = Type.NONE.method(EMPTY_STRING);

		for (Entry<String, List<String>> entry : headers.entrySet()) {
			if (HDR_WWW_AUTHENTICATE.equalsIgnoreCase(entry.getKey())) {
				if (entry.getValue() != null) {
					for (String value : entry.getValue()) {
						if (value != null && value.length() != 0) {
							final String[] valuePart = value.split(
									SCHEMA_NAME_SEPARATOR, 2);

							try {
								Type methodType = Type.valueOf(
										valuePart[0].toUpperCase(Locale.ROOT));

								if ((ignoreTypes != null)
										&& (ignoreTypes.contains(methodType))) {
									continue;
								}

								if (authentication.getType().compareTo(methodType) >= 0) {
									continue;
								}

								final String param;
								if (valuePart.length == 1)
									param = EMPTY_STRING;
								else
									param = valuePart[1];

								authentication = methodType
										.method(param);
							} catch (IllegalArgumentException e) {
								// This auth method is not supported
							}
						}
					}
				}
				break;
			}
		}

		return authentication;
	}

	protected final Type type;

	/**
	 * Constructor for HttpAuthMethod.
	 *
	 * @param type
	 *            authentication method type
	 */
	protected HttpAuthMethod(Type type) {
		this.type = type;
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
				char[] v = p.getValue();
				password = (v == null) ? null : new String(p.getValue());
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
	abstract void configureRequest(HttpConnection conn) throws IOException;

	/**
	 * Gives the method type associated to this http auth method
	 *
	 * @return the method type
	 */
	public Type getType() {
		return type;
	}

	/** Performs no user authentication. */
	private static class None extends HttpAuthMethod {
		static final None INSTANCE = new None();
		public None() {
			super(Type.NONE);
		}

		@Override
		void authorize(String user, String pass) {
			// Do nothing when no authentication is enabled.
		}

		@Override
		void configureRequest(HttpConnection conn) throws IOException {
			// Do nothing when no authentication is enabled.
		}
	}

	/** Performs HTTP basic authentication (plaintext username/password). */
	private static class Basic extends HttpAuthMethod {
		private String user;

		private String pass;

		public Basic() {
			super(Type.BASIC);
		}

		@Override
		void authorize(String username, String password) {
			this.user = username;
			this.pass = password;
		}

		@Override
		void configureRequest(HttpConnection conn) throws IOException {
			String ident = user + ":" + pass; //$NON-NLS-1$
			String enc = Base64.encodeBytes(ident.getBytes(CHARSET));
			conn.setRequestProperty(HDR_AUTHORIZATION, type.getSchemeName()
					+ " " + enc); //$NON-NLS-1$
		}
	}

	/** Performs HTTP digest authentication. */
	private static class Digest extends HttpAuthMethod {
		private static final SecureRandom PRNG = new SecureRandom();

		private final Map<String, String> params;

		private int requestCount;

		private String user;

		private String pass;

		Digest(String hdr) {
			super(Type.DIGEST);
			params = parse(hdr);

			final String qop = params.get("qop"); //$NON-NLS-1$
			if ("auth".equals(qop)) { //$NON-NLS-1$
				final byte[] bin = new byte[8];
				PRNG.nextBytes(bin);
				params.put("cnonce", Base64.encodeBytes(bin)); //$NON-NLS-1$
			}
		}

		@Override
		void authorize(String username, String password) {
			this.user = username;
			this.pass = password;
		}

		@SuppressWarnings("boxing")
		@Override
		void configureRequest(HttpConnection conn) throws IOException {
			final Map<String, String> r = new LinkedHashMap<>();

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
			conn.setRequestProperty(HDR_AUTHORIZATION, type.getSchemeName()
					+ " " + v); //$NON-NLS-1$
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
			MessageDigest md = newMD5();
			md.update(data.getBytes(CHARSET));
			return LHEX(md.digest());
		}

		private static String KD(String secret, String data) {
			MessageDigest md = newMD5();
			md.update(secret.getBytes(CHARSET));
			md.update((byte) ':');
			md.update(data.getBytes(CHARSET));
			return LHEX(md.digest());
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
			Map<String, String> p = new HashMap<>();
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

	private static class Negotiate extends HttpAuthMethod {
		private static final GSSManagerFactory GSS_MANAGER_FACTORY = GSSManagerFactory
				.detect();

		private static final Oid OID;
		static {
			try {
				// OID for SPNEGO
				OID = new Oid("1.3.6.1.5.5.2"); //$NON-NLS-1$
			} catch (GSSException e) {
				throw new Error("Cannot create NEGOTIATE oid.", e); //$NON-NLS-1$
			}
		}

		private final byte[] prevToken;

		public Negotiate(String hdr) {
			super(Type.NEGOTIATE);
			prevToken = Base64.decode(hdr);
		}

		@Override
		void authorize(String user, String pass) {
			// not used
		}

		@Override
		void configureRequest(HttpConnection conn) throws IOException {
			GSSManager gssManager = GSS_MANAGER_FACTORY.newInstance(conn
					.getURL());
			String host = conn.getURL().getHost();
			String peerName = "HTTP@" + host.toLowerCase(Locale.ROOT); //$NON-NLS-1$
			try {
				GSSName gssName = gssManager.createName(peerName,
						GSSName.NT_HOSTBASED_SERVICE);
				GSSContext context = gssManager.createContext(gssName, OID,
						null, GSSContext.DEFAULT_LIFETIME);
				// Respect delegation policy in HTTP/SPNEGO.
				context.requestCredDeleg(true);

				byte[] token = context.initSecContext(prevToken, 0,
						prevToken.length);

				conn.setRequestProperty(HDR_AUTHORIZATION, getType().getSchemeName()
						+ " " + Base64.encodeBytes(token)); //$NON-NLS-1$
			} catch (GSSException e) {
				throw new IOException(e);
			}
		}
	}
}
