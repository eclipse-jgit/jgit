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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lfs.Protocol.OPERATION_UPLOAD;
import static org.eclipse.jgit.lfs.internal.LfsConnectionFactory.toRequest;
import static org.eclipse.jgit.transport.http.HttpConnection.HTTP_OK;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;
import static org.eclipse.jgit.util.HttpSupport.METHOD_PUT;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.lfs.Protocol.ObjectInfo;
import org.eclipse.jgit.lfs.errors.CorruptMediaFile;
import org.eclipse.jgit.lfs.internal.LfsConnectionFactory;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.http.HttpConnection;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/**
 * Pre-push hook that handles uploading LFS artefacts.
 *
 * @since 4.11
 */
public class LfsPrePushHook extends PrePushHook {

	private static final String EMPTY = ""; //$NON-NLS-1$
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

	/**
	 * @param repo
	 *            the repository
	 * @param outputStream
	 *            not used by this implementation
	 * @param errorStream
	 *            not used by this implementation
	 * @since 5.6
	 */
	public LfsPrePushHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	@Override
	public void setRefs(Collection<RemoteRefUpdate> toRefs) {
		this.refs = toRefs;
	}

	@Override
	public String call() throws IOException, AbortedByHookException {
		Set<LfsPointer> toPush = findObjectsToPush();
		if (toPush.isEmpty()) {
			return EMPTY;
		}
		HttpConnection api = LfsConnectionFactory.getLfsConnection(
				getRepository(), METHOD_POST, OPERATION_UPLOAD);
		Map<String, LfsPointer> oid2ptr = requestBatchUpload(api, toPush);
		uploadContents(api, oid2ptr);
		return EMPTY;

	}

	private Set<LfsPointer> findObjectsToPush() throws IOException,
			MissingObjectException, IncorrectObjectTypeException {
		Set<LfsPointer> toPush = new TreeSet<>();

		try (ObjectWalk walk = new ObjectWalk(getRepository())) {
			for (RemoteRefUpdate up : refs) {
				walk.setRewriteParents(false);
				excludeRemoteRefs(walk);
				walk.markStart(walk.parseCommit(up.getNewObjectId()));
				while (walk.next() != null) {
					// walk all commits to populate objects
				}
				findLfsPointers(toPush, walk);
			}
		}
		return toPush;
	}

	private static void findLfsPointers(Set<LfsPointer> toPush, ObjectWalk walk)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		RevObject obj;
		ObjectReader r = walk.getObjectReader();
		while ((obj = walk.nextObject()) != null) {
			if (obj.getType() == Constants.OBJ_BLOB
					&& getObjectSize(r, obj) < LfsPointer.SIZE_THRESHOLD) {
				LfsPointer ptr = loadLfsPointer(r, obj);
				if (ptr != null) {
					toPush.add(ptr);
				}
			}
		}
	}

	private static long getObjectSize(ObjectReader r, RevObject obj)
			throws IOException {
		return r.getObjectSize(obj.getId(), Constants.OBJ_BLOB);
	}

	private static LfsPointer loadLfsPointer(ObjectReader r, AnyObjectId obj)
			throws IOException {
		try (InputStream is = r.open(obj, Constants.OBJ_BLOB).openStream()) {
			return LfsPointer.parseLfsPointer(is);
		}
	}

	private void excludeRemoteRefs(ObjectWalk walk) throws IOException {
		RefDatabase refDatabase = getRepository().getRefDatabase();
		List<Ref> remoteRefs = refDatabase.getRefsByPrefix(remote());
		for (Ref r : remoteRefs) {
			ObjectId oid = r.getPeeledObjectId();
			if (oid == null) {
				oid = r.getObjectId();
			}
			if (oid == null) {
				// ignore (e.g. symbolic, ...)
				continue;
			}
			RevObject o = walk.parseAny(oid);
			if (o.getType() == Constants.OBJ_COMMIT
					|| o.getType() == Constants.OBJ_TAG) {
				walk.markUninteresting(o);
			}
		}
	}

	private String remote() {
		String remoteName = getRemoteName() == null
				? Constants.DEFAULT_REMOTE_NAME
				: getRemoteName();
		return Constants.R_REMOTES + remoteName;
	}

	private Map<String, LfsPointer> requestBatchUpload(HttpConnection api,
			Set<LfsPointer> toPush) throws IOException {
		LfsPointer[] res = toPush.toArray(new LfsPointer[0]);
		Map<String, LfsPointer> oidStr2ptr = new HashMap<>();
		for (LfsPointer p : res) {
			oidStr2ptr.put(p.getOid().name(), p);
		}
		Gson gson = Protocol.gson();
		api.getOutputStream().write(
				gson.toJson(toRequest(OPERATION_UPLOAD, res)).getBytes(UTF_8));
		int responseCode = api.getResponseCode();
		if (responseCode != HTTP_OK) {
			throw new IOException(
					MessageFormat.format(LfsText.get().serverFailure,
							api.getURL(), Integer.valueOf(responseCode)));
		}
		return oidStr2ptr;
	}

	private void uploadContents(HttpConnection api,
			Map<String, LfsPointer> oid2ptr) throws IOException {
		try (JsonReader reader = new JsonReader(
				new InputStreamReader(api.getInputStream(), UTF_8))) {
			for (Protocol.ObjectInfo o : parseObjects(reader)) {
				if (o.actions == null) {
					continue;
				}
				LfsPointer ptr = oid2ptr.get(o.oid);
				if (ptr == null) {
					// received an object we didn't request
					continue;
				}
				Protocol.Action uploadAction = o.actions.get(OPERATION_UPLOAD);
				if (uploadAction == null || uploadAction.href == null) {
					continue;
				}

				Lfs lfs = new Lfs(getRepository());
				Path path = lfs.getMediaFile(ptr.getOid());
				if (!Files.exists(path)) {
					throw new IOException(MessageFormat
							.format(LfsText.get().missingLocalObject, path));
				}
				uploadFile(o, uploadAction, path);
			}
		}
	}

	private List<ObjectInfo> parseObjects(JsonReader reader) {
		Gson gson = new Gson();
		Protocol.Response resp = gson.fromJson(reader, Protocol.Response.class);
		return resp.objects;
	}

	private void uploadFile(Protocol.ObjectInfo o,
			Protocol.Action uploadAction, Path path)
			throws IOException, CorruptMediaFile {
		HttpConnection contentServer = LfsConnectionFactory
				.getLfsContentConnection(getRepository(), uploadAction,
						METHOD_PUT);
		contentServer.setDoOutput(true);
		try (OutputStream out = contentServer
				.getOutputStream()) {
			long size = Files.copy(path, out);
			if (size != o.size) {
				throw new CorruptMediaFile(path, o.size, size);
			}
		}
		int responseCode = contentServer.getResponseCode();
		if (responseCode != HTTP_OK) {
			throw new IOException(MessageFormat.format(
					LfsText.get().serverFailure, contentServer.getURL(),
					Integer.valueOf(responseCode)));
		}
	}
}
