/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;
import static org.eclipse.jgit.util.HttpSupport.HDR_PRAGMA;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;
import static org.eclipse.jgit.util.HttpSupport.METHOD_GET;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.storage.file.RefDirectory;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.util.io.UnionInputStream;

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
	private static final String SVC_UPLOAD_PACK = "git-upload-pack"; //$NON-NLS-1$

	private static final String SVC_RECEIVE_PACK = "git-receive-pack"; //$NON-NLS-1$

	private static final String userAgent = computeUserAgent();

	static final TransportProtocol PROTO_HTTP = new TransportProtocol() {
		private final String[] schemeNames = { "http", "https" }; //$NON-NLS-1$ //$NON-NLS-2$

		private final Set<String> schemeSet = Collections
				.unmodifiableSet(new LinkedHashSet<String>(Arrays
						.asList(schemeNames)));

		public String getName() {
			return JGitText.get().transportProtoHTTP;
		}

		public Set<String> getSchemes() {
			return schemeSet;
		}

		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.USER,
					URIishField.PASS, URIishField.PORT));
		}

		public int getDefaultPort() {
			return 80;
		}

		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportHttp(local, uri);
		}

		public Transport open(URIish uri) throws NotSupportedException {
			return new TransportHttp(uri);
		}
	};

	static final TransportProtocol PROTO_FTP = new TransportProtocol() {
		public String getName() {
			return JGitText.get().transportProtoFTP;
		}

		public Set<String> getSchemes() {
			return Collections.singleton("ftp"); //$NON-NLS-1$
		}

		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.USER,
					URIishField.PASS, URIishField.PORT));
		}

		public int getDefaultPort() {
			return 21;
		}

		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportHttp(local, uri);
		}
	};

	private static String computeUserAgent() {
		String version;
		final Package pkg = TransportHttp.class.getPackage();
		if (pkg != null && pkg.getImplementationVersion() != null) {
			version = pkg.getImplementationVersion();
		} else {
			version = "unknown"; //$NON-NLS-1$
		}
		return "JGit/" + version; //$NON-NLS-1$
	}

	private static final Config.SectionParser<HttpConfig> HTTP_KEY = new SectionParser<HttpConfig>() {
		public HttpConfig parse(final Config cfg) {
			return new HttpConfig(cfg);
		}
	};

	private static class HttpConfig {
		final int postBuffer;

		final boolean sslVerify;

		HttpConfig(final Config rc) {
			postBuffer = rc.getInt("http", "postbuffer", 1 * 1024 * 1024); //$NON-NLS-1$  //$NON-NLS-2$
			sslVerify = rc.getBoolean("http", "sslVerify", true); //$NON-NLS-1$ //$NON-NLS-2$
		}

		private HttpConfig() {
			this(new Config());
		}
	}

	private final URL baseUrl;

	private final URL objectsUrl;

	private final HttpConfig http;

	private final ProxySelector proxySelector;

	private boolean useSmartHttp = true;

	private HttpAuthMethod authMethod = HttpAuthMethod.NONE;

	TransportHttp(final Repository local, final URIish uri)
			throws NotSupportedException {
		super(local, uri);
		try {
			String uriString = uri.toString();
			if (!uriString.endsWith("/")) //$NON-NLS-1$
				uriString += "/"; //$NON-NLS-1$
			baseUrl = new URL(uriString);
			objectsUrl = new URL(baseUrl, "objects/"); //$NON-NLS-1$
		} catch (MalformedURLException e) {
			throw new NotSupportedException(MessageFormat.format(JGitText.get().invalidURL, uri), e);
		}
		http = local.getConfig().get(HTTP_KEY);
		proxySelector = ProxySelector.getDefault();
	}

	/**
	 * Create a minimal HTTP transport with default configuration values.
	 *
	 * @param uri
	 * @throws NotSupportedException
	 */
	TransportHttp(final URIish uri) throws NotSupportedException {
		super(uri);
		try {
			String uriString = uri.toString();
			if (!uriString.endsWith("/")) //$NON-NLS-1$
				uriString += "/"; //$NON-NLS-1$
			baseUrl = new URL(uriString);
			objectsUrl = new URL(baseUrl, "objects/"); //$NON-NLS-1$
		} catch (MalformedURLException e) {
			throw new NotSupportedException(MessageFormat.format(JGitText.get().invalidURL, uri), e);
		}
		http = new HttpConfig();
		proxySelector = ProxySelector.getDefault();
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
	public void setUseSmartHttp(final boolean on) {
		useSmartHttp = on;
	}

	@Override
	public FetchConnection openFetch() throws TransportException,
			NotSupportedException {
		final String service = SVC_UPLOAD_PACK;
		try {
			final HttpURLConnection c = connect(service);
			final InputStream in = openInputStream(c);
			try {
				if (isSmartHttp(c, service)) {
					readSmartHeaders(in, service);
					return new SmartHttpFetchConnection(in);

				} else {
					// Assume this server doesn't support smart HTTP fetch
					// and fall back on dumb object walking.
					//
					return newDumbConnection(in);
				}
			} finally {
				in.close();
			}
		} catch (NotSupportedException err) {
			throw err;
		} catch (TransportException err) {
			throw err;
		} catch (IOException err) {
			throw new TransportException(uri, JGitText.get().errorReadingInfoRefs, err);
		}
	}

	private FetchConnection newDumbConnection(InputStream in)
			throws IOException, PackProtocolException {
		HttpObjectDB d = new HttpObjectDB(objectsUrl);
		BufferedReader br = toBufferedReader(in);
		Map<String, Ref> refs;
		try {
			refs = d.readAdvertisedImpl(br);
		} finally {
			br.close();
		}

		if (!refs.containsKey(Constants.HEAD)) {
			// If HEAD was not published in the info/refs file (it usually
			// is not there) download HEAD by itself as a loose file and do
			// the resolution by hand.
			//
			HttpURLConnection conn = httpOpen(new URL(baseUrl, Constants.HEAD));
			int status = HttpSupport.response(conn);
			switch (status) {
			case HttpURLConnection.HTTP_OK: {
				br = toBufferedReader(openInputStream(conn));
				try {
					String line = br.readLine();
					if (line != null && line.startsWith(RefDirectory.SYMREF)) {
						String target = line.substring(RefDirectory.SYMREF.length());
						Ref r = refs.get(target);
						if (r == null)
							r = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, target, null);
						r = new SymbolicRef(Constants.HEAD, r);
						refs.put(r.getName(), r);
					} else if (line != null && ObjectId.isId(line)) {
						Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK,
								Constants.HEAD, ObjectId.fromString(line));
						refs.put(r.getName(), r);
					}
				} finally {
					br.close();
				}
				break;
			}

			case HttpURLConnection.HTTP_NOT_FOUND:
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
		return new BufferedReader(new InputStreamReader(in, Constants.CHARSET));
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		final String service = SVC_RECEIVE_PACK;
		try {
			final HttpURLConnection c = connect(service);
			final InputStream in = openInputStream(c);
			try {
				if (isSmartHttp(c, service)) {
					readSmartHeaders(in, service);
					return new SmartHttpPushConnection(in);

				} else if (!useSmartHttp) {
					final String msg = JGitText.get().smartHTTPPushDisabled;
					throw new NotSupportedException(msg);

				} else {
					final String msg = JGitText.get().remoteDoesNotSupportSmartHTTPPush;
					throw new NotSupportedException(msg);
				}
			} finally {
				in.close();
			}
		} catch (NotSupportedException err) {
			throw err;
		} catch (TransportException err) {
			throw err;
		} catch (IOException err) {
			throw new TransportException(uri, JGitText.get().errorReadingInfoRefs, err);
		}
	}

	@Override
	public void close() {
		// No explicit connections are maintained.
	}

	private HttpURLConnection connect(final String service)
			throws TransportException, NotSupportedException {
		final URL u;
		try {
			final StringBuilder b = new StringBuilder();
			b.append(baseUrl);

			if (b.charAt(b.length() - 1) != '/')
				b.append('/');
			b.append(Constants.INFO_REFS);

			if (useSmartHttp) {
				b.append(b.indexOf("?") < 0 ? '?' : '&'); //$NON-NLS-1$
				b.append("service="); //$NON-NLS-1$
				b.append(service);
			}

			u = new URL(b.toString());
		} catch (MalformedURLException e) {
			throw new NotSupportedException(MessageFormat.format(JGitText.get().invalidURL, uri), e);
		}

		try {
			int authAttempts = 1;
			for (;;) {
				final HttpURLConnection conn = httpOpen(u);
				if (useSmartHttp) {
					String exp = "application/x-" + service + "-advertisement"; //$NON-NLS-1$ //$NON-NLS-2$
					conn.setRequestProperty(HDR_ACCEPT, exp + ", */*"); //$NON-NLS-1$
				} else {
					conn.setRequestProperty(HDR_ACCEPT, "*/*"); //$NON-NLS-1$
				}
				final int status = HttpSupport.response(conn);
				switch (status) {
				case HttpURLConnection.HTTP_OK:
					return conn;

				case HttpURLConnection.HTTP_NOT_FOUND:
					throw new NoRemoteRepositoryException(uri,
							MessageFormat.format(JGitText.get().uriNotFound, u));

				case HttpURLConnection.HTTP_UNAUTHORIZED:
					authMethod = HttpAuthMethod.scanResponse(conn);
					if (authMethod == HttpAuthMethod.NONE)
						throw new TransportException(uri, MessageFormat.format(
								JGitText.get().authenticationNotSupported, uri));
					if (1 < authAttempts
							|| !authMethod.authorize(uri,
									getCredentialsProvider())) {
						throw new TransportException(uri,
								JGitText.get().notAuthorized);
					}
					authAttempts++;
					continue;

				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new TransportException(uri, MessageFormat.format(
							JGitText.get().serviceNotPermitted, service));

				default:
					String err = status + " " + conn.getResponseMessage(); //$NON-NLS-1$
					throw new TransportException(uri, err);
				}
			}
		} catch (NotSupportedException e) {
			throw e;
		} catch (TransportException e) {
			throw e;
		} catch (IOException e) {
			throw new TransportException(uri, MessageFormat.format(JGitText.get().cannotOpenService, service), e);
		}
	}

	final HttpURLConnection httpOpen(URL u) throws IOException {
		return httpOpen(METHOD_GET, u);
	}

	/**
	 * Open an http connection.
	 *
	 * @param method
	 * @param u
	 * @return the connection
	 * @throws IOException
	 */
	protected HttpURLConnection httpOpen(String method, URL u)
			throws IOException {
		final Proxy proxy = HttpSupport.proxyFor(proxySelector, u);
		HttpURLConnection conn = (HttpURLConnection) u.openConnection(proxy);

		if (!http.sslVerify && "https".equals(u.getProtocol())) { //$NON-NLS-1$
			disableSslVerify(conn);
		}

		conn.setRequestMethod(method);
		conn.setUseCaches(false);
		conn.setRequestProperty(HDR_ACCEPT_ENCODING, ENCODING_GZIP);
		conn.setRequestProperty(HDR_PRAGMA, "no-cache"); //$NON-NLS-1$
		conn.setRequestProperty(HDR_USER_AGENT, userAgent);
		int timeOut = getTimeout();
		if (timeOut != -1) {
			int effTimeOut = timeOut * 1000;
			conn.setConnectTimeout(effTimeOut);
			conn.setReadTimeout(effTimeOut);
		}
		authMethod.configureRequest(conn);
		return conn;
	}

	private void disableSslVerify(URLConnection conn)
			throws IOException {
		final TrustManager[] trustAllCerts = new TrustManager[] { new DummyX509TrustManager() };
		try {
			SSLContext ctx = SSLContext.getInstance("SSL"); //$NON-NLS-1$
			ctx.init(null, trustAllCerts, null);
			final HttpsURLConnection sslConn = (HttpsURLConnection) conn;
			sslConn.setSSLSocketFactory(ctx.getSocketFactory());
		} catch (KeyManagementException e) {
			throw new IOException(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e.getMessage());
		}
	}

	final InputStream openInputStream(HttpURLConnection conn)
			throws IOException {
		InputStream input = conn.getInputStream();
		if (ENCODING_GZIP.equals(conn.getHeaderField(HDR_CONTENT_ENCODING)))
			input = new GZIPInputStream(input);
		return input;
	}

	IOException wrongContentType(String expType, String actType) {
		final String why = MessageFormat.format(JGitText.get().expectedReceivedContentType, expType, actType);
		return new TransportException(uri, why);
	}

	private boolean isSmartHttp(final HttpURLConnection c, final String service) {
		final String expType = "application/x-" + service + "-advertisement"; //$NON-NLS-1$ //$NON-NLS-2$
		final String actType = c.getContentType();
		return expType.equals(actType);
	}

	private void readSmartHeaders(final InputStream in, final String service)
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

		while (pckIn.readString() != PacketLineIn.END) {
			// for now, ignore the remaining header lines
		}
	}

	class HttpObjectDB extends WalkRemoteObjectDatabase {
		private final URL objectsUrl;

		HttpObjectDB(final URL b) {
			objectsUrl = b;
		}

		@Override
		URIish getURI() {
			return new URIish(objectsUrl);
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
		WalkRemoteObjectDatabase openAlternate(final String location)
				throws IOException {
			return new HttpObjectDB(new URL(objectsUrl, location));
		}

		@Override
		Collection<String> getPackNames() throws IOException {
			final Collection<String> packs = new ArrayList<String>();
			try {
				final BufferedReader br = openReader(INFO_PACKS);
				try {
					for (;;) {
						final String s = br.readLine();
						if (s == null || s.length() == 0)
							break;
						if (!s.startsWith("P pack-") || !s.endsWith(".pack")) //$NON-NLS-1$ //$NON-NLS-2$
							throw invalidAdvertisement(s);
						packs.add(s.substring(2));
					}
					return packs;
				} finally {
					br.close();
				}
			} catch (FileNotFoundException err) {
				return packs;
			}
		}

		@Override
		FileStream open(final String path) throws IOException {
			final URL base = objectsUrl;
			final URL u = new URL(base, path);
			final HttpURLConnection c = httpOpen(u);
			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_OK:
				final InputStream in = openInputStream(c);
				final int len = c.getContentLength();
				return new FileStream(in, len);
			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new FileNotFoundException(u.toString());
			default:
				throw new IOException(u.toString() + ": " //$NON-NLS-1$
						+ HttpSupport.response(c) + " " //$NON-NLS-1$
						+ c.getResponseMessage());
			}
		}

		Map<String, Ref> readAdvertisedImpl(final BufferedReader br)
				throws IOException, PackProtocolException {
			final TreeMap<String, Ref> avail = new TreeMap<String, Ref>();
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

		private PackProtocolException outOfOrderAdvertisement(final String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().advertisementOfCameBefore, n, n));
		}

		private PackProtocolException invalidAdvertisement(final String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().invalidAdvertisementOf, n));
		}

		private PackProtocolException duplicateAdvertisement(final String n) {
			return new PackProtocolException(MessageFormat.format(JGitText.get().duplicateAdvertisementsOf, n));
		}

		@Override
		void close() {
			// We do not maintain persistent connections.
		}
	}

	class SmartHttpFetchConnection extends BasePackFetchConnection {
		private MultiRequestService svc;

		SmartHttpFetchConnection(final InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			readAdvertisedRefs();
		}

		@Override
		protected void doFetch(final ProgressMonitor monitor,
				final Collection<Ref> want, final Set<ObjectId> have)
				throws TransportException {
			try {
				svc = new MultiRequestService(SVC_UPLOAD_PACK);
				init(svc.getInputStream(), svc.getOutputStream());
				super.doFetch(monitor, want, have);
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
		SmartHttpPushConnection(final InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			readAdvertisedRefs();
		}

		protected void doPush(final ProgressMonitor monitor,
				final Map<String, RemoteRefUpdate> refUpdates)
				throws TransportException {
			final Service svc = new MultiRequestService(SVC_RECEIVE_PACK);
			init(svc.getInputStream(), svc.getOutputStream());
			super.doPush(monitor, refUpdates);
		}
	}

	/** Basic service for sending and receiving HTTP requests. */
	abstract class Service {
		protected final String serviceName;

		protected final String requestType;

		protected final String responseType;

		protected HttpURLConnection conn;

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
			conn = httpOpen(METHOD_POST, new URL(baseUrl, serviceName));
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestProperty(HDR_CONTENT_TYPE, requestType);
			conn.setRequestProperty(HDR_ACCEPT, responseType);
		}

		void sendRequest() throws IOException {
			// Try to compress the content, but only if that is smaller.
			TemporaryBuffer buf = new TemporaryBuffer.Heap(http.postBuffer);
			try {
				GZIPOutputStream gzip = new GZIPOutputStream(buf);
				out.writeTo(gzip, null);
				gzip.close();
				if (out.length() < buf.length())
					buf = out;
			} catch (IOException err) {
				// Most likely caused by overflowing the buffer, meaning
				// its larger if it were compressed. Don't compress.
				buf = out;
			}

			openStream();
			if (buf != out)
				conn.setRequestProperty(HDR_CONTENT_ENCODING, ENCODING_GZIP);
			conn.setFixedLengthStreamingMode((int) buf.length());
			final OutputStream httpOut = conn.getOutputStream();
			try {
				buf.writeTo(httpOut, null);
			} finally {
				httpOut.close();
			}
		}

		void openResponse() throws IOException {
			final int status = HttpSupport.response(conn);
			if (status != HttpURLConnection.HTTP_OK) {
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
			public int read() throws IOException {
				execute();
				return -1;
			}

			public int read(byte[] b, int off, int len) throws IOException {
				execute();
				return -1;
			}

			public long skip(long n) throws IOException {
				execute();
				return 0;
			}
		}

		class HttpOutputStream extends TemporaryBuffer {
			HttpOutputStream() {
				super(http.postBuffer);
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

		MultiRequestService(final String serviceName) {
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

	private static class DummyX509TrustManager implements X509TrustManager {
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		public void checkClientTrusted(X509Certificate[] certs, String authType) {
			// no check
		}

		public void checkServerTrusted(X509Certificate[] certs, String authType) {
			// no check
		}
	}
}
