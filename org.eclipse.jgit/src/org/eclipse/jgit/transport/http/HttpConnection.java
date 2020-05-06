/*
 * Copyright (C) 2013, 2017 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import javax.net.ssl.TrustManager;

import org.eclipse.jgit.annotations.NonNull;

/**
 * The interface of connections used during HTTP communication. This interface
 * is that subset of the interface exposed by {@link java.net.HttpURLConnection}
 * which is used by JGit
 *
 * @since 3.3
 */
public interface HttpConnection {
	/**
	 * @see HttpURLConnection#HTTP_OK
	 */
	int HTTP_OK = java.net.HttpURLConnection.HTTP_OK;

	/**
	 * @see HttpURLConnection#HTTP_MOVED_PERM
	 * @since 4.7
	 */
	int HTTP_MOVED_PERM = java.net.HttpURLConnection.HTTP_MOVED_PERM;

	/**
	 * @see HttpURLConnection#HTTP_MOVED_TEMP
	 * @since 4.9
	 */
	int HTTP_MOVED_TEMP = java.net.HttpURLConnection.HTTP_MOVED_TEMP;

	/**
	 * @see HttpURLConnection#HTTP_SEE_OTHER
	 * @since 4.9
	 */
	int HTTP_SEE_OTHER = java.net.HttpURLConnection.HTTP_SEE_OTHER;

	/**
	 * HTTP 1.1 additional "temporary redirect" status code; value = 307.
	 *
	 * @see #HTTP_MOVED_TEMP
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.7">RFC
	 *      7231, section 6.4.7: 307 Temporary Redirect</a>
	 * @since 4.9
	 */
	int HTTP_11_MOVED_TEMP = 307;

	/**
	 * HTTP 1.1 additional "permanent redirect" status code; value = 308.
	 *
	 * @see #HTTP_MOVED_TEMP
	 * @see <a href="https://tools.ietf.org/html/rfc7538#section-3">RFC 7538,
	 *      section 3: 308 Permanent Redirect</a>
	 * @since 5.8
	 */
	int HTTP_11_MOVED_PERM = 308;

	/**
	 * @see HttpURLConnection#HTTP_NOT_FOUND
	 */
	int HTTP_NOT_FOUND = java.net.HttpURLConnection.HTTP_NOT_FOUND;

	/**
	 * @see HttpURLConnection#HTTP_UNAUTHORIZED
	 */
	int HTTP_UNAUTHORIZED = java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

	/**
	 * @see HttpURLConnection#HTTP_FORBIDDEN
	 */
	int HTTP_FORBIDDEN = java.net.HttpURLConnection.HTTP_FORBIDDEN;

	/**
	 * Get response code
	 *
	 * @see HttpURLConnection#getResponseCode()
	 * @return the HTTP Status-Code, or -1
	 * @throws java.io.IOException
	 */
	int getResponseCode() throws IOException;

	/**
	 * Get URL
	 *
	 * @see HttpURLConnection#getURL()
	 * @return the URL.
	 */
	URL getURL();

	/**
	 * Get response message
	 *
	 * @see HttpURLConnection#getResponseMessage()
	 * @return the HTTP response message, or <code>null</code>
	 * @throws java.io.IOException
	 */
	String getResponseMessage() throws IOException;

	/**
	 * Get map of header fields
	 *
	 * @see HttpURLConnection#getHeaderFields()
	 * @return a Map of header fields
	 */
	Map<String, List<String>> getHeaderFields();

	/**
	 * Set request property
	 *
	 * @see HttpURLConnection#setRequestProperty(String, String)
	 * @param key
	 *            the keyword by which the request is known (e.g., "
	 *            <code>Accept</code>").
	 * @param value
	 *            the value associated with it.
	 */
	void setRequestProperty(String key, String value);

	/**
	 * Set request method
	 *
	 * @see HttpURLConnection#setRequestMethod(String)
	 * @param method
	 *            the HTTP method
	 * @exception ProtocolException
	 *                if the method cannot be reset or if the requested method
	 *                isn't valid for HTTP.
	 * @throws java.net.ProtocolException
	 *             if any.
	 */
	void setRequestMethod(String method)
			throws ProtocolException;

	/**
	 * Set if to use caches
	 *
	 * @see HttpURLConnection#setUseCaches(boolean)
	 * @param usecaches
	 *            a <code>boolean</code> indicating whether or not to allow
	 *            caching
	 */
	void setUseCaches(boolean usecaches);

	/**
	 * Set connect timeout
	 *
	 * @see HttpURLConnection#setConnectTimeout(int)
	 * @param timeout
	 *            an <code>int</code> that specifies the connect timeout value
	 *            in milliseconds
	 */
	void setConnectTimeout(int timeout);

	/**
	 * Set read timeout
	 *
	 * @see HttpURLConnection#setReadTimeout(int)
	 * @param timeout
	 *            an <code>int</code> that specifies the timeout value to be
	 *            used in milliseconds
	 */
	void setReadTimeout(int timeout);

