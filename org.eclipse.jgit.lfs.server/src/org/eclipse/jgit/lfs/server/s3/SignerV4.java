/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com>
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
package org.eclipse.jgit.lfs.server.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

/**
 * Signing support for Amazon AWS signing V4
 * <p>
 * See
 * http://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html
 */
class SignerV4 {
	static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"; //$NON-NLS-1$

	private static final String ALGORITHM = "HMAC-SHA256"; //$NON-NLS-1$
	private static final String DATE_STRING_FORMAT = "yyyyMMdd"; //$NON-NLS-1$
	private static final String HEX = "0123456789abcdef"; //$NON-NLS-1$
	private static final String HMACSHA256 = "HmacSHA256"; //$NON-NLS-1$
	private static final String ISO8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'"; //$NON-NLS-1$
	private static final String S3 = "s3"; //$NON-NLS-1$
	private static final String SCHEME = "AWS4"; //$NON-NLS-1$
	private static final String TERMINATOR = "aws4_request"; //$NON-NLS-1$
	private static final String UTC = "UTC"; //$NON-NLS-1$
	private static final String X_AMZ_ALGORITHM = "X-Amz-Algorithm"; //$NON-NLS-1$
	private static final String X_AMZ_CREDENTIAL = "X-Amz-Credential"; //$NON-NLS-1$
	private static final String X_AMZ_DATE = "X-Amz-Date"; //$NON-NLS-1$
	private static final String X_AMZ_SIGNATURE = "X-Amz-Signature"; //$NON-NLS-1$
	private static final String X_AMZ_SIGNED_HEADERS = "X-Amz-SignedHeaders"; //$NON-NLS-1$

	static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256"; //$NON-NLS-1$
	static final String X_AMZ_EXPIRES = "X-Amz-Expires"; //$NON-NLS-1$
	static final String X_AMZ_STORAGE_CLASS = "x-amz-storage-class"; //$NON-NLS-1$

	/**
	 * Create an AWSV4 authorization for a request, suitable for embedding in
	 * query parameters.
	 *
	 * @param bucketConfig
	 *            configuration of S3 storage bucket this request should be
	 *            signed for
	 * @param url
	 *            HTTP request URL
	 * @param httpMethod
	 *            HTTP method
	 * @param headers
	 *            The HTTP request headers; 'Host' and 'X-Amz-Date' will be
	 *            added to this set.
	 * @param queryParameters
	 *            Any query parameters that will be added to the endpoint. The
	 *            parameters should be specified in canonical format.
	 * @param bodyHash
	 *            Pre-computed SHA256 hash of the request body content; this
	 *            value should also be set as the header 'X-Amz-Content-SHA256'
	 *            for non-streaming uploads.
	 * @return The computed authorization string for the request. This value
	 *         needs to be set as the header 'Authorization' on the subsequent
	 *         HTTP request.
	 */
	static String createAuthorizationQuery(S3Config bucketConfig, URL url,
			String httpMethod, Map<String, String> headers,
			Map<String, String> queryParameters, String bodyHash) {
		addHostHeader(url, headers);

		queryParameters.put(X_AMZ_ALGORITHM, SCHEME + "-" + ALGORITHM); //$NON-NLS-1$

		Date now = new Date();
		String dateStamp = dateStamp(now);
		String scope = scope(bucketConfig.getRegion(), dateStamp);
		queryParameters.put(X_AMZ_CREDENTIAL,
				bucketConfig.getAccessKey() + "/" + scope); //$NON-NLS-1$

		String dateTimeStampISO8601 = dateTimeStampISO8601(now);
		queryParameters.put(X_AMZ_DATE, dateTimeStampISO8601);

		String canonicalizedHeaderNames = canonicalizeHeaderNames(headers);
		queryParameters.put(X_AMZ_SIGNED_HEADERS, canonicalizedHeaderNames);

		String canonicalizedQueryParameters = canonicalizeQueryString(
				queryParameters);
		String canonicalizedHeaders = canonicalizeHeaderString(headers);
		String canonicalRequest = canonicalRequest(url, httpMethod,
				canonicalizedQueryParameters, canonicalizedHeaderNames,
				canonicalizedHeaders, bodyHash);
		byte[] signature = createSignature(bucketConfig, dateTimeStampISO8601,
				dateStamp, scope, canonicalRequest);
		queryParameters.put(X_AMZ_SIGNATURE, toHex(signature));

		return formatAuthorizationQuery(queryParameters);
	}

