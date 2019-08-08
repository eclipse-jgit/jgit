/*
 * Copyright (C) 2010, Google Inc.
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
					if (ids.compareAndSet(i, null, toAdd.copy()))
						return true;
					else
						continue;
				}

				if (AnyObjectId.isEqual(obj, toAdd))
					return true;

				if (++i == ids.length())
					i = 0;
				n++;
			}
			return false;
		}

		private int index(AnyObjectId id) {
			return id.hashCode() >>> shift;
		}
	}
}
