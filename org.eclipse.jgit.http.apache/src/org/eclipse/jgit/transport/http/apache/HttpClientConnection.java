/*
 * Copyright (C) 2013, 2020 Christian Halstrick <christian.halstrick@sap.com>
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

import static org.eclipse.jgit.util.HttpSupport.METHOD_GET;
import static org.eclipse.jgit.util.HttpSupport.METHOD_HEAD;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;
import static org.eclipse.jgit.util.HttpSupport.METHOD_PUT;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.internal.HttpApacheText;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;

/**
 * A {@link org.eclipse.jgit.transport.http.HttpConnection} which uses
 * {@link org.apache.http.client.HttpClient}
 *
 * @since 3.3
 */
public class HttpClientConnection implements HttpConnection {
	HttpClient client;

	URL url;

	HttpUriRequest req;

	HttpResponse resp = null;

	String method = "GET"; //$NON-NLS-1$

	private TemporaryBufferEntity entity;

	private boolean isUsingProxy = false;

	private Proxy proxy;

	private Integer timeout = null;

	private Integer readTimeout;

	private Boolean followRedirects;

	private HostnameVerifier hostnameverifier;

	SSLContext ctx;

	private HttpClient getClient() {
		if (client == null) {
			HttpClientBuilder clientBuilder = HttpClients.custom();
			RequestConfig.Builder configBuilder = RequestConfig.custom();
			if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
				isUsingProxy = true;
				InetSocketAddress adr = (InetSocketAddress) proxy.address();
				clientBuilder.setProxy(
						new HttpHost(adr.getHostName(), adr.getPort()));
			}
			if (timeout != null) {
				configBuilder.setConnectTimeout(timeout.intValue());
			}
			if (readTimeout != null) {
				configBuilder.setSocketTimeout(readTimeout.intValue());
			}
			if (followRedirects != null) {
				configBuilder
						.setRedirectsEnabled(followRedirects.booleanValue());
			}
			SSLConnectionSocketFactory sslConnectionFactory = getSSLSocketFactory();
			clientBuilder.setSSLSocketFactory(sslConnectionFactory);
			if (hostnameverifier != null) {
				// Using a custom verifier: we don't want pooled connections
				// with this.
				Registry<ConnectionSocketFactory> registry = RegistryBuilder
						.<ConnectionSocketFactory> create()
						.register("https", sslConnectionFactory)
						.register("http", PlainConnectionSocketFactory.INSTANCE)
						.build();
				clientBuilder.setConnectionManager(
						new BasicHttpClientConnectionManager(registry));
			}
			clientBuilder.setDefaultRequestConfig(configBuilder.build());
			clientBuilder.setDefaultCredentialsProvider(
					new SystemDefaultCredentialsProvider());
			client = clientBuilder.build();
		}

