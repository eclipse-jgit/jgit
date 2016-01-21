/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Reference database backed by a {@link RefTree}.
 * <p>
 * The storage for RefTreeDatabase has two parts. The main part is a native Git
 * tree object stored under the {@code refs/txn} namespace. To avoid cycles,
 * references to {@code refs/txn} are not stored in that tree object, but
 * instead in a "bootstrap" layer, which is a separate {@link RefDatabase} such
 * as {@link org.eclipse.jgit.internal.storage.file.RefDirectory} using local
 * reference files inside of {@code $GIT_DIR/refs}.
 */
public class RefTreeDatabase extends RefDatabase {
	private final Repository repo;
	private final RefDatabase bootstrap;
	private final String txnCommitted;

	@Nullable
	private final String txnNamespace;
	private volatile Scanner.Result refs;

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 *            the repository using references in this database.
	 * @param bootstrap
	 *            bootstrap reference database storing the references that
	 *            anchor the {@link RefTree}.
	 */
	public RefTreeDatabase(Repository repo, RefDatabase bootstrap) {
		Config cfg = repo.getConfig();
		String committed = cfg.getString("reftree", null, "committedRef"); //$NON-NLS-1$ //$NON-NLS-2$
		if (committed == null || committed.isEmpty()) {
			committed = "refs/txn/committed"; //$NON-NLS-1$
		}

		this.repo = repo;
		this.bootstrap = bootstrap;
		this.txnNamespace = initNamespace(committed);
		this.txnCommitted = committed;
	}

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 *            the repository using references in this database.
	 * @param bootstrap
	 *            bootstrap reference database storing the references that
	 *            anchor the {@link RefTree}.
	 * @param txnCommitted
	 *            name of the bootstrap reference holding the committed RefTree.
	 */
	public RefTreeDatabase(Repository repo, RefDatabase bootstrap,
			String txnCommitted) {
		this.repo = repo;
		this.bootstrap = bootstrap;
		this.txnNamespace = initNamespace(txnCommitted);
		this.txnCommitted = txnCommitted;
	}

	private static String initNamespace(String committed) {
		int s = committed.lastIndexOf('/');
		if (s < 0) {
			return null;
		}
		return committed.substring(0, s + 1); // Keep trailing '/'.
	}

	Repository getRepository() {
		return repo;
	}

	/**
	 * @return the bootstrap reference database, which must be used to access
	 *         {@link #getTxnCommitted()}, {@link #getTxnNamespace()}.
	 */
	public RefDatabase getBootstrap() {
		return bootstrap;
	}

	/** @return name of bootstrap reference anchoring committed RefTree. */
	public String getTxnCommitted() {
		return txnCommitted;
	}

	/**
	 * @return namespace used by bootstrap layer, e.g. {@code refs/txn/}.
	 *         Always ends in {@code '/'}.
	 */
	@Nullable
	public String getTxnNamespace() {
		return txnNamespace;
	}

