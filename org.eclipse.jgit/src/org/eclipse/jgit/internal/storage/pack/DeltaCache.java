/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

import org.eclipse.jgit.storage.pack.PackConfig;

class DeltaCache {
	private final long size;

	private final int entryLimit;

	private final ReferenceQueue<byte[]> queue;

	private long used;

	DeltaCache(PackConfig pc) {
		size = pc.getDeltaCacheSize();
		entryLimit = pc.getDeltaCacheLimit();
		queue = new ReferenceQueue<>();
	}

	boolean canCache(int length, ObjectToPack src, ObjectToPack res) {
		// If the cache would overflow, don't store.
		//
		if (0 < size && size < used + length) {
			checkForGarbageCollectedObjects();
			if (0 < size && size < used + length)
				return false;
		}

		if (length < entryLimit) {
			used += length;
			return true;
		}

		// If the combined source files are multiple megabytes but the delta
		// is on the order of a kilobyte or two, this was likely costly to
		// construct. Cache it anyway, even though its over the limit.
		//
		if (length >> 10 < (src.getWeight() >> 20) + (res.getWeight() >> 21)) {
			used += length;
			return true;
		}

		return false;
	}

	void credit(int reservedSize) {
		used -= reservedSize;
	}

	Ref cache(byte[] data, int actLen, int reservedSize) {
		// The caller may have had to allocate more space than is
		// required. If we are about to waste anything, shrink it.
		//
		data = resize(data, actLen);

		// When we reserved space for this item we did it for the
		// inflated size of the delta, but we were just given the
		// compressed version. Adjust the cache cost to match.
		//
		if (reservedSize != data.length) {
			used -= reservedSize;
			used += data.length;
		}
		return new Ref(data, queue);
	}

	byte[] resize(byte[] data, int actLen) {
		if (data.length != actLen) {
			byte[] nbuf = new byte[actLen];
			System.arraycopy(data, 0, nbuf, 0, actLen);
			data = nbuf;
		}
		return data;
	}

	private void checkForGarbageCollectedObjects() {
		Ref r;
		while ((r = (Ref) queue.poll()) != null)
			used -= r.cost;
	}

	static class Ref extends SoftReference<byte[]> {
		final int cost;

		Ref(byte[] array, ReferenceQueue<byte[]> queue) {
			super(array, queue);
			cost = array.length;
		}
	}
}
