/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.UnionInputStream;

/**
 * Storage for recorded push certificates.
 * <p>
 * Push certificates are stored in a special ref {@code refs/push-certs}. The
 * filenames in the tree are ref names, and the contents of each ref name
 * contains the concatenated list of push certificates affecting that ref.
 *
 * @since 4.1
 */
public class PushCertificateStore implements AutoCloseable {
	/** Ref name storing push certificates. */
	public static final String REF_NAME =
			Constants.R_REFS + "push-certs"; //$NON-NLS-1$

	private final Repository db;
	private final Map<String, List<PushCertificate>> added;
	private ObjectReader reader;
	private RevCommit commit;

	/**
	 * Create a new store backed by the given repository.
	 *
	 * @param db
	 *            the repository.
	 */
  public PushCertificateStore(Repository db) {
		this.db = db;
		added = new LinkedHashMap<>();
  }

	/**
	 * Close resources opened by this store.
	 * <p>
	 * If {@link #get(String)} was called, closes the cached object reader created
	 * by that method. Does not close the underlying repository.
	 * <p>
	 * Certificates added with {@link #add(String, PushCertificate)} are not
	 * cleared. This allows callers to implement a retry loop of {@link
	 * #save(CommitBuilder)} attempts with {@code close()} calls in between.
	 */
	public void close() {
		if (reader != null) {
			reader.close();
			reader = null;
			commit = null;
		}
	}

	/**
	 * Get certificates associated with a ref.
	 * <p>
	 * Lazily opens the ref and reads from the repository as necessary. The ref
	 * state is cached between calls to {@code get}; to reread the ref, call
	 * {@link #close()} first.
	 *
	 * @param refName
	 *            the ref name to get certificates for.
	 * @return certificates affecting the ref.
	 * @throws IOException
	 *             if a problem occurred reading the repository.
	 */
	public List<PushCertificate> get(String refName) throws IOException {
		if (reader == null) {
			load();
		}
		List<PushCertificate> result = new ArrayList<>();
		try (TreeWalk tw = newTreeWalk(refName)) {
			if (tw != null) {
				ObjectLoader loader = reader.open(tw.getObjectId(0), OBJ_BLOB);
				try (InputStream in = loader.openStream();
						Reader r = new InputStreamReader(in)) {
					PushCertificate cert;
					while ((cert = PushCertificateParser.fromReader(r)) != null) {
						result.add(cert);
					}
				}
			}
		}
		List<PushCertificate> additional = added.get(refName);
		if (additional != null) {
			result.addAll(additional);
		}
		return Collections.unmodifiableList(result);
	}

	private void load() throws IOException {
		close();
		reader = db.newObjectReader();
		Ref ref = db.getRefDatabase().exactRef(REF_NAME);
		if (ref == null) {
			// No ref, same as empty map.
			return;
		}
		try (RevWalk rw = new RevWalk(reader)) {
			commit = rw.parseCommit(ref.getObjectId());
		}
	}

	/**
	 * Add a certificate to the store.
	 *
	 * @param refName
	 *            ref name for the certificate; must be a valid ref name.
	 * @param cert
	 *            certificate to store.
	 */
	public void add(String refName, PushCertificate cert) {
		if (!Repository.isValidRefName(refName)) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidRefName, refName));
		}
		List<PushCertificate> list = added.get(refName);
		if (list == null) {
			list = new ArrayList<>();
			added.put(refName, list);
		}
		list.add(cert);
	}

	/**
	 * Attempt to save certificates for the ref to the store.
	 * <p>
	 * Lazily reads the state of the ref if necessary, and appends any refs added
	 * with {@link #add(String, PushCertificate)} to the stored value.
	 * <p>
	 * In the event of {@code LOCK_FAILURE}, may be retried by calling {@link
	 * #close()} followed by {@code save}, which will reread the ref and attempt
	 * to append the same set of new certificates.
	 * <p>
	 * If the update succeeds, readers are closed and the list of added changes is
	 * cleared, so this update can be reused.
	 *
	 * @param cb
	 *            commit builder containing committer, message, etc.; parents and
	 *            tree are ignored.
	 * @return the result of attempting to store the ref.
	 * @throws IOException
	 *             if there was an error reading from or writing to the
	 *             repository.
	 */
	public RefUpdate.Result save(CommitBuilder cb) throws IOException {
		if (added.isEmpty()) {
			return RefUpdate.Result.NO_CHANGE;
		}
		if (reader == null) {
			load();
		}
		DirCache dc = newDirCache();
		DirCacheEditor editor = dc.editor();
		try (ObjectInserter inserter = db.newObjectInserter()) {
			for (Map.Entry<String, List<PushCertificate>> e : added.entrySet()) {
				String refName = e.getKey();
				List<InputStream> streams = new ArrayList<>(e.getValue().size() + 1);
				long len = readExisting(refName, streams);
				len += joinCertificates(e.getValue(), streams);
				final ObjectId blobId = inserter.insert(
						OBJ_BLOB, len,
						new UnionInputStream(streams.toArray(new InputStream[0])));
				editor.add(new PathEdit(refName) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.setFileMode(FileMode.REGULAR_FILE);
						ent.setObjectId(blobId);
					}
				});
			}
			editor.finish();
			cb.setTreeId(dc.writeTree(inserter));
			if (commit != null) {
				cb.setParentId(commit);
			} else {
				cb.setParentIds(Collections.<ObjectId> emptyList());
			}
			ObjectId newCommit = inserter.insert(OBJ_COMMIT, cb.build());
			inserter.flush();

			RefUpdate ru = db.updateRef(REF_NAME);
			ru.setExpectedOldObjectId(commit);
			ru.setNewObjectId(newCommit);
			ru.setRefLogIdent(cb.getCommitter());
			// TODO(dborowitz): Set message?
			RefUpdate.Result result = ru.update(new RevWalk(inserter.newReader()));
			if (result == RefUpdate.Result.NEW
					|| result == RefUpdate.Result.FAST_FORWARD) {
				added.clear();
			}
			return result;
		} finally {
			close();
		}
	}

	private DirCache newDirCache() throws IOException {
		DirCache dc = DirCache.newInCore();
		if (commit != null) {
			DirCacheBuilder b = dc.builder();
			b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, commit.getTree());
			b.finish();
		}
		return dc;
	}

	private static long joinCertificates(Iterable<PushCertificate> certs,
			List<InputStream> streams) {
		long len = 0;
		for (PushCertificate c : certs) {
			len += newInputStream(c.toText(), streams);
			len += newInputStream(c.getSignature(), streams);
		}
		return len;
	}

	private static long newInputStream(String str, List<InputStream> streams) {
		byte[] bytes = Constants.encode(str);
		streams.add(new ByteArrayInputStream(bytes));
		return bytes.length;
	}

	private long readExisting(String refName, List<InputStream> streams)
			throws IOException {
		try (TreeWalk tw = newTreeWalk(refName)) {
			if (tw == null) {
				return 0;
			}
			ObjectLoader loader = reader.open(tw.getObjectId(0), OBJ_BLOB);
			streams.add(loader.openStream());
			return loader.getSize();
		}
	}

	private TreeWalk newTreeWalk(String path) throws IOException {
		if (commit == null) {
			return null;
		}
		return TreeWalk.forPath(reader, path, commit.getTree());
	}
}
