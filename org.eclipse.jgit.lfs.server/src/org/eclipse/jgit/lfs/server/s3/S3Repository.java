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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.eclipse.jgit.lfs.server.s3.SignerV4.UNSIGNED_PAYLOAD;
import static org.eclipse.jgit.lfs.server.s3.SignerV4.X_AMZ_CONTENT_SHA256;
import static org.eclipse.jgit.lfs.server.s3.SignerV4.X_AMZ_EXPIRES;
import static org.eclipse.jgit.lfs.server.s3.SignerV4.X_AMZ_STORAGE_CLASS;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_LENGTH;
import static org.eclipse.jgit.util.HttpSupport.METHOD_GET;
import static org.eclipse.jgit.util.HttpSupport.METHOD_HEAD;
import static org.eclipse.jgit.util.HttpSupport.METHOD_PUT;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.Response;
import org.eclipse.jgit.lfs.server.Response.Action;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.util.HttpSupport;

/**
 * Repository storing LFS objects in Amazon S3
 *
 * @since 4.3
 */
public class S3Repository implements LargeFileRepository {

	private S3Config s3Config;

	/**
	 * Construct a LFS repository storing large objects in Amazon S3
	 *
	 * @param config
	 *            AWS S3 storage bucket configuration
	 */
	public S3Repository(S3Config config) {
		validateConfig(config);
		this.s3Config = config;
	}

	@Override
	public Response.Action getDownloadAction(AnyLongObjectId oid) {
		URL endpointUrl = getObjectUrl(oid);
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put(X_AMZ_EXPIRES,
				Integer.toString(s3Config.getExpirationSeconds()));
		Map<String, String> headers = new HashMap<>();
		String authorizationQueryParameters = SignerV4.createAuthorizationQuery(
				s3Config, endpointUrl, METHOD_GET, headers, queryParams,
				UNSIGNED_PAYLOAD);

		Response.Action a = new Response.Action();
		a.href = endpointUrl.toString() + "?" + authorizationQueryParameters; //$NON-NLS-1$
		return a;
	}

	@Override
	public Response.Action getUploadAction(AnyLongObjectId oid, long size) {
		cacheObjectMetaData(oid, size);
		URL objectUrl = getObjectUrl(oid);
		Map<String, String> headers = new HashMap<>();
		headers.put(X_AMZ_CONTENT_SHA256, oid.getName());
		headers.put(HDR_CONTENT_LENGTH, Long.toString(size));
		headers.put(X_AMZ_STORAGE_CLASS, s3Config.getStorageClass());
		headers.put(HttpSupport.HDR_CONTENT_TYPE, "application/octet-stream"); //$NON-NLS-1$
		headers = SignerV4.createHeaderAuthorization(s3Config, objectUrl,
				METHOD_PUT, headers, oid.getName());

		Response.Action a = new Response.Action();
		a.href = objectUrl.toString();
		a.header = new HashMap<>();
		a.header.putAll(headers);
		return a;
	}

	@Override
	public Action getVerifyAction(AnyLongObjectId id) {
		return null; // TODO(ms) implement this
	}

	@Override
	public long getSize(AnyLongObjectId oid) throws IOException {
		URL endpointUrl = getObjectUrl(oid);
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put(X_AMZ_EXPIRES,
				Integer.toString(s3Config.getExpirationSeconds()));
		Map<String, String> headers = new HashMap<>();

		String authorizationQueryParameters = SignerV4.createAuthorizationQuery(
				s3Config, endpointUrl, METHOD_HEAD, headers, queryParams,
				UNSIGNED_PAYLOAD);
		String href = endpointUrl.toString() + "?" //$NON-NLS-1$
				+ authorizationQueryParameters;

		Proxy proxy = HttpSupport.proxyFor(ProxySelector.getDefault(),
				endpointUrl);
		HttpClientConnectionFactory f = new HttpClientConnectionFactory();
		HttpConnection conn = f.create(new URL(href), proxy);
		if (s3Config.isDisableSslVerify()) {
			HttpSupport.disableSslVerify(conn);
		}
		conn.setRequestMethod(METHOD_HEAD);
		conn.connect();
		int status = conn.getResponseCode();
		if (status == SC_OK) {
			String contentLengthHeader = conn
					.getHeaderField(HDR_CONTENT_LENGTH);
			if (contentLengthHeader != null) {
				return Integer.parseInt(contentLengthHeader);
			}
		}
		return -1;
	}

	/**
	 * Cache metadata (size) for an object to avoid extra roundtrip to S3 in
	 * order to retrieve this metadata for a given object. Subclasses can
	 * implement a local cache and override {{@link #getSize(AnyLongObjectId)}
	 * to retrieve the object size from the local cache to eliminate the need
	 * for another roundtrip to S3
	 *
	 * @param oid
	 *            the object id identifying the object to be cached
	 * @param size
	 *            the object's size (in bytes)
	 */
	protected void cacheObjectMetaData(AnyLongObjectId oid, long size) {
		// no caching
	}

	private void validateConfig(S3Config config) {
		assertNotEmpty(LfsServerText.get().undefinedS3AccessKey,
				config.getAccessKey());
		assertNotEmpty(LfsServerText.get().undefinedS3Bucket,
				config.getBucket());
		assertNotEmpty(LfsServerText.get().undefinedS3Region,
				config.getRegion());
		assertNotEmpty(LfsServerText.get().undefinedS3SecretKey,
				config.getSecretKey());
		assertNotEmpty(LfsServerText.get().undefinedS3StorageClass,
				config.getStorageClass());
	}

	private void assertNotEmpty(String message, String value) {
		if (value == null || value.trim().length() == 0) {
			throw new IllegalArgumentException(message);
		}
	}

	private URL getObjectUrl(AnyLongObjectId oid) {
		try {
			return new URL(String.format("https://s3-%s.amazonaws.com/%s/%s", //$NON-NLS-1$
					s3Config.getRegion(), s3Config.getBucket(),
					getPath(oid)));
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(MessageFormat.format(
					LfsServerText.get().unparsableEndpoint, e.getMessage()));
		}
	}

	private String getPath(AnyLongObjectId oid) {
		return oid.getName();
	}
}
