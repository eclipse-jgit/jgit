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
package org.eclipse.jgit.lfs;

import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ProxySelector;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.lfs.internal.LfsConnectionHelper;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/**
 * Pre-push hook that handles uploading LFS artefacts.
 */
public class LfsPrePushHook extends PrePushHook {

	private Collection<RemoteRefUpdate> refs;

	/**
	 * @param repo
	 *            the repository
	 * @param outputStream
	 *            not used by this implementation
	 */
	public LfsPrePushHook(Repository repo, PrintStream outputStream) {
		super(repo, outputStream);
	}

	@Override
	public void setRefs(Collection<RemoteRefUpdate> toRefs) {
		this.refs = toRefs;
	}

	@SuppressWarnings("boxing")
	@Override
	public String call() throws IOException, AbortedByHookException {
		Set<LfsPointer> toPush = new TreeSet<>();

		// for all refs to be pushed, determine which commits are to be pushed.
		for (RemoteRefUpdate up : refs) {
			try (ObjectWalk walk = new ObjectWalk(getRepository())) {
				walk.setRewriteParents(false);

				// exclude all remote ref's current state - no need to go
				// remote, we're only interested in our deltas.
				Map<String, Ref> rr = getRepository().getRefDatabase()
						.getRefs(Constants.R_REMOTES + (getRemoteName() == null
								? Constants.DEFAULT_REMOTE_NAME
								: getRemoteName()));
				for (Ref r : rr.values()) {
					ObjectId oid = r.getPeeledObjectId();
					if (oid == null)
						oid = r.getObjectId();
					try {
						walk.markUninteresting(walk.parseCommit(oid));
					} catch (IncorrectObjectTypeException e) {
						// Ignore all refs which are not commits
					}
				}

				// find all objects that changed, and find all LfsPointers in
				// there.
				walk.markStart(walk.parseCommit(up.getNewObjectId()));
				for (;;) {
					// walk all commits to populate objects
					if (walk.next() == null)
						break;
				}
				for (;;) {
					final RevObject obj = walk.nextObject();
					if (obj == null)
						break;

					if (obj.getType() == Constants.OBJ_BLOB) {
						long objectSize = walk.getObjectReader()
								.getObjectSize(obj.getId(), Constants.OBJ_BLOB);
						if (objectSize > LfsPointer.SIZE_THRESHOLD) {
							continue;
						} else {
							try (InputStream is = walk.getObjectReader()
									.open(obj.getId(), Constants.OBJ_BLOB)
									.openStream()) {
								LfsPointer ptr = LfsPointer.parseLfsPointer(is);
								if (ptr != null) {
									toPush.add(ptr);
								}
							}
						}
					}
				}
			}
		}

		// build batch upload request to announce identified objects
		if (toPush.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		Lfs lfs = new Lfs(getRepository());

		LfsPointer[] res = toPush.toArray(new LfsPointer[toPush.size()]);
		Map<String, LfsPointer> oidStr2ptr = new HashMap<>();
		for (LfsPointer p : res) {
			oidStr2ptr.put(p.getOid().name(), p);
		}
		HttpConnection lfsServerConn = LfsConnectionHelper.getLfsConnection(
				getRepository(), HttpSupport.METHOD_POST,
				Protocol.OPERATION_UPLOAD);
		Gson gson = new Gson();
		lfsServerConn.getOutputStream()
				.write(gson
						.toJson(LfsConnectionHelper
								.toRequest(Protocol.OPERATION_UPLOAD, res))
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
				Protocol.Action uploadAction = o.actions
						.get(Protocol.OPERATION_UPLOAD);
				if (uploadAction == null || uploadAction.href == null) {
					continue;
				}
				URL contentUrl = new URL(uploadAction.href);
				HttpConnection contentServerConn = HttpTransport
						.getConnectionFactory().create(contentUrl,
								HttpSupport.proxyFor(ProxySelector.getDefault(),
										contentUrl));
				contentServerConn.setRequestMethod(HttpSupport.METHOD_PUT);
				uploadAction.header.forEach(
						(k, v) -> contentServerConn.setRequestProperty(k, v));
				if (contentUrl.getProtocol().equals("https") //$NON-NLS-1$
						&& !getRepository().getConfig().getBoolean("http", "sslVerify", true)) { //$NON-NLS-1$ //$NON-NLS-2$
					HttpSupport.disableSslVerify(contentServerConn);
				}
				contentServerConn.setRequestProperty(HDR_ACCEPT_ENCODING,
						ENCODING_GZIP);
				contentServerConn.setDoOutput(true);
				Path path = lfs.getMediaFile(ptr.getOid());
				if (!Files.exists(path)) {
					throw new IOException(MessageFormat
							.format(LfsText.get().missingLocalObject, path));
				}
				try (OutputStream contentOut = contentServerConn
						.getOutputStream()) {
					long bytesCopied = Files.copy(path, contentOut);
					if (bytesCopied != o.size) {
						throw new IOException(MessageFormat.format(
								LfsText.get().wrongAmoutOfDataWritten,
								contentServerConn.getURL(), bytesCopied,
								o.size));
					}
				}
				responseCode = contentServerConn.getResponseCode();
				if (responseCode != HttpConnection.HTTP_OK) {
					throw new IOException(
							MessageFormat.format(LfsText.get().serverFailure,
									contentServerConn.getURL(), responseCode));
				}
			}
		}
		return ""; //$NON-NLS-1$
	}

}
