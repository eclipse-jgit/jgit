/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Matthias Sohn <matthias.sohn@sap.com>
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_X_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;
import static org.eclipse.jgit.util.HttpSupport.HDR_COOKIE;
import static org.eclipse.jgit.util.HttpSupport.HDR_LOCATION;
import static org.eclipse.jgit.util.HttpSupport.HDR_PRAGMA;
import static org.eclipse.jgit.util.HttpSupport.HDR_SET_COOKIE;
import static org.eclipse.jgit.util.HttpSupport.HDR_SET_COOKIE2;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;
import static org.eclipse.jgit.util.HttpSupport.HDR_WWW_AUTHENTICATE;
import static org.eclipse.jgit.util.HttpSupport.METHOD_GET;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.internal.transport.http.NetscapeCookieFile;
import org.eclipse.jgit.internal.transport.http.NetscapeCookieFileCache;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.HttpAuthMethod.Type;
import org.eclipse.jgit.transport.HttpConfig.HttpRedirectMode;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.util.io.UnionInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport over HTTP and FTP protocols.
 * <p>
 * If the transport is using HTTP and the remote HTTP service is Git-aware
 * (speaks the "smart-http protocol") this client will automatically take
 * advantage of the additional Git-specific HTTP extensions. If the remote
 * service does not support these extensions, the client will degrade to direct
 * file fetching.
 * <p>
 * If the remote (server side) repository does not have the specialized Git
 * support, object files are retrieved directly through standard HTTP GET (or
 * binary FTP GET) requests. This make it easy to serve a Git repository through
 * a standard web host provider that does not offer specific support for Git.
 *
 * @see WalkFetchConnection
 */
