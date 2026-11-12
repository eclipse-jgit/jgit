/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.AwsRequestSignerV4;
import org.eclipse.jgit.util.HttpSupport;

/**
 * {@link ExternalObjectStore} keeping blobs in an Amazon S3 bucket or an
 * S3-compatible service.
 * <p>
 * Each blob is one S3 object at {@code <prefix>/<object id>}, holding the
 * blob in Git's object format without compression: the header
 * {@code blob <size>\0} followed by the content. The SHA-1 of an S3 object is
 * therefore its Git object id, which lets S3 verify every upload: the id is
 * sent as the {@code x-amz-checksum-sha1} of the {@code PUT}, and S3 rejects
 * content that does not match.
 * <p>
 * Requests are signed with AWS Signature Version 4. Upload bodies are not
 * hashed for the signature ({@code UNSIGNED-PAYLOAD}), so content streams
 * straight from the caller with constant memory. A single {@code PUT} holds
 * at most {@value #MAX_SINGLE_PUT_SIZE} bytes; larger blobs are rejected.
 * <p>
 * {@code HEAD}, {@code GET} and {@code DELETE} are retried on connection
 * errors and server errors. {@code PUT} is not retried, because the content
 * stream cannot be read twice.
 */
public class S3ObjectStore implements ExternalObjectStore {

	/** Largest object a single S3 {@code PUT} accepts: 5 GiB. */
	public static final long MAX_SINGLE_PUT_SIZE = 5L * 1024 * 1024 * 1024;

	private static final String SERVICE = "s3"; //$NON-NLS-1$

	private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"; //$NON-NLS-1$

	private static final String HDR_CHECKSUM_SHA1 = "x-amz-checksum-sha1"; //$NON-NLS-1$

	private static final String CONTENT_TYPE = "application/octet-stream"; //$NON-NLS-1$

	private static final Pattern PREFIX = Pattern
			.compile("[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]+)*"); //$NON-NLS-1$

	private static final Pattern ERROR_CODE = Pattern
			.compile("<Code>([^<]*)</Code>"); //$NON-NLS-1$

	private static final int BUFFER_SIZE = 64 * 1024;

	private static final int MAX_HEADER_LENGTH = 32;

	private final String bucket;

	private final String region;

	private final String prefix;

	@Nullable
	private final URL endpoint;

	private final String accessKey;

	private final char[] secretKey;

	private int connectTimeoutMillis = 30_000;

	private int readTimeoutMillis = 60_000;

	private int maxAttempts = 3;

	private long retryDelayMillis = 200;

	/**
	 * Create a store.
	 *
	 * @param bucket
	 *            name of the bucket
	 * @param region
	 *            region of the bucket, used for signing and, without an
	 *            {@code endpoint}, to build the AWS host name
	 * @param prefix
	 *            key prefix, or {@code null} or empty to store blobs at the
	 *            top of the bucket. It may only contain letters, digits,
	 *            {@code . _ -} and single {@code /} between segments.
	 * @param endpoint
	 *            base URL of an S3-compatible service, for example
	 *            {@code http://localhost:9090}; requests then use path-style
	 *            URLs. {@code null} to use AWS with virtual-hosted URLs.
	 * @param accessKey
	 *            access key id
	 * @param secretKey
	 *            secret access key
	 * @throws IllegalArgumentException
	 *             {@code prefix} contains characters that are not allowed
	 */
	public S3ObjectStore(String bucket, String region, @Nullable String prefix,
			@Nullable URL endpoint, String accessKey, char[] secretKey) {
		this.bucket = bucket;
		this.region = region;
		this.prefix = normalizePrefix(prefix);
		this.endpoint = endpoint;
		this.accessKey = accessKey;
		this.secretKey = secretKey.clone();
	}

