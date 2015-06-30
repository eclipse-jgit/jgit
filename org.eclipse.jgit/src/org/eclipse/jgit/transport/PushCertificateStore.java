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

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Storage for recorded push certificates.
 * <p>
 * Push certificates are stored in a special ref {@code refs/push-certs}. The
 * filenames in the tree are ref names followed by the special suffix
 * <code>@{certs}</code>, and the contents are the latest push cert affecting
 * that ref. The special suffix allows storing certificates for both refs/foo
 * and refs/foo/bar in case those both existed at some point.
 * <p>
 * When a push certificate is stored with {@link #save(PushCertificate,
 * PersonIdent)}, all affected refs are reread and the certificate value only
 * written for those refs whose current state matches the value reflected by
 * this command. This minimizes, but does not eliminate, race conditions caused
 * by the push cert ref being updated separately from the affected refs.
 *
 * @since 4.1
 */
public class PushCertificateStore implements AutoCloseable {
	/** Ref name storing push certificates. */
	static final String REF_NAME = Constants.R_REFS + "push-certs"; //$NON-NLS-1$

	private final Repository db;
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
  }

	/**
	 * Close resources opened by this store.
	 * <p>
	 * If {@link #get(String)} was called, closes the cached object reader created
	 * by that method. Does not close the underlying repository.
	 */
	public void close() {
		if (reader != null) {
			reader.close();
			reader = null;
			commit = null;
		}
	}

	/**
	 * Get latest push certificate associated with a ref.
	 * <p>
	* Lazily opens {@code refs/push-certs} and reads from the repository as
	* necessary. The state is cached between calls to {@code get}; to reread the,
	* call {@link #close()} first.
	 *
	 * @param refName
	 *            the ref name to get the certificate for.
	 * @return last certificate affecting the ref, or null if no cert was recorded
	 *         for the last update to this ref.
	 * @throws IOException
	 *             if a problem occurred reading the repository.
	 */
	public PushCertificate get(String refName) throws IOException {
		if (reader == null) {
			load();
		}
		try (TreeWalk tw = newTreeWalk(refName)) {
			return read(tw);
		}
	}

	/**
	 * Iterate over all push certificates affecting a ref.
	 * <p>
	 * Only includes push certificates actually stored in the tree; see class
	 * Javadoc for conditions where this might not include all push certs ever
	 * seen for this ref.
	 * <p>
	 * The returned iterable may be iterated multiple times, and push certs will
	 * be re-read from the current state of the store on each call to {@link
	 * Iterable#iterator()}. However, method calls on the returned iterator may
	 * fail if {@code save} or {@code close} is called on the enclosing store
	 * during iteration.
	 *
	 * @param refName
	 *            the ref name to get certificates for.
	 * @return iterable over certificates; must be fully iterated in order to
	 *         close resources.
	 */
	public Iterable<PushCertificate> getAll(final String refName) {
		return new Iterable<PushCertificate>() {
			@Override
			public Iterator<PushCertificate> iterator() {
				final String path = pathName(refName);
				final RevWalk rw;
				try {
					if (reader == null) {
						load();
					}
					if (commit == null) {
						return Collections.emptyIterator();
					}
					rw = new RevWalk(reader);
					rw.setTreeFilter(AndTreeFilter.create(
							PathFilterGroup.create(
								Collections.singleton(PathFilter.create(path))),
							TreeFilter.ANY_DIFF));
					rw.setRewriteParents(false);
					rw.markStart(rw.parseCommit(commit));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				return new Iterator<PushCertificate>() {
					private PushCertificate next;

					@Override
					public boolean hasNext() {
						try {
							if (next == null) {
								try {
									RevCommit c = rw.next();
									if (c != null) {
										try (TreeWalk tw = TreeWalk.forPath(
												rw.getObjectReader(), path, c.getTree())) {
											next = read(tw);
										}
									} else {
										next = null;
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
							return next != null;
						} finally {
							if (next == null) {
								rw.close();
							}
						}
					}

					@Override
					public PushCertificate next() {
						hasNext();
						PushCertificate n = next;
						if (n == null) {
							throw new NoSuchElementException();
						}
						next = null;
						return n;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	private void load() throws IOException {
		close();
		reader = db.newObjectReader();
		Ref ref = db.getRefDatabase().exactRef(REF_NAME);
		if (ref == null) {
			// No ref, same as empty.
			return;
		}
		try (RevWalk rw = new RevWalk(reader)) {
			commit = rw.parseCommit(ref.getObjectId());
		}
	}

	private static PushCertificate read(TreeWalk tw) throws IOException {
		if (tw == null || (tw.getRawMode(0) & FileMode.TYPE_FILE) == 0) {
			return null;
		}
		ObjectLoader loader =
				tw.getObjectReader().open(tw.getObjectId(0), OBJ_BLOB);
		try (InputStream in = loader.openStream();
				Reader r = new BufferedReader(new InputStreamReader(in))) {
			return PushCertificateParser.fromReader(r);
		}
	}

	/**
	 * Save a certificate to the store.
	 * <p>
	 * A single commit is added on the {@code refs/push-certs} ref, storing this
	 * certificate under each ref mentioned in the cert whose current value
	 * matches the new value in the cert.
	 * <p>
	 * If the current value of a ref does not match the value set by the given
	 * cert, for example if two post-receive hooks are racing, then the stored
	 * cert for that value is not touched. A commit is still created, however, and
	 * the commit message still contains the original cert data as well.
	 *
	 * @param cert
	 *            certificate to store.
	 * @param ident
	 *            author and committer identity.
	 * @return the result of attempting to store the ref.
	 * @throws IOException
	 *             if there was an error reading from or writing to the
	 *             repository.
	 */
	public RefUpdate.Result save(PushCertificate cert, PersonIdent ident)
			throws IOException {
		if (cert == null || cert.getCommands().isEmpty()) {
			return RefUpdate.Result.NO_CHANGE;
		}
		if (reader == null) {
			load();
		}
		DirCache dc = newDirCache();
		DirCacheEditor editor = dc.editor();

		Map<String, ObjectId> newIds = new HashMap<>();
		for (ReceiveCommand cmd : cert.getCommands()) {
			newIds.put(cmd.getRefName(), cmd.getNewId());
		}
		Map<String, Ref> refs = db.getRefDatabase().exactRef(
				newIds.keySet().toArray(new String[0]));

		String certText = cert.toText() + cert.getSignature();
		ObjectId certId = null;

		try (ObjectInserter inserter = db.newObjectInserter()) {
			for (Map.Entry<String, ObjectId> e : newIds.entrySet()) {
				String refName = e.getKey();
				Ref ref = refs.get(refName);
				if (!e.getValue().equals(
						ref != null ? ref.getObjectId() : ObjectId.zeroId())) {
					continue;
				}
				if (certId == null) {
					certId = inserter.insert(OBJ_BLOB, certText.getBytes(UTF_8));
				}
				final ObjectId id = certId;
				editor.add(new PathEdit(pathName(refName)) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.setFileMode(FileMode.REGULAR_FILE);
						ent.setObjectId(id);
					}
				});
			}
			editor.finish();
			CommitBuilder cb = new CommitBuilder();
			cb.setAuthor(ident);
			cb.setCommitter(ident);
			cb.setTreeId(dc.writeTree(inserter));
			if (commit != null) {
				cb.setParentId(commit);
			} else {
				cb.setParentIds(Collections.<ObjectId> emptyList());
			}
			cb.setMessage(buildMessage(cert, certText));
			ObjectId newCommit = inserter.insert(OBJ_COMMIT, cb.build());
			inserter.flush();

			RefUpdate ru = db.updateRef(REF_NAME);
			ru.setExpectedOldObjectId(commit);
			ru.setNewObjectId(newCommit);
			ru.setRefLogIdent(cb.getCommitter());
			ru.setRefLogMessage(JGitText.get().storePushCertReflog, false);
			try (RevWalk rw = new RevWalk(reader)) {
				return ru.update(rw);
			}
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

	private TreeWalk newTreeWalk(String refName) throws IOException {
		if (commit == null) {
			return null;
		}
		return TreeWalk.forPath(reader, pathName(refName), commit.getTree());
	}

	private static String pathName(String refName) {
		return refName + "@{certs}"; //$NON-NLS-1$
	}

	private static String buildMessage(PushCertificate cert, String certText) {
		StringBuilder sb = new StringBuilder();
		if (cert.getCommands().size() == 1) {
			sb.append(MessageFormat.format(
					JGitText.get().storePushCertOneRef,
					cert.getCommands().get(0).getRefName()));
		} else {
			sb.append(MessageFormat.format(
					JGitText.get().storePushCertMultipleRefs,
					Integer.valueOf(cert.getCommands().size())));
		}
		return sb.append('\n').append('\n').append(certText).toString();
	}
}
