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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lfs.errors.LfsTransportException;
import org.eclipse.jgit.lfs.internal.AtomicObjectOutputStream;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.util.FilterCommand;
import org.eclipse.jgit.util.FilterCommandFactory;
import org.eclipse.jgit.util.HttpSupport;

/**
 * Built-in LFS smudge filter
 *
 * When content is read from git's object-database and written to the filesystem
 * and this filter is configured for that content, then this filter will replace
 * the content of LFS pointer files with the original content. This happens e.g.
 * when a checkout needs to update a working tree file which is under LFS
 * control. This implementation expects that the origin content is already
 * available in the .git/lfs/objects folder. This implementation will not
 * contact any lfs servers in order to get the missing content.
 *
 * @since 4.5
 */
public class SmudgeFilter extends FilterCommand {
	/**
	 * The factory is responsible for creating instances of {@link SmudgeFilter}
	 * . This factory can be registered using
	 * {@link Repository#registerCommand(String, FilterCommandFactory)}
	 */
	public final static FilterCommandFactory FACTORY = new FilterCommandFactory() {
		@Override
		public FilterCommand create(Repository db, InputStream in,
				OutputStream out) throws IOException {
			return new SmudgeFilter(db, in, out);
		}
	};

	/**
	 * Registers this filter to JGit by calling
	 * {@link Repository#registerCommand(String, FilterCommandFactory)}
	 */
	public final static void register() {
		Repository.registerCommand(
				org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
						+ "lfs/smudge", //$NON-NLS-1$
				FACTORY);
	}

	private LfsUtil lfsUtil;

	/**
	 * @param db
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public SmudgeFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		super(in, out);
		lfsUtil = new LfsUtil(db.getDirectory().toPath().resolve("lfs")); //$NON-NLS-1$
		LfsPointer ptr = LfsPointer.parseLfsPointer(in);
		if (ptr != null) {
			Path mediaFile = lfsUtil.getMediaFile(ptr.getOid());
			if (!Files.exists(mediaFile)) {
				downloadLfsObject(db, ptr.getOid());
			}
			this.in = Files.newInputStream(mediaFile);
		}
	}

	private void downloadLfsObject(Repository db, LongObjectId longObjectId)
			throws LfsTransportException {
		HttpConnection connection;
		try {
			connection = getLfsServerConnection(db, longObjectId);
			connection.setRequestMethod(HttpSupport.METHOD_GET);
			int rc = connection.getResponseCode();
			if (rc != HttpURLConnection.HTTP_OK) {
				throw new LfsTransportException("LFS server returned rc=" + rc); //$NON-NLS-1$
			}
			InputStream inputStream = connection.getInputStream();

			try (AtomicObjectOutputStream aoos = new AtomicObjectOutputStream(
					lfsUtil.getMediaFile(longObjectId), longObjectId)) {
				// TODO: use Channels
				byte buffer[] = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					aoos.write(buffer, 0, bytesRead);
				}
			}
		} catch (IOException e) {
			throw new LfsTransportException(e.getMessage(), e);
		}
	}

	private HttpConnection getLfsServerConnection(Repository db,
			LongObjectId loid)
			throws IOException {
		Config config = db.getConfig();
		String lfsUrl = config.getString("lfs", null, "url"); //$NON-NLS-1$ //$NON-NLS-2$
		if (lfsUrl == null) {
			// TODO: compute from <remote>.lfsUrl, or compute from cloneUrl
			return null;
		}
		HttpConnectionFactory cf = HttpTransport.getConnectionFactory();
		URL url = new URL(lfsUrl + "/lfs/objects/" + loid.getName()); //$NON-NLS-1$
		final Proxy proxy = HttpSupport.proxyFor(ProxySelector.getDefault(), url);// TODO: better proxy support
		// TODO: authentication

		return cf.create(url, proxy);
	}

	@Override
	public int run() throws IOException {
		int b;
		if (in != null) {
			while ((b = in.read()) != -1)
				out.write(b);
			in.close();
		}
		out.close();
		return -1;
	}
}
