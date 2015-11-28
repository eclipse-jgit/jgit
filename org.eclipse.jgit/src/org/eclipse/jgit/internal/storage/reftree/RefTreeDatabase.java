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

import static org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase.Layering.REJECT_REFS_TXN;
import static org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase.Layering.SHOW_ALL;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.BatchRefUpdate;
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
	static final String R_TXN = "refs/txn/"; //$NON-NLS-1$
	static final String R_TXN_COMMITTED = "refs/txn/committed"; //$NON-NLS-1$

	/** How the RefTreeDb should handle the bootstrap layer. */
	public enum Layering {
		/**
		 * Union the bootstrap references into the same namespace.
		 * <p>
		 * Users will be able to see and directly update bootstrap references.
		 * Some updates may fail, for example {@code refs/heads/master} and
		 * {@code refs/txn/committed} at the same time is not possible.
		 */
		SHOW_ALL,

		/**
		 * Hide bootstrap references and reject updates in its namespace.
		 * <p>
		 * Bootstrap references cannot be read through this database, so it is
		 * as if they do not exist. Updates to the bootstrap namespace are
		 * rejected with "funny refname" errors to prevent users from
		 * overwriting a bootstrap reference.
		 */
		REJECT_REFS_TXN,

		/**
		 * Hide the bootstrap references and store over them.
		 * <p>
		 * This behavior makes the bootstrap invisible to users and requires any
		 * code that needs to access a bootstrap reference to explicitly do so
		 * through {@link RefTreeDatabase#getBootstrap()}. By making the
		 * bootstrap layer invisible to users, users may use the bootstrap
		 * namespace for their own data.
		 */
		HIDE_REFS_TXN;
	}

	private final Repository repo;
	private final RefDatabase bootstrap;
	private final Layering behavior;
	private volatile Scanner.Result refs;

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 *            the repository using references in this database.
	 * @param bootstrap
	 *            bootstrap reference database storing the references that
	 *            anchor the {@link RefTree}.
	 * @param behavior
	 *            how this database should expose the bootstrap references.
	 */
	public RefTreeDatabase(Repository repo, RefDatabase bootstrap,
			Layering behavior) {
		this.repo = repo;
		this.bootstrap = bootstrap;
		this.behavior = behavior;
	}

	Repository getRepository() {
		return repo;
	}

	/** @return how the bootstrap layer is treated by this database. */
	public Layering getBehavior() {
		return behavior;
	}

	/** @return the bootstrap reference database. */
	public RefDatabase getBootstrap() {
		return bootstrap;
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
		return findRef(getRefs(ALL), name);
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		if (behavior == SHOW_ALL && name.startsWith(R_TXN)) {
			return bootstrap.exactRef(name);
		}

		boolean partial = false;
		Ref src = bootstrap.exactRef(R_TXN_COMMITTED);
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
		if (behavior == SHOW_ALL && prefix.startsWith(R_TXN)) {
			return bootstrap.getRefs(prefix);
		}
		if (!prefix.isEmpty() && prefix.charAt(prefix.length() - 1) != '/') {
			return new HashMap<>(0);
		}

		RefList<Ref> txn;
		Ref src;
		if (behavior == SHOW_ALL && prefix.isEmpty()) {
			txn = listRefsTxn();
			src = txn.get(R_TXN_COMMITTED);
		} else {
			txn = RefList.emptyList();
			src = bootstrap.exactRef(R_TXN_COMMITTED);
		}

		Scanner.Result c = refs;
		if (c == null || !c.refTreeId.equals(idOf(src))) {
			c = Scanner.scanRefTree(repo, src, prefix, true);
			if (prefix.isEmpty()) {
				refs = c;
			}
		}
		return new RefMap(prefix, txn, c.all, c.sym);
	}

	private static ObjectId idOf(@Nullable Ref src) {
		return src != null && src.getObjectId() != null
				? src.getObjectId()
				: ObjectId.zeroId();
	}

	private RefList<Ref> listRefsTxn() throws IOException {
		RefList.Builder<Ref> txn = new RefList.Builder<>();
		for (Ref r : bootstrap.getRefs(R_TXN).values()) {
			txn.add(r);
		}
		txn.sort();
		return txn.toRefList();
	}

	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		if (behavior == SHOW_ALL) {
			return bootstrap.getAdditionalRefs();
		}
		return Collections.emptyList();
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
		if (behavior == SHOW_ALL && name.startsWith(R_TXN)) {
			return bootstrap.isNameConflicting(name);
		}
		return !getConflictingNames(name).isEmpty();
	}

	@Override
	public BatchRefUpdate newBatchUpdate() {
		return new RefTreeBatch(this);
	}

	@Override
	public RefUpdate newUpdate(String name, boolean detach) throws IOException {
		if (name.startsWith(R_TXN)) {
			if (behavior == SHOW_ALL) {
				return bootstrap.newUpdate(name, detach);
			} else if (behavior == REJECT_REFS_TXN) {
				return new FailUpdate(this, name);
			}
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
		if (behavior == SHOW_ALL && fromName.startsWith(R_TXN)
				&& toName.startsWith(R_TXN)) {
			return bootstrap.newRename(fromName, toName);
		}

		RefUpdate from = newUpdate(fromName, true);
		RefUpdate to = newUpdate(toName, true);
		return new RefTreeRename(this, from, to);
	}
}
