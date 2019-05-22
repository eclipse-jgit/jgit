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
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BatchRefUpdate;
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
 * Push certificates are stored in a special ref {@code refs/meta/push-certs}.
 * The filenames in the tree are ref names followed by the special suffix
 * <code>@{cert}</code>, and the contents are the latest push cert affecting
 * that ref. The special suffix allows storing certificates for both refs/foo
 * and refs/foo/bar in case those both existed at some point.
 *
 * @since 4.1
 */
public class PushCertificateStore implements AutoCloseable {
	/** Ref name storing push certificates. */
	static final String REF_NAME =
			Constants.R_REFS + "meta/push-certs"; //$NON-NLS-1$

	private static class PendingCert {
		PushCertificate cert;
		PersonIdent ident;
		Collection<ReceiveCommand> matching;

		PendingCert(PushCertificate cert, PersonIdent ident,
				Collection<ReceiveCommand> matching) {
			this.cert = cert;
			this.ident = ident;
			this.matching = matching;
		}
	}

	private final Repository db;
	private final List<PendingCert> pending;
	ObjectReader reader;
	RevCommit commit;

	/**
	 * Create a new store backed by the given repository.
	 *
	 * @param db
	 *            the repository.
	 */
	public PushCertificateStore(Repository db) {
		this.db = db;
		pending = new ArrayList<>();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Close resources opened by this store.
	 * <p>
	 * If {@link #get(String)} was called, closes the cached object reader
	 * created by that method. Does not close the underlying repository.
	 */
	@Override
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
	 * Lazily opens {@code refs/meta/push-certs} and reads from the repository as
	 * necessary. The state is cached between calls to {@code get}; to reread the,
	 * call {@link #close()} first.
	 *
	 * @param refName
	 *            the ref name to get the certificate for.
	 * @return last certificate affecting the ref, or null if no cert was recorded
	 *         for the last update to this ref.
	 * @throws java.io.IOException
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
	public Iterable<PushCertificate> getAll(String refName) {
		return () -> new Iterator<PushCertificate>() {
			private final String path = pathName(refName);

			private PushCertificate next;

			private RevWalk rw;
			{
				try {
					if (reader == null) {
						load();
					}
					if (commit != null) {
						rw = new RevWalk(reader);
						rw.setTreeFilter(AndTreeFilter.create(
								PathFilterGroup.create(Collections
										.singleton(PathFilter.create(path))),
								TreeFilter.ANY_DIFF));
						rw.setRewriteParents(false);
						rw.markStart(rw.parseCommit(commit));
					} else {
						rw = null;
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public boolean hasNext() {
				try {
					if (next == null) {
						if (rw == null) {
							return false;
						}
						try {
							RevCommit c = rw.next();
							if (c != null) {
								try (TreeWalk tw = TreeWalk.forPath(
										rw.getObjectReader(), path,
										c.getTree())) {
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
					if (next == null && rw != null) {
						rw.close();
						rw = null;
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

	void load() throws IOException {
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

	static PushCertificate read(TreeWalk tw) throws IOException {
		if (tw == null || (tw.getRawMode(0) & TYPE_FILE) != TYPE_FILE) {
			return null;
		}
		ObjectLoader loader =
				tw.getObjectReader().open(tw.getObjectId(0), OBJ_BLOB);
		try (InputStream in = loader.openStream();
				Reader r = new BufferedReader(
						new InputStreamReader(in, UTF_8))) {
			return PushCertificateParser.fromReader(r);
		}
	}

	/**
	 * Put a certificate to be saved to the store.
	 * <p>
	 * Writes the contents of this certificate for each ref mentioned. It is up
	 * to the caller to ensure this certificate accurately represents the state
	 * of the ref.
	 * <p>
	 * Pending certificates added to this method are not returned by
	 * {@link #get(String)} and {@link #getAll(String)} until after calling
	 * {@link #save()}.
	 *
	 * @param cert
	 *            certificate to store.
	 * @param ident
	 *            identity for the commit that stores this certificate. Pending
	 *            certificates are sorted by identity timestamp during
	 *            {@link #save()}.
	 */
	public void put(PushCertificate cert, PersonIdent ident) {
		put(cert, ident, null);
	}

	/**
	 * Put a certificate to be saved to the store, matching a set of commands.
	 * <p>
	 * Like {@link #put(PushCertificate, PersonIdent)}, except a value is only
	 * stored for a push certificate if there is a corresponding command in the
	 * list that exactly matches the old/new values mentioned in the push
	 * certificate.
	 * <p>
	 * Pending certificates added to this method are not returned by
	 * {@link #get(String)} and {@link #getAll(String)} until after calling
	 * {@link #save()}.
	 *
	 * @param cert
	 *            certificate to store.
	 * @param ident
	 *            identity for the commit that stores this certificate. Pending
	 *            certificates are sorted by identity timestamp during
	 *            {@link #save()}.
	 * @param matching
	 *            only store certs for the refs listed in this list whose values
	 *            match the commands in the cert.
	 */
	public void put(PushCertificate cert, PersonIdent ident,
			Collection<ReceiveCommand> matching) {
		pending.add(new PendingCert(cert, ident, matching));
	}

	/**
	 * Save pending certificates to the store.
	 * <p>
	 * One commit is created per certificate added with
	 * {@link #put(PushCertificate, PersonIdent)}, in order of identity
	 * timestamps, and a single ref update is performed.
	 * <p>
	 * The pending list is cleared if and only the ref update fails, which
	 * allows for easy retries in case of lock failure.
	 *
	 * @return the result of attempting to update the ref.
	 * @throws java.io.IOException
	 *             if there was an error reading from or writing to the
	 *             repository.
	 */
	public RefUpdate.Result save() throws IOException {
		ObjectId newId = write();
		if (newId == null) {
			return RefUpdate.Result.NO_CHANGE;
		}
		try (ObjectInserter inserter = db.newObjectInserter()) {
			RefUpdate.Result result = updateRef(newId);
			switch (result) {
				case FAST_FORWARD:
				case NEW:
				case NO_CHANGE:
					pending.clear();
					break;
				default:
					break;
			}
			return result;
		} finally {
			close();
		}
	}

	/**
	 * Save pending certificates to the store in an existing batch ref update.
	 * <p>
	 * One commit is created per certificate added with
	 * {@link #put(PushCertificate, PersonIdent)}, in order of identity
	 * timestamps, all commits are flushed, and a single command is added to the
	 * batch.
	 * <p>
	 * The cached ref value and pending list are <em>not</em> cleared. If the
	 * ref update succeeds, the caller is responsible for calling
	 * {@link #close()} and/or {@link #clear()}.
	 *
	 * @param batch
	 *            update to save to.
	 * @return whether a command was added to the batch.
	 * @throws java.io.IOException
	 *             if there was an error reading from or writing to the
	 *             repository.
	 */
	public boolean save(BatchRefUpdate batch) throws IOException {
		ObjectId newId = write();
		if (newId == null || newId.equals(commit)) {
			return false;
		}
		batch.addCommand(new ReceiveCommand(
				commit != null ? commit : ObjectId.zeroId(), newId, REF_NAME));
		return true;
	}

	/**
	 * Clear pending certificates added with {@link #put(PushCertificate,
	 * PersonIdent)}.
	 */
	public void clear() {
		pending.clear();
	}

	private ObjectId write() throws IOException {
		if (pending.isEmpty()) {
			return null;
		}
		if (reader == null) {
			load();
		}
		sortPending(pending);

		ObjectId curr = commit;
		DirCache dc = newDirCache();
		try (ObjectInserter inserter = db.newObjectInserter()) {
			for (PendingCert pc : pending) {
				curr = saveCert(inserter, dc, pc, curr);
			}
			inserter.flush();
			return curr;
		}
	}

	private static void sortPending(List<PendingCert> pending) {
		Collections.sort(pending, (PendingCert a, PendingCert b) -> Long.signum(
				a.ident.getWhen().getTime() - b.ident.getWhen().getTime()));
	}

	private DirCache newDirCache() throws IOException {
		if (commit != null) {
			return DirCache.read(reader, commit.getTree());
		}
		return DirCache.newInCore();
	}

	private ObjectId saveCert(ObjectInserter inserter, DirCache dc,
			PendingCert pc, ObjectId curr) throws IOException {
		Map<String, ReceiveCommand> byRef;
		if (pc.matching != null) {
			byRef = new HashMap<>();
			for (ReceiveCommand cmd : pc.matching) {
				if (byRef.put(cmd.getRefName(), cmd) != null) {
					throw new IllegalStateException();
				}
			}
		} else {
			byRef = null;
		}

		DirCacheEditor editor = dc.editor();
		String certText = pc.cert.toText() + pc.cert.getSignature();
		final ObjectId certId = inserter.insert(OBJ_BLOB, certText.getBytes(UTF_8));
		boolean any = false;
		for (ReceiveCommand cmd : pc.cert.getCommands()) {
			if (byRef != null && !commandsEqual(cmd, byRef.get(cmd.getRefName()))) {
				continue;
			}
			any = true;
			editor.add(new PathEdit(pathName(cmd.getRefName())) {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.REGULAR_FILE);
					ent.setObjectId(certId);
				}
			});
		}
		if (!any) {
			return curr;
		}
		editor.finish();
		CommitBuilder cb = new CommitBuilder();
		cb.setAuthor(pc.ident);
		cb.setCommitter(pc.ident);
		cb.setTreeId(dc.writeTree(inserter));
		if (curr != null) {
			cb.setParentId(curr);
		} else {
			cb.setParentIds(Collections.<ObjectId> emptyList());
		}
		cb.setMessage(buildMessage(pc.cert));
		return inserter.insert(OBJ_COMMIT, cb.build());
	}

	private static boolean commandsEqual(ReceiveCommand c1, ReceiveCommand c2) {
		if (c1 == null || c2 == null) {
			return c1 == c2;
		}
		return c1.getRefName().equals(c2.getRefName())
				&& c1.getOldId().equals(c2.getOldId())
				&& c1.getNewId().equals(c2.getNewId());
	}

	private RefUpdate.Result updateRef(ObjectId newId) throws IOException {
		RefUpdate ru = db.updateRef(REF_NAME);
		ru.setExpectedOldObjectId(commit != null ? commit : ObjectId.zeroId());
		ru.setNewObjectId(newId);
		ru.setRefLogIdent(pending.get(pending.size() - 1).ident);
		ru.setRefLogMessage(JGitText.get().storePushCertReflog, false);
		try (RevWalk rw = new RevWalk(reader)) {
			return ru.update(rw);
		}
	}

	private TreeWalk newTreeWalk(String refName) throws IOException {
		if (commit == null) {
			return null;
		}
		return TreeWalk.forPath(reader, pathName(refName), commit.getTree());
	}

	static String pathName(String refName) {
		return refName + "@{cert}"; //$NON-NLS-1$
	}

	private static String buildMessage(PushCertificate cert) {
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
		return sb.append('\n').toString();
	}
}