	/**
	 * Get content type
	 *
	 * @see HttpURLConnection#getContentType()
	 * @return the content type of the resource that the URL references, or
	 *         <code>null</code> if not known.
	 */
	String getContentType();

	/**
	 * Get input stream
	 *
	 * @see HttpURLConnection#getInputStream()
	 * @return an input stream that reads from this open connection.
	 * @exception IOException
	 *                if an I/O error occurs while creating the input stream.
	 * @throws java.io.IOException
	 *             if any.
	 */
	InputStream getInputStream() throws IOException;

	/**
	 * Get header field. According to
	 * {@link <a href="https://tools.ietf.org/html/rfc2616#section-4.2">RFC
	 * 2616</a>} header field names are case insensitive. Header fields defined
	 * as a comma separated list can have multiple header fields with the same
	 * field name. This method only returns one of these header fields. If you
	 * want the union of all values of all multiple header fields with the same
	 * field name then use {@link #getHeaderFields(String)}
	 *
	 * @see HttpURLConnection#getHeaderField(String)
	 * @param name
	 *            the name of a header field.
	 * @return the value of the named header field, or <code>null</code> if
	 *         there is no such field in the header.
	 */
	String getHeaderField(@NonNull String name);

	/**
	 * Get all values of given header field. According to
	 * {@link <a href="https://tools.ietf.org/html/rfc2616#section-4.2">RFC
	 * 2616</a>} header field names are case insensitive. Header fields defined
	 * as a comma separated list can have multiple header fields with the same
	 * field name. This method does not validate if the given header field is
	 * defined as a comma separated list.
	 *
	 * @param name
	 *            the name of a header field.
	 * @return the list of values of the named header field
	 * @since 5.2
	 */
	List<String> getHeaderFields(@NonNull String name);

	/**
	 * Get content length
	 *
	 * @see HttpURLConnection#getContentLength()
	 * @return the content length of the resource that this connection's URL
	 *         references, {@code -1} if the content length is not known, or if
	 *         the content length is greater than Integer.MAX_VALUE.
	 */
	int getContentLength();

	/**
	 * Set whether or not to follow HTTP redirects.
	 *
	 * @see HttpURLConnection#setInstanceFollowRedirects(boolean)
	 * @param followRedirects
	 *            a <code>boolean</code> indicating whether or not to follow
	 *            HTTP redirects.
	 */
	void setInstanceFollowRedirects(boolean followRedirects);

	/**
	 * Set if to do output
	 *
	 * @see HttpURLConnection#setDoOutput(boolean)
	 * @param dooutput
	 *            the new value.
	 */
	void setDoOutput(boolean dooutput);

	/**
	 * Set fixed length streaming mode
	 *
	 * @see HttpURLConnection#setFixedLengthStreamingMode(int)
	 * @param contentLength
	 *            The number of bytes which will be written to the OutputStream.
	 */
	void setFixedLengthStreamingMode(int contentLength);

	/**
	 * Get output stream
	 *
	 * @see HttpURLConnection#getOutputStream()
	 * @return an output stream that writes to this connection.
	 * @throws java.io.IOException
	 */
	OutputStream getOutputStream() throws IOException;

	/**
	 * Set chunked streaming mode
	 *
	 * @see HttpURLConnection#setChunkedStreamingMode(int)
	 * @param chunklen
	 *            The number of bytes to write in each chunk. If chunklen is
	 *            less than or equal to zero, a default value will be used.
	 */
	void setChunkedStreamingMode(int chunklen);

	/**
	 * Get request method
	 *
	 * @see HttpURLConnection#getRequestMethod()
	 * @return the HTTP request method
	 */
	String getRequestMethod();

	/**
	 * Whether we use a proxy
	 *
	 * @see HttpURLConnection#usingProxy()
	 * @return a boolean indicating if the connection is using a proxy.
	 */
	boolean usingProxy();

	/**
	 * Connect
	 *
	 * @see HttpURLConnection#connect()
	 * @throws java.io.IOException
	 */
	void connect() throws IOException;

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
	 *            {@link javax.net.ssl.SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}
	 * @throws java.security.NoSuchAlgorithmException
	 * @throws java.security.KeyManagementException
	 */
	void configure(KeyManager[] km, TrustManager[] tm,
			SecureRandom random) throws NoSuchAlgorithmException,
			KeyManagementException;

	/**
	 * Set the {@link javax.net.ssl.HostnameVerifier} used during https
	 * communication
	 *
	 * @param hostnameverifier
	 *            a {@link javax.net.ssl.HostnameVerifier} object.
	 * @throws java.security.NoSuchAlgorithmException
	 * @throws java.security.KeyManagementException
	 */
	void setHostnameVerifier(HostnameVerifier hostnameverifier)
			throws NoSuchAlgorithmException, KeyManagementException;
}