	private static String normalizePrefix(@Nullable String prefix) {
		if (prefix == null) {
			return ""; //$NON-NLS-1$
		}
		String p = prefix;
		while (p.startsWith("/")) { //$NON-NLS-1$
			p = p.substring(1);
		}
		while (p.endsWith("/")) { //$NON-NLS-1$
			p = p.substring(0, p.length() - 1);
		}
		if (!p.isEmpty() && !PREFIX.matcher(p).matches()) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().s3ObjectStoreInvalidPrefix, prefix));
		}
		return p;
	}

	/**
	 * Set the timeouts for connecting and for reading a response.
	 *
	 * @param connectMillis
	 *            connect timeout in milliseconds
	 * @param readMillis
	 *            read timeout in milliseconds
	 */
	public void setTimeouts(int connectMillis, int readMillis) {
		connectTimeoutMillis = connectMillis;
		readTimeoutMillis = readMillis;
	}

	/**
	 * Set how often requests that can be repeated are attempted.
	 *
	 * @param attempts
	 *            number of attempts, at least 1
	 * @param delayMillis
	 *            delay before the first retry; it doubles for every further
	 *            retry
	 */
	public void setRetries(int attempts, long delayMillis) {
		maxAttempts = Math.max(1, attempts);
		retryDelayMillis = delayMillis;
	}

	@Override
	public long find(AnyObjectId id) throws IOException {
		String key = keyFor(id);
		HttpURLConnection c = execute("HEAD", key); //$NON-NLS-1$
		try {
			int status = c.getResponseCode();
			if (status == HttpURLConnection.HTTP_NOT_FOUND) {
				return -1;
			}
			if (status != HttpURLConnection.HTTP_OK) {
				throw error("HEAD", key, c); //$NON-NLS-1$
			}
			long total = c.getContentLengthLong();
			long size = blobSize(total);
			if (size < 0) {
				throw invalidObject(key);
			}
			return size;
		} finally {
			c.disconnect();
		}
	}

	@Override
	public InputStream open(AnyObjectId id) throws IOException {
		String key = keyFor(id);
		HttpURLConnection c = execute("GET", key); //$NON-NLS-1$
		boolean ok = false;
		try {
			int status = c.getResponseCode();
			if (status == HttpURLConnection.HTTP_NOT_FOUND) {
				throw new MissingObjectException(id.copy(),
						JGitText.get().unknownObjectType2);
			}
			if (status != HttpURLConnection.HTTP_OK) {
				throw error("GET", key, c); //$NON-NLS-1$
			}
			InputStream in = c.getInputStream();
			skipHeader(in, key);
			ok = true;
			return new FilterInputStream(in) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						c.disconnect();
					}
				}
			};
		} finally {
			if (!ok) {
				c.disconnect();
			}
		}
	}

	@Override
	public void put(AnyObjectId id, long size, InputStream content)
			throws IOException {
		byte[] header = Constants.encodedTypeString(Constants.OBJ_BLOB);
		ByteArrayOutputStream h = new ByteArrayOutputStream();
		h.write(header);
		h.write(' ');
		h.write(Constants.encodeASCII(size));
		h.write(0);
		byte[] objectHeader = h.toByteArray();
		long total = objectHeader.length + size;
		String key = keyFor(id);
		if (total > MAX_SINGLE_PUT_SIZE) {
			throw new IOException(MessageFormat.format(
					JGitText.get().s3ObjectStoreObjectTooLarge, id.name(),
					Long.valueOf(size), Long.valueOf(MAX_SINGLE_PUT_SIZE)));
		}

		byte[] raw = new byte[Constants.OBJECT_ID_LENGTH];
		id.copyRawTo(raw, 0);
		HttpURLConnection c = connect("PUT", key); //$NON-NLS-1$
		try {
			c.setDoOutput(true);
			c.setFixedLengthStreamingMode(total);
			c.setRequestProperty(HttpSupport.HDR_CONTENT_TYPE, CONTENT_TYPE);
			c.setRequestProperty(HDR_CHECKSUM_SHA1,
					Base64.getEncoder().encodeToString(raw));
			sign(c, total, UNSIGNED_PAYLOAD);
			try (OutputStream out = c.getOutputStream()) {
				writeBody(id, size, objectHeader, content, out);
			}
			int status = c.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw error("PUT", key, c); //$NON-NLS-1$
			}
		} finally {
			// On failure, disconnecting before the body is complete makes
			// S3 discard the upload; a partial or rejected upload is never
			// stored.
			c.disconnect();
		}
	}

	// Write the object header and exactly size bytes of content. The body is
	// complete once its last byte is written, at which point S3 would store
	// it. Extra content is therefore detected before the last chunk is
	// written, so a body that is too long is never stored.
	private static void writeBody(AnyObjectId id, long size, byte[] header,
			InputStream in, OutputStream out) throws IOException {
		if (size == 0) {
			ensureEnd(id, size, in);
			out.write(header);
			return;
		}
		out.write(header);
		byte[] buf = new byte[BUFFER_SIZE];
		long remaining = size;
		while (remaining > 0) {
			int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
			if (n < 0) {
				throw sizeMismatch(id, size);
			}
			remaining -= n;
			if (remaining == 0) {
				ensureEnd(id, size, in);
			}
			out.write(buf, 0, n);
		}
	}

	private static void ensureEnd(AnyObjectId id, long size, InputStream in)
			throws IOException {
		if (in.read() >= 0) {
			throw sizeMismatch(id, size);
		}
	}

	private static IOException sizeMismatch(AnyObjectId id, long size) {
		return new IOException(MessageFormat.format(
				JGitText.get().externalObjectSizeMismatch, id.name(),
				Long.valueOf(size)));
	}

	@Override
	public void delete(AnyObjectId id) throws IOException {
		String key = keyFor(id);
		HttpURLConnection c = execute("DELETE", key); //$NON-NLS-1$
		try {
			int status = c.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK
					&& status != HttpURLConnection.HTTP_NO_CONTENT
					&& status != HttpURLConnection.HTTP_NOT_FOUND) {
				throw error("DELETE", key, c); //$NON-NLS-1$
			}
		} finally {
			c.disconnect();
		}
	}

	@Override
	public void close() {
		// Connections are not kept open between requests.
	}

	@Override
	public String toString() {
		return "S3ObjectStore[" + bucket + '/' + prefix + ']'; //$NON-NLS-1$
	}

	String keyFor(AnyObjectId id) {
		return prefix.isEmpty() ? id.name() : prefix + '/' + id.name();
	}

	URL urlFor(String key) throws IOException {
		URL base = endpoint;
		String url;
		if (base != null) {
			String b = base.toExternalForm();
			if (!b.endsWith("/")) { //$NON-NLS-1$
				b += '/';
			}
			url = b + bucket + '/' + key;
		} else {
			url = "https://" + bucket + ".s3." + region //$NON-NLS-1$ //$NON-NLS-2$
					+ ".amazonaws.com/" + key; //$NON-NLS-1$
		}
		try {
			return new URI(url).toURL();
		} catch (URISyntaxException | IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	// Send a request without a body, retrying connection errors and server
	// errors. The last attempt's response is returned whatever its status.
	private HttpURLConnection execute(String method, String key)
			throws IOException {
		IOException lastError = new IOException();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			if (attempt > 1) {
				pause(attempt);
			}
			HttpURLConnection c = connect(method, key);
			try {
				sign(c, 0, null);
				int status = c.getResponseCode();
				if (status < 500 || attempt == maxAttempts) {
					return c;
				}
				lastError = error(method, key, c);
			} catch (IOException e) {
				lastError = e;
			}
			c.disconnect();
		}
		throw lastError;
	}

	private void pause(int attempt) throws IOException {
		long delay = retryDelayMillis << (attempt - 2);
		if (delay <= 0) {
			return;
		}
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}

	private HttpURLConnection connect(String method, String key)
			throws IOException {
		URL url = urlFor(key);
		HttpURLConnection c = (HttpURLConnection) url.openConnection(
				HttpSupport.proxyFor(ProxySelector.getDefault(), url));
		c.setRequestMethod(method);
		c.setConnectTimeout(connectTimeoutMillis);
		c.setReadTimeout(readTimeoutMillis);
		c.setUseCaches(false);
		return c;
	}

	private void sign(HttpURLConnection c, long contentLength,
			@Nullable String bodyHash) {
		AwsRequestSignerV4.sign(c, Collections.emptyMap(), contentLength,
				bodyHash, SERVICE, region, accessKey, secretKey);
	}

	private IOException error(String method, String key, HttpURLConnection c)
			throws IOException {
		int status = c.getResponseCode();
		String code = errorCode(c);
		return new IOException(MessageFormat.format(
				JGitText.get().s3ObjectStoreRequestFailed, method, key, bucket,
				Integer.valueOf(status),
				code != null ? code : c.getResponseMessage()));
	}

	@Nullable
	private static String errorCode(HttpURLConnection c) {
		try (InputStream err = c.getErrorStream()) {
			if (err == null) {
				return null;
			}
			byte[] body = err.readNBytes(4096);
			Matcher m = ERROR_CODE.matcher(new String(body, UTF_8));
			return m.find() ? m.group(1) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private IOException invalidObject(String key) {
		return new IOException(MessageFormat.format(
				JGitText.get().s3ObjectStoreInvalidObject, key, bucket));
	}

	// Read and check the "blob <size>\0" header at the start of an object.
	private void skipHeader(InputStream in, String key) throws IOException {
		byte[] expected = Constants.encodedTypeString(Constants.OBJ_BLOB);
		for (byte b : expected) {
			if (in.read() != (b & 0xff)) {
				throw invalidObject(key);
			}
		}
		if (in.read() != ' ') {
			throw invalidObject(key);
		}
		for (int i = 0; i < MAX_HEADER_LENGTH; i++) {
			int b = in.read();
			if (b == 0) {
				return;
			}
			if (b < '0' || b > '9') {
				throw invalidObject(key);
			}
		}
		throw invalidObject(key);
	}

	// Compute the blob size from the length of a stored object, which is
	// "blob ".length() + digits(size) + 1 + size. Returns -1 if no size
	// matches total.
	static long blobSize(long total) {
		int fixed = Constants.encodedTypeString(Constants.OBJ_BLOB).length
				+ 2;
		for (int digits = 1; digits <= 19; digits++) {
			long size = total - fixed - digits;
			if (size < 0) {
				return -1;
			}
			if (Long.toString(size).length() == digits) {
				return size;
			}
		}
		return -1;
	}
}
