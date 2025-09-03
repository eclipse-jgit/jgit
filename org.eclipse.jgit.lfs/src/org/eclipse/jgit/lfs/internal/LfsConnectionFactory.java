/*
 * Copyright (C) 2017, 2022 Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.internal;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_LFSDEFAULT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_LFSPUSHDEFAULT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_LFSPUSHURL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_LFSURL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PUSHURL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PUSH_DEFAULT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_REMOTE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_SECTION_LFS;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SshSupport;
import org.eclipse.jgit.util.StringUtils;

/**
 * Provides means to get a valid LFS connection for a given repository.
 */
public class LfsConnectionFactory {
	private static final int SSH_AUTH_TIMEOUT_SECONDS = 30;
	private static final String SCHEME_HTTPS = "https"; //$NON-NLS-1$
	private static final String SCHEME_SSH = "ssh"; //$NON-NLS-1$
	private static final Map<String, AuthCache> sshAuthCache = new TreeMap<>();

	/**
	 * Determine URL of LFS server by looking into config parameters lfs.url,
	 * remote.[remote].lfsurl or remote.[remote].url. The LFS server URL is
	 * computed from remote.[remote].url by appending "/info/lfs". In case there
	 * is no URL configured, a SSH remote URI can be used to auto-detect the LFS
	 * URI by using the remote "git-lfs-authenticate" command.
	 *
	 * @param db
	 *            the repository to work with
	 * @param method
	 *            the method (GET,PUT,...) of the request this connection will
	 *            be used for
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @param remoteName
	 *            name of explicit remote or {@code null}
	 * @return the connection for the lfs server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 *             if an IO error occurred
	 */
	public static HttpConnection getLfsConnection(Repository db, String method,
			String purpose, String remoteName) throws IOException {
		StoredConfig config = db.getConfig();
		Map<String, String> additionalHeaders = new TreeMap<>();
		String lfsUrl = getLfsUrl(db, purpose, remoteName, additionalHeaders);
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

	/**
	 * Determine URL of LFS server by looking into config parameters lfs.url,
	 * remote.[remote].lfsurl or remote.[remote].url. The LFS server URL is
	 * computed from remote.[remote].url by appending "/info/lfs". In case there
	 * is no URL configured, a SSH remote URI can be used to auto-detect the LFS
	 * URI by using the remote "git-lfs-authenticate" command.
	 *
	 * @param db
	 *            the repository to work with
	 * @param method
	 *            the method (GET,PUT,...) of the request this connection will
	 *            be used for
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @return the connection for the lfs server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 *             if an IO error occurred
	 */
	public static HttpConnection getLfsConnection(Repository db, String method,
			String purpose) throws IOException {
		return getLfsConnection(db, method, purpose, null);
	}

	/**
	 * Get LFS Server URL.
	 *
	 * <ol>
	 * <li>URL from config {@code lfs.pushurl} (upload only) or
	 * {@code lfs.url}</li>
	 * <li>remote specific URL<br/>
	 * select remote:
	 * <ol>
	 * <li>explicit/current remote</li>
	 * <li>remote specified by config {@code remote.lfspushdefault} (upload
	 * only), {@code remote.pushDefault} (upload only) or
	 * {@code remote.lfsdefault}</li>
	 * <li>single remote
	 * <li>default remote 'origin'</li>
	 * </ol>
	 * remote -> URL:
	 * <ol>
	 * <li>URL from config {@code remote.<remote>.lfspushurl} (upload only) or
	 * {@code remote.<remote>.lfsurl}</li>
	 * <li>derive URL from {@code remote.<remote>.pushurl} (upload only) or
	 * {@code remote.<remote>.url}</li>
	 * </ol>
	 * </li>
	 * </ol>
	 *
	 * @param db
	 *            the repository to work with
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @param remote
	 *            explicit remote or {@code null}
	 * @param additionalHeaders
	 *            additional headers that can be used to connect to LFS server
	 * @return the URL for the LFS server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 *             if the LFS config is invalid or cannot be accessed
	 * @see <a href=
	 *      "https://github.com/git-lfs/git-lfs/blob/main/docs/api/server-discovery.md">
	 *      Server Discovery documentation</a>
	 * @see <a href=
	 *      "https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-config.adoc">
	 *      git-lfs-config - Configuration options for git-lfs</a>
	 */
	private static String getLfsUrl(Repository db, String purpose,
			String remote, Map<String, String> additionalHeaders)
			throws IOException {
		LfsConfig config = new LfsConfig(db);
		String lfsUrl = null;
		if (purpose.equals(Protocol.OPERATION_UPLOAD)) {
			lfsUrl = config.getString(CONFIG_SECTION_LFS, null,
					CONFIG_KEY_PUSHURL);
		}
		if (lfsUrl == null) {
			lfsUrl = config.getString(CONFIG_SECTION_LFS, null,
					CONFIG_KEY_URL);
		}

		if (lfsUrl == null) {
			if (remote == null) {
				remote = getLfsRemote(db, purpose, config);
			}
			if (remote != null) {
				if (purpose.equals(Protocol.OPERATION_UPLOAD)) {
					lfsUrl = config.getString(CONFIG_REMOTE_SECTION, remote,
							CONFIG_KEY_LFSPUSHURL);
				}
				if (lfsUrl == null) {
					lfsUrl = config.getString(CONFIG_REMOTE_SECTION, remote,
							CONFIG_KEY_LFSURL);
					if (lfsUrl == null) {
						// old config 'lfs.<remote>.url' of jgit
						lfsUrl = config.getString(CONFIG_SECTION_LFS, remote,
								CONFIG_KEY_URL);
					}
				}
				if (lfsUrl == null) {
					String remoteUrl = null;
					if (purpose.equals(Protocol.OPERATION_UPLOAD)) {
						remoteUrl = config.getString(CONFIG_REMOTE_SECTION,
								remote, CONFIG_KEY_PUSHURL);
					}
					if (remoteUrl == null) {
						remoteUrl = config.getString(CONFIG_REMOTE_SECTION,
								remote, CONFIG_KEY_URL);
					}
					if (remoteUrl != null) {
						try {
							lfsUrl = discoverLfsUrl(db, purpose,
									additionalHeaders, remoteUrl);
						} catch (URISyntaxException | IOException
								| CommandFailedException e) {
							throw new LfsConfigInvalidException(
									LfsText.get().lfsNoDownloadUrl, e);
						}
					}
				}
			}
		}
		if (lfsUrl == null) {
			throw new LfsConfigInvalidException(LfsText.get().lfsNoDownloadUrl);
		}
		return lfsUrl;
	}

	private static @Nullable String getLfsRemote(Repository db, String purpose,
			LfsConfig config) throws IOException {
		String remote = null;
		if (purpose.equals(Protocol.OPERATION_UPLOAD)) {
			remote = config.getString(CONFIG_REMOTE_SECTION, null,
					CONFIG_KEY_LFSPUSHDEFAULT);
			if (remote == null) {
				remote = config.getString(CONFIG_REMOTE_SECTION, null,
						CONFIG_KEY_PUSH_DEFAULT);
			}
		}
		if (remote == null) {
			remote = config.getString(CONFIG_REMOTE_SECTION, null,
					CONFIG_KEY_LFSDEFAULT);
		}
		Set<String> remotes = db.getRemoteNames();
		if (remote == null || !remotes.contains(remote)) {
			if (remotes.size() == 1) {
				remote = remotes.iterator().next();
			} else if (remotes.contains(DEFAULT_REMOTE_NAME)) {
				remote = DEFAULT_REMOTE_NAME;
			}
		}
		return remote;
	}

	private static String discoverLfsUrl(Repository db, String purpose,
			Map<String, String> additionalHeaders, String remoteUrl)
			throws URISyntaxException, IOException, CommandFailedException {
		URIish u = new URIish(remoteUrl);
		if (u.getScheme() == null || SCHEME_SSH.equals(u.getScheme())) {
			Protocol.ExpiringAction action = getSshAuthentication(db, purpose,
					remoteUrl, u);
			additionalHeaders.putAll(action.header);
			return action.href;
		}
		return StringUtils.nameWithDotGit(remoteUrl)
				+ Protocol.INFO_LFS_ENDPOINT;
	}

	private static Protocol.ExpiringAction getSshAuthentication(
			Repository db, String purpose, String remoteUrl, URIish u)
			throws IOException, CommandFailedException {
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
	@NonNull
	public static HttpConnection getLfsContentConnection(
			Repository repo, Protocol.Action action, String method)
			throws IOException {
		URL contentUrl = new URL(action.href);
		HttpConnection contentServerConn = HttpTransport.getConnectionFactory()
				.create(contentUrl, HttpSupport
						.proxyFor(ProxySelector.getDefault(), contentUrl));
		contentServerConn.setRequestMethod(method);
		if (action.header != null) {
			action.header.forEach(
					(k, v) -> contentServerConn.setRequestProperty(k, v));
		}
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
		String path = u.getPath();

		// begins with a slash if the url contains a port (gerrit vs. github).
		if (path.startsWith("/")) { //$NON-NLS-1$
			path = path.substring(1);
		}

		if (path.endsWith(org.eclipse.jgit.lib.Constants.DOT_GIT)) {
			return path.substring(0, path.length() - 4);
		}
		return path;
	}

	/**
	 * Create request that can be serialized to JSON
	 *
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
			req.objects = new ArrayList<>();
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

		private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter
				.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"); //$NON-NLS-1$

		/**
		 * Creates a cache entry for an authentication response.
		 * <p>
		 * The timeout of the cache token is extracted from the given action. If
		 * no timeout can be determined, the token will be used only once.
		 *
		 * @param action
		 *            action with an additional expiration timestamp
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
					this.validUntil = LocalDateTime
							.parse(action.expiresAt, ISO_FORMAT)
							.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
							- AUTH_CACHE_EAGER_TIMEOUT;
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
