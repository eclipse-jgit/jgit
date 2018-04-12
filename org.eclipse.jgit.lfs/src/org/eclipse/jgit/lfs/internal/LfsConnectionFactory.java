/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com>
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
package org.eclipse.jgit.lfs.internal;

import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SshSupport;

/**
 * Provides means to get a valid LFS connection for a given repository.
 */
public class LfsConnectionFactory {

	private static final int SSH_AUTH_TIMEOUT_SECONDS = 5;
	private static final String SCHEME_HTTPS = "https"; //$NON-NLS-1$
	private static final String SCHEME_SSH = "ssh"; //$NON-NLS-1$
	private static final Map<String, AuthCache> sshAuthCache = new TreeMap<>();

	/**
	 * Determine URL of LFS server by looking into config parameters lfs.url,
	 * lfs.[remote].url or remote.[remote].url. The LFS server URL is computed
	 * from remote.[remote].url by appending "/info/lfs". In case there is no
	 * URL configured, a SSH remote URI can be used to auto-detect the LFS URI
	 * by using the remote "git-lfs-authenticate" command.
	 *
	 * @param db
	 *            the repository to work with
	 * @param method
	 *            the method (GET,PUT,...) of the request this connection will
	 *            be used for
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @return the url for the lfs server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 */
	public static HttpConnection getLfsConnection(Repository db, String method,
			String purpose) throws IOException {
		StoredConfig config = db.getConfig();
		Map<String, String> additionalHeaders = new TreeMap<>();
		String lfsUrl = getLfsUrl(db, purpose, additionalHeaders);
		URL url = new URL(lfsUrl + Protocol.OBJECTS_LFS_ENDPOINT);
		HttpConnection connection = HttpTransport.getConnectionFactory().create(
				url, HttpSupport.proxyFor(ProxySelector.getDefault(), url));
		connection.setDoOutput(true);
		if (url.getProtocol().equals(SCHEME_HTTPS)
				&& !config.getBoolean(HttpConfig.HTTP,
						HttpConfig.SSL_VERIFY_KEY, true)) {
			HttpSupport.disableSslVerify(connection);
		}
		connection.setRequestMethod(method);
		connection.setRequestProperty(HDR_ACCEPT,
				Protocol.CONTENTTYPE_VND_GIT_LFS_JSON);
		connection.setRequestProperty(HDR_CONTENT_TYPE,
				Protocol.CONTENTTYPE_VND_GIT_LFS_JSON);
		additionalHeaders
				.forEach((k, v) -> connection.setRequestProperty(k, v));
		return connection;
	}

	private static String getLfsUrl(Repository db, String purpose,
			Map<String, String> additionalHeaders)
			throws LfsConfigInvalidException {
		StoredConfig config = db.getConfig();
		String lfsUrl = config.getString(ConfigConstants.CONFIG_SECTION_LFS,
				null,
				ConfigConstants.CONFIG_KEY_URL);
		if (lfsUrl == null) {
			String remoteUrl = null;
			for (String remote : db.getRemoteNames()) {
				lfsUrl = config.getString(ConfigConstants.CONFIG_SECTION_LFS,
						remote,
						ConfigConstants.CONFIG_KEY_URL);
				// This could be done better (more precise logic), but according
				// to https://github.com/git-lfs/git-lfs/issues/1759 git-lfs
				// generally only supports 'origin' in an integrated workflow.
				if (lfsUrl == null && (remote.equals(
						org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME))) {
					remoteUrl = config.getString(
							ConfigConstants.CONFIG_KEY_REMOTE, remote,
							ConfigConstants.CONFIG_KEY_URL);
				}
				break;
			}
			if (lfsUrl == null && remoteUrl != null) {
				lfsUrl = discoverLfsUrl(db, purpose, additionalHeaders,
						remoteUrl);
			} else {
				lfsUrl = lfsUrl + Protocol.INFO_LFS_ENDPOINT;
			}
		}
		if (lfsUrl == null) {
			throw new LfsConfigInvalidException(LfsText.get().lfsNoDownloadUrl);
		}
		return lfsUrl;
	}

	private static String discoverLfsUrl(Repository db, String purpose,
			Map<String, String> additionalHeaders, String remoteUrl) {
		try {
			URIish u = new URIish(remoteUrl);
			if (SCHEME_SSH.equals(u.getScheme())) {
				Protocol.ExpiringAction action = getSshAuthentication(
						db, purpose, remoteUrl, u);
				additionalHeaders.putAll(action.header);
				return action.href;
			} else {
				return remoteUrl + Protocol.INFO_LFS_ENDPOINT;
			}
		} catch (Exception e) {
			return null; // could not discover
		}
	}