		return client;
	}

	private SSLConnectionSocketFactory getSSLSocketFactory() {
		HostnameVerifier verifier = hostnameverifier;
		SSLContext context;
		if (verifier == null) {
			// Use defaults
			context = SSLContexts.createDefault();
			verifier = new DefaultHostnameVerifier(
					PublicSuffixMatcherLoader.getDefault());
		} else {
			// Using a custom verifier. Attention: configure() must have been
			// called already, otherwise one gets a "context not initialized"
			// exception. In JGit this branch is reached only when hostname
			// verification is switched off, and JGit _does_ call configure()
			// before we get here.
			context = getSSLContext();
		}
		return new SSLConnectionSocketFactory(context, verifier) {

			@Override
			protected void prepareSocket(SSLSocket socket) throws IOException {
				super.prepareSocket(socket);
				HttpSupport.configureTLS(socket);
			}
		};
	}

	private SSLContext getSSLContext() {
		if (ctx == null) {
			try {
				ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(
						HttpApacheText.get().unexpectedSSLContextException, e);
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
	 * Constructor for HttpClientConnection.
	 *
	 * @param urlStr
	 * @throws MalformedURLException
	 */
	public HttpClientConnection(String urlStr) throws MalformedURLException {
		this(urlStr, null);
	}

	/**
	 * Constructor for HttpClientConnection.
	 *
	 * @param urlStr
	 * @param proxy
	 * @throws MalformedURLException
	 */
	public HttpClientConnection(String urlStr, Proxy proxy)
			throws MalformedURLException {
		this(urlStr, proxy, null);
	}

	/**
	 * Constructor for HttpClientConnection.
	 *
	 * @param urlStr
	 * @param proxy
	 * @param cl
	 * @throws MalformedURLException
	 */
	public HttpClientConnection(String urlStr, Proxy proxy, HttpClient cl)
			throws MalformedURLException {
		this.client = cl;
		this.url = new URL(urlStr);
		this.proxy = proxy;
	}

	/** {@inheritDoc} */
	@Override
	public int getResponseCode() throws IOException {
		execute();
		return resp.getStatusLine().getStatusCode();
	}

	/** {@inheritDoc} */
	@Override
	public URL getURL() {
		return url;
	}

	/** {@inheritDoc} */
	@Override
	public String getResponseMessage() throws IOException {
		execute();
		return resp.getStatusLine().getReasonPhrase();
	}

	private void execute() throws IOException, ClientProtocolException {
		if (resp != null) {
			return;
		}

		if (entity == null) {
			resp = getClient().execute(req);
			return;
		}

		try {
			if (req instanceof HttpEntityEnclosingRequest) {
				HttpEntityEnclosingRequest eReq = (HttpEntityEnclosingRequest) req;
				eReq.setEntity(entity);
			}
			resp = getClient().execute(req);
		} finally {
			entity.close();
			entity = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, List<String>> getHeaderFields() {
		Map<String, List<String>> ret = new HashMap<>();
		for (Header hdr : resp.getAllHeaders()) {
			List<String> list = ret.get(hdr.getName());
			if (list == null) {
				list = new LinkedList<>();
				ret.put(hdr.getName(), list);
			}
			for (HeaderElement hdrElem : hdr.getElements()) {
				list.add(hdrElem.toString());
			}
		}
		return ret;
	}

	/** {@inheritDoc} */
	@Override
	public void setRequestProperty(String name, String value) {
		req.addHeader(name, value);
	}

	/** {@inheritDoc} */
	@Override
	public void setRequestMethod(String method) throws ProtocolException {
		this.method = method;
		if (METHOD_GET.equalsIgnoreCase(method)) {
			req = new HttpGet(url.toString());
		} else if (METHOD_HEAD.equalsIgnoreCase(method)) {
			req = new HttpHead(url.toString());
		} else if (METHOD_PUT.equalsIgnoreCase(method)) {
			req = new HttpPut(url.toString());
		} else if (METHOD_POST.equalsIgnoreCase(method)) {
			req = new HttpPost(url.toString());
		} else {
			this.method = null;
			throw new UnsupportedOperationException();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void setUseCaches(boolean usecaches) {
		// not needed
	}

	/** {@inheritDoc} */
	@Override
	public void setConnectTimeout(int timeout) {
		this.timeout = Integer.valueOf(timeout);
	}

	/** {@inheritDoc} */
	@Override
	public void setReadTimeout(int readTimeout) {
		this.readTimeout = Integer.valueOf(readTimeout);
	}

	/** {@inheritDoc} */
	@Override
	public String getContentType() {
		HttpEntity responseEntity = resp.getEntity();
		if (responseEntity != null) {
			Header contentType = responseEntity.getContentType();
			if (contentType != null)
				return contentType.getValue();
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public InputStream getInputStream() throws IOException {
		execute();
		return resp.getEntity().getContent();
	}

	// will return only the first field
	/** {@inheritDoc} */
	@Override
	public String getHeaderField(@NonNull String name) {
		Header header = resp.getFirstHeader(name);
		return (header == null) ? null : header.getValue();
	}

	@Override
	public List<String> getHeaderFields(@NonNull String name) {
		return Collections.unmodifiableList(Arrays.asList(resp.getHeaders(name))
				.stream().map(Header::getValue).collect(Collectors.toList()));
	}

	/** {@inheritDoc} */
	@Override
	public int getContentLength() {
		Header contentLength = resp.getFirstHeader("content-length"); //$NON-NLS-1$
		if (contentLength == null) {
			return -1;
		}

		try {
			int l = Integer.parseInt(contentLength.getValue());
			return l < 0 ? -1 : l;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void setInstanceFollowRedirects(boolean followRedirects) {
		this.followRedirects = Boolean.valueOf(followRedirects);
	}

	/** {@inheritDoc} */
	@Override
	public void setDoOutput(boolean dooutput) {
		// TODO: check whether we can really ignore this.
	}

	/** {@inheritDoc} */
	@Override
	public void setFixedLengthStreamingMode(int contentLength) {
		if (entity != null)
			throw new IllegalArgumentException();
		entity = new TemporaryBufferEntity(new LocalFile(null));
		entity.setContentLength(contentLength);
	}

	/** {@inheritDoc} */
	@Override
	public OutputStream getOutputStream() throws IOException {
		if (entity == null)
			entity = new TemporaryBufferEntity(new LocalFile(null));
		return entity.getBuffer();
	}

	/** {@inheritDoc} */
	@Override
	public void setChunkedStreamingMode(int chunklen) {
		if (entity == null)
			entity = new TemporaryBufferEntity(new LocalFile(null));
		entity.setChunked(true);
	}

	/** {@inheritDoc} */
	@Override
	public String getRequestMethod() {
		return method;
	}

	/** {@inheritDoc} */
	@Override
	public boolean usingProxy() {
		return isUsingProxy;
	}

	/** {@inheritDoc} */
	@Override
	public void connect() throws IOException {
		execute();
	}

	/** {@inheritDoc} */
	@Override
	public void setHostnameVerifier(HostnameVerifier hostnameverifier) {
		this.hostnameverifier = hostnameverifier;
	}

	/** {@inheritDoc} */
	@Override
	public void configure(KeyManager[] km, TrustManager[] tm,
			SecureRandom random) throws KeyManagementException {
		getSSLContext().init(km, tm, random);
	}
}
