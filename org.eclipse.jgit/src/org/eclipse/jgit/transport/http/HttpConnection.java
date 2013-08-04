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
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * The interface of connections used during HTTP communication. This interface
 * is that subset of the interface exposed by {@link HttpURLConnection} which is
 * used by JGit
 *
 * @since 3.3
 */
public interface HttpConnection {
	/**
	 * @see HttpURLConnection#HTTP_OK
	 */
	public static final int HTTP_OK = java.net.HttpURLConnection.HTTP_OK;

	/**
	 * @see HttpURLConnection#HTTP_NOT_FOUND
	 */
	public static final int HTTP_NOT_FOUND = java.net.HttpURLConnection.HTTP_NOT_FOUND;

	/**
	 * @see HttpURLConnection#HTTP_UNAUTHORIZED
	 */
	public static final int HTTP_UNAUTHORIZED = java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

	/**
	 * @see HttpURLConnection#HTTP_FORBIDDEN
	 */
	public static final int HTTP_FORBIDDEN = java.net.HttpURLConnection.HTTP_FORBIDDEN;

	/**
	 * @see HttpURLConnection#getResponseCode()
	 * @return the HTTP Status-Code, or -1
	 * @throws IOException
	 */
	public int getResponseCode() throws IOException;

	/**
	 * @see HttpURLConnection#getURL()
	 * @return the URL.
	 */
	public URL getURL();

	/**
	 * @see HttpURLConnection#getResponseMessage()
	 * @return the HTTP response message, or <code>null</code>
	 * @throws IOException
	 */
	public String getResponseMessage() throws IOException;

	/**
	 * @see HttpURLConnection#getHeaderFields()
	 * @return a Map of header fields
	 */
	public Map<String, List<String>> getHeaderFields();

	/**
	 * @see HttpURLConnection#setRequestProperty(String, String)
	 * @param key
	 *            the keyword by which the request is known (e.g., "
	 *            <code>Accept</code>").
	 * @param value
	 *            the value associated with it.
	 */
	public void setRequestProperty(String key, String value);

	/**
	 * @see HttpURLConnection#setRequestMethod(String)
	 * @param method
	 *            the HTTP method
	 * @exception ProtocolException
	 *                if the method cannot be reset or if the requested method
	 *                isn't valid for HTTP.
	 */
	public void setRequestMethod(String method)
			throws ProtocolException;

	/**
	 * @see HttpURLConnection#setUseCaches(boolean)
	 * @param usecaches
	 *            a <code>boolean</code> indicating whether or not to allow
	 *            caching
	 */
	public void setUseCaches(boolean usecaches);

	/**
	 * @see HttpURLConnection#setConnectTimeout(int)
	 * @param timeout
	 *            an <code>int</code> that specifies the connect timeout value
	 *            in milliseconds
	 */
	public void setConnectTimeout(int timeout);

	/**
	 * @see HttpURLConnection#setReadTimeout(int)
	 * @param timeout
	 *            an <code>int</code> that specifies the timeout value to be
	 *            used in milliseconds
	 */
	public void setReadTimeout(int timeout);

	/**
	 * @see HttpURLConnection#getContentType()
	 * @return the content type of the resource that the URL references, or
	 *         <code>null</code> if not known.
	 */
	public String getContentType();

	/**
	 * @see HttpURLConnection#getInputStream()
	 * @return an input stream that reads from this open connection.
	 * @exception IOException
	 *                if an I/O error occurs while creating the input stream.
	 */
	public InputStream getInputStream() throws IOException;

	/**
	 * @see HttpURLConnection#getHeaderField(String)
	 * @param name
	 *            the name of a header field.
	 * @return the value of the named header field, or <code>null</code> if
	 *         there is no such field in the header.
	 */
	public String getHeaderField(String name);

	/**
	 * @see HttpURLConnection#getContentLength()
	 * @return the content length of the resource that this connection's URL
	 *         references, {@code -1} if the content length is not known, or if
	 *         the content length is greater than Integer.MAX_VALUE.
	 */
	public int getContentLength();

	/**
	 * @see HttpURLConnection#setInstanceFollowRedirects(boolean)
	 * @param followRedirects
	 *            a <code>boolean</code> indicating whether or not to follow
	 *            HTTP redirects.
	 */
	public void setInstanceFollowRedirects(boolean followRedirects);

	/**
	 * @see HttpURLConnection#setDoOutput(boolean)
     * @param   dooutput   the new value.
	 */
	public void setDoOutput(boolean dooutput);

	/**
	 * @see HttpURLConnection#setFixedLengthStreamingMode(int)
	 * @param contentLength
	 *            The number of bytes which will be written to the OutputStream.
	 *
	 */
	public void setFixedLengthStreamingMode(int contentLength);

	/**
	 * @see HttpURLConnection#getOutputStream()
	 * @return an output stream that writes to this connection.
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;

	/**
	 * @see HttpURLConnection#setChunkedStreamingMode(int)
	 * @param chunklen
	 *            The number of bytes to write in each chunk. If chunklen is
	 *            less than or equal to zero, a default value will be used.
	 */
	public void setChunkedStreamingMode(int chunklen);

	/**
	 * @see HttpURLConnection#getRequestMethod()
	 * @return the HTTP request method
	 */
	public String getRequestMethod();

	/**
	 * @see HttpURLConnection#disconnect()
	 */
	public void disconnect();

	/**
	 * @see HttpURLConnection#usingProxy()
	 * @return a boolean indicating if the connection is using a proxy.
	 */
	public boolean usingProxy();

	/**
	 * @see HttpURLConnection#connect()
	 * @throws IOException
	 */
	public void connect() throws IOException;

	/**
	 * Configure the connection so that it can be used for https communication.
	 * 
	 * @param km
	 *            the keymanager managing the key material used to authenticate
	 *            the local SSLSocket to its peer
	 * @param tm
	 *            the trustmanager responsible for managing the trust material
	 *            that is used when making trust decisions, and for deciding
	 *            whether credentials presented by a peer should be accepted.
	 * @param random
	 *            the source of randomness for this generator or null. See
	 *            {@link SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 */
	public void configure(KeyManager[] km, TrustManager[] tm,
			SecureRandom random) throws NoSuchAlgorithmException,
			KeyManagementException;

	/**
	 * Set the {@link HostnameVerifier} used during https communication
	 *
	 * @param hostnameverifier
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 */
	public void setHostnameVerifier(HostnameVerifier hostnameverifier)
			throws NoSuchAlgorithmException, KeyManagementException;
}
