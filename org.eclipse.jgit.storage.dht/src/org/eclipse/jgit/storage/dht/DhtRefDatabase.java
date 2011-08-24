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
import static org.eclipse.jgit.storage.dht.RefDataUtil.NONE;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.RefData;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.RefDataUtil.IdWithChunk;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/** Repository references stored on top of a DHT database. */
public class DhtRefDatabase extends RefDatabase {
	private final DhtRepository repository;

	private final Database db;

	private final AtomicReference<RefCache> cache;

	DhtRefDatabase(DhtRepository repository, Database db) {
		this.repository = repository;
		this.db = db;
		this.cache = new AtomicReference<RefCache>();
	}

	DhtRepository getRepository() {
		return repository;
	}

	ChunkKey findChunk(AnyObjectId id) {
		RefCache c = cache.get();
		if (c != null) {
			IdWithChunk i = c.hints.get(id);
			if (i != null)
				return i.getChunkKey();
		}
		return null;
	}

	@Override
	public Ref getRef(String needle) throws IOException {
		RefCache curr = readRefs();
		for (String prefix : SEARCH_PATH) {
			DhtRef ref = curr.ids.get(prefix + needle);
			if (ref != null) {
				ref = resolve(ref, 0, curr.ids);
				return ref;
			}
		}
		return null;
	}

	private DhtRef getOneRef(String refName) throws IOException {
		RefCache curr = readRefs();
		DhtRef ref = curr.ids.get(refName);
		if (ref != null)
			return resolve(ref, 0, curr.ids);
		return ref;
	}

	@Override
	public List<Ref> getAdditionalRefs() {
		return Collections.emptyList();
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		RefCache curr = readRefs();
		RefList<DhtRef> packed = RefList.emptyList();
		RefList<DhtRef> loose = curr.ids;
		RefList.Builder<DhtRef> sym = new RefList.Builder<DhtRef>(curr.sym.size());

		for (int idx = 0; idx < curr.sym.size(); idx++) {
			DhtRef ref = curr.sym.get(idx);
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

	private DhtRef resolve(DhtRef ref, int depth, RefList<DhtRef> loose)
			throws IOException {
		if (!ref.isSymbolic())
			return ref;

		DhtRef dst = (DhtRef) ref.getTarget();

		if (MAX_SYMBOLIC_REF_DEPTH <= depth)
			return null; // claim it doesn't exist

		dst = loose.get(dst.getName());
		if (dst == null)
			return ref;

		dst = resolve(dst, depth + 1, loose);
		if (dst == null)
			return null;

		return new DhtSymbolicRef(
				ref.getName(),
				dst,
				((DhtSymbolicRef) ref).getRefData());
	}

	@Override
	public Ref peel(Ref ref) throws IOException {
		final Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null)
			return ref;

		DhtRef newLeaf = doPeel(oldLeaf);

		RefCache cur = readRefs();
		int idx = cur.ids.find(oldLeaf.getName());
		if (0 <= idx && cur.ids.get(idx) == oldLeaf) {
			RefList<DhtRef> newList = cur.ids.set(idx, newLeaf);
			if (cache.compareAndSet(cur, new RefCache(newList, cur)))
				cachePeeledState(oldLeaf, newLeaf);
		}

		return recreate(ref, newLeaf);
	}

	private void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
		// TODO(spearce) Use an ExecutorService here
		try {
			RepositoryKey repo = repository.getRepositoryKey();
			RefKey key = RefKey.create(repo, newLeaf.getName());
			RefData oldData = ((DhtRef) oldLeaf).getRefData();
			RefData newData = ((DhtRef) newLeaf).getRefData();
			db.ref().compareAndPut(key, oldData, newData);
		} catch (TimeoutException e) {
			// Ignore a timeout here, we were only trying to update
			// a cached value to save peeling costs in the future.

		} catch (DhtException e) {
			// Ignore a database error, this was only an attempt to
			// fix a value that could be cached to save time later.
		}
	}

