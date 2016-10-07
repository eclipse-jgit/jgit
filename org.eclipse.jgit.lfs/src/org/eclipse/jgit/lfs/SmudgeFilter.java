/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lfs;

import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.StreamCopyThread;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/**
 * Built-in LFS smudge filter
 *
 * When content is read from git's object-database and written to the filesystem
 * and this filter is configured for that content, then this filter will replace
 * the content of LFS pointer files with the original content. This happens e.g.
 * when a checkout needs to update a working tree file which is under LFS
 * control.
 *
 * @since 4.6
 */
public class SmudgeFilter extends FilterCommand {

	/**
	 * Max number of bytes to copy in a single {@link #run()} call.
	 */
	private static final int MAX_COPY_BYTES = 1024 * 1024 * 256;

	/**
	 * The factory is responsible for creating instances of
	 * {@link org.eclipse.jgit.lfs.SmudgeFilter}
	 */
	public final static FilterCommandFactory FACTORY = new FilterCommandFactory() {
		@Override
		public FilterCommand create(Repository db, InputStream in,
				OutputStream out) throws IOException {
			return new SmudgeFilter(db, in, out);
		}
	};

	/**
	 * Registers this filter in JGit by calling
	 */
	public final static void register() {
		FilterCommandRegistry
				.register(org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
						+ Constants.ATTR_FILTER_DRIVER_PREFIX
						+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_SMUDGE,
						FACTORY);
	}

	private Lfs lfs;

