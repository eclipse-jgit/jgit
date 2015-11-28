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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.IOException;
import java.util.Collections;
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
 *
 * @since 4.2
 */
public class RefTreeDb extends RefDatabase {
	private static final String R_TXN = "refs/txn/"; //$NON-NLS-1$
	static final String R_TXN_COMMITTED = "refs/txn/committed"; //$NON-NLS-1$
	static final int MAX_SYMREF_DEPTH = MAX_SYMBOLIC_REF_DEPTH;

	private final Repository repo;
	private final RefDatabase bootstrap;
	private volatile Scanner.Result refs;

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 * @param bootstrap
	 */
	public RefTreeDb(Repository repo, RefDatabase bootstrap) {
		this.repo = repo;
		this.bootstrap = bootstrap;
	}

	Repository getRepository() {
		return repo;
	}

	@Override
	public void create() throws IOException {
		bootstrap.create();
	}

	@Override
	public void close() {
		refs = null;
		bootstrap.close();
	}

	@Override
	public Ref getRef(String name) throws IOException {
		for (String p : SEARCH_PATH) {
			Ref r = exactRef(p + name);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		if (name.startsWith(R_TXN)) {
			return bootstrap.exactRef(name);
		}
		return getRefs(ALL).get(name);
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		if (prefix.startsWith(R_TXN)) {
			return bootstrap.getRefs(prefix);
		}

		RefList<Ref> txn;
		Ref src;
		if (prefix.isEmpty()) {
			txn = listRefsTxn();
			src = txn.get(R_TXN_COMMITTED);
		} else {
			txn = RefList.emptyList();
			src = bootstrap.exactRef(R_TXN_COMMITTED);
		}

		Scanner.Result r = refs;
		if (r == null || !r.id.equals(idOf(src))) {
			r = Scanner.scanRefTree(repo, src);
			refs = r;
		}
		return new RefMap(prefix, txn, r.all, r.sym);
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
	public List<Ref> getAdditionalRefs() {
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
	public void refresh() {
		refs = null;
		bootstrap.refresh();
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		if (name.startsWith(R_TXN)) {
			return bootstrap.isNameConflicting(name);
		}
		return !getConflictingNames(name).isEmpty();
	}

	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}

	@Override
	public BatchRefUpdate newBatchUpdate() {
		return new Batch(this);
	}

	@Override
	public RefUpdate newUpdate(String name, boolean detach) throws IOException {
		if (name.startsWith(R_TXN)) {
			return bootstrap.newUpdate(name, detach);
		}

		Ref r = exactRef(name);
		if (r == null) {
			r = new ObjectIdRef.Unpeeled(Storage.NEW, name, null);
		}
		if (!detach && r.isSymbolic()) {
			r = r.getLeaf();
		}
		return new Update(this, r);
	}

	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		if (fromName.startsWith(R_TXN) && toName.startsWith(R_TXN)) {
			return bootstrap.newRename(fromName, toName);
		}
		return new Rename(this,
				newUpdate(fromName, true),
				newUpdate(toName, true));
	}
}