	private DhtRef doPeel(final Ref leaf) throws MissingObjectException,
			IOException {
		RevWalk rw = new RevWalk(getRepository());
		try {
			DhtReader ctx = (DhtReader) rw.getObjectReader();
			RevObject obj = rw.parseAny(leaf.getObjectId());
			RefData.Builder d = RefData.newBuilder(((DhtRef) leaf).getRefData());

			ChunkKey oKey = ctx.findChunk(leaf.getObjectId());
			if (oKey != null)
				d.getTargetBuilder().setChunkKey(oKey.asString());
			else
				d.getTargetBuilder().clearChunkKey();

			if (obj instanceof RevTag) {
				ObjectId pId = rw.peel(obj);
				d.getPeeledBuilder().setObjectName(pId.name());

				ChunkKey pKey = ctx.findChunk(pId);
				if (pKey != null)
					d.getPeeledBuilder().setChunkKey(pKey.asString());
				else
					d.getPeeledBuilder().clearChunkKey();
			} else {
				d.clearPeeled();
			}

			d.setIsPeeled(true);
			d.setSequence(d.getSequence() + 1);
			return new DhtObjectIdRef(leaf.getName(), d.build());
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
		boolean detachingSymbolicRef = false;
		DhtRef ref = getOneRef(refName);
		if (ref == null)
			ref = new DhtObjectIdRef(refName, NONE);
		else
			detachingSymbolicRef = detach && ref.isSymbolic();

		if (detachingSymbolicRef) {
			RefData src = ((DhtRef) ref.getLeaf()).getRefData();
			RefData.Builder b = RefData.newBuilder(ref.getRefData());
			b.clearSymref();
			b.setTarget(src.getTarget());
			ref = new DhtObjectIdRef(refName, b.build());
		}

		RepositoryKey repo = repository.getRepositoryKey();
		DhtRefUpdate update = new DhtRefUpdate(this, repo, db, ref);
		if (detachingSymbolicRef)
			update.setDetachingSymbolicRef();
		return update;
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
		RefList<DhtRef> all = readRefs().ids;

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

	void stored(String refName, RefData newData) {
		DhtRef ref = fromData(refName, newData);
		RefCache oldCache, newCache;
		do {
			oldCache = cache.get();
			if (oldCache == null)
				return;

			RefList<DhtRef> ids = oldCache.ids.put(ref);
			RefList<DhtRef> sym = oldCache.sym;

			if (ref.isSymbolic()) {
				sym = sym.put(ref);
			} else {
				int p = sym.find(refName);
				if (0 <= p)
					sym = sym.remove(p);
			}

			newCache = new RefCache(ids, sym, oldCache.hints);
		} while (!cache.compareAndSet(oldCache, newCache));
	}

	void removed(String refName) {
		RefCache oldCache, newCache;
		do {
			oldCache = cache.get();
			if (oldCache == null)
				return;

			int p;

			RefList<DhtRef> ids = oldCache.ids;
			p = ids.find(refName);
			if (0 <= p)
				ids = ids.remove(p);

			RefList<DhtRef> sym = oldCache.sym;
			p = sym.find(refName);
			if (0 <= p)
				sym = sym.remove(p);

			newCache = new RefCache(ids, sym, oldCache.hints);
		} while (!cache.compareAndSet(oldCache, newCache));
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
		RefList.Builder<DhtRef> id = new RefList.Builder<DhtRef>();
		RefList.Builder<DhtRef> sym = new RefList.Builder<DhtRef>();
		ObjectIdSubclassMap<IdWithChunk> hints = new ObjectIdSubclassMap<IdWithChunk>();

		for (Map.Entry<RefKey, RefData> e : scan()) {
			DhtRef ref = fromData(e.getKey().getName(), e.getValue());

			if (ref.isSymbolic())
				sym.add(ref);
			id.add(ref);

			if (ref.getObjectId() instanceof IdWithChunk
					&& !hints.contains(ref.getObjectId()))
				hints.add((IdWithChunk) ref.getObjectId());
			if (ref.getPeeledObjectId() instanceof IdWithChunk
					&& !hints.contains(ref.getPeeledObjectId()))
				hints.add((IdWithChunk) ref.getPeeledObjectId());
		}

		id.sort();
		sym.sort();

		return new RefCache(id.toRefList(), sym.toRefList(), hints);
	}

	static DhtRef fromData(String name, RefData data) {
		if (data.hasSymref())
			return new DhtSymbolicRef(name, data);
		else
			return new DhtObjectIdRef(name, data);
	}

	private static ObjectId idFrom(RefData.Id src) {
		ObjectId id = ObjectId.fromString(src.getObjectName());
		if (!src.hasChunkKey())
			return id;
		return new IdWithChunk(id, ChunkKey.fromString(src.getChunkKey()));
	}

	private Set<Map.Entry<RefKey, RefData>> scan() throws DhtException,
			TimeoutException {
		// TODO(spearce) Do we need to perform READ_REPAIR here?
		RepositoryKey repo = repository.getRepositoryKey();
		return db.ref().getAll(Context.LOCAL, repo).entrySet();
	}

	private static class RefCache {
		final RefList<DhtRef> ids;

		final RefList<DhtRef> sym;

		final ObjectIdSubclassMap<IdWithChunk> hints;

		RefCache(RefList<DhtRef> ids, RefList<DhtRef> sym,
				ObjectIdSubclassMap<IdWithChunk> hints) {
			this.ids = ids;
			this.sym = sym;
			this.hints = hints;
		}

		RefCache(RefList<DhtRef> ids, RefCache old) {
			this(ids, old.sym, old.hints);
		}
	}

	static interface DhtRef extends Ref {
		RefData getRefData();
	}

	private static class DhtSymbolicRef extends SymbolicRef implements DhtRef {
		private final RefData data;

		DhtSymbolicRef(String refName,RefData data) {
			super(refName, new DhtObjectIdRef(data.getSymref(), NONE));
			this.data = data;
		}

		DhtSymbolicRef(String refName, Ref target, RefData data) {
			super(refName, target);
			this.data = data;
		}

		public RefData getRefData() {
			return data;
		}
	}

	private static class DhtObjectIdRef implements DhtRef {
		private final String name;
		private final RefData data;
		private final ObjectId objectId;
		private final ObjectId peeledId;

		DhtObjectIdRef(String name, RefData data) {
			this.name = name;
			this.data = data;
			this.objectId = data.hasTarget() ? idFrom(data.getTarget()) : null;
			this.peeledId = data.hasPeeled() ? idFrom(data.getPeeled()) : null;
		}

		public String getName() {
			return name;
		}

		public boolean isSymbolic() {
			return false;
		}

		public Ref getLeaf() {
			return this;
		}

		public Ref getTarget() {
			return this;
		}

		public ObjectId getObjectId() {
			return objectId;
		}

		public Ref.Storage getStorage() {
			return data.hasTarget() ? LOOSE : NEW;
		}

		public boolean isPeeled() {
			return data.getIsPeeled();
		}

		public ObjectId getPeeledObjectId() {
			return peeledId;
		}

		public RefData getRefData() {
			return data;
		}
	}
}
