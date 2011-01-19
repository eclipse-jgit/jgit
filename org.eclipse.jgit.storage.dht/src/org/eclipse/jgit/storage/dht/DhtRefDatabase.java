/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef.PeeledNonTag;
import org.eclipse.jgit.lib.ObjectIdRef.PeeledTag;
import org.eclipse.jgit.lib.ObjectIdRef.Unpeeled;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.RefTransaction;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

class DhtRefDatabase extends RefDatabase {
	private final DhtRepository repository;

	private final RepositoryKey repo;

	private final Database db;

	private final AtomicReference<RefCache> cache;

	DhtRefDatabase(DhtRepository repository, RepositoryKey repo, Database db) {
		this.repository = repository;
		this.repo = repo;
		this.db = db;
		this.cache = new AtomicReference<RefCache>();
	}

	DhtRepository getRepository() {
		return repository;
	}

	ChunkKey find(AnyObjectId id) {
		RefCache c = cache.get();
		if (c != null) {
			RefData.IdWithChunk i = c.hints.get(id);
			if (i != null)
				return i.getChunkKey();
		}
		return null;
	}

	@Override
	public Ref getRef(String needle) throws IOException {
		Map<String, Ref> all = getRefs(ALL);
		for (String prefix : SEARCH_PATH) {
			Ref ref = all.get(prefix + needle);
			if (ref != null)
				return ref;
		}
		return null;
	}

	@Override
	public List<Ref> getAdditionalRefs() {
		return Collections.emptyList();
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		final RefCache c = readRefs();

		RefList<Ref> packed = RefList.emptyList();
		RefList<Ref> loose = c.ids;
		RefList.Builder<Ref> sym = new RefList.Builder<Ref>(c.sym.size());

		for (int idx = 0; idx < c.sym.size(); idx++) {
			Ref ref = c.sym.get(idx);
			String name = ref.getName();
			ref = resolve(ref, 0, loose);
			if (ref != null && ref.getObjectId() != null) {
				sym.add(ref);
			} else {
				// A broken symbolic reference, we have to drop it from the
				// collections the client is about to receive. Should be a
				// rare occurrence so pay a copy penalty.
				int toRemove = loose.find(name);
				if (0 <= toRemove)
					loose = loose.remove(toRemove);
			}
		}

		return new RefMap(prefix, packed, loose, sym.toRefList());
	}

	private Ref resolve(Ref ref, int depth, RefList<Ref> loose)
			throws IOException {
		if (!ref.isSymbolic())
			return ref;

		Ref dst = ref.getTarget();

		if (MAX_SYMBOLIC_REF_DEPTH <= depth)
			return null; // claim it doesn't exist

		dst = loose.get(dst.getName());
		if (dst == null)
			return ref;

		dst = resolve(dst, depth + 1, loose);
		if (dst == null)
			return null;

		return new SymbolicRef(ref.getName(), dst);
	}

	@Override
	public Ref peel(Ref ref) throws IOException {
		final Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null)
			return ref;

		Ref newLeaf = doPeel(oldLeaf);

		RefCache cur = readRefs();
		int idx = cur.ids.find(oldLeaf.getName());
		if (0 <= idx && cur.ids.get(idx) == oldLeaf) {
			RefList<Ref> newList = cur.ids.set(idx, newLeaf);
			if (cache.compareAndSet(cur, new RefCache(newList, cur)))
				cachePeeledState(oldLeaf, newLeaf);
		}

