/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Transport over the non-Git aware HTTP and FTP protocol.
 * <p>
 * The HTTP transport does not require any specialized Git support on the remote
 * (server side) repository. Object files are retrieved directly through
 * standard HTTP GET requests, making it easy to serve a Git repository through
 * a standard web host provider that does not offer specific support for Git.
 * 
 * @see WalkFetchConnection
 */
public class TransportHttp extends HttpTransport implements WalkTransport,
		PackTransport {
	static boolean canHandle(final URIish uri) {
		if (!uri.isRemote())
			return false;
		final String s = uri.getScheme();
		return "http".equals(s) || "https".equals(s) || "ftp".equals(s);
	}

	private final URL baseUrl;

	private final URL objectsUrl;

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
		proxySelector = ProxySelector.getDefault();
	}

	@Override
	public FetchConnection openFetch() throws TransportException {
		final HttpObjectDB c = new HttpObjectDB(objectsUrl);
		final WalkFetchConnection r = new WalkFetchConnection(this, c);
		r.available(c.readAdvertisedRefs());
		return r;
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		try {
			final String service = "git-receive-pack";
			final HttpURLConnection c = connect(service);
			final InputStream in = new BufferedInputStream(c.getInputStream());
			try {
				String expType = "application/x-" + service + "-advertisement";
				String actType = c.getContentType();
				if (expType.equals(actType)) {
					checkPacketLineStream(in, service);
					return new SmartHttpPushConnection(in);

				} else {
					String msg = "Smart HTTP transport not supported";
					IOException why = wrongContentType(expType, actType);
					throw new NotSupportedException(msg, why);
				}
			} finally {
				in.close();
			}
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
			final String args = "service=" + service;
			u = new URL(baseUrl, Constants.INFO_REFS + "?" + args);
		} catch (MalformedURLException e) {
			throw new NotSupportedException("Invalid URL " + uri, e);
		}

		try {
			final Proxy proxy = HttpSupport.proxyFor(proxySelector, u);
			final HttpURLConnection c;

			c = (HttpURLConnection) u.openConnection(proxy);
			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_OK:
				return c;

			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new NoRemoteRepositoryException(uri, u + " not found");

			case HttpURLConnection.HTTP_FORBIDDEN:
				throw new TransportException(uri, service + " not permitted");

			default:
				throw new TransportException(uri, HttpSupport.response(c) + " "
						+ c.getResponseMessage());
			}
		} catch (NotSupportedException e) {
			throw e;
		} catch (TransportException e) {
			throw e;
		} catch (IOException e) {
			throw new TransportException(uri, "cannot open " + service, e);
		}
	}

	static IOException wrongContentType(String expType, String actType) {
		final String why = "Expected Content-Type " + expType
				+ "; received Content-Type " + actType;
		return new IOException(why);
	}

	private void checkPacketLineStream(final InputStream in,
			final String service) throws IOException {
		final byte[] test = new byte[5];
		in.mark(test.length);
		NB.readFully(in, test, 0, test.length);

		final int lineLen;
		try {
			lineLen = RawParseUtils.parseHexInt16(test, 0);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IOException("Expected pkt-line packet header");
		}
		if (test[4] != '#')
			throw new IOException("Expected pkt-line with '# service='");
		if (lineLen <= 0 || 1000 < lineLen)
			throw new IOException("First pkt-line is too large");

		in.reset();
		String exp = "# service=" + service;
		String act = new PacketLineIn(in).readString().trim();
		if (!exp.equals(act))
			throw new IOException("Expected '" + exp + "', got '" + act + "'");
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
			final Proxy proxy = HttpSupport.proxyFor(proxySelector, u);
			final HttpURLConnection c;

			c = (HttpURLConnection) u.openConnection(proxy);
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

		Map<String, Ref> readAdvertisedRefs() throws TransportException {
			try {
				final BufferedReader br = openReader(INFO_REFS);
				try {
					return readAdvertisedImpl(br);
				} finally {
					br.close();
				}
			} catch (IOException err) {
				try {
					throw new TransportException(new URL(objectsUrl, INFO_REFS)
							+ ": cannot read available refs", err);
				} catch (MalformedURLException mue) {
					throw new TransportException(objectsUrl + INFO_REFS
							+ ": cannot read available refs", err);
				}
			}
		}

		private Map<String, Ref> readAdvertisedImpl(final BufferedReader br)
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

	class SmartHttpPushConnection extends BasePackPushConnection {
		private static final String REQ_TYPE = "application/x-git-receive-pack-input";

		private static final String RSP_TYPE = "application/x-git-receive-pack-status";

		SmartHttpPushConnection(final InputStream advertisement)
				throws TransportException {
			super(TransportHttp.this);

			init(advertisement, DisabledOutputStream.INSTANCE);
			outNeedsEnd = false;
			try {
				readAdvertisedRefs();
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						"remote hung up unexpectedly", err);
			}
		}

		protected void doPush(final ProgressMonitor monitor,
				final Map<String, RemoteRefUpdate> refUpdates)
				throws TransportException {
			final InputStream httpIn;
			final OutputStream httpOut;
			try {
				final URL url = new URL(baseUrl, "git-receive-pack");
				final Proxy proxy = HttpSupport.proxyFor(proxySelector, url);
				final HttpURLConnection conn;

				conn = (HttpURLConnection) url.openConnection(proxy);
				conn.setRequestMethod(HttpSupport.METHOD_POST);
				conn.setInstanceFollowRedirects(false);
				conn.setDoOutput(true);
				conn.setChunkedStreamingMode(0);
				conn.setRequestProperty(HttpSupport.HDR_CONTENT_TYPE, REQ_TYPE);

				httpOut = new BufferedOutputStream(conn.getOutputStream());
				httpIn = new HttpInputStream(conn, httpOut, RSP_TYPE);
			} catch (IOException e) {
				throw new TransportException(uri, "Cannot begin HTTP push", e);
			}

			init(new BufferedInputStream(httpIn), httpOut);
			super.doPush(monitor, refUpdates);
		}
	}

	static class HttpInputStream extends InputStream {
		private final HttpURLConnection conn;

		private final OutputStream httpOut;

		private final String expectedContentType;

		private InputStream httpIn;

		private HttpInputStream(HttpURLConnection conn, OutputStream httpOut,
				String contentType) {
			this.conn = conn;
			this.httpOut = httpOut;
			this.expectedContentType = contentType;
		}

		private InputStream self() throws IOException {
			if (httpIn == null)
				endOutput();
			return httpIn;
		}

		private void endOutput() throws IOException {
			httpOut.close();

			final int status = HttpSupport.response(conn);
			if (status != HttpURLConnection.HTTP_OK)
				throw new IOException(status + " " + conn.getResponseMessage());

			httpIn = conn.getInputStream();
			final String actualContentType = conn.getContentType();
			if (!expectedContentType.equals(actualContentType))
				throw wrongContentType(expectedContentType, actualContentType);
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
			if (httpIn != null) {
				httpIn.close();
				httpIn = null;
			}
		}
	}
}
