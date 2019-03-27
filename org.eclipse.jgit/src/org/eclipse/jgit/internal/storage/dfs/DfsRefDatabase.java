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

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.Ref.UNDEFINED_UPDATE_INDEX;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Abstract DfsRefDatabase class.
 *
 */
public abstract class DfsRefDatabase extends RefDatabase {
	private final DfsRepository repository;

	private final AtomicReference<RefCache> cache;

	/**
	 * Initialize the reference database for a repository.
	 *
	 * @param repository
	 *            the repository this database instance manages references for.
	 */
	protected DfsRefDatabase(DfsRepository repository) {
		this.repository = repository;
		this.cache = new AtomicReference<>();
	}

	/**
	 * Get the repository the database holds the references of.
	 *
	 * @return the repository the database holds the references of.
	 */
	protected DfsRepository getRepository() {
		return repository;
	}

	boolean exists() throws IOException {
		return 0 < read().size();
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		RefCache curr = read();
		Ref ref = curr.ids.get(name);
		return ref != null ? resolve(ref, 0, curr.ids) : null;
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> getAdditionalRefs() {
		return Collections.emptyList();
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		RefCache curr = read();
		RefList<Ref> packed = RefList.emptyList();
		RefList<Ref> loose = curr.ids;
		RefList.Builder<Ref> sym = new RefList.Builder<>(curr.sym.size());

		for (int idx = 0; idx < curr.sym.size(); idx++) {
			Ref ref = curr.sym.get(idx);
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

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
		final Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null)
			return ref;

		Ref newLeaf = doPeel(oldLeaf);

		RefCache cur = read();
		int idx = cur.ids.find(oldLeaf.getName());
		if (0 <= idx && cur.ids.get(idx) == oldLeaf) {
			RefList<Ref> newList = cur.ids.set(idx, newLeaf);
			cache.compareAndSet(cur, new RefCache(newList, cur));
			cachePeeledState(oldLeaf, newLeaf);
		}

		return recreate(ref, newLeaf, hasVersioning());
	}

	Ref doPeel(Ref leaf) throws MissingObjectException,
			IOException {
		try (RevWalk rw = new RevWalk(repository)) {
			RevObject obj = rw.parseAny(leaf.getObjectId());
			if (obj instanceof RevTag) {
				return new ObjectIdRef.PeeledTag(
						leaf.getStorage(),
						leaf.getName(),
						leaf.getObjectId(),
						rw.peel(obj).copy(),
						hasVersioning() ? leaf.getUpdateIndex()
								: UNDEFINED_UPDATE_INDEX);
			} else {
				return new ObjectIdRef.PeeledNonTag(
						leaf.getStorage(),
						leaf.getName(),
						leaf.getObjectId(),
						hasVersioning() ? leaf.getUpdateIndex()
								: UNDEFINED_UPDATE_INDEX);
			}
		}
	}

	static Ref recreate(Ref old, Ref leaf, boolean hasVersioning) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf, hasVersioning);
			return new SymbolicRef(old.getName(), dst,
					hasVersioning ? old.getUpdateIndex()
							: UNDEFINED_UPDATE_INDEX);
		}
		return leaf;
	}

	/** {@inheritDoc} */
	@Override
	public RefUpdate newUpdate(String refName, boolean detach)
			throws IOException {
		boolean detachingSymbolicRef = false;
		Ref ref = exactRef(refName);
		if (ref == null)
			ref = new ObjectIdRef.Unpeeled(NEW, refName, null);
		else
			detachingSymbolicRef = detach && ref.isSymbolic();

		DfsRefUpdate update = new DfsRefUpdate(this, ref);
		if (detachingSymbolicRef)
			update.setDetachingSymbolicRef();
		return update;
	}

	/** {@inheritDoc} */
	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		RefUpdate src = newUpdate(fromName, true);
		RefUpdate dst = newUpdate(toName, true);
		return new DfsRefRename(src, dst);
	}

	/** {@inheritDoc} */
	@Override
	public boolean isNameConflicting(String refName) throws IOException {
		RefList<Ref> all = read().ids;

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

	/** {@inheritDoc} */
	@Override
	public void create() {
		// Nothing to do.
	}

	/** {@inheritDoc} */
	@Override
	public void refresh() {
		clearCache();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		clearCache();
	}

	void clearCache() {
		cache.set(null);
	}

	void stored(Ref ref) {
		RefCache oldCache, newCache;
		do {
			oldCache = cache.get();
			if (oldCache == null)
				return;
			newCache = oldCache.put(ref);
		} while (!cache.compareAndSet(oldCache, newCache));
	}

	void removed(String refName) {
		RefCache oldCache, newCache;
		do {
			oldCache = cache.get();
			if (oldCache == null)
				return;
			newCache = oldCache.remove(refName);
		} while (!cache.compareAndSet(oldCache, newCache));
	}

	private RefCache read() throws IOException {
		RefCache c = cache.get();
		if (c == null) {
			c = scanAllRefs();
			cache.set(c);
		}
		return c;
	}

	/**
	 * Read all known references in the repository.
	 *
	 * @return all current references of the repository.
	 * @throws java.io.IOException
	 *             references cannot be accessed.
	 */
	protected abstract RefCache scanAllRefs() throws IOException;

	/**
	 * Compare a reference, and put if it matches.
	 * <p>
	 * Two reference match if and only if they satisfy the following:
	 *
	 * <ul>
	 * <li>If one reference is a symbolic ref, the other one should be a symbolic
	 * ref.
	 * <li>If both are symbolic refs, the target names should be same.
	 * <li>If both are object ID refs, the object IDs should be same.
	 * </ul>
	 *
	 * @param oldRef
	 *            old value to compare to. If the reference is expected to not
	 *            exist the old value has a storage of
	 *            {@link org.eclipse.jgit.lib.Ref.Storage#NEW} and an ObjectId
	 *            value of {@code null}.
	 * @param newRef
	 *            new reference to store.
	 * @return true if the put was successful; false otherwise.
	 * @throws java.io.IOException
	 *             the reference cannot be put due to a system error.
	 */
	protected abstract boolean compareAndPut(Ref oldRef, Ref newRef)
			throws IOException;

	/**
	 * Compare a reference, and delete if it matches.
	 *
	 * @param oldRef
	 *            the old reference information that was previously read.
	 * @return true if the remove was successful; false otherwise.
	 * @throws java.io.IOException
	 *             the reference could not be removed due to a system error.
	 */
	protected abstract boolean compareAndRemove(Ref oldRef) throws IOException;

	/**
	 * Update the cached peeled state of a reference
	 * <p>
	 * The ref database invokes this method after it peels a reference that had
	 * not been peeled before. This allows the storage to cache the peel state
	 * of the reference, and if it is actually peelable, the target that it
	 * peels to, so that on-the-fly peeling doesn't have to happen on the next
	 * reference read.
	 *
	 * @param oldLeaf
	 *            the old reference.
	 * @param newLeaf
	 *            the new reference, with peel information.
	 */
	protected void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
		try {
			compareAndPut(oldLeaf, newLeaf);
		} catch (IOException e) {
			// Ignore an exception during caching.
		}
	}

	/** Collection of references managed by this database. */
	public static class RefCache {
		final RefList<Ref> ids;

		final RefList<Ref> sym;

		/**
		 * Initialize a new reference cache.
		 * <p>
		 * The two reference lists supplied must be sorted in correct order
		 * (string compare order) by name.
		 *
		 * @param ids
		 *            references that carry an ObjectId, and all of {@code sym}.
		 * @param sym
		 *            references that are symbolic references to others.
		 */
		public RefCache(RefList<Ref> ids, RefList<Ref> sym) {
			this.ids = ids;
			this.sym = sym;
		}

		RefCache(RefList<Ref> ids, RefCache old) {
			this(ids, old.sym);
		}

		/** @return number of references in this cache. */
		public int size() {
			return ids.size();
		}

		/**
		 * Find a reference by name.
		 *
		 * @param name
		 *            full name of the reference.
		 * @return the reference, if it exists, otherwise null.
		 */
		public Ref get(String name) {
			return ids.get(name);
		}

		/**
		 * Obtain a modified copy of the cache with a ref stored.
		 * <p>
		 * This cache instance is not modified by this method.
		 *
		 * @param ref
		 *            reference to add or replace.
		 * @return a copy of this cache, with the reference added or replaced.
		 */
		public RefCache put(Ref ref) {
			RefList<Ref> newIds = this.ids.put(ref);
			RefList<Ref> newSym = this.sym;
			if (ref.isSymbolic()) {
				newSym = newSym.put(ref);
			} else {
				int p = newSym.find(ref.getName());
				if (0 <= p)
					newSym = newSym.remove(p);
			}
			return new RefCache(newIds, newSym);
		}

		/**
		 * Obtain a modified copy of the cache with the ref removed.
		 * <p>
		 * This cache instance is not modified by this method.
		 *
		 * @param refName
		 *            reference to remove, if it exists.
		 * @return a copy of this cache, with the reference removed.
		 */
		public RefCache remove(String refName) {
			RefList<Ref> newIds = this.ids;
			int p = newIds.find(refName);
			if (0 <= p)
				newIds = newIds.remove(p);

			RefList<Ref> newSym = this.sym;
			p = newSym.find(refName);
			if (0 <= p)
				newSym = newSym.remove(p);
			return new RefCache(newIds, newSym);
		}
	}
}
