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

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefCache;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

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
		reload();
	}

	@Override
	public void close() {
		refDb.close();
		cache.clear();
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
		return refDb.newRename(fromName, toName);
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		Ref ref = cache.get(name);
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		Map<String, Ref> refs = new HashMap<>();
		for (String key : cache.getKeysWithPrefix(prefix)) {
			refs.put(key, cache.get(key));
		}
		return refs;
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

	@Override
	public void onUpdated(RefUpdate updated, Result result) {
		cache.insert(updated.getName(), updated.getRef());

	}

	@Override
	public void onDeleted(RefUpdate deleted, Result result) {
		cache.delete(deleted.getName());
	}

	@Override
	public void onLinked(RefUpdate linked, Result result) {
		cache.insert(linked.getName(), linked.getRef());
	}

	@Override
	public void onRenamed(RefUpdate src, RefUpdate dst, Result status) {
		cache.delete(src.getName());
		cache.insert(dst.getName(), dst.getRef());
	}

	@Override
	public void onBatchUpdated(Iterable<Entry<String, Ref>> newRefs) {
		cache.reload(newRefs);
	}

	@Override
	public ReadWriteLock getLock() {
		return cache.getLock();
	}
}