	private static Protocol.ExpiringAction getSshAuthentication(
			Repository db, String purpose, String remoteUrl, URIish u)
			throws IOException {
		AuthCache cached = sshAuthCache.get(remoteUrl);
		Protocol.ExpiringAction action = null;
		if (cached != null && cached.validUntil > System.currentTimeMillis()) {
			action = cached.cachedAction;
		}

		if (action == null) {
			// discover and authenticate; git-lfs does "ssh
			// -p <port> -- <host> git-lfs-authenticate
			// <project> <upload/download>"
			String json = SshSupport.runSshCommand(u.setPath(""), //$NON-NLS-1$
					null, db.getFS(),
					"git-lfs-authenticate " + extractProjectName(u) + " " //$NON-NLS-1$//$NON-NLS-2$
							+ purpose,
					SSH_AUTH_TIMEOUT_SECONDS);

			action = Protocol.gson().fromJson(json,
					Protocol.ExpiringAction.class);

			// cache the result as long as possible.
			AuthCache c = new AuthCache(action);
			sshAuthCache.put(remoteUrl, c);
		}
		return action;
	}

	/**
	 * Create a connection for the specified
	 * {@link org.eclipse.jgit.lfs.Protocol.Action}.
	 *
	 * @param repo
	 *            the repo to fetch required configuration from
	 * @param action
	 *            the action for which to create a connection
	 * @param method
	 *            the target method (GET or PUT)
	 * @return a connection. output mode is not set.
	 * @throws IOException
	 *             in case of any error.
	 */
	public static @NonNull HttpConnection getLfsContentConnection(
			Repository repo, Protocol.Action action, String method)
			throws IOException {
		URL contentUrl = new URL(action.href);
		HttpConnection contentServerConn = HttpTransport.getConnectionFactory()
				.create(contentUrl, HttpSupport
						.proxyFor(ProxySelector.getDefault(), contentUrl));
		contentServerConn.setRequestMethod(method);
		action.header
				.forEach((k, v) -> contentServerConn.setRequestProperty(k, v));
		if (contentUrl.getProtocol().equals(SCHEME_HTTPS)
				&& !repo.getConfig().getBoolean(HttpConfig.HTTP,
						HttpConfig.SSL_VERIFY_KEY, true)) {
			HttpSupport.disableSslVerify(contentServerConn);
		}

		contentServerConn.setRequestProperty(HDR_ACCEPT_ENCODING,
				ENCODING_GZIP);

		return contentServerConn;
	}

	private static String extractProjectName(URIish u) {
		String path = u.getPath().substring(1);
		if (path.endsWith(org.eclipse.jgit.lib.Constants.DOT_GIT)) {
			return path.substring(0, path.length() - 4);
		} else {
			return path;
		}
	}

	/**
	 * @param operation
	 *            the operation to perform, e.g. Protocol.OPERATION_DOWNLOAD
	 * @param resources
	 *            the LFS resources affected
	 * @return a request that can be serialized to JSON
	 */
	public static Protocol.Request toRequest(String operation,
			LfsPointer... resources) {
		Protocol.Request req = new Protocol.Request();
		req.operation = operation;
		if (resources != null) {
			req.objects = new LinkedList<>();
			for (LfsPointer res : resources) {
				Protocol.ObjectSpec o = new Protocol.ObjectSpec();
				o.oid = res.getOid().getName();
				o.size = res.getSize();
				req.objects.add(o);
			}
		}
		return req;
	}

	private static final class AuthCache {
		private static final long AUTH_CACHE_EAGER_TIMEOUT = 500;

		private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat(
				"yyyy-MM-dd'T'HH:mm:ss.SSSX"); //$NON-NLS-1$

		/**
		 * Creates a cache entry for an authentication response.
		 * <p>
		 * The timeout of the cache token is extracted from the given action. If
		 * no timeout can be determined, the token will be used only once.
		 *
		 * @param action
		 */
		public AuthCache(Protocol.ExpiringAction action) {
			this.cachedAction = action;
			try {
				if (action.expiresIn != null && !action.expiresIn.isEmpty()) {
					this.validUntil = (System.currentTimeMillis()
							+ Long.parseLong(action.expiresIn))
							- AUTH_CACHE_EAGER_TIMEOUT;
				} else if (action.expiresAt != null
						&& !action.expiresAt.isEmpty()) {
					this.validUntil = ISO_FORMAT.parse(action.expiresAt)
							.getTime() - AUTH_CACHE_EAGER_TIMEOUT;
				} else {
					this.validUntil = System.currentTimeMillis();
				}
			} catch (Exception e) {
				this.validUntil = System.currentTimeMillis();
			}
		}

		long validUntil;

		Protocol.ExpiringAction cachedAction;
	}

}
