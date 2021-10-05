/*
 * Copyright (C) 2021, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;

import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefCache;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;

/**
 * An in-memory RefDatabase which can be used as a cache for another wrapped
 * RefDatabase
 */
public class InMemoryRefDatabase extends RefDatabase
		implements RefCache {

	private final RefDatabase refDb;

	private final TernarySearchTree<Ref> cache;

	/**
	 * @param repo
	 *            the repository
	 */
	public InMemoryRefDatabase(Repository repo) {
		this.refDb = repo.getRefDatabase();
		this.cache = new TernarySearchTree<>();
	}

	/**
	 * Reload all refs using the loader
	 *
	 * @return number of reloaded refs
	 */
	public int reload() {
		try {
			return cache.reload(refDb.getRefs(ALL).entrySet());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void create() throws IOException {
		refDb.create();
		reload();
	}

	@Override
	public void close() {
		refDb.close();
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		return refDb.isNameConflicting(name);
	}

	@Override
	public RefUpdate newUpdate(String name, boolean detach)
			throws IOException {
		RefUpdate update = refDb.newUpdate(name, detach);
		update.setRefCache(this);
		return update;
	}

	@Override
	public BatchRefUpdate newBatchUpdate() {
		BatchRefUpdate batchUpdate = refDb.newBatchUpdate();
		batchUpdate.setRefCache(this);
		return batchUpdate;
	}

	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		RefRename rename = refDb.newRename(fromName, toName);
		rename.setRefCache(this);
		return rename;
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		Ref ref = readAndResolve(name);
		return ref;
	}

	private Ref readAndResolve(String name) throws IOException {
		Ref ref = cache.get(name);
		if (ref != null) {
			ref = resolve(ref, 0);
		}
		return ref;
	}

	private Ref resolve(final Ref ref, int depth)
			throws IOException {
		if (ref.isSymbolic()) {
			Ref dst = ref.getTarget();
			if (MAX_SYMBOLIC_REF_DEPTH <= depth) {
				return null; // claim it doesn't exist
			}

			dst = cache.get(dst.getName());
			if (dst == null) {
				return ref;
			}

			dst = resolve(dst, depth + 1);
			if (dst == null) {
				return null;
			}
			return new SymbolicRef(ref.getName(), dst);
		}
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		Map<String, Ref> refs = new HashMap<>();
		for (String key : cache.getKeysWithPrefix(prefix)) {
			Ref ref = readAndResolve(key);
			refs.put(toRefName(prefix, key), ref);
		}
		return refs;
	}

	private String toRefName(String prefix, String name) {
		if (0 < prefix.length()) {
			name = prefix + name;
		}
		return name;
	}

	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		List<Ref> addtlRefs = new LinkedList<>();
		for (String name : additionalRefsNames) {
			Ref r = exactRef(name);
			if (r != null) {
				addtlRefs.add(r);
			}
		}
		return addtlRefs;
	}

	@Override
	public Ref peel(Ref ref) throws IOException {
		return ref.getLeaf();
	}

	/**
	 * Write reflog, use only in tests
	 *
	 * @param force
	 * @param update
	 * @param msg
	 * @param deref
	 * @throws IOException
	 */
	public void log(boolean force, RefUpdate update, String msg, boolean deref)
			throws IOException {
		if (refDb instanceof RefDirectory) {
			((RefDirectory) refDb).log(force, update, msg, deref);
		}
	}

	@Override
	public ReadWriteLock getLock() {
		return cache.getLock();
	}

	@Override
	public void reload(Iterable<Entry<String, Ref>> newRefs) {
		cache.reload(newRefs);
	}

	@Override
	public void insert(Ref ref) {
		cache.insert(ref.getName(), ref);
		if (ref.isSymbolic()) {
			Ref leaf = ref.getLeaf();
			cache.insert(leaf.getName(), leaf);
		}
	}

	@Override
	public void delete(String refName) {
		cache.delete(refName);
	}

	@Override
	public void rename(Ref oldRef, Ref newRef) {
		cache.delete(oldRef.getName());
		cache.insert(newRef.getName(), newRef);
	}
}
