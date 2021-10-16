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

import org.eclipse.jgit.lib.RefUpdate.Result;

/**
 * Interface to notify a ref cache about ref updates
 *
 * @since 6.0
 */
public interface RefCache {

	/**
	 * Get the lock guarding read and write access to the cache
	 *
	 * @return lock guarding read and write access to the cache
	 */
	ReadWriteLock getLock();

	/**
	 * Ref was updated
	 *
	 * @param updated
	 *            the RefUpdate
	 * @param status
	 *            result of the update
	 */
	void onUpdated(RefUpdate updated, Result status);

	/**
	 * Refs were updated in a batch
	 *
	 * @param newRefs
	 */
	void onBatchUpdated(Iterable<Entry<String, Ref>> newRefs);

	/**
	 * Ref was deleted
	 *
	 * @param deleted
	 *            the RefUpdate that deleted this ref
	 * @param status
	 *            result of the update
	 */
	void onDeleted(RefUpdate deleted, Result status);

	/**
	 * Ref was linked
	 *
	 * @param linked
	 *            RefUpdate that linked this ref
	 * @param status
	 *            result of the update
	 */
	void onLinked(RefUpdate linked, Result status);

	/**
	 * Ref was renamed
	 * @param src old name
	 * @param dst new name
	 * @param status result of the rename
	 */
	void onRenamed(RefUpdate src, RefUpdate dst, Result status);

}