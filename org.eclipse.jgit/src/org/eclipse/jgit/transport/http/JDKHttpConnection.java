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
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * A {@link HttpConnection} which simply delegates every call to a
 * {@link HttpURLConnection}. This is the default implementation used by JGit
 *
 * @since 3.3
 */
public class JDKHttpConnection implements HttpConnection {
	HttpURLConnection wrappedUrlConnection;

	/**
	 * @param url
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	protected JDKHttpConnection(URL url)
			throws MalformedURLException,
			IOException {
		this.wrappedUrlConnection = (HttpURLConnection) url.openConnection();
	}

	/**
	 * @param url
	 * @param proxy
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	protected JDKHttpConnection(URL url, Proxy proxy)
			throws MalformedURLException, IOException {
		this.wrappedUrlConnection = (HttpURLConnection) url
				.openConnection(proxy);
	}

	@Override
	public int getResponseCode() throws IOException {
		return wrappedUrlConnection.getResponseCode();
	}

	@Override
	public URL getURL() {
		return wrappedUrlConnection.getURL();
	}

	@Override
	public String getResponseMessage() throws IOException {
		return wrappedUrlConnection.getResponseMessage();
	}

	@Override
	public Map<String, List<String>> getHeaderFields() {
		return wrappedUrlConnection.getHeaderFields();
	}

	@Override
	public void setRequestProperty(String key, String value) {
		wrappedUrlConnection.setRequestProperty(key, value);
	}

	@Override
	public void setRequestMethod(String method) throws ProtocolException {
		wrappedUrlConnection.setRequestMethod(method);
	}

	@Override
	public void setUseCaches(boolean usecaches) {
		wrappedUrlConnection.setUseCaches(usecaches);
	}

	@Override
	public void setConnectTimeout(int timeout) {
		wrappedUrlConnection.setConnectTimeout(timeout);
	}

	@Override
	public void setReadTimeout(int timeout) {
		wrappedUrlConnection.setReadTimeout(timeout);
	}

	@Override
	public String getContentType() {
		return wrappedUrlConnection.getContentType();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return wrappedUrlConnection.getInputStream();
	}

	@Override
	public String getHeaderField(String name) {
		return wrappedUrlConnection.getHeaderField(name);
	}

	@Override
	public int getContentLength() {
		return wrappedUrlConnection.getContentLength();
	}

	@Override
	public void setInstanceFollowRedirects(boolean followRedirects) {
		wrappedUrlConnection.setInstanceFollowRedirects(followRedirects);
	}

	@Override
	public void setDoOutput(boolean dooutput) {
		wrappedUrlConnection.setDoOutput(dooutput);
	}

	@Override
	public void setFixedLengthStreamingMode(int contentLength) {
		wrappedUrlConnection.setFixedLengthStreamingMode(contentLength);
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return wrappedUrlConnection.getOutputStream();
	}

	@Override
	public void setChunkedStreamingMode(int chunklen) {
		wrappedUrlConnection.setChunkedStreamingMode(chunklen);
	}

	@Override
	public String getRequestMethod() {
		return wrappedUrlConnection.getRequestMethod();
	}

	@Override
	public boolean usingProxy() {
		return wrappedUrlConnection.usingProxy();
	}

	@Override
	public void connect() throws IOException {
		wrappedUrlConnection.connect();
	}

	@Override
	public void setHostnameVerifier(HostnameVerifier hostnameverifier) {
		((HttpsURLConnection) wrappedUrlConnection)
				.setHostnameVerifier(hostnameverifier);
	}

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
