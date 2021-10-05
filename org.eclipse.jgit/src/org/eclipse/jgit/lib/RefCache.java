/*
 * Copyright (C) 2021, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Interface to notify a ref cache about ref updates
 *
 * @since 5.12.1
 */
public interface RefCache {

	/**
	 * Get the lock guarding read and write access to the cache
	 *
	 * @return lock guarding read and write access to the cache
	 */
	ReadWriteLock getLock();

	/**
	 * Reload complete cache content from the underlying RefDatabase
	 *
	 * @return size of the cache after reloading
	 */
	int reload();

	/**
	 * Update selected cache content from the underlying RefDatabase
	 *
	 * @param reload
	 *            name of refs to reload, updates cache entry if found in the
	 *            underlying RefDatabase, otherwise it is deleted
	 * @param delete
	 *            name of refs to delete
	 * @return size of cache after updating
	 */
	int update(Iterable<String> reload, Iterable<String> delete);

	/**
	 * Replace complete cache content with new entries from given iterable
	 *
	 * @param newRefs
	 * @return size of the cache after replacing
	 */
	int replace(Iterable<Entry<String, Ref>> newRefs);

	/**
	 * Insert Ref which was newly created or updated
	 *
	 * @param ref
	 *            a ref to insert into the cache
	 */
	void insert(Ref ref);

	/**
	 * Ref was deleted
	 *
	 * @param refName
	 *            full name of ref to be deleted
	 */
	void delete(String refName);

	/**
	 * Ref was renamed
	 *
	 * @param oldRef
	 *            the old ref before the rename
	 * @param newRef
	 *            the new ref after the rename
	 */
	void rename(Ref oldRef, Ref newRef);

}