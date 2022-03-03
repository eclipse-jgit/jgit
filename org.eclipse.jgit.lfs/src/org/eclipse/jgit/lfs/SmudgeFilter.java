/*
 * Copyright (C) 2016, 2021 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.lfs.internal.LfsConnectionFactory;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.Repository;
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
	public static final FilterCommandFactory FACTORY = SmudgeFilter::new;

	/**
	 * Register this filter in JGit
	 */
	static void register() {
		FilterCommandRegistry
				.register(org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
						+ Constants.ATTR_FILTER_DRIVER_PREFIX
						+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_SMUDGE,
						FACTORY);
	}

	/**
	 * Constructor for SmudgeFilter.
	 *
	 * @param db
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param in
	 *            a {@link java.io.InputStream} object. The stream is closed in
	 *            any case.
	 * @param out
	 *            a {@link java.io.OutputStream} object.
	 * @throws java.io.IOException
	 *             in case of an error
	 */
	public SmudgeFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		this(in.markSupported() ? in : new BufferedInputStream(in), out, db);
	}

	private SmudgeFilter(InputStream in, OutputStream out, Repository db)
			throws IOException {
		super(in, out);
		InputStream from = in;
		try {
			LfsPointer res = LfsPointer.parseLfsPointer(from);
			if (res != null) {
				AnyLongObjectId oid = res.getOid();
				Lfs lfs = new Lfs(db);
				Path mediaFile = lfs.getMediaFile(oid);
				if (!Files.exists(mediaFile)) {
					downloadLfsResource(lfs, db, res);
				}
				this.in = Files.newInputStream(mediaFile);
			} else {
				// Not swapped; stream was reset, don't close!
				from = null;
			}
		} finally {
			if (from != null) {
				from.close(); // Close the swapped-out stream
			}
		}
	}

	/**
	 * Download content which is hosted on a LFS server
	 *
	 * @param lfs
	 *            local {@link Lfs} storage.
	 * @param db
	 *            the repository to work with
	 * @param res
	 *            the objects to download
	 * @return the paths of all mediafiles which have been downloaded
	 * @throws IOException
	 * @since 4.11
	 */
	public static Collection<Path> downloadLfsResource(Lfs lfs, Repository db,
			LfsPointer... res) throws IOException {
		Collection<Path> downloadedPaths = new ArrayList<>();
		Map<String, LfsPointer> oidStr2ptr = new HashMap<>();
		for (LfsPointer p : res) {
			oidStr2ptr.put(p.getOid().name(), p);
		}
		HttpConnection lfsServerConn = LfsConnectionFactory.getLfsConnection(db,
				HttpSupport.METHOD_POST, Protocol.OPERATION_DOWNLOAD);
		Gson gson = Protocol.gson();
		lfsServerConn.getOutputStream()
				.write(gson
						.toJson(LfsConnectionFactory
								.toRequest(Protocol.OPERATION_DOWNLOAD, res))
						.getBytes(UTF_8));
		int responseCode = lfsServerConn.getResponseCode();
		if (!(responseCode == HttpConnection.HTTP_OK
				|| responseCode == HttpConnection.HTTP_NOT_AUTHORITATIVE)) {
			throw new IOException(
					MessageFormat.format(LfsText.get().serverFailure,
							lfsServerConn.getURL(),
							Integer.valueOf(responseCode)));
		}
		try (JsonReader reader = new JsonReader(
				new InputStreamReader(lfsServerConn.getInputStream(),
						UTF_8))) {
			Protocol.Response resp = gson.fromJson(reader,
					Protocol.Response.class);
			for (Protocol.ObjectInfo o : resp.objects) {
				if (o.error != null) {
					throw new IOException(
							MessageFormat.format(LfsText.get().protocolError,
									Integer.valueOf(o.error.code),
									o.error.message));
				}
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

				HttpConnection contentServerConn = LfsConnectionFactory
						.getLfsContentConnection(db, downloadAction,
								HttpSupport.METHOD_GET);

				responseCode = contentServerConn.getResponseCode();
				if (responseCode != HttpConnection.HTTP_OK) {
					throw new IOException(
							MessageFormat.format(LfsText.get().serverFailure,
									contentServerConn.getURL(),
									Integer.valueOf(responseCode)));
				}
				Path path = lfs.getMediaFile(ptr.getOid());
				Path parent = path.getParent();
				if (parent != null) {
					parent.toFile().mkdirs();
				}
				try (InputStream contentIn = contentServerConn
						.getInputStream()) {
					long bytesCopied = Files.copy(contentIn, path);
					if (bytesCopied != o.size) {
						throw new IOException(MessageFormat.format(
								LfsText.get().wrongAmountOfDataReceived,
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

	/** {@inheritDoc} */
	@Override
	public int run() throws IOException {
		try {
			int totalRead = 0;
			int length = 0;
			if (in != null) {
				byte[] buf = new byte[8192];
				while ((length = in.read(buf)) != -1) {
					out.write(buf, 0, length);
					totalRead += length;

					// when threshold reached, loop back to the caller.
					// otherwise we could only support files up to 2GB (int
					// return type) properly. we will be called again as long as
					// we don't return -1 here.
					if (totalRead >= MAX_COPY_BYTES) {
						// leave streams open - we need them in the next call.
						return totalRead;
					}
				}
			}

			if (totalRead == 0 && length == -1) {
				// we're totally done :) cleanup all streams
				in.close();
				out.close();
				return length;
			}

			return totalRead;
		} catch (IOException e) {
			in.close(); // clean up - we swapped this stream.
			out.close();
			throw e;
		}
	}

}