public class TransportHttp extends HttpTransport implements WalkTransport,
		PackTransport {

	private static final Logger LOG = LoggerFactory
			.getLogger(TransportHttp.class);

	private static final String SVC_UPLOAD_PACK = "git-upload-pack"; //$NON-NLS-1$

	private static final String SVC_RECEIVE_PACK = "git-receive-pack"; //$NON-NLS-1$

	/**
	 * Accept-Encoding header in the HTTP request
	 * (https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html).
	 *
	 * @since 4.6
	 */
	public enum AcceptEncoding {
		/**
		 * Do not specify an Accept-Encoding header. In most servers this
		 * results in the content being transmitted as-is.
		 */
		UNSPECIFIED,

		/**
		 * Accept gzip content encoding.
		 */
		GZIP
	}

	static final TransportProtocol PROTO_HTTP = new TransportProtocol() {
		private final String[] schemeNames = { "http", "https" }; //$NON-NLS-1$ //$NON-NLS-2$

		private final Set<String> schemeSet = Collections
				.unmodifiableSet(new LinkedHashSet<>(Arrays
						.asList(schemeNames)));

		@Override
		public String getName() {
			return JGitText.get().transportProtoHTTP;
		}

		@Override
		public Set<String> getSchemes() {
			return schemeSet;
		}

		@Override
		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		@Override
		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.USER,
					URIishField.PASS, URIishField.PORT));
		}

		@Override
		public int getDefaultPort() {
			return 80;
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportHttp(local, uri);
		}

		@Override
		public Transport open(URIish uri) throws NotSupportedException {
			return new TransportHttp(uri);
		}
	};

	static final TransportProtocol PROTO_FTP = new TransportProtocol() {
		@Override
		public String getName() {
			return JGitText.get().transportProtoFTP;
		}

		@Override
		public Set<String> getSchemes() {
			return Collections.singleton("ftp"); //$NON-NLS-1$
		}

		@Override
		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		@Override
		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.USER,
					URIishField.PASS, URIishField.PORT));
		}

		@Override
		public int getDefaultPort() {
			return 21;
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportHttp(local, uri);
		}
	};

	/**
	 * The current URI we're talking to. The inherited (final) field
	 * {@link #uri} stores the original URI; {@code currentUri} may be different
	 * after redirects.
	 */
	private URIish currentUri;

	private URL baseUrl;

	private URL objectsUrl;

	private final HttpConfig http;

	private final ProxySelector proxySelector;

	private boolean useSmartHttp = true;

	private HttpAuthMethod authMethod = HttpAuthMethod.Type.NONE.method(null);

	private Map<String, String> headers;

	private boolean sslVerify;

	private boolean sslFailure = false;

	/**
	 * All stored cookies bound to this repo (independent of the baseUrl)
	 */
	private final NetscapeCookieFile cookieFile;

	/**
	 * The cookies to be sent with each request to the given {@link #baseUrl}.
	 * Filtered view on top of {@link #cookieFile} where only cookies which
	 * apply to the current url are left. This set needs to be filtered for
	 * expired entries each time prior to sending them.
	 */
	private final Set<HttpCookie> relevantCookies;

	TransportHttp(Repository local, URIish uri)
			throws NotSupportedException {
		super(local, uri);
		setURI(uri);
		http = new HttpConfig(local.getConfig(), uri);
		proxySelector = ProxySelector.getDefault();
		sslVerify = http.isSslVerify();
		cookieFile = getCookieFileFromConfig(http);
		relevantCookies = filterCookies(cookieFile, baseUrl);
	}

	private URL toURL(URIish urish) throws MalformedURLException {
		String uriString = urish.toString();
		if (!uriString.endsWith("/")) { //$NON-NLS-1$
			uriString += '/';
		}
		return new URL(uriString);
	}

	/**
	 * Set uri a {@link org.eclipse.jgit.transport.URIish} object.
	 *
	 * @param uri
	 *            a {@link org.eclipse.jgit.transport.URIish} object.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 * @since 4.9
	 */
	protected void setURI(URIish uri) throws NotSupportedException {
		try {
			currentUri = uri;
			baseUrl = toURL(uri);
			objectsUrl = new URL(baseUrl, "objects/"); //$NON-NLS-1$
		} catch (MalformedURLException e) {
			throw new NotSupportedException(MessageFormat.format(JGitText.get().invalidURL, uri), e);
		}
	}

	/**
	 * Create a minimal HTTP transport with default configuration values.
	 *
	 * @param uri
	 * @throws NotSupportedException
	 */
	TransportHttp(URIish uri) throws NotSupportedException {
		super(uri);
		setURI(uri);
		http = new HttpConfig(uri);
		proxySelector = ProxySelector.getDefault();
		sslVerify = http.isSslVerify();
		cookieFile = getCookieFileFromConfig(http);
		relevantCookies = filterCookies(cookieFile, baseUrl);
	}

	/**
	 * Toggle whether or not smart HTTP transport should be used.
	 * <p>
	 * This flag exists primarily to support backwards compatibility testing
	 * within a testing framework, there is no need to modify it in most
	 * applications.
	 *
	 * @param on
	 *            if {@code true} (default), smart HTTP is enabled.
	 */
	public void setUseSmartHttp(boolean on) {
		useSmartHttp = on;
	}

	@SuppressWarnings("resource") // Closed by caller
	private FetchConnection getConnection(HttpConnection c, InputStream in,
			String service) throws IOException {
		BaseConnection f;
		if (isSmartHttp(c, service)) {
			readSmartHeaders(in, service);
			f = new SmartHttpFetchConnection(in);
		} else {
			// Assume this server doesn't support smart HTTP fetch
			// and fall back on dumb object walking.
			f = newDumbConnection(in);
		}
		f.setPeerUserAgent(c.getHeaderField(HttpSupport.HDR_SERVER));
		return (FetchConnection) f;
	}

	/** {@inheritDoc} */
	@Override
	public FetchConnection openFetch() throws TransportException,
			NotSupportedException {
		final String service = SVC_UPLOAD_PACK;
		try {
			final HttpConnection c = connect(service);
			try (InputStream in = openInputStream(c)) {
				return getConnection(c, in, service);
			}
		} catch (NotSupportedException | TransportException err) {
			throw err;
		} catch (IOException err) {
			throw new TransportException(uri, JGitText.get().errorReadingInfoRefs, err);
		}
	}

	private WalkFetchConnection newDumbConnection(InputStream in)
			throws IOException, PackProtocolException {
		HttpObjectDB d = new HttpObjectDB(objectsUrl);
		Map<String, Ref> refs;
		try (BufferedReader br = toBufferedReader(in)) {
			refs = d.readAdvertisedImpl(br);
		}

		if (!refs.containsKey(HEAD)) {
			// If HEAD was not published in the info/refs file (it usually
			// is not there) download HEAD by itself as a loose file and do
			// the resolution by hand.
			//
			HttpConnection conn = httpOpen(
					METHOD_GET,
					new URL(baseUrl, HEAD),
					AcceptEncoding.GZIP);
			int status = HttpSupport.response(conn);
			switch (status) {
			case HttpConnection.HTTP_OK: {
				try (BufferedReader br = toBufferedReader(
						openInputStream(conn))) {
					String line = br.readLine();
					if (line != null && line.startsWith(RefDirectory.SYMREF)) {
						String target = line.substring(RefDirectory.SYMREF.length());
						Ref r = refs.get(target);
						if (r == null)
							r = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, target, null);
						r = new SymbolicRef(HEAD, r);
						refs.put(r.getName(), r);
					} else if (line != null && ObjectId.isId(line)) {
						Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK,
								HEAD, ObjectId.fromString(line));
						refs.put(r.getName(), r);
					}
				}
				break;
			}

			case HttpConnection.HTTP_NOT_FOUND:
				break;

			default:
				throw new TransportException(uri, MessageFormat.format(
						JGitText.get().cannotReadHEAD, Integer.valueOf(status),
						conn.getResponseMessage()));
			}
		}

		WalkFetchConnection wfc = new WalkFetchConnection(this, d);
		wfc.available(refs);
		return wfc;
	}

	private BufferedReader toBufferedReader(InputStream in) {
		return new BufferedReader(new InputStreamReader(in, UTF_8));
	}

	/** {@inheritDoc} */
	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		final String service = SVC_RECEIVE_PACK;
		try {
			final HttpConnection c = connect(service);
			try (InputStream in = openInputStream(c)) {
				if (isSmartHttp(c, service)) {
					return smartPush(service, c, in);
				} else if (!useSmartHttp) {
					final String msg = JGitText.get().smartHTTPPushDisabled;
					throw new NotSupportedException(msg);

				} else {
					final String msg = JGitText.get().remoteDoesNotSupportSmartHTTPPush;
					throw new NotSupportedException(msg);
				}
			}
		} catch (NotSupportedException | TransportException err) {
			throw err;
		} catch (IOException err) {
			throw new TransportException(uri, JGitText.get().errorReadingInfoRefs, err);
		}
	}

	private PushConnection smartPush(String service, HttpConnection c,
			InputStream in) throws IOException, TransportException {
		readSmartHeaders(in, service);
		SmartHttpPushConnection p = new SmartHttpPushConnection(in);
		p.setPeerUserAgent(c.getHeaderField(HttpSupport.HDR_SERVER));
		return p;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// No explicit connections are maintained.
	}

	/**
	 * Set additional headers on the HTTP connection
	 *
	 * @param headers
	 *            a map of name:values that are to be set as headers on the HTTP
	 *            connection
	 * @since 3.4
	 */
	public void setAdditionalHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	private NoRemoteRepositoryException createNotFoundException(URIish u,
			URL url, String msg) {
		String text;
		if (msg != null && !msg.isEmpty()) {
			text = MessageFormat.format(JGitText.get().uriNotFoundWithMessage,
					url, msg);
		} else {
			text = MessageFormat.format(JGitText.get().uriNotFound, url);
		}
		return new NoRemoteRepositoryException(u, text);
	}

	private HttpConnection connect(String service)
			throws TransportException, NotSupportedException {
		URL u = getServiceURL(service);
		int authAttempts = 1;
		int redirects = 0;
		Collection<Type> ignoreTypes = null;
		for (;;) {
			try {
				final HttpConnection conn = httpOpen(METHOD_GET, u, AcceptEncoding.GZIP);
				if (useSmartHttp) {
					String exp = "application/x-" + service + "-advertisement"; //$NON-NLS-1$ //$NON-NLS-2$
					conn.setRequestProperty(HDR_ACCEPT, exp + ", */*"); //$NON-NLS-1$
				} else {
					conn.setRequestProperty(HDR_ACCEPT, "*/*"); //$NON-NLS-1$
				}
				final int status = HttpSupport.response(conn);
				processResponseCookies(conn);
				switch (status) {
				case HttpConnection.HTTP_OK:
					// Check if HttpConnection did some authentication in the
					// background (e.g Kerberos/SPNEGO).
					// That may not work for streaming requests and jgit
					// explicit authentication would be required
					if (authMethod.getType() == HttpAuthMethod.Type.NONE
							&& conn.getHeaderField(HDR_WWW_AUTHENTICATE) != null)
						authMethod = HttpAuthMethod.scanResponse(conn, ignoreTypes);
					return conn;

				case HttpConnection.HTTP_NOT_FOUND:
					throw createNotFoundException(uri, u,
							conn.getResponseMessage());

				case HttpConnection.HTTP_UNAUTHORIZED:
					authMethod = HttpAuthMethod.scanResponse(conn, ignoreTypes);
					if (authMethod.getType() == HttpAuthMethod.Type.NONE)
						throw new TransportException(uri, MessageFormat.format(
								JGitText.get().authenticationNotSupported, uri));
					CredentialsProvider credentialsProvider = getCredentialsProvider();
					if (credentialsProvider == null)
						throw new TransportException(uri,
								JGitText.get().noCredentialsProvider);
					if (authAttempts > 1)
						credentialsProvider.reset(currentUri);
					if (3 < authAttempts
							|| !authMethod.authorize(currentUri,
									credentialsProvider)) {
						throw new TransportException(uri,
								JGitText.get().notAuthorized);
					}
					authAttempts++;
					continue;

				case HttpConnection.HTTP_FORBIDDEN:
					throw new TransportException(uri, MessageFormat.format(
							JGitText.get().serviceNotPermitted, baseUrl,
							service));

				case HttpConnection.HTTP_MOVED_PERM:
				case HttpConnection.HTTP_MOVED_TEMP:
				case HttpConnection.HTTP_SEE_OTHER:
				case HttpConnection.HTTP_11_MOVED_TEMP:
					// SEE_OTHER should actually never be sent by a git server,
					// and in general should occur only on POST requests. But it
					// doesn't hurt to accept it here as a redirect.
					if (http.getFollowRedirects() == HttpRedirectMode.FALSE) {
						throw new TransportException(uri,
								MessageFormat.format(
										JGitText.get().redirectsOff,
										Integer.valueOf(status)));
					}
					URIish newUri = redirect(conn.getHeaderField(HDR_LOCATION),
							Constants.INFO_REFS, redirects++);
					setURI(newUri);
					u = getServiceURL(service);
					authAttempts = 1;
					break;
				default:
					String err = status + " " + conn.getResponseMessage(); //$NON-NLS-1$
					throw new TransportException(uri, err);
				}
			} catch (NotSupportedException | TransportException e) {
				throw e;
			} catch (SSLHandshakeException e) {
				handleSslFailure(e);
				continue; // Re-try
			} catch (IOException e) {
				if (authMethod.getType() != HttpAuthMethod.Type.NONE) {
					if (ignoreTypes == null) {
						ignoreTypes = new HashSet<>();
					}

					ignoreTypes.add(authMethod.getType());

					// reset auth method & attempts for next authentication type
					authMethod = HttpAuthMethod.Type.NONE.method(null);
					authAttempts = 1;

					continue;
				}

				throw new TransportException(uri, MessageFormat.format(JGitText.get().cannotOpenService, service), e);
			}
		}
	}

	void processResponseCookies(HttpConnection conn) {
		if (cookieFile != null && http.getSaveCookies()) {
			List<HttpCookie> foundCookies = new LinkedList<>();

			List<String> cookieHeaderValues = conn
					.getHeaderFields(HDR_SET_COOKIE);
			if (!cookieHeaderValues.isEmpty()) {
				foundCookies.addAll(
						extractCookies(HDR_SET_COOKIE, cookieHeaderValues));
			}
			cookieHeaderValues = conn.getHeaderFields(HDR_SET_COOKIE2);
			if (!cookieHeaderValues.isEmpty()) {
				foundCookies.addAll(
						extractCookies(HDR_SET_COOKIE2, cookieHeaderValues));
			}
			if (foundCookies.size() > 0) {
				try {
					// update cookie lists with the newly received cookies!
					Set<HttpCookie> cookies = cookieFile.getCookies(false);
					cookies.addAll(foundCookies);
					cookieFile.write(baseUrl);
					relevantCookies.addAll(foundCookies);
				} catch (IOException | IllegalArgumentException
						| InterruptedException e) {
					LOG.warn(MessageFormat.format(
							JGitText.get().couldNotPersistCookies,
							cookieFile.getPath()), e);
				}
			}
		}
	}

	private List<HttpCookie> extractCookies(String headerKey,
			List<String> headerValues) {
		List<HttpCookie> foundCookies = new LinkedList<>();
		for (String headerValue : headerValues) {
			foundCookies
					.addAll(HttpCookie.parse(headerKey + ':' + headerValue));
		}
		// HttpCookies.parse(...) is only compliant with RFC 2965. Make it RFC
		// 6265 compliant by applying the logic from
		// https://tools.ietf.org/html/rfc6265#section-5.2.3
		for (HttpCookie foundCookie : foundCookies) {
			String domain = foundCookie.getDomain();
			if (domain != null && domain.startsWith(".")) { //$NON-NLS-1$
				foundCookie.setDomain(domain.substring(1));
			}
		}
		return foundCookies;
	}

	private static class CredentialItems {
		CredentialItem.InformationalMessage message;

		/** Trust the server for this git operation */
		CredentialItem.YesNoType now;

		/**
		 * Trust the server for all git operations from this repository; may be
		 * {@code null} if the transport was created via
		 * {@link #TransportHttp(URIish)}.
		 */
		CredentialItem.YesNoType forRepo;

		/** Always trust the server from now on. */
		CredentialItem.YesNoType always;

		public CredentialItem[] items() {
			if (forRepo == null) {
				return new CredentialItem[] { message, now, always };
			} else {
				return new CredentialItem[] { message, now, forRepo, always };
			}
		}
	}

	private void handleSslFailure(Throwable e) throws TransportException {
		if (sslFailure || !trustInsecureSslConnection(e.getCause())) {
			throw new TransportException(uri,
					MessageFormat.format(
							JGitText.get().sslFailureExceptionMessage,
							currentUri.setPass(null)),
					e);
		}
		sslFailure = true;
	}

	private boolean trustInsecureSslConnection(Throwable cause) {
		if (cause instanceof CertificateException
				|| cause instanceof CertPathBuilderException
				|| cause instanceof CertPathValidatorException) {
			// Certificate expired or revoked, PKIX path building not
			// possible, self-signed certificate, host does not match ...
			CredentialsProvider provider = getCredentialsProvider();
			if (provider != null) {
				CredentialItems trust = constructSslTrustItems(cause);
				CredentialItem[] items = trust.items();
				if (provider.supports(items)) {
					boolean answered = provider.get(uri, items);
					if (answered) {
						// Not canceled
						boolean trustNow = trust.now.getValue();
						boolean trustLocal = trust.forRepo != null
								&& trust.forRepo.getValue();
						boolean trustAlways = trust.always.getValue();
						if (trustNow || trustLocal || trustAlways) {
							sslVerify = false;
							if (trustAlways) {
								updateSslVerifyUser(false);
							} else if (trustLocal) {
								updateSslVerify(local.getConfig(), false);
							}
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private CredentialItems constructSslTrustItems(Throwable cause) {
		CredentialItems items = new CredentialItems();
		String info = MessageFormat.format(JGitText.get().sslFailureInfo,
				currentUri.setPass(null));
		String sslMessage = cause.getLocalizedMessage();
		if (sslMessage == null) {
			sslMessage = cause.toString();
		}
		sslMessage = MessageFormat.format(JGitText.get().sslFailureCause,
				sslMessage);
		items.message = new CredentialItem.InformationalMessage(info + '\n'
				+ sslMessage + '\n'
				+ JGitText.get().sslFailureTrustExplanation);
		items.now = new CredentialItem.YesNoType(JGitText.get().sslTrustNow);
		if (local != null) {
			items.forRepo = new CredentialItem.YesNoType(
					MessageFormat.format(JGitText.get().sslTrustForRepo,
					local.getDirectory()));
		}
		items.always = new CredentialItem.YesNoType(
				JGitText.get().sslTrustAlways);
		return items;
	}

	private void updateSslVerify(StoredConfig config, boolean value) {
		// Since git uses the original URI for matching, we must also use the
		// original URI and cannot use the current URI (which might be different
		// after redirects).
		String uriPattern = uri.getScheme() + "://" + uri.getHost(); //$NON-NLS-1$
		int port = uri.getPort();
		if (port > 0) {
			uriPattern += ":" + port; //$NON-NLS-1$
		}
		config.setBoolean(HttpConfig.HTTP, uriPattern,
				HttpConfig.SSL_VERIFY_KEY, value);
		try {
			config.save();
		} catch (IOException e) {
			LOG.error(JGitText.get().sslVerifyCannotSave, e);
		}
	}

	private void updateSslVerifyUser(boolean value) {
		FileBasedConfig userConfig = SystemReader.getInstance()
				.openUserConfig(null, FS.DETECTED);
		try {
			userConfig.load();
			updateSslVerify(userConfig, value);
		} catch (IOException | ConfigInvalidException e) {
			// Log it, but otherwise ignore here.
			LOG.error(MessageFormat.format(JGitText.get().userConfigFileInvalid,
					userConfig.getFile().getAbsolutePath(), e));
		}
	}

	private URIish redirect(String location, String checkFor, int redirects)
			throws TransportException {
		if (location == null || location.isEmpty()) {
			throw new TransportException(uri,
					MessageFormat.format(JGitText.get().redirectLocationMissing,
							baseUrl));
		}
		if (redirects >= http.getMaxRedirects()) {
			throw new TransportException(uri,
					MessageFormat.format(JGitText.get().redirectLimitExceeded,
							Integer.valueOf(http.getMaxRedirects()), baseUrl,
							location));
		}
		try {
			if (!isValidRedirect(baseUrl, location, checkFor)) {
				throw new TransportException(uri,
						MessageFormat.format(JGitText.get().redirectBlocked,
								baseUrl, location));
			}
			location = location.substring(0, location.indexOf(checkFor));
			URIish result = new URIish(location);
			if (LOG.isInfoEnabled()) {
				LOG.info(MessageFormat.format(JGitText.get().redirectHttp,
						uri.setPass(null),
						Integer.valueOf(redirects), baseUrl, result));
			}
			return result;
		} catch (URISyntaxException e) {
			throw new TransportException(uri,
					MessageFormat.format(JGitText.get().invalidRedirectLocation,
							baseUrl, location),
					e);
		}
	}

	private boolean isValidRedirect(URL current, String next, String checkFor) {
		// Protocols must be the same, or current is "http" and next "https". We
		// do not follow redirects from https back to http.
		String oldProtocol = current.getProtocol().toLowerCase(Locale.ROOT);
		int schemeEnd = next.indexOf("://"); //$NON-NLS-1$
		if (schemeEnd < 0) {
			return false;
		}
		String newProtocol = next.substring(0, schemeEnd)
				.toLowerCase(Locale.ROOT);
		if (!oldProtocol.equals(newProtocol)) {
			if (!"https".equals(newProtocol)) { //$NON-NLS-1$
				return false;
			}
		}
		// git allows only rewriting the root, i.e., everything before INFO_REFS
		// or the service name
		if (!next.contains(checkFor)) {
			return false;
		}
		// Basically we should test here that whatever follows INFO_REFS is
		// unchanged. But since we re-construct the query part
		// anyway, it doesn't matter.
		return true;
	}

	private URL getServiceURL(String service)
			throws NotSupportedException {
		try {
			final StringBuilder b = new StringBuilder();
			b.append(baseUrl);

			if (b.charAt(b.length() - 1) != '/') {
				b.append('/');
			}
			b.append(Constants.INFO_REFS);

			if (useSmartHttp) {
				b.append(b.indexOf("?") < 0 ? '?' : '&'); //$NON-NLS-1$
				b.append("service="); //$NON-NLS-1$
				b.append(service);
			}

			return new URL(b.toString());
		} catch (MalformedURLException e) {
			throw new NotSupportedException(MessageFormat.format(JGitText.get().invalidURL, uri), e);
		}
	}

	/**
	 * Open an HTTP connection.
	 *
	 * @param method HTTP request method
	 * @param u url of the HTTP connection
	 * @param acceptEncoding accept-encoding header option
	 * @return the HTTP connection
	 * @throws java.io.IOException
	 * @since 4.6
	 */
	protected HttpConnection httpOpen(String method, URL u,
			AcceptEncoding acceptEncoding) throws IOException {
		if (method == null || u == null || acceptEncoding == null) {
			throw new NullPointerException();
		}

		final Proxy proxy = HttpSupport.proxyFor(proxySelector, u);
		HttpConnection conn = connectionFactory.create(u, proxy);

		if (!sslVerify && "https".equals(u.getProtocol())) { //$NON-NLS-1$
			HttpSupport.disableSslVerify(conn);
		}

		// We must do our own redirect handling to implement git rules and to
		// handle http->https redirects
		conn.setInstanceFollowRedirects(false);

		conn.setRequestMethod(method);
		conn.setUseCaches(false);
		if (acceptEncoding == AcceptEncoding.GZIP) {
			conn.setRequestProperty(HDR_ACCEPT_ENCODING, ENCODING_GZIP);
		}
		conn.setRequestProperty(HDR_PRAGMA, "no-cache"); //$NON-NLS-1$
		if (UserAgent.get() != null) {
			conn.setRequestProperty(HDR_USER_AGENT, UserAgent.get());
		}
		int timeOut = getTimeout();
		if (timeOut != -1) {
			int effTimeOut = timeOut * 1000;
			conn.setConnectTimeout(effTimeOut);
			conn.setReadTimeout(effTimeOut);
		}
		// set cookie header if necessary
		if (relevantCookies.size() > 0) {
			setCookieHeader(conn);
		}

		if (this.headers != null && !this.headers.isEmpty()) {
			for (Map.Entry<String, String> entry : this.headers.entrySet()) {
				conn.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}
		authMethod.configureRequest(conn);
		return conn;
	}

	private void setCookieHeader(HttpConnection conn) {
		StringBuilder cookieHeaderValue = new StringBuilder();
		for (HttpCookie cookie : relevantCookies) {
			if (!cookie.hasExpired()) {
				if (cookieHeaderValue.length() > 0) {
					cookieHeaderValue.append(';');
				}
				cookieHeaderValue.append(cookie.toString());
			}
		}
		if (cookieHeaderValue.length() >= 0) {
			conn.setRequestProperty(HDR_COOKIE, cookieHeaderValue.toString());
		}
	}

	final InputStream openInputStream(HttpConnection conn)
			throws IOException {
		InputStream input = conn.getInputStream();
		if (isGzipContent(conn))
			input = new GZIPInputStream(input);
		return input;
	}

	IOException wrongContentType(String expType, String actType) {
		final String why = MessageFormat.format(JGitText.get().expectedReceivedContentType, expType, actType);
		return new TransportException(uri, why);
	}

	private static NetscapeCookieFile getCookieFileFromConfig(
			HttpConfig config) {
		if (!StringUtils.isEmptyOrNull(config.getCookieFile())) {
			try {
				Path cookieFilePath = Paths.get(config.getCookieFile());
				return NetscapeCookieFileCache.getInstance(config)
						.getEntry(cookieFilePath);
			} catch (InvalidPathException e) {
				LOG.warn(MessageFormat.format(
						JGitText.get().couldNotReadCookieFile,
						config.getCookieFile()), e);
			}
		}
		return null;
	}

	private static Set<HttpCookie> filterCookies(NetscapeCookieFile cookieFile,
			URL url) {
		if (cookieFile != null) {
			return filterCookies(cookieFile.getCookies(true), url);
		}
		return Collections.emptySet();
	}

	/**
	 *
	 * @param allCookies
	 *            a list of cookies.
	 * @param url
	 *            the url for which to filter the list of cookies.
	 * @return only the cookies from {@code allCookies} which are relevant (i.e.
	 *         are not expired, have a matching domain, have a matching path and
	 *         have a matching secure attribute)
	 */
	private static Set<HttpCookie> filterCookies(Set<HttpCookie> allCookies,
			URL url) {
		Set<HttpCookie> filteredCookies = new HashSet<>();
		for (HttpCookie cookie : allCookies) {
			if (cookie.hasExpired()) {
				continue;
			}
			if (!matchesCookieDomain(url.getHost(), cookie.getDomain())) {
				continue;
			}
			if (!matchesCookiePath(url.getPath(), cookie.getPath())) {
				continue;
			}
			if (cookie.getSecure() && !"https".equals(url.getProtocol())) { //$NON-NLS-1$
				continue;
			}
			filteredCookies.add(cookie);
		}
		return filteredCookies;
	}

	/**
	 *
	 * The utility method to check whether a host name is in a cookie's domain
	 * or not. Similar to {@link HttpCookie#domainMatches(String, String)} but
	 * implements domain matching rules according to
	 * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">RFC 6265,
	 * section 5.1.3</a> instead of the rules from
	 * <a href="https://tools.ietf.org/html/rfc2965#section-3.3">RFC 2965,
	 * section 3.3.1</a>.
	 * <p>
	 * The former rules are also used by libcurl internally.
	 * <p>
	 * The rules are as follows
	 *
	 * A string matches another domain string if at least one of the following
	 * conditions holds:
	 * <ul>
	 * <li>The domain string and the string are identical. (Note that both the
	 * domain string and the string will have been canonicalized to lower case
	 * at this point.)</li>
	 * <li>All of the following conditions hold
	 * <ul>
	 * <li>The domain string is a suffix of the string.</li>
	 * <li>The last character of the string that is not included in the domain
	 * string is a %x2E (".") character.</li>
	 * <li>The string is a host name (i.e., not an IP address).</li>
	 * </ul>
	 * </li>
	 * </ul>
	 *
	 * @param host
	 *            the host to compare against the cookieDomain
	 * @param cookieDomain
	 *            the domain to compare against
	 * @return {@code true} if they domain-match; {@code false} if not
	 *
	 * @see <a href= "https://tools.ietf.org/html/rfc6265#section-5.1.3">RFC
	 *      6265, section 5.1.3 (Domain Matching)</a>
	 * @see <a href=
	 *      "https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8206092">JDK-8206092
	 *      : HttpCookie.domainMatches() does not match to sub-sub-domain</a>
	 */
	static boolean matchesCookieDomain(String host, String cookieDomain) {
		cookieDomain = cookieDomain.toLowerCase(Locale.ROOT);
		host = host.toLowerCase(Locale.ROOT);
		if (host.equals(cookieDomain)) {
			return true;
		} else {
			if (!host.endsWith(cookieDomain)) {
				return false;
			}
			return host
					.charAt(host.length() - cookieDomain.length() - 1) == '.';
		}
	}

	/**
	 * The utility method to check whether a path is matching a cookie path
	 * domain or not. The rules are defined by
	 * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">RFC 6265,
	 * section 5.1.4</a>:
	 *
	 * A request-path path-matches a given cookie-path if at least one of the
	 * following conditions holds:
	 * <ul>
	 * <li>The cookie-path and the request-path are identical.</li>
	 * <li>The cookie-path is a prefix of the request-path, and the last
	 * character of the cookie-path is %x2F ("/").</li>
	 * <li>The cookie-path is a prefix of the request-path, and the first
	 * character of the request-path that is not included in the cookie- path is
	 * a %x2F ("/") character.</li>
	 * </ul>
	 * @param path
	 *            the path to check
	 * @param cookiePath
	 *            the cookie's path
	 *
	 * @return {@code true} if they path-match; {@code false} if not
	 */
	static boolean matchesCookiePath(String path, String cookiePath) {
		if (cookiePath.equals(path)) {
			return true;
		}
		if (!cookiePath.endsWith("/")) { //$NON-NLS-1$
			cookiePath += "/"; //$NON-NLS-1$
		}
		return path.startsWith(cookiePath);
	}

	private boolean isSmartHttp(HttpConnection c, String service) {
		final String expType = "application/x-" + service + "-advertisement"; //$NON-NLS-1$ //$NON-NLS-2$
		final String actType = c.getContentType();
		return expType.equals(actType);
	}

	private boolean isGzipContent(HttpConnection c) {
		return ENCODING_GZIP.equals(c.getHeaderField(HDR_CONTENT_ENCODING))
				|| ENCODING_X_GZIP.equals(c.getHeaderField(HDR_CONTENT_ENCODING));
	}

	private void readSmartHeaders(InputStream in, String service)
			throws IOException {
		// A smart reply will have a '#' after the first 4 bytes, but
		// a dumb reply cannot contain a '#' until after byte 41. Do a
		// quick check to make sure its a smart reply before we parse
		// as a pkt-line stream.
		//
		final byte[] magic = new byte[5];
		IO.readFully(in, magic, 0, magic.length);
		if (magic[4] != '#') {
			throw new TransportException(uri, MessageFormat.format(
					JGitText.get().expectedPktLineWithService, RawParseUtils.decode(magic)));
		}

		final PacketLineIn pckIn = new PacketLineIn(new UnionInputStream(
				new ByteArrayInputStream(magic), in));
		final String exp = "# service=" + service; //$NON-NLS-1$
		final String act = pckIn.readString();
		if (!exp.equals(act)) {
			throw new TransportException(uri, MessageFormat.format(
					JGitText.get().expectedGot, exp, act));
		}

		while (!PacketLineIn.isEnd(pckIn.readString())) {
			// for now, ignore the remaining header lines
		}
	}

	class HttpObjectDB extends WalkRemoteObjectDatabase {
		private final URL httpObjectsUrl;

		HttpObjectDB(URL b) {
			httpObjectsUrl = b;
		}

		@Override
		URIish getURI() {
			return new URIish(httpObjectsUrl);
		}

		@Override
		Collection<WalkRemoteObjectDatabase> getAlternates() throws IOException {
			try {
				return readAlternates(INFO_HTTP_ALTERNATES);
			} catch (FileNotFoundException err) {
				// Fall through.
			}

			try {
				return readAlternates(INFO_ALTERNATES);
			} catch (FileNotFoundException err) {
				// Fall through.
			}

			return null;
		}

		@Override
		WalkRemoteObjectDatabase openAlternate(String location)
				throws IOException {
			return new HttpObjectDB(new URL(httpObjectsUrl, location));
		}

		@Override
		BufferedReader openReader(String path) throws IOException {
			// Line oriented readable content is likely to compress well.
			// Request gzip encoding.
			InputStream is = open(path, AcceptEncoding.GZIP).in;
			return new BufferedReader(new InputStreamReader(is, UTF_8));
		}

		@Override
		Collection<String> getPackNames() throws IOException {
			final Collection<String> packs = new ArrayList<>();
			try (BufferedReader br = openReader(INFO_PACKS)) {
				for (;;) {
					final String s = br.readLine();
					if (s == null || s.length() == 0)
						break;
					if (!s.startsWith("P pack-") || !s.endsWith(".pack")) //$NON-NLS-1$ //$NON-NLS-2$
						throw invalidAdvertisement(s);
					packs.add(s.substring(2));
				}
				return packs;
			} catch (FileNotFoundException err) {
				return packs;
			}
		}

		@Override
		FileStream open(String path) throws IOException {
			return open(path, AcceptEncoding.UNSPECIFIED);
		}

		FileStream open(String path, AcceptEncoding acceptEncoding)
				throws IOException {
			final URL base = httpObjectsUrl;
			final URL u = new URL(base, path);
			final HttpConnection c = httpOpen(METHOD_GET, u, acceptEncoding);
			switch (HttpSupport.response(c)) {
			case HttpConnection.HTTP_OK:
				final InputStream in = openInputStream(c);
				// If content is being gzipped and then transferred, the content
				// length in the header is the zipped content length, not the
				// actual content length.
				if (!isGzipContent(c)) {
					final int len = c.getContentLength();
					return new FileStream(in, len);
				}
				return new FileStream(in);
			case HttpConnection.HTTP_NOT_FOUND:
				throw new FileNotFoundException(u.toString());
			default:
				throw new IOException(u.toString() + ": " //$NON-NLS-1$
						+ HttpSupport.response(c) + " " //$NON-NLS-1$
						+ c.getResponseMessage());
			}
		}

		Map<String, Ref> readAdvertisedImpl(final BufferedReader br)
				throws IOException, PackProtocolException {
			final TreeMap<String, Ref> avail = new TreeMap<>();
			for (;;) {
				String line = br.readLine();
				if (line == null)
					break;

				final int tab = line.indexOf('\t');
				if (tab < 0)
					throw invalidAdvertisement(line);

				String name;
				final ObjectId id;

				name = line.substring(tab + 1);
				id = ObjectId.fromString(line.substring(0, tab));
				if (name.endsWith("^{}")) { //$NON-NLS-1$
					name = name.substring(0, name.length() - 3);
					final Ref prior = avail.get(name);
					if (prior == null)
						throw outOfOrderAdvertisement(name);

					if (prior.getPeeledObjectId() != null)
						throw duplicateAdvertisement(name + "^{}"); //$NON-NLS-1$

					avail.put(name, new ObjectIdRef.PeeledTag(
							Ref.Storage.NETWORK, name,
							prior.getObjectId(), id));
				} else {
					Ref prior = avail.put(name, new ObjectIdRef.PeeledNonTag(
							Ref.Storage.NETWORK, name, id));
					if (prior != null)
						throw duplicateAdvertisement(name);
				}
			}
			return avail;
		}

		private PackProtocolException outOfOrderAdvertisement(String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().advertisementOfCameBefore, n, n));
		}

		private PackProtocolException invalidAdvertisement(String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().invalidAdvertisementOf, n));
		}

		private PackProtocolException duplicateAdvertisement(String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().duplicateAdvertisementsOf, n));
		}

		@Override
		void close() {
			// We do not maintain persistent connections.
		}
	}

	class SmartHttpFetchConnection extends BasePackFetchConnection {
		private MultiRequestService svc;

		SmartHttpFetchConnection(InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			readAdvertisedRefs();
		}

		@Override
		protected void doFetch(final ProgressMonitor monitor,
				final Collection<Ref> want, final Set<ObjectId> have,
				final OutputStream outputStream) throws TransportException {
			try {
				svc = new MultiRequestService(SVC_UPLOAD_PACK);
				init(svc.getInputStream(), svc.getOutputStream());
				super.doFetch(monitor, want, have, outputStream);
			} finally {
				svc = null;
			}
		}

		@Override
		protected void onReceivePack() {
			svc.finalRequest = true;
		}
	}

	class SmartHttpPushConnection extends BasePackPushConnection {
		SmartHttpPushConnection(InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			readAdvertisedRefs();
		}

		@Override
		protected void doPush(final ProgressMonitor monitor,
				final Map<String, RemoteRefUpdate> refUpdates,
				OutputStream outputStream) throws TransportException {
			final Service svc = new MultiRequestService(SVC_RECEIVE_PACK);
			init(svc.getInputStream(), svc.getOutputStream());
			super.doPush(monitor, refUpdates, outputStream);
		}
	}

	/** Basic service for sending and receiving HTTP requests. */
	abstract class Service {
		protected final String serviceName;

		protected final String requestType;

		protected final String responseType;

		protected HttpConnection conn;

		protected HttpOutputStream out;

		protected final HttpExecuteStream execute;

		final UnionInputStream in;

		Service(String serviceName) {
			this.serviceName = serviceName;
			this.requestType = "application/x-" + serviceName + "-request"; //$NON-NLS-1$ //$NON-NLS-2$
			this.responseType = "application/x-" + serviceName + "-result"; //$NON-NLS-1$ //$NON-NLS-2$

			this.out = new HttpOutputStream();
			this.execute = new HttpExecuteStream();
			this.in = new UnionInputStream(execute);
		}

		void openStream() throws IOException {
			conn = httpOpen(METHOD_POST, new URL(baseUrl, serviceName),
					AcceptEncoding.GZIP);
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestProperty(HDR_CONTENT_TYPE, requestType);
			conn.setRequestProperty(HDR_ACCEPT, responseType);
		}

		void sendRequest() throws IOException {
			// Try to compress the content, but only if that is smaller.
			TemporaryBuffer buf = new TemporaryBuffer.Heap(
					http.getPostBuffer());
			try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
				out.writeTo(gzip, null);
				if (out.length() < buf.length())
					buf = out;
			} catch (IOException err) {
				// Most likely caused by overflowing the buffer, meaning
				// its larger if it were compressed. Don't compress.
				buf = out;
			}

			HttpAuthMethod authenticator = null;
			Collection<Type> ignoreTypes = EnumSet.noneOf(Type.class);
			// Counts number of repeated authentication attempts using the same
			// authentication scheme
			int authAttempts = 1;
			int redirects = 0;
			for (;;) {
				try {
					// The very first time we will try with the authentication
					// method used on the initial GET request. This is a hint
					// only; it may fail. If so, we'll then re-try with proper
					// 401 handling, going through the available authentication
					// schemes.
					openStream();
					if (buf != out) {
						conn.setRequestProperty(HDR_CONTENT_ENCODING,
								ENCODING_GZIP);
					}
					conn.setFixedLengthStreamingMode((int) buf.length());
					try (OutputStream httpOut = conn.getOutputStream()) {
						buf.writeTo(httpOut, null);
					}

					final int status = HttpSupport.response(conn);
					switch (status) {
					case HttpConnection.HTTP_OK:
						// We're done.
						return;

					case HttpConnection.HTTP_NOT_FOUND:
						throw createNotFoundException(uri, conn.getURL(),
								conn.getResponseMessage());

					case HttpConnection.HTTP_FORBIDDEN:
						throw new TransportException(uri,
								MessageFormat.format(
										JGitText.get().serviceNotPermitted,
										baseUrl, serviceName));

					case HttpConnection.HTTP_MOVED_PERM:
					case HttpConnection.HTTP_MOVED_TEMP:
					case HttpConnection.HTTP_11_MOVED_TEMP:
						// SEE_OTHER after a POST doesn't make sense for a git
						// server, so we don't handle it here and thus we'll
						// report an error in openResponse() later on.
						if (http.getFollowRedirects() != HttpRedirectMode.TRUE) {
							// Let openResponse() issue an error
							return;
						}
						currentUri = redirect(conn.getHeaderField(HDR_LOCATION),
								'/' + serviceName, redirects++);
						try {
							baseUrl = toURL(currentUri);
						} catch (MalformedURLException e) {
							throw new TransportException(uri,
									MessageFormat.format(
											JGitText.get().invalidRedirectLocation,
											baseUrl, currentUri),
									e);
						}
						continue;

					case HttpConnection.HTTP_UNAUTHORIZED:
						HttpAuthMethod nextMethod = HttpAuthMethod
								.scanResponse(conn, ignoreTypes);
						switch (nextMethod.getType()) {
						case NONE:
							throw new TransportException(uri,
									MessageFormat.format(
											JGitText.get().authenticationNotSupported,
											conn.getURL()));
						case NEGOTIATE:
							// RFC 4559 states "When using the SPNEGO [...] with
							// [...] POST, the authentication should be complete
							// [...] before sending the user data." So in theory
							// the initial GET should have been authenticated
							// already. (Unless there was a redirect?)
							//
							// We try this only once:
							ignoreTypes.add(HttpAuthMethod.Type.NEGOTIATE);
							if (authenticator != null) {
								ignoreTypes.add(authenticator.getType());
							}
							authAttempts = 1;
							// We only do the Kerberos part of SPNEGO, which
							// requires only one round.
							break;
						default:
							// DIGEST or BASIC. Let's be sure we ignore
							// NEGOTIATE; if it was available, we have tried it
							// before.
							ignoreTypes.add(HttpAuthMethod.Type.NEGOTIATE);
							if (authenticator == null || authenticator
									.getType() != nextMethod.getType()) {
								if (authenticator != null) {
									ignoreTypes.add(authenticator.getType());
								}
								authAttempts = 1;
							}
							break;
						}
						authMethod = nextMethod;
						authenticator = nextMethod;
						CredentialsProvider credentialsProvider = getCredentialsProvider();
						if (credentialsProvider == null) {
							throw new TransportException(uri,
									JGitText.get().noCredentialsProvider);
						}
						if (authAttempts > 1) {
							credentialsProvider.reset(currentUri);
						}
						if (3 < authAttempts || !authMethod
								.authorize(currentUri, credentialsProvider)) {
							throw new TransportException(uri,
									JGitText.get().notAuthorized);
						}
						authAttempts++;
						continue;

					default:
						// Just return here; openResponse() will report an
						// appropriate error.
						return;
					}
				} catch (SSLHandshakeException e) {
					handleSslFailure(e);
					continue; // Re-try
				} catch (IOException e) {
					if (authenticator == null || authMethod
							.getType() != HttpAuthMethod.Type.NONE) {
						// Can happen for instance if the server advertises
						// Negotiate, but the client isn't configured for
						// Kerberos. The first time (authenticator == null) we
						// must re-try even if the authMethod was NONE: this may
						// occur if the server advertised NTLM on the GET
						// and the HttpConnection managed to successfully
						// authenticate under the hood with NTLM. We might not
						// have picked this up on the GET's 200 response.
						if (authMethod.getType() != HttpAuthMethod.Type.NONE) {
							ignoreTypes.add(authMethod.getType());
						}
						// Start over with the remaining available methods.
						authMethod = HttpAuthMethod.Type.NONE.method(null);
						authenticator = authMethod;
						authAttempts = 1;
						continue;
					}
					throw e;
				}
			}
		}

		void openResponse() throws IOException {
			final int status = HttpSupport.response(conn);
			if (status != HttpConnection.HTTP_OK) {
				throw new TransportException(uri, status + " " //$NON-NLS-1$
						+ conn.getResponseMessage());
			}

			final String contentType = conn.getContentType();
			if (!responseType.equals(contentType)) {
				conn.getInputStream().close();
				throw wrongContentType(responseType, contentType);
			}
		}

		HttpOutputStream getOutputStream() {
			return out;
		}

		InputStream getInputStream() {
			return in;
		}

		abstract void execute() throws IOException;

		class HttpExecuteStream extends InputStream {
			@Override
			public int read() throws IOException {
				execute();
				return -1;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				execute();
				return -1;
			}

			@Override
			public long skip(long n) throws IOException {
				execute();
				return 0;
			}
		}

		class HttpOutputStream extends TemporaryBuffer {
			HttpOutputStream() {
				super(http.getPostBuffer());
			}

			@Override
			protected OutputStream overflow() throws IOException {
				openStream();
				conn.setChunkedStreamingMode(0);
				return conn.getOutputStream();
			}
		}
	}

	/**
	 * State required to speak multiple HTTP requests with the remote.
	 * <p>
	 * A service wrapper provides a normal looking InputStream and OutputStream
	 * pair which are connected via HTTP to the named remote service. Writing to
	 * the OutputStream is buffered until either the buffer overflows, or
	 * reading from the InputStream occurs. If overflow occurs HTTP/1.1 and its
	 * chunked transfer encoding is used to stream the request data to the
	 * remote service. If the entire request fits in the memory buffer, the
	 * older HTTP/1.0 standard and a fixed content length is used instead.
	 * <p>
	 * It is an error to attempt to read without there being outstanding data
	 * ready for transmission on the OutputStream.
	 * <p>
	 * No state is preserved between write-read request pairs. The caller is
	 * responsible for replaying state vector information as part of the request
	 * data written to the OutputStream. Any session HTTP cookies may or may not
	 * be preserved between requests, it is left up to the JVM's implementation
	 * of the HTTP client.
	 */
	class MultiRequestService extends Service {
		boolean finalRequest;

		MultiRequestService(String serviceName) {
			super(serviceName);
		}

		/** Keep opening send-receive pairs to the given URI. */
		@Override
		void execute() throws IOException {
			out.close();

			if (conn == null) {
				if (out.length() == 0) {
					// Request output hasn't started yet, but more data is being
					// requested. If there is no request data buffered and the
					// final request was already sent, do nothing to ensure the
					// caller is shown EOF on the InputStream; otherwise an
					// programming error has occurred within this module.
					if (finalRequest)
						return;
					throw new TransportException(uri,
							JGitText.get().startingReadStageWithoutWrittenRequestDataPendingIsNotSupported);
				}

				sendRequest();
			}

			out.reset();

			openResponse();

			in.add(openInputStream(conn));
			if (!finalRequest)
				in.add(execute);
			conn = null;
		}
	}

	/** Service for maintaining a single long-poll connection. */
	class LongPollService extends Service {
		/**
		 * @param serviceName
		 */
		LongPollService(String serviceName) {
			super(serviceName);
		}

		/** Only open one send-receive request. */
		@Override
		void execute() throws IOException {
			out.close();
			if (conn == null)
				sendRequest();
			openResponse();
			in.add(openInputStream(conn));
		}
	}
}
