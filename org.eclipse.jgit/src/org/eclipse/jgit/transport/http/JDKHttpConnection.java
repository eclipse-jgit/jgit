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
package org.eclipse.jgit.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.eclipse.jgit.annotations.NonNull;
/**
 * A {@link org.eclipse.jgit.transport.http.HttpConnection} which simply
 * delegates every call to a {@link java.net.HttpURLConnection}. This is the
 * default implementation used by JGit
 *
 * @since 3.3
 */
public class JDKHttpConnection implements HttpConnection {
	HttpURLConnection wrappedUrlConnection;

	// used for mock testing
	JDKHttpConnection(HttpURLConnection urlConnection) {
		this.wrappedUrlConnection = urlConnection;
	}

	/**
	 * Constructor for JDKHttpConnection.
	 *
	 * @param url
	 *            a {@link java.net.URL} object.
	 * @throws java.net.MalformedURLException
	 * @throws java.io.IOException
	 */
	protected JDKHttpConnection(URL url)
			throws MalformedURLException,
			IOException {
		this.wrappedUrlConnection = (HttpURLConnection) url.openConnection();
	}

	/**
	 * Constructor for JDKHttpConnection.
	 *
	 * @param url
	 *            a {@link java.net.URL} object.
	 * @param proxy
	 *            a {@link java.net.Proxy} object.
	 * @throws java.net.MalformedURLException
	 * @throws java.io.IOException
	 */
	protected JDKHttpConnection(URL url, Proxy proxy)
			throws MalformedURLException, IOException {
		this.wrappedUrlConnection = (HttpURLConnection) url
				.openConnection(proxy);
	}

	/** {@inheritDoc} */
	@Override
	public int getResponseCode() throws IOException {
		return wrappedUrlConnection.getResponseCode();
	}

	/** {@inheritDoc} */
	@Override
	public URL getURL() {
		return wrappedUrlConnection.getURL();
	}

	/** {@inheritDoc} */
	@Override
	public String getResponseMessage() throws IOException {
		return wrappedUrlConnection.getResponseMessage();
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, List<String>> getHeaderFields() {
		return wrappedUrlConnection.getHeaderFields();
	}

	/** {@inheritDoc} */
	@Override
	public void setRequestProperty(String key, String value) {
		wrappedUrlConnection.setRequestProperty(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public void setRequestMethod(String method) throws ProtocolException {
		wrappedUrlConnection.setRequestMethod(method);
	}

	/** {@inheritDoc} */
	@Override
	public void setUseCaches(boolean usecaches) {
		wrappedUrlConnection.setUseCaches(usecaches);
	}

	/** {@inheritDoc} */
	@Override
	public void setConnectTimeout(int timeout) {
		wrappedUrlConnection.setConnectTimeout(timeout);
	}

	/** {@inheritDoc} */
	@Override
	public void setReadTimeout(int timeout) {
		wrappedUrlConnection.setReadTimeout(timeout);
	}

	/** {@inheritDoc} */
	@Override
	public String getContentType() {
		return wrappedUrlConnection.getContentType();
	}

	/** {@inheritDoc} */
	@Override
	public InputStream getInputStream() throws IOException {
		return wrappedUrlConnection.getInputStream();
	}

	/** {@inheritDoc} */
	@Override
	public String getHeaderField(@NonNull String name) {
		return wrappedUrlConnection.getHeaderField(name);
	}

	@Override
	public List<String> getHeaderFields(@NonNull String name) {
		Map<String, List<String>> m = wrappedUrlConnection.getHeaderFields();
		List<String> fields = mapValuesToListIgnoreCase(name, m);
		return fields;
	}

	private static List<String> mapValuesToListIgnoreCase(String keyName,
			Map<String, List<String>> m) {
		List<String> fields = new LinkedList<>();
		m.entrySet().stream().filter(e -> keyName.equalsIgnoreCase(e.getKey()))
				.filter(e -> e.getValue() != null)
				.forEach(e -> fields.addAll(e.getValue()));
		return fields;
	}

	/** {@inheritDoc} */
	@Override
	public int getContentLength() {
		return wrappedUrlConnection.getContentLength();
	}

	/** {@inheritDoc} */
	@Override
	public void setInstanceFollowRedirects(boolean followRedirects) {
		wrappedUrlConnection.setInstanceFollowRedirects(followRedirects);
	}

	/** {@inheritDoc} */
	@Override
	public void setDoOutput(boolean dooutput) {
		wrappedUrlConnection.setDoOutput(dooutput);
	}

	/** {@inheritDoc} */
	@Override
	public void setFixedLengthStreamingMode(int contentLength) {
		wrappedUrlConnection.setFixedLengthStreamingMode(contentLength);
	}

	/** {@inheritDoc} */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return wrappedUrlConnection.getOutputStream();
	}

	/** {@inheritDoc} */
	@Override
	public void setChunkedStreamingMode(int chunklen) {
		wrappedUrlConnection.setChunkedStreamingMode(chunklen);
	}

	/** {@inheritDoc} */
	@Override
	public String getRequestMethod() {
		return wrappedUrlConnection.getRequestMethod();
	}

	/** {@inheritDoc} */
	@Override
	public boolean usingProxy() {
		return wrappedUrlConnection.usingProxy();
	}

	/** {@inheritDoc} */
	@Override
	public void connect() throws IOException {
		wrappedUrlConnection.connect();
	}

	/** {@inheritDoc} */
	@Override
	public void setHostnameVerifier(HostnameVerifier hostnameverifier) {
		((HttpsURLConnection) wrappedUrlConnection)
				.setHostnameVerifier(hostnameverifier);
	}

	/** {@inheritDoc} */
	@Override
	public void configure(KeyManager[] km, TrustManager[] tm,
			SecureRandom random) throws NoSuchAlgorithmException,
			KeyManagementException {
		SSLContext ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
		ctx.init(km, tm, random);
		((HttpsURLConnection) wrappedUrlConnection).setSSLSocketFactory(ctx
				.getSocketFactory());
	}
}
