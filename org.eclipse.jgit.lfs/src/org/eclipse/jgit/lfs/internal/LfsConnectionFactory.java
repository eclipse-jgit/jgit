/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.internal;

import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SshSupport;

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
				null, ConfigConstants.CONFIG_KEY_URL);
		if (lfsUrl == null) {
			/*
			 * @formatter:off
			 *
			 * Try to get url from lfsconfig if not available in other git
			 * config files.
			 * See https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-config.5.ronn#configuration-files
			 *
			 * @formatter:on
			 */
			lfsUrl = getLfsConfig(db).getString(
					ConfigConstants.CONFIG_SECTION_LFS,
					null, ConfigConstants.CONFIG_KEY_URL);
		}
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
			} else {
				lfsUrl = lfsUrl + Protocol.INFO_LFS_ENDPOINT;
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

	/**
	 * @formatter:off
	 *
	 * Read the .lfsconfig file from the repository
	 *
	 * According to the document
	 * https://github.com/git-lfs/git-lfs/blob/main/docs/man/git-lfs-config.5.ronn
	 * the order to find the .lfsconfig file is:
	 * 1. in the root of the working tree
	 * 2. in the index
	 * 3. in the HEAD (For bare repositories this is the only place that is searched)
	 *
	 * @param db
	 *            the associated repo
	 * @return The loaded lfs config or null if it does not exist
	 *
	 * @throws LfsConfigInvalidException
	 *
	 * @formatter:on
	 */
	private static Config getLfsConfig(Repository db)
			throws LfsConfigInvalidException {
		if (!db.isBare()) {
			/* Search in Working Tree */
			File lfsConfig = db.getFS().resolve(db.getWorkTree(),
					Constants.DOT_LFS_CONFIG);
			/* If config file exists, create a file based config for it */
			if (lfsConfig.exists() && lfsConfig.isFile()) {
				FileBasedConfig config = new FileBasedConfig(lfsConfig,
						db.getFS());
				try {
					config.load();
					return config;
				} catch (ConfigInvalidException | IOException e) {
					/*
					 * .lfsonfig file is present, but reading failed anyway.
					 * Seems to be a real error, e.g. invalid config file
					 * syntax.
					 */
					throw new LfsConfigInvalidException(
							LfsText.get().dotLfsConfigReadFailed, e);
				}
			}

			/* Search in Index */
			try {
				DirCacheEntry Entry = db.readDirCache()
						.getEntry(Constants.DOT_LFS_CONFIG);
				if (Entry != null) {
					return new BlobBasedConfig(null, db, Entry.getObjectId());
				}
			} catch (NoWorkTreeException | IOException
					| ConfigInvalidException e) {
				/*
				 * Entry for .lfsonfig file is exists, but reading failed
				 * anyway. Seems to be a real error, e.g. invalid config file
				 * syntax.
				 */
				throw new LfsConfigInvalidException(
						LfsText.get().dotLfsConfigReadFailed, e);
			}
		}

		/* Search in HEAD Revision */
		try (RevWalk revWalk = new RevWalk(db)) {
			ObjectId headCommitId = db
					.resolve(org.eclipse.jgit.lib.Constants.HEAD);
			RevCommit commit = revWalk.parseCommit(headCommitId);
			RevTree tree = commit.getTree();
			TreeWalk treewalk = TreeWalk.forPath(db, Constants.DOT_LFS_CONFIG,
					tree);
			if (treewalk != null) {
				return new BlobBasedConfig(null, db, treewalk.getObjectId(0));
			}
		} catch (RevisionSyntaxException | IOException
				| ConfigInvalidException e) {
			/*
			 * Entry for .lfsonfig file is exists, but reading failed anyway.
			 * Seems to be a real error, e.g. invalid config file syntax.
			 */
			throw new LfsConfigInvalidException(
					LfsText.get().dotLfsConfigReadFailed, e);
		}

		/* Create empty config to avoid null pointer handling */
		return new Config();
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
		return remoteUrl + Protocol.INFO_LFS_ENDPOINT;
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