	private static String formatAuthorizationQuery(
			Map<String, String> queryParameters) {
		StringBuilder s = new StringBuilder();
		for (String key : queryParameters.keySet()) {
			appendQuery(s, key, queryParameters.get(key));
		}
		return s.toString();
	}

	private static void appendQuery(StringBuilder s, String key,
			String value) {
		if (s.length() != 0) {
			s.append("&"); //$NON-NLS-1$
		}
		s.append(key).append("=").append(value); //$NON-NLS-1$
	}

	/**
	 * Sign headers for given bucket, url and HTTP method and add signature in
	 * Authorization header.
	 *
	 * @param bucketConfig
	 *            configuration of S3 storage bucket this request should be
	 *            signed for
	 * @param url
	 *            HTTP request URL
	 * @param httpMethod
	 *            HTTP method
	 * @param headers
	 *            HTTP headers to sign
	 * @param bodyHash
	 *            Pre-computed SHA256 hash of the request body content; this
	 *            value should also be set as the header 'X-Amz-Content-SHA256'
	 *            for non-streaming uploads.
	 * @return HTTP headers signd by an Authorization header added to the
	 *         headers
	 */
	static Map<String, String> createHeaderAuthorization(
			S3Config bucketConfig, URL url, String httpMethod,
			Map<String, String> headers, String bodyHash) {
		addHostHeader(url, headers);

		Date now = new Date();
		String dateTimeStamp = dateTimeStampISO8601(now);
		headers.put(X_AMZ_DATE, dateTimeStamp);

		String canonicalizedHeaderNames = canonicalizeHeaderNames(headers);
		String canonicalizedHeaders = canonicalizeHeaderString(headers);
		String canonicalRequest = canonicalRequest(url, httpMethod, "", //$NON-NLS-1$
				canonicalizedHeaderNames, canonicalizedHeaders, bodyHash);
		String dateStamp = dateStamp(now);
		String scope = scope(bucketConfig.getRegion(), dateStamp);

		byte[] signature = createSignature(bucketConfig, dateTimeStamp,
				dateStamp, scope, canonicalRequest);

		headers.put(HDR_AUTHORIZATION, formatAuthorizationHeader(bucketConfig,
				canonicalizedHeaderNames, scope, signature)); // $NON-NLS-1$

		return headers;
	}

	private static String formatAuthorizationHeader(
			S3Config bucketConfig, String canonicalizedHeaderNames,
			String scope, byte[] signature) {
		StringBuilder s = new StringBuilder();
		s.append(SCHEME).append("-").append(ALGORITHM).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("Credential=").append(bucketConfig.getAccessKey()).append("/") //$NON-NLS-1$//$NON-NLS-2$
				.append(scope).append(","); //$NON-NLS-1$
		s.append("SignedHeaders=").append(canonicalizedHeaderNames).append(","); //$NON-NLS-1$ //$NON-NLS-2$
		s.append("Signature=").append(toHex(signature)); //$NON-NLS-1$
		return s.toString();
	}

	private static void addHostHeader(URL url,
			Map<String, String> headers) {
		StringBuilder hostHeader = new StringBuilder(url.getHost());
		int port = url.getPort();
		if (port > -1) {
			hostHeader.append(":").append(port); //$NON-NLS-1$
		}
		headers.put("Host", hostHeader.toString()); //$NON-NLS-1$
	}

	private static String canonicalizeHeaderNames(
			Map<String, String> headers) {
		List<String> sortedHeaders = new ArrayList<>();
		sortedHeaders.addAll(headers.keySet());
		Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

		StringBuilder buffer = new StringBuilder();
		for (String header : sortedHeaders) {
			if (buffer.length() > 0)
				buffer.append(";"); //$NON-NLS-1$
			buffer.append(header.toLowerCase(Locale.ROOT));
		}

		return buffer.toString();
	}

	private static String canonicalizeHeaderString(
			Map<String, String> headers) {
		if (headers == null || headers.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		List<String> sortedHeaders = new ArrayList<>();
		sortedHeaders.addAll(headers.keySet());
		Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

		StringBuilder buffer = new StringBuilder();
		for (String key : sortedHeaders) {
			buffer.append(
					key.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ headers.get(key).replaceAll("\\s+", " ")); //$NON-NLS-1$//$NON-NLS-2$
			buffer.append("\n"); //$NON-NLS-1$
		}

		return buffer.toString();
	}

	private static String dateStamp(Date now) {
		// TODO(ms) cache and reuse DateFormat instances
		SimpleDateFormat dateStampFormat = new SimpleDateFormat(
				DATE_STRING_FORMAT);
		dateStampFormat.setTimeZone(new SimpleTimeZone(0, UTC));
		String dateStamp = dateStampFormat.format(now);
		return dateStamp;
	}

	private static String dateTimeStampISO8601(Date now) {
		// TODO(ms) cache and reuse DateFormat instances
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
				ISO8601_BASIC_FORMAT);
		dateTimeFormat.setTimeZone(new SimpleTimeZone(0, UTC));
		String dateTimeStamp = dateTimeFormat.format(now);
		return dateTimeStamp;
	}

