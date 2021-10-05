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

import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * An in-memory RefDatabase which can be used as a cache for another wrapped
 * RefDatabase
 */
public class InMemoryRefDatabase extends RefDatabase
		implements RefsChangedListener {

	private final Repository repo;

	private final RefDatabase refDb;

	private final TernarySearchTree<Ref> refCache;

	private final TernarySearchTree.Loader<Ref> loader;

	private ListenerHandle listener;

	/**
	 * @param repo
	 *            the repository
	 */
	public InMemoryRefDatabase(Repository repo) {
		this.repo = repo;
		this.refDb = repo.getRefDatabase();
		this.refCache = new TernarySearchTree<>();
		this.loader = new TernarySearchTree.Loader<>() {

			@Override
			public Map<String, Ref> loadAll() {
				try {
					return refDb.getRefs(ALL);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}

	@Override
	public void create() throws IOException {
		refCache.reload(loader);
		listener = repo.getListenerList().addRefsChangedListener(this);
	}

	@Override
	public void close() {
		refDb.close();
		refCache.clear();
		listener.remove();
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		return refDb.isNameConflicting(name);
	}

	@Override
	public RefUpdate newUpdate(String name, boolean detach)
			throws IOException {
		return refDb.newUpdate(name, detach);
	}

	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		return refDb.newRename(fromName, toName);
	}

	@Override
	public Ref exactRef(String name) throws IOException {
		Ref ref = refCache.get(name);
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		Map<String, Ref> refs = new HashMap<>();
		for (String key : refCache.getKeysWithPrefix(prefix)) {
			refs.put(key, refCache.get(key));
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
	public void onRefsChanged(RefsChangedEvent event) {
		refCache.reload(loader);
	}

}
