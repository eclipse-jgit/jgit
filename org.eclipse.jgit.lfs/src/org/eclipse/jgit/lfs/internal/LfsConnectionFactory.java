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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.LfsFactory;
import org.eclipse.jgit.util.SshSupport;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides means to get a valid LFS connection for a given repository.
 */
public class LfsConnectionFactory {
	private static final Logger LOG = LoggerFactory
			.getLogger(LfsConnectionFactory.class);

	private static final int SSH_AUTH_TIMEOUT_SECONDS = 30;
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
	 * @return the connection for the lfs server. e.g.
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

		if (!authorizeFromUri(connection, lfsUrl)) {
			authorizeFromCredentialsProvider(connection);
		}

		return connection;
	}

	private static void authorizeFromCredentialsProvider(HttpConnection connection) {
		CredentialsProvider provider = LfsFactory.getCredentialsProvider();

		if (provider == null) {
			return;
		}

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password p = new CredentialItem.Password();

		final URIish uri = new URIish(connection.getURL());
		provider.get(uri, u);
		provider.get(uri, p);

		if (provider.supports(u, p) && provider.get(uri, u, p)) {
			String username;
			String password;
			username = u.getValue();
			char[] v = p.getValue();
			password = (v == null) ? null : new String(v);
			p.clear();

			authorizeConnection(connection, username, password);
		}
	}

	private static boolean authorizeFromUri(HttpConnection connection, String lfsUrl) {
		try {
			URIish uri = new URIish(lfsUrl);
			String user = uri.getUser();
			String pass = uri.getPass();

			if (user != null && pass != null) {
				// User/password are _not_ application/x-www-form-urlencoded. In
				// particular the "+" sign would be replaced by a space.
				user = URLDecoder.decode(user.replace("+", "%2B"), //$NON-NLS-1$ //$NON-NLS-2$
						StandardCharsets.UTF_8.name());
				pass = URLDecoder.decode(pass.replace("+", "%2B"), //$NON-NLS-1$ //$NON-NLS-2$
						StandardCharsets.UTF_8.name());

				authorizeConnection(connection, user, pass);

				return true;
			}
		} catch (URISyntaxException | UnsupportedEncodingException e) {
			LOG.warn(JGitText.get().httpUserInfoDecodeError, lfsUrl);
		}

		return false;
	}

	private static void authorizeConnection(HttpConnection connection, String username, String password) {
		String ident = username + ":" + password; //$NON-NLS-1$
		String enc = Base64.encodeBytes(ident.getBytes(UTF_8));
		connection.setRequestProperty(HDR_AUTHORIZATION, "Basic " + enc); //$NON-NLS-1$
	}

	/**
	 * Get LFS Server URL.
	 *
	 * @param db
	 *            the repository to work with
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @param additionalHeaders
	 *            additional headers that can be used to connect to LFS server
	 * @return the URL for the LFS server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws LfsConfigInvalidException
	 *             if the LFS config is invalid
	 * @see <a href=
	 *      "https://github.com/git-lfs/git-lfs/blob/main/docs/api/server-discovery.md">
	 *      Server Discovery documentation</a>
	 */
	static String getLfsUrl(Repository db, String purpose,
			Map<String, String> additionalHeaders)
			throws LfsConfigInvalidException {
		StoredConfig config = db.getConfig();
		String lfsUrl = config.getString(ConfigConstants.CONFIG_SECTION_LFS,
				null,
				ConfigConstants.CONFIG_KEY_URL);
		Exception ex = null;
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
					break;
				}
			}
			if (lfsUrl == null && remoteUrl != null) {
				try {
					lfsUrl = discoverLfsUrl(db, purpose, additionalHeaders,
							remoteUrl);
				} catch (URISyntaxException | IOException
						| CommandFailedException e) {
					ex = e;
				}
			}
		}
		if (lfsUrl == null) {
			if (ex != null) {
				throw new LfsConfigInvalidException(
						LfsText.get().lfsNoDownloadUrl, ex);
			}
			throw new LfsConfigInvalidException(LfsText.get().lfsNoDownloadUrl);
		}
		return lfsUrl;
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

		private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter
				.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"); //$NON-NLS-1$

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
