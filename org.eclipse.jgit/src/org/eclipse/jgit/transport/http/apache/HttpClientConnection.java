/*
 * Copyright (C) 2013 Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.transport.http.apache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;

/**
 * A {@link HttpConnection} which uses {@link HttpClient}
 *
 * @since 3.3
 */
public class HttpClientConnection implements HttpConnection {
	HttpClient client;

	String urlStr;

	HttpUriRequest req;

	HttpResponse resp = null;

	String method = "GET"; //$NON-NLS-1$

	private TemporaryBufferEntity entity;

	private boolean isUsingProxy = false;

	private Proxy proxy;

	private Integer timeout = null;

	private Integer readTimeout;

	private Boolean followRedirects;

	private X509HostnameVerifier hostnameverifier;

	SSLContext ctx;

	private HttpClient getClient() {
		if (client == null)
			client = new DefaultHttpClient();
		HttpParams params = client.getParams();
		if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
			isUsingProxy = true;
			InetSocketAddress adr = (InetSocketAddress) proxy.address();
			params.setParameter(ConnRoutePNames.DEFAULT_PROXY,
					new HttpHost(adr.getHostName(), adr.getPort()));
		}
		if (timeout != null)
			params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
					timeout.intValue());
		if (readTimeout != null)
			params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,
					readTimeout.intValue());
		if (followRedirects != null)
			params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS,
					followRedirects.booleanValue());
		if (hostnameverifier != null) {
			SSLSocketFactory sf;
			sf = new SSLSocketFactory(getSSLContext(), hostnameverifier);
			Scheme https = new Scheme("https", 443, sf); //$NON-NLS-1$
			client.getConnectionManager().getSchemeRegistry().register(https);
		}

		return client;
	}

	private SSLContext getSSLContext() {
		if (ctx == null) {
			try {
				ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(
						JGitText.get().unexpectedSSLContextException, e);
			}
		}
		return ctx;
	}

	/**
	 * Sets the buffer from which to take the request body
	 *
	 * @param buffer
	 */
	public void setBuffer(TemporaryBuffer buffer) {
		this.entity = new TemporaryBufferEntity(buffer);
	}

	/**
	 * @param urlStr
	 */
	public HttpClientConnection(String urlStr) {
		this(urlStr, null);
	}

	/**
	 * @param urlStr
	 * @param proxy
	 */
	public HttpClientConnection(String urlStr, Proxy proxy) {
		this(urlStr, proxy, null);
	}

	/**
	 * @param urlStr
	 * @param proxy
	 * @param cl
	 */
	public HttpClientConnection(String urlStr, Proxy proxy, HttpClient cl) {
		this.client = cl;
		this.urlStr = urlStr;
		this.proxy = proxy;
	}

	public int getResponseCode() throws IOException {
		execute();
		return resp.getStatusLine().getStatusCode();
	}

	public URL getURL() {
		try {
			return new URL(urlStr);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public String getResponseMessage() throws IOException {
		execute();
		return resp.getStatusLine().getReasonPhrase();
	}

	private void execute() throws IOException, ClientProtocolException {
		if (resp == null)
			if (entity != null) {
				if (req instanceof HttpEntityEnclosingRequest) {
					HttpEntityEnclosingRequest eReq = (HttpEntityEnclosingRequest) req;
					eReq.setEntity(entity);
				}
				resp = getClient().execute(req);
				entity.getBuffer().close();
				entity = null;
			} else
				resp = getClient().execute(req);
	}

	public Map<String, List<String>> getHeaderFields() {
		Map<String, List<String>> ret = new HashMap<String, List<String>>();
		for (Header hdr : resp.getAllHeaders()) {
			List<String> list = new LinkedList<String>();
			for (HeaderElement hdrElem : hdr.getElements())
				list.add(hdrElem.toString());
			ret.put(hdr.getName(), list);
		}
		return ret;
	}

	public void setRequestProperty(String name, String value) {
		req.addHeader(name, value);
	}

	public void setRequestMethod(String method) throws ProtocolException {
		this.method = method;
		if ("GET".equalsIgnoreCase(method)) //$NON-NLS-1$
			req = new HttpGet(urlStr);
		else if ("PUT".equalsIgnoreCase(method)) //$NON-NLS-1$
			req = new HttpPut(urlStr);
		else if ("POST".equalsIgnoreCase(method)) //$NON-NLS-1$
			req = new HttpPost(urlStr);
		else {
			this.method = null;
			throw new UnsupportedOperationException();
		}
	}

	public void setUseCaches(boolean usecaches) {
		// not needed
	}

	public void setConnectTimeout(int timeout) {
		this.timeout = new Integer(timeout);
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = new Integer(readTimeout);
	}

	public String getContentType() {
		HttpEntity entity = resp.getEntity();
		if (entity != null) {
			Header contentType = entity.getContentType();
			if (contentType != null)
				return contentType.getValue();
		}
		return null;
	}

	public InputStream getInputStream() throws IOException {
		return resp.getEntity().getContent();
	}

	// will return only the first field
	public String getHeaderField(String name) {
		Header header = resp.getFirstHeader(name);
		return (header == null) ? null : header.getValue();
	}

	public int getContentLength() {
		return Integer.parseInt(resp.getFirstHeader("content-length")
				.getValue());
	}

	public void setInstanceFollowRedirects(boolean followRedirects) {
		this.followRedirects = new Boolean(followRedirects);
	}

	public void setDoOutput(boolean dooutput) {
		// TODO: check whether we can really ignore this.
	}

	public void setFixedLengthStreamingMode(int contentLength) {
		if (entity != null)
			throw new IllegalArgumentException();
		entity = new TemporaryBufferEntity(new LocalFile());
		entity.setContentLength(contentLength);
	}

	public OutputStream getOutputStream() throws IOException {
		if (entity == null)
			entity = new TemporaryBufferEntity(new LocalFile());
		return entity.getBuffer();
	}

	public void setChunkedStreamingMode(int chunklen) {
		if (entity == null)
			entity = new TemporaryBufferEntity(new LocalFile());
		entity.setChunked(true);
	}

	public String getRequestMethod() {
		return method;
	}

	public void disconnect() {
		throw new UnsupportedOperationException();
	}

	public boolean usingProxy() {
		return isUsingProxy;
	}

	public void connect() throws IOException {
		execute();
	}

	public void setHostnameVerifier(final HostnameVerifier hostnameverifier) {
		this.hostnameverifier = new X509HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return hostnameverifier.verify(hostname, session);
			}

			public void verify(String arg0, String[] arg1, String[] arg2)
					throws SSLException {
				throw new UnsupportedOperationException(); // TODO message
			}

			public void verify(String arg0, X509Certificate arg1)
					throws SSLException {
				throw new UnsupportedOperationException(); // TODO message
			}
			public void verify(String arg0, SSLSocket arg1) throws IOException {
				hostnameverifier.verify(arg0, arg1.getSession());
			}
		};
	}

	public void configure(KeyManager[] km, TrustManager[] tm,
			SecureRandom random) throws KeyManagementException {
		getSSLContext().init(km, tm, random);
	}
}
