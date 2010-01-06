/*
 * Copyright (C) 2009-2010, Google Inc.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Config.SectionParser;
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
	private static final String SVC_UPLOAD_PACK = "git-upload-pack";

	private static final String SVC_RECEIVE_PACK = "git-receive-pack";

	static boolean canHandle(final URIish uri) {
		if (!uri.isRemote())
			return false;
		final String s = uri.getScheme();
		return "http".equals(s) || "https".equals(s) || "ftp".equals(s);
	}

	private static final Config.SectionParser<HttpConfig> HTTP_KEY = new SectionParser<HttpConfig>() {
		public HttpConfig parse(final Config cfg) {
			return new HttpConfig(cfg);
		}
	};

	private static class HttpConfig {
		final int postBuffer;

		HttpConfig(final Config rc) {
			postBuffer = rc.getInt("http", "postbuffer", 1 * 1024 * 1024);
		}
	}

	private final URL baseUrl;

	private final URL objectsUrl;

	private final HttpConfig http;

	private final ProxySelector proxySelector;

	TransportHttp(final Repository local, final URIish uri)
			throws NotSupportedException {
		super(local, uri);
		try {
			String uriString = uri.toString();
			if (!uriString.endsWith("/"))
				uriString += "/";
			baseUrl = new URL(uriString);
			objectsUrl = new URL(baseUrl, "objects/");
		} catch (MalformedURLException e) {
			throw new NotSupportedException("Invalid URL " + uri, e);
		}
		http = local.getConfig().get(HTTP_KEY);
		proxySelector = ProxySelector.getDefault();
	}

	@Override
	public FetchConnection openFetch() throws TransportException,
			NotSupportedException {
		final String service = SVC_UPLOAD_PACK;
		try {
			final HttpURLConnection c = connect(service);
			final InputStream in = c.getInputStream();
			try {
				if (isSmartHttp(c, service)) {
					readSmartHeaders(in, service);
					return new SmartHttpFetchConnection(in);

				} else {
					// Assume this server doesn't support smart HTTP fetch
					// and fall back on dumb object walking.
					//
					HttpObjectDB d = new HttpObjectDB(objectsUrl);
					WalkFetchConnection wfc = new WalkFetchConnection(this, d);
					BufferedReader br = new BufferedReader(
							new InputStreamReader(in, Constants.CHARSET));
					try {
						wfc.available(d.readAdvertisedImpl(br));
					} finally {
						br.close();
					}
					return wfc;
				}
			} finally {
				in.close();
			}
		} catch (NotSupportedException err) {
			throw err;
		} catch (TransportException err) {
			throw err;
		} catch (IOException err) {
			throw new TransportException(uri, "error reading info/refs", err);
		}
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		final String service = SVC_RECEIVE_PACK;
		try {
			final HttpURLConnection c = connect(service);
			final InputStream in = c.getInputStream();
			try {
				if (isSmartHttp(c, service)) {
					readSmartHeaders(in, service);
					return new SmartHttpPushConnection(in);

				} else {
					final String msg = "remote does not support smart HTTP push";
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
			throw new TransportException(uri, "error reading info/refs", err);
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

			b.append(b.indexOf("?") < 0 ? '?' : '&');
			b.append("service=");
			b.append(service);

			u = new URL(b.toString());
		} catch (MalformedURLException e) {
			throw new NotSupportedException("Invalid URL " + uri, e);
		}

		try {
			final HttpURLConnection conn = httpOpen(u);
			final int status = HttpSupport.response(conn);
			switch (status) {
			case HttpURLConnection.HTTP_OK:
				return conn;

			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new NoRemoteRepositoryException(uri, u + " not found");

			case HttpURLConnection.HTTP_FORBIDDEN:
				throw new TransportException(uri, service + " not permitted");

			default:
				String err = status + " " + conn.getResponseMessage();
				throw new TransportException(uri, err);
			}
		} catch (NotSupportedException e) {
			throw e;
		} catch (TransportException e) {
			throw e;
		} catch (IOException e) {
			throw new TransportException(uri, "cannot open " + service, e);
		}
	}

	final HttpURLConnection httpOpen(final URL u) throws IOException {
		final Proxy proxy = HttpSupport.proxyFor(proxySelector, u);
		return (HttpURLConnection) u.openConnection(proxy);
	}

	IOException wrongContentType(String expType, String actType) {
		final String why = "expected Content-Type " + expType
				+ "; received Content-Type " + actType;
		return new TransportException(uri, why);
	}

	private boolean isSmartHttp(final HttpURLConnection c, final String service) {
		final String expType = "application/x-" + service + "-advertisement";
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
			throw new TransportException(uri, "expected pkt-line with"
					+ " '# service=', got '" + RawParseUtils.decode(magic)
					+ "'");
		}

		final PacketLineIn pckIn = new PacketLineIn(new UnionInputStream(
				new ByteArrayInputStream(magic), in));
		final String exp = "# service=" + service;
		final String act = pckIn.readString();
		if (!exp.equals(act)) {
			throw new TransportException(uri, "expected '" + exp + "', got '"
					+ act + "'");
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
						if (!s.startsWith("P pack-") || !s.endsWith(".pack"))
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
				final InputStream in = c.getInputStream();
				final int len = c.getContentLength();
				return new FileStream(in, len);
			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new FileNotFoundException(u.toString());
			default:
				throw new IOException(u.toString() + ": "
						+ HttpSupport.response(c) + " "
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
				if (name.endsWith("^{}")) {
					name = name.substring(0, name.length() - 3);
					final Ref prior = avail.get(name);
					if (prior == null)
						throw outOfOrderAdvertisement(name);

					if (prior.getPeeledObjectId() != null)
						throw duplicateAdvertisement(name + "^{}");

					avail.put(name, new Ref(Ref.Storage.NETWORK, name, prior
							.getObjectId(), id, true));
				} else {
					final Ref prior = avail.put(name, new Ref(
							Ref.Storage.NETWORK, name, id));
					if (prior != null)
						throw duplicateAdvertisement(name);
				}
			}
			return avail;
		}

		private PackProtocolException outOfOrderAdvertisement(final String n) {
			return new PackProtocolException("advertisement of " + n
					+ "^{} came before " + n);
		}

		private PackProtocolException invalidAdvertisement(final String n) {
			return new PackProtocolException("invalid advertisement of " + n);
		}

		private PackProtocolException duplicateAdvertisement(final String n) {
			return new PackProtocolException("duplicate advertisements of " + n);
		}

		@Override
		void close() {
			// We do not maintain persistent connections.
		}
	}

	class SmartHttpFetchConnection extends BasePackFetchConnection {
		SmartHttpFetchConnection(final InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			try {
				readAdvertisedRefs();
			} catch (IOException err) {
				close();
				throw new TransportException(uri, "remote hung up", err);
			}
		}

		@Override
		protected void doFetch(final ProgressMonitor monitor,
				final Collection<Ref> want, final Set<ObjectId> have)
				throws TransportException {
			final Service svc = new Service(SVC_UPLOAD_PACK);
			init(svc.in, svc.out);
			super.doFetch(monitor, want, have);
		}
	}

	class SmartHttpPushConnection extends BasePackPushConnection {
		SmartHttpPushConnection(final InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);
			statelessRPC = true;

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			try {
				readAdvertisedRefs();
			} catch (IOException err) {
				close();
				throw new TransportException(uri, "remote hung up", err);
			}
		}

		protected void doPush(final ProgressMonitor monitor,
				final Map<String, RemoteRefUpdate> refUpdates)
				throws TransportException {
			final Service svc = new Service(SVC_RECEIVE_PACK);
			init(svc.in, svc.out);
			super.doPush(monitor, refUpdates);
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
	class Service {
		private final String serviceName;

		private final String requestType;

		private final String responseType;

		private final UnionInputStream httpIn;

		final HttpInputStream in;

		final HttpOutputStream out;

		HttpURLConnection conn;

		Service(final String serviceName) {
			this.serviceName = serviceName;
			this.requestType = "application/x-" + serviceName + "-request";
			this.responseType = "application/x-" + serviceName + "-result";

			this.httpIn = new UnionInputStream();
			this.in = new HttpInputStream(httpIn);
			this.out = new HttpOutputStream();
		}

		void openStream() throws IOException {
			conn = httpOpen(new URL(baseUrl, serviceName));
			conn.setRequestMethod(HttpSupport.METHOD_POST);
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestProperty(HttpSupport.HDR_CONTENT_TYPE, requestType);
		}

		void execute() throws IOException {
			out.close();

			if (conn == null) {
				// Output hasn't started yet, because everything fit into
				// our request buffer. Send with a Content-Length header.
				//
				if (out.length() == 0) {
					throw new TransportException(uri, "Starting read stage"
							+ " without written request data pending"
							+ " is not supported");
				}
				openStream();
				conn.setFixedLengthStreamingMode((int) out.length());
				final OutputStream httpOut = conn.getOutputStream();
				try {
					out.writeTo(httpOut, null);
				} finally {
					httpOut.close();
				}
			}

			out.reset();

			final int status = HttpSupport.response(conn);
			if (status != HttpURLConnection.HTTP_OK) {
				throw new TransportException(uri, status + " "
						+ conn.getResponseMessage());
			}

			httpIn.add(conn.getInputStream());

			final String contentType = conn.getContentType();
			if (!responseType.equals(contentType)) {
				httpIn.close();
				throw wrongContentType(responseType, contentType);
			}

			conn = null;
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

		class HttpInputStream extends InputStream {
			private final UnionInputStream src;

			HttpInputStream(UnionInputStream httpIn) {
				this.src = httpIn;
			}

			private InputStream self() throws IOException {
				if (src.isEmpty()) {
					// If we have no InputStreams available it means we must
					// have written data previously to the service, but have
					// not yet finished the HTTP request in order to get the
					// response from the service. Ensure we get it now.
					//
					execute();
				}
				return src;
			}

			public int available() throws IOException {
				return self().available();
			}

			public int read() throws IOException {
				return self().read();
			}

			public int read(byte[] b, int off, int len) throws IOException {
				return self().read(b, off, len);
			}

			public long skip(long n) throws IOException {
				return self().skip(n);
			}

			public void close() throws IOException {
				src.close();
			}
		}
	}
}
