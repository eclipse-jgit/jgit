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
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.lfs.internal.LfsConnectionHelper;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/**
 * Built-in LFS smudge filter
 *
 * When content is read from git's object-database and written to the filesystem
 * and this filter is configured for that content, then this filter will replace
 * the content of LFS pointer files with the original content. This happens e.g.
 * when a checkout needs to update a working tree file which is under LFS
 * control. This implementation expects that the origin content is already
 * available in the .git/lfs/objects folder. This implementation will not
 * contact any LFS servers in order to get the missing content.
 *
 * @since 4.6
 */
public class SmudgeFilter extends FilterCommand {
	/**
	 * The factory is responsible for creating instances of {@link SmudgeFilter}
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
						+ Constants.ATTR_FILTER_TYPE_SMUDGE, FACTORY);
	}

	private Lfs lfs;

	/**
	 * Instantiate a LFS smudge filter implementation
	 *
	 * @param db
	 *            the associated repository
	 * @param in
	 *            the {@link InputStream} to read the original data (typically
	 *            the content of a lfs pointer file)
	 * @param out
	 *            the {@link OutputStream} into which to write the content
	 * @throws IOException
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
				downloadLfsResource(lfs, db, res);

			}
			this.in = Files.newInputStream(mediaFile);
		}
	}

	/**
	 * Download content which is hosted on a LFS server
	 * 
	 * @param lfs
	 *
	 * @param db
	 *            the repository to work with
	 * @param res
	 *            the objects to download
	 * @return the pathes of all mediafiles which have been downloaded
	 * @throws IOException
	 */
	@SuppressWarnings("boxing")
	public static Set<Path> downloadLfsResource(Lfs lfs, Repository db,
			LfsPointer... res)
			throws IOException {
		Set<Path> downloadedPathes = new HashSet<>();
		Map<String, LfsPointer> oidStr2ptr = new HashMap<>();
		for (LfsPointer p : res) {
			oidStr2ptr.put(p.getOid().name(), p);
		}
		HttpConnection lfsServerConn = LfsConnectionHelper.getLfsConnection(db,
				HttpSupport.METHOD_POST, Protocol.OPERATION_DOWNLOAD);
		Gson gson = new Gson();
		lfsServerConn.getOutputStream()
				.write(gson
						.toJson(LfsConnectionHelper
								.toRequest(Protocol.OPERATION_DOWNLOAD, res))
						.getBytes(StandardCharsets.UTF_8));
		int responseCode = lfsServerConn.getResponseCode();
		if (responseCode != 200) {
			throw new IOException(
					MessageFormat.format(LfsText.get().serverFailure,
							lfsServerConn.getURL(), responseCode));
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
					// received an object we didn't requested
					continue;
				}
				if (ptr.getSize() != o.size) {
					throw new IOException(MessageFormat.format(
							LfsText.get().inconsistentContentLength,
							lfsServerConn.getURL(), ptr.getSize(), o.size));
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
						.getBoolean("http", "sslVerify", true)) { //$NON-NLS-1$ //$NON-NLS-2$
					HttpSupport.disableSslVerify(contentServerConn);
				}
				contentServerConn.setRequestProperty(HDR_ACCEPT_ENCODING,
						ENCODING_GZIP);
				responseCode = contentServerConn.getResponseCode();
				if (responseCode != HttpConnection.HTTP_OK) {
					throw new IOException(
							MessageFormat.format(LfsText.get().serverFailure,
									contentServerConn.getURL(), responseCode));
				}
				Path path = lfs.getMediaFile(ptr.getOid());
				path.getParent().toFile().mkdirs();
				try (InputStream contentIn = contentServerConn
						.getInputStream()) {
					long bytesCopied = Files.copy(contentIn, path);
					if (bytesCopied != o.size) {
						throw new IOException(MessageFormat.format(
								LfsText.get().wrongAmoutOfDataReceived,
								contentServerConn.getURL(), bytesCopied,
								o.size));
					}
					downloadedPathes.add(path);
				}
			}
		}
		return downloadedPathes;
	}

	@Override
	public int run() throws IOException {
		int length = 0;
		if (in != null) {
			byte[] buf = new byte[8192];
			while ((length = in.read(buf)) != -1) {
				out.write(buf, 0, length);
			}
			in.close();
		}
		out.close();
		return length;
	}

}