	@Override
	public void create() throws IOException {
		bootstrap.create();
	}

	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}

	@Override
	public void refresh() {
		bootstrap.refresh();
	}

	@Override
	public void close() {
		refs = null;
		bootstrap.close();
	}

	@Override
	public Ref getRef(String name) throws IOException {
		String[] needle = new String[SEARCH_PATH.length];
		for (int i = 0; i < SEARCH_PATH.length; i++) {
			needle[i] = SEARCH_PATH[i] + name;
		}
		return firstExactRef(needle);
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		if (!repo.isBare() && name.indexOf('/') < 0 && !HEAD.equals(name)) {
			// Pass through names like MERGE_HEAD, ORIG_HEAD, FETCH_HEAD.
			return bootstrap.exactRef(name);
		} else if (conflictsWithBootstrap(name)) {
			return null;
		}

		boolean partial = false;
		Ref src = bootstrap.exactRef(txnCommitted);
		Scanner.Result c = refs;
		if (c == null || !c.refTreeId.equals(idOf(src))) {
			c = Scanner.scanRefTree(repo, src, prefixOf(name), false);
			partial = true;
		}

		Ref r = c.all.get(name);
		if (r != null && r.isSymbolic()) {
			r = c.sym.get(name);
			if (partial && r.getObjectId() == null) {
				// Attempting exactRef("HEAD") with partial scan will leave
				// an unresolved symref as its target e.g. refs/heads/master
				// was not read by the partial scan. Scan everything instead.
				return getRefs(ALL).get(name);
			}
		}
		return r;
	}

	private static String prefixOf(String name) {
		int s = name.lastIndexOf('/');
		if (s >= 0) {
			return name.substring(0, s);
		}
		return ""; //$NON-NLS-1$
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		if (!prefix.isEmpty() && prefix.charAt(prefix.length() - 1) != '/') {
			return new HashMap<>(0);
		}

		Ref src = bootstrap.exactRef(txnCommitted);
		Scanner.Result c = refs;
		if (c == null || !c.refTreeId.equals(idOf(src))) {
			c = Scanner.scanRefTree(repo, src, prefix, true);
			if (prefix.isEmpty()) {
				refs = c;
			}
		}
		return new RefMap(prefix, RefList.<Ref> emptyList(), c.all, c.sym);
	}

	private static ObjectId idOf(@Nullable Ref src) {
		return src != null && src.getObjectId() != null
				? src.getObjectId()
				: ObjectId.zeroId();
	}

	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		Collection<Ref> txnRefs;
		if (txnNamespace != null) {
			txnRefs = bootstrap.getRefs(txnNamespace).values();
		} else {
			Ref r = bootstrap.exactRef(txnCommitted);
			if (r != null && r.getObjectId() != null) {
				txnRefs = Collections.singleton(r);
			} else {
				txnRefs = Collections.emptyList();
			}
		}

		List<Ref> otherRefs = bootstrap.getAdditionalRefs();
		List<Ref> all = new ArrayList<>(txnRefs.size() + otherRefs.size());
		all.addAll(txnRefs);
		all.addAll(otherRefs);
		return all;
	}

	@Override
	public Ref peel(Ref ref) throws IOException {
		Ref i = ref.getLeaf();
		ObjectId id = i.getObjectId();
		if (i.isPeeled() || id == null) {
			return ref;
		}
		try (RevWalk rw = new RevWalk(repo)) {
			RevObject obj = rw.parseAny(id);
			if (obj instanceof RevTag) {
				ObjectId p = rw.peel(obj).copy();
				i = new ObjectIdRef.PeeledTag(PACKED, i.getName(), id, p);
			} else {
				i = new ObjectIdRef.PeeledNonTag(PACKED, i.getName(), id);
			}
		}
		return recreate(ref, i);
	}

	private static Ref recreate(Ref old, Ref leaf) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf);
			return new SymbolicRef(old.getName(), dst);
		}
		return leaf;
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		return conflictsWithBootstrap(name)
				|| !getConflictingNames(name).isEmpty();
	}

	@Override
	public BatchRefUpdate newBatchUpdate() {
		return new RefTreeBatch(this);
	}

	@Override
	public RefUpdate newUpdate(String name, boolean detach) throws IOException {
		if (!repo.isBare() && name.indexOf('/') < 0 && !HEAD.equals(name)) {
			return bootstrap.newUpdate(name, detach);
		}
		if (conflictsWithBootstrap(name)) {
			return new AlwaysFailUpdate(this, name);
		}

		Ref r = exactRef(name);
		if (r == null) {
			r = new ObjectIdRef.Unpeeled(Storage.NEW, name, null);
		}

		boolean detaching = detach && r.isSymbolic();
		if (detaching) {
			r = new ObjectIdRef.Unpeeled(LOOSE, name, r.getObjectId());
		}

		RefTreeUpdate u = new RefTreeUpdate(this, r);
		if (detaching) {
			u.setDetachingSymbolicRef();
		}
		return u;
	}

	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		RefUpdate from = newUpdate(fromName, true);
		RefUpdate to = newUpdate(toName, true);
		return new RefTreeRename(this, from, to);
	}

	boolean conflictsWithBootstrap(String name) {
		if (txnNamespace != null && name.startsWith(txnNamespace)) {
			return true;
		} else if (txnCommitted.equals(name)) {
			return true;
		}

		if (name.indexOf('/') < 0 && !HEAD.equals(name)) {
			return true;
		}

		if (name.length() > txnCommitted.length()
				&& name.charAt(txnCommitted.length()) == '/'
				&& name.startsWith(txnCommitted)) {
			return true;
		}
		return false;
	}
}