		return recreate(ref, newLeaf);
	}

	private void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
		try {
			RefKey key = RefKey.create(repo, newLeaf.getName());
			RefTransaction txn = db.ref().newTransaction(key);
			if (RefData.fromRef(oldLeaf).equals(txn.getOldData()))
				txn.compareAndPut(RefData.fromRef(newLeaf));
			else
				txn.abort();
		} catch (TimeoutException e) {
			// Ignore a timeout here, we were only trying to update
			// a cached value to save peeling costs in the future.

		} catch (DhtException e) {
			// Ignore a database error, this was only an attempt to
			// fix a value that could be cached to save time later.
		}
	}

	private Ref doPeel(final Ref leaf) throws MissingObjectException,
			IOException {
		RevWalk rw = new RevWalk(getRepository());
		try {
			String name = leaf.getName();
			ObjectId oId = leaf.getObjectId();
			RevObject obj = rw.parseAny(oId);
			DhtReader ctx = (DhtReader) rw.getObjectReader();

			ChunkKey key = ctx.findChunk(oId);
			if (key != null)
				oId = new RefData.IdWithChunk(oId, key);

			if (obj instanceof RevTag) {
				ObjectId pId = rw.peel(obj);
				key = ctx.findChunk(pId);
				pId = key != null ? new RefData.IdWithChunk(pId, key) : pId
						.copy();
				return new PeeledTag(leaf.getStorage(), name, oId, pId);
			} else {
				return new PeeledNonTag(leaf.getStorage(), name, oId);
			}
		} finally {
			rw.release();
		}
	}

	private static Ref recreate(final Ref old, final Ref leaf) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf);
			return new SymbolicRef(old.getName(), dst);
		}
		return leaf;
	}

	@Override
	public DhtRefUpdate newUpdate(String refName, boolean detach)
			throws IOException {
		Ref ref = getRefs(ALL).get(refName);
		if (ref == null)
			ref = new Unpeeled(NEW, refName, null);
		return new DhtRefUpdate(this, repo, db, ref);
	}

	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		DhtRefUpdate src = newUpdate(fromName, true);
		DhtRefUpdate dst = newUpdate(toName, true);
		return new DhtRefRename(src, dst);
	}

	@Override
	public boolean isNameConflicting(String refName) throws IOException {
		RefList<Ref> all = readRefs().ids;

		// Cannot be nested within an existing reference.
		int lastSlash = refName.lastIndexOf('/');
		while (0 < lastSlash) {
			String needle = refName.substring(0, lastSlash);
			if (all.contains(needle))
				return true;
			lastSlash = refName.lastIndexOf('/', lastSlash - 1);
		}

		// Cannot be the container of an existing reference.
		String prefix = refName + '/';
		int idx = -(all.find(prefix) + 1);
		if (idx < all.size() && all.get(idx).getName().startsWith(prefix))
			return true;
		return false;
	}

	@Override
	public void create() {
		// Nothing to do.
	}

	@Override
	public void close() {
		clearCache();
	}

	void clearCache() {
		cache.set(null);
	}

	private RefCache readRefs() throws DhtException {
		RefCache c = cache.get();
		if (c == null) {
			try {
				c = read();
			} catch (TimeoutException e) {
				throw new DhtTimeoutException(e);
			}
			cache.set(c);
		}
		return c;
	}

	private RefCache read() throws DhtException, TimeoutException {
		RefList.Builder<Ref> id = new RefList.Builder<Ref>();
		RefList.Builder<Ref> sym = new RefList.Builder<Ref>();
		ObjectIdSubclassMap<RefData.IdWithChunk> hints = new ObjectIdSubclassMap<RefData.IdWithChunk>();

		SCAN: for (Map.Entry<RefKey, RefData> e : scan()) {
			String name = e.getKey().getName();
			RefData data = e.getValue();

			ObjectId oId = null;
			boolean peeled = false;
			ObjectId pId = null;

			TinyProtobuf.Decoder d = data.decode();
			DECODE: for (;;) {
				switch (d.next()) {
				case 0:
					break DECODE;

				case RefData.TAG_SYMREF: {
					String symref = d.string();
					Ref leaf = new Unpeeled(NEW, symref, null);
					SymbolicRef ref = new SymbolicRef(name, leaf);
					id.add(ref);
					sym.add(ref);
					continue SCAN;
				}

				case RefData.TAG_TARGET:
					oId = RefData.IdWithChunk.decode(d.message());
					continue;
				case RefData.TAG_IS_PEELED:
					peeled = d.bool();
					continue;
				case RefData.TAG_PEELED:
					pId = RefData.IdWithChunk.decode(d.message());
					continue;
				default:
					d.skip();
					continue;
				}
			}

			if (oId instanceof RefData.IdWithChunk && !hints.contains(oId))
				hints.add((RefData.IdWithChunk) oId);
			if (pId instanceof RefData.IdWithChunk && !hints.contains(pId))
				hints.add((RefData.IdWithChunk) pId);

			if (peeled && pId != null)
				id.add(new PeeledTag(LOOSE, name, oId, pId));
			else if (peeled)
				id.add(new PeeledNonTag(LOOSE, name, oId));
			else
				id.add(new Unpeeled(LOOSE, name, oId));
		}

		id.sort();
		sym.sort();

		return new RefCache(id.toRefList(), sym.toRefList(), hints);
	}

	private Set<Map.Entry<RefKey, RefData>> scan() throws DhtException,
			TimeoutException {
		// TODO(spearce) Do we need to perform READ_REPAIR here?
		return db.ref().getAll(Context.LOCAL, repo).entrySet();
	}

	private static class RefCache {
		final RefList<Ref> ids;

		final RefList<Ref> sym;

		final ObjectIdSubclassMap<RefData.IdWithChunk> hints;

		RefCache(RefList<Ref> ids, RefList<Ref> sym,
				ObjectIdSubclassMap<RefData.IdWithChunk> hints) {
			this.ids = ids;
			this.sym = sym;
			this.hints = hints;
		}

		RefCache(RefList<Ref> ids, RefCache old) {
			this(ids, old.sym, old.hints);
		}
	}
}
