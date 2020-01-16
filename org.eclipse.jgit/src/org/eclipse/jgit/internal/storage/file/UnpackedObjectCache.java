/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/** Remembers objects that are currently unpacked. */
class UnpackedObjectCache {
	private static final int INITIAL_BITS = 5; // size = 32

	private static final int MAX_BITS = 11; // size = 2048

	private volatile Table table;

	UnpackedObjectCache() {
		table = new Table(INITIAL_BITS);
	}

	boolean isUnpacked(AnyObjectId objectId) {
		return table.contains(objectId);
	}

	void add(AnyObjectId objectId) {
		Table t = table;
		if (t.add(objectId)) {
			// The object either already exists in the table, or was
			// successfully added. Either way leave the table alone.
			//
		} else {
			// The object won't fit into the table. Implement a crude
			// cache removal by just dropping the table away, but double
			// it in size for the next incarnation.
			//
			Table n = new Table(Math.min(t.bits + 1, MAX_BITS));
			n.add(objectId);
			table = n;
		}
	}

	void remove(AnyObjectId objectId) {
		if (isUnpacked(objectId))
			clear();
	}

	void clear() {
		table = new Table(INITIAL_BITS);
	}

	private static class Table {
		private static final int MAX_CHAIN = 8;

		private final AtomicReferenceArray<ObjectId> ids;

		private final int shift;

		final int bits;

		Table(int bits) {
			this.ids = new AtomicReferenceArray<>(1 << bits);
			this.shift = 32 - bits;
			this.bits = bits;
		}

		boolean contains(AnyObjectId toFind) {
			int i = index(toFind);
			for (int n = 0; n < MAX_CHAIN; n++) {
				ObjectId obj = ids.get(i);
				if (obj == null)
					break;

				if (AnyObjectId.isEqual(obj, toFind))
					return true;

				if (++i == ids.length())
					i = 0;
			}
			return false;
		}

		boolean add(AnyObjectId toAdd) {
			int i = index(toAdd);
			for (int n = 0; n < MAX_CHAIN;) {
				ObjectId obj = ids.get(i);
				if (obj == null) {
					if (ids.compareAndSet(i, null, toAdd.copy())) {
						return true;
					}
					continue;
				}

				if (AnyObjectId.isEqual(obj, toAdd)) {
					return true;
				}

				if (++i == ids.length()) {
					i = 0;
				}
				n++;
			}
			return false;
		}

		private int index(AnyObjectId id) {
			return id.hashCode() >>> shift;
		}
	}
}
