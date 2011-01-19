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

package org.eclipse.jgit.storage.dht;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;

/**
 * Writes objects for a pack stream, but re-orders by chunk locality.
 * <p>
 * By re-ordering objects according to chunk locality, and then the original
 * order the PackWriter intended to use, objects can be copied quickly from
 * chunks, and each chunk is visited at most once. A {@link Prefetcher} for the
 * {@link DhtReader} is used to fetch chunks in the order they will be used,
 * improving throughput by reducing the number of round-trips required to the
 * storage system.
 */
final class ObjectWriter {
	private final DhtReader ctx;

	private Prefetcher prefetch;

	private LinkedHashMap<ChunkKey, Integer> allVisits;

	private int curVisit;

	ObjectWriter(DhtReader ctx) {
		this.ctx = ctx;
	}

	void writeObjects(PackOutputStream out, List<DhtObjectToPack> list)
			throws IOException {
		prefetch = ctx.beginPrefetch();
		prefetch.setIncludeFragments(true);
		plan(list);

		for (ObjectToPack otp : list)
			out.writeObject(otp);
	}

	private void plan(List<DhtObjectToPack> list) {
		allVisits = new LinkedHashMap<ChunkKey, Integer>();
		curVisit = 1;

		for (DhtObjectToPack obj : list)
			visit(obj);
		prefetch.push(allVisits.keySet());
		allVisits = null;

		Collections.sort(list, new Comparator<DhtObjectToPack>() {
			public int compare(DhtObjectToPack a, DhtObjectToPack b) {
				return a.visitOrder - b.visitOrder;
			}
		});
	}

	private void visit(DhtObjectToPack obj) {
		// Plan the visit to the delta base before the object. This
		// ensures the base is in the stream first, and OFS_DELTA can
		// be used for the delta.
		//
		DhtObjectToPack base = (DhtObjectToPack) obj.getDeltaBase();
		if (base != null && base.visitOrder == 0) {
			// Use the current visit, even if its wrong. This will
			// prevent infinite recursion when there is a cycle in the
			// delta chain. Cycles are broken during writing, not in
			// the earlier planning phases.
			//
			obj.visitOrder = curVisit;
			visit(base);
		}

		ChunkKey key = obj.chunk;
		if (key != null) {
			Integer i = allVisits.get(key);
			if (i == null) {
				i = Integer.valueOf(1 + allVisits.size());
				allVisits.put(key, i);
			}
			curVisit = i.intValue();
		}

		obj.visitOrder = curVisit;
	}
}