	/**
	 * Constructor for SmudgeFilter.
	 *
	 * @param db
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param in
	 *            a {@link java.io.InputStream} object.
	 * @param out
	 *            a {@link java.io.OutputStream} object.
	 * @throws java.io.IOException
	 */
	public SmudgeFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		super(in, out);
		lfs = new Lfs(db);
		LfsPointer res = LfsPointer.parseLfsPointer(in);
		if (res != null) {
			AnyLongObjectId oid = res.getOid();
			Path mediaFile = lfs.getMediaFile(oid);
			if (!Files.exists(mediaFile)) {
				downloadLfsResource(db, res);
			}
			this.in = Files.newInputStream(mediaFile);
		}
	}

	/**
	 * Download content which is hosted on a LFS server
	 *
	 * @param db
	 *            the repository to work with
	 * @param res
	 *            the objects to download
	 * @return the paths of all mediafiles which have been downloaded
	 * @throws IOException
	 */
	private Collection<Path> downloadLfsResource(Repository db,
			LfsPointer... res)
			throws IOException {
		Collection<Path> downloadedPaths = new ArrayList<>();
		Map<String, LfsPointer> oidStr2ptr = new HashMap<>();
		for (LfsPointer p : res) {
			oidStr2ptr.put(p.getOid().name(), p);
		}
		HttpConnection lfsServerConn = getLfsConnection(db,
				HttpSupport.METHOD_POST);
		Gson gson = new Gson();
		lfsServerConn.getOutputStream()
				.write(gson.toJson(body(res)).getBytes(StandardCharsets.UTF_8));
		int responseCode = lfsServerConn.getResponseCode();
		if (responseCode != HttpConnection.HTTP_OK) {
			throw new IOException(
					MessageFormat.format(LfsText.get().serverFailure,
							lfsServerConn.getURL(),
							Integer.valueOf(responseCode)));
		}
		try (JsonReader reader = new JsonReader(
				new InputStreamReader(lfsServerConn.getInputStream()))) {
			Protocol.Response resp = gson.fromJson(reader,
					Protocol.Response.class);
			for (Protocol.ObjectInfo o : resp.objects) {
				if (o.actions == null) {
					continue;
				}
				LfsPointer ptr = oidStr2ptr.get(o.oid);
				if (ptr == null) {
					// received an object we didn't request
					continue;
				}
				if (ptr.getSize() != o.size) {
					throw new IOException(MessageFormat.format(
							LfsText.get().inconsistentContentLength,
							lfsServerConn.getURL(), Long.valueOf(ptr.getSize()),
							Long.valueOf(o.size)));
				}
				Protocol.Action downloadAction = o.actions
						.get(Protocol.OPERATION_DOWNLOAD);
				if (downloadAction == null || downloadAction.href == null) {
					continue;
				}
				URL contentUrl = new URL(downloadAction.href);
				HttpConnection contentServerConn = HttpTransport
						.getConnectionFactory().create(contentUrl,
								HttpSupport.proxyFor(ProxySelector.getDefault(),
										contentUrl));
				contentServerConn.setRequestMethod(HttpSupport.METHOD_GET);
				downloadAction.header.forEach(
						(k, v) -> contentServerConn.setRequestProperty(k, v));
				if (contentUrl.getProtocol().equals("https") && !db.getConfig() //$NON-NLS-1$
						.getBoolean(HttpConfig.HTTP, HttpConfig.SSL_VERIFY_KEY,
								true)) {
					HttpSupport.disableSslVerify(contentServerConn);
				}
				contentServerConn.setRequestProperty(HDR_ACCEPT_ENCODING,
						ENCODING_GZIP);
				responseCode = contentServerConn.getResponseCode();
				if (responseCode != HttpConnection.HTTP_OK) {
					throw new IOException(
							MessageFormat.format(LfsText.get().serverFailure,
									contentServerConn.getURL(),
									Integer.valueOf(responseCode)));
				}
				Path path = lfs.getMediaFile(ptr.getOid());
				path.getParent().toFile().mkdirs();
				try (InputStream contentIn = contentServerConn
						.getInputStream()) {
					long bytesCopied = Files.copy(contentIn, path);
					if (bytesCopied != o.size) {
						throw new IOException(MessageFormat.format(
								LfsText.get().wrongAmoutOfDataReceived,
								contentServerConn.getURL(),
								Long.valueOf(bytesCopied),
								Long.valueOf(o.size)));
					}
					downloadedPaths.add(path);
				}
			}
		}
		return downloadedPaths;
	}

	private Protocol.Request body(LfsPointer... resources) {
		Protocol.Request req = new Protocol.Request();
		req.operation = Protocol.OPERATION_DOWNLOAD;
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

	/**
	 * Determine URL of LFS server by looking into config parameters lfs.url,
	 * lfs.<remote>.url or remote.<remote>.url. The LFS server URL is computed
	 * from remote.<remote>.url by appending "/info/lfs"
	 *
	 * @param db
	 *            the repository to work with
	 * @param method
	 *            the method (GET,PUT,...) of the request this connection will
	 *            be used for
	 * @return the url for the lfs server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 */
	private HttpConnection getLfsConnection(Repository db, String method)
			throws IOException {
		StoredConfig config = db.getConfig();
		String lfsEndpoint = config.getString(Constants.LFS, null,
				ConfigConstants.CONFIG_KEY_URL);
		Map<String, String> additionalHeaders = new TreeMap<>();
		if (lfsEndpoint == null) {
			String remoteUrl = null;
			for (String remote : db.getRemoteNames()) {
				lfsEndpoint = config.getString(Constants.LFS, remote,
						ConfigConstants.CONFIG_KEY_URL);
				if (lfsEndpoint == null
						&& (remote.equals(
								org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME))) {
					remoteUrl = config.getString(
							ConfigConstants.CONFIG_KEY_REMOTE, remote,
							ConfigConstants.CONFIG_KEY_URL);
				}
				break;
			}
			if (lfsEndpoint == null && remoteUrl != null) {
				try {
					URIish u = new URIish(remoteUrl);

					if ("ssh".equals(u.getScheme())) { //$NON-NLS-1$
						// discover and authenticate; git-lfs does "ssh -p
						// <port> -- <host> git-lfs-authenticate <project>
						// <upload/download>"
						String json = runSshCommand(u.setPath(""), db.getFS(), //$NON-NLS-1$
								"git-lfs-authenticate " + extractProjectName(u) //$NON-NLS-1$
										+ " " + Protocol.OPERATION_DOWNLOAD); //$NON-NLS-1$

						Protocol.Action action = new Gson().fromJson(json,
								Protocol.Action.class);
						additionalHeaders.putAll(action.header);
						lfsEndpoint = action.href;
					} else {
						lfsEndpoint = remoteUrl + Protocol.INFO_LFS_ENDPOINT;
					}
				} catch (Exception e) {
					lfsEndpoint = null; // could not discover
				}
			} else {
				lfsEndpoint = lfsEndpoint + Protocol.INFO_LFS_ENDPOINT;
			}
		}
		if (lfsEndpoint == null) {
			throw new LfsConfigInvalidException(LfsText.get().lfsNoDownloadUrl);
		}
		URL url = new URL(lfsEndpoint + Protocol.OBJECTS_LFS_ENDPOINT);
		HttpConnection connection = HttpTransport.getConnectionFactory().create(
				url, HttpSupport.proxyFor(ProxySelector.getDefault(), url));
		connection.setDoOutput(true);
		if (url.getProtocol().equals("https") //$NON-NLS-1$
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

	private String extractProjectName(URIish u) {
		String path = u.getPath().substring(1);
		if (path.endsWith(org.eclipse.jgit.lib.Constants.DOT_GIT)) {
			return path.substring(0, path.length() - 4);
		} else {
			return path;
		}
	}

	private String runSshCommand(URIish sshUri, FS fs, String command)
			throws IOException {
		RemoteSession session = null;
		Process process = null;
		StreamCopyThread errorThread = null;
		try (MessageWriter stderr = new MessageWriter()) {
			session = SshSessionFactory.getInstance().getSession(sshUri,
					null, fs, 5_000);
			process = session.exec(command, 0);
			errorThread = new StreamCopyThread(process.getErrorStream(),
					stderr.getRawStream());
			errorThread.start();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(),
							org.eclipse.jgit.lib.Constants.CHARSET))) {
				return reader.readLine();
			}
		} finally {
			if (process != null) {
				process.destroy();
			}
			if (errorThread != null) {
				try {
					errorThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorThread = null;
				}
			}
			if (session != null) {
				SshSessionFactory.getInstance().releaseSession(session);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public int run() throws IOException {
		int totalRead = 0;
		int length = 0;
		if (in != null) {
			byte[] buf = new byte[8192];
			while ((length = in.read(buf)) != -1) {
				out.write(buf, 0, length);
				totalRead += length;

				// when threshold reached, loop back to the caller. otherwise we
				// could only support files up to 2GB (int return type)
				// properly. we will be called again as long as we don't return
				// -1 here.
				if (totalRead >= MAX_COPY_BYTES) {
					// leave streams open - we need them in the next call.
					return totalRead;
				}
			}
		}

		if (totalRead == 0 && length == -1) {
			// we're totally done :)
			in.close();
			out.close();
			return length;
		}

		return totalRead;
	}

}