	private static String scope(String region, String dateStamp) {
		String scope = String.format("%s/%s/%s/%s", dateStamp, region, S3, //$NON-NLS-1$
				TERMINATOR);
		return scope;
	}

	private static String canonicalizeQueryString(
			Map<String, String> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		SortedMap<String, String> sorted = new TreeMap<>();

		Iterator<Map.Entry<String, String>> pairs = parameters.entrySet()
				.iterator();
		while (pairs.hasNext()) {
			Map.Entry<String, String> pair = pairs.next();
			String key = pair.getKey();
			String value = pair.getValue();
			sorted.put(urlEncode(key, false), urlEncode(value, false));
		}

		StringBuilder builder = new StringBuilder();
		pairs = sorted.entrySet().iterator();
		while (pairs.hasNext()) {
			Map.Entry<String, String> pair = pairs.next();
			builder.append(pair.getKey());
			builder.append("="); //$NON-NLS-1$
			builder.append(pair.getValue());
			if (pairs.hasNext()) {
				builder.append("&"); //$NON-NLS-1$
			}
		}

		return builder.toString();
	}

	private static String canonicalRequest(URL endpoint, String httpMethod,
			String queryParameters, String canonicalizedHeaderNames,
			String canonicalizedHeaders, String bodyHash) {
		return String.format("%s\n%s\n%s\n%s\n%s\n%s", //$NON-NLS-1$
				httpMethod, canonicalizeResourcePath(endpoint),
				queryParameters, canonicalizedHeaders, canonicalizedHeaderNames,
				bodyHash);
	}

	private static String canonicalizeResourcePath(URL endpoint) {
		if (endpoint == null) {
			return "/"; //$NON-NLS-1$
		}
		String path = endpoint.getPath();
		if (path == null || path.isEmpty()) {
			return "/"; //$NON-NLS-1$
		}

		String encodedPath = urlEncode(path, true);
		if (encodedPath.startsWith("/")) { //$NON-NLS-1$
			return encodedPath;
		} else {
			return "/" + encodedPath; //$NON-NLS-1$
		}
	}

	private static byte[] hash(String s) {
		MessageDigest md = Constants.newMessageDigest();
		md.update(s.getBytes(UTF_8));
		return md.digest();
	}

	private static byte[] sign(String stringData, byte[] key) {
		try {
			byte[] data = stringData.getBytes(UTF_8);
			Mac mac = Mac.getInstance(HMACSHA256);
			mac.init(new SecretKeySpec(key, HMACSHA256));
			return mac.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException(MessageFormat.format(
					LfsServerText.get().failedToCalcSignature, e.getMessage()),
					e);
		}
	}

	private static String stringToSign(String scheme, String algorithm,
			String dateTime, String scope, String canonicalRequest) {
		return String.format("%s-%s\n%s\n%s\n%s", //$NON-NLS-1$
				scheme, algorithm, dateTime, scope,
				toHex(hash(canonicalRequest)));
	}

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(2 * bytes.length);
		for (byte b : bytes) {
			builder.append(HEX.charAt((b & 0xF0) >> 4));
			builder.append(HEX.charAt(b & 0xF));
		}
		return builder.toString();
	}

	private static String urlEncode(String url, boolean keepPathSlash) {
		String encoded;
		try {
			encoded = URLEncoder.encode(url, UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(LfsServerText.get().unsupportedUtf8, e);
		}
		if (keepPathSlash) {
			encoded = encoded.replace("%2F", "/"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return encoded;
	}

	private static byte[] createSignature(S3Config bucketConfig,
			String dateTimeStamp, String dateStamp,
			String scope, String canonicalRequest) {
		String stringToSign = stringToSign(SCHEME, ALGORITHM, dateTimeStamp,
				scope, canonicalRequest);

		byte[] signature = (SCHEME + bucketConfig.getSecretKey())
				.getBytes(UTF_8);
		signature = sign(dateStamp, signature);
		signature = sign(bucketConfig.getRegion(), signature);
		signature = sign(S3, signature);
		signature = sign(TERMINATOR, signature);
		signature = sign(stringToSign, signature);
		return signature;
	}
}
