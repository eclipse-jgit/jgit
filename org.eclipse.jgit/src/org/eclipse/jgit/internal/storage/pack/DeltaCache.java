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
