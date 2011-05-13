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

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.RefData;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/** Tools to work with {@link RefData}. */
public class RefDataUtil {
	/** Magic constant meaning does not exist. */
	public static final RefData NONE = RefData.newBuilder().build();

	static RefData symbolic(String target) {
		RefData.Builder b = RefData.newBuilder();
		b.setSymref(target);
		return b.build();
	}

	static RefData id(AnyObjectId id) {
		RefData.Builder b = RefData.newBuilder();
		b.setTarget(toRefData(id));
		return b.build();
	}

	static RefData fromRef(Ref ref) {
		if (ref.isSymbolic())
			return symbolic(ref.getTarget().getName());

		if (ref.getObjectId() == null)
			return NONE;

		RefData.Builder b = RefData.newBuilder();
		b.setTarget(toRefData(ref.getObjectId()));
		if (ref.isPeeled()) {
			b.setIsPeeled(true);
			if (ref.getPeeledObjectId() != null)
				b.setPeeled(toRefData(ref.getPeeledObjectId()));
		}
		return b.build();
	}

	static RefData peeled(ObjectId targetId, ObjectId peeledId) {
		RefData.Builder b = RefData.newBuilder();
		b.setTarget(toRefData(targetId));
		b.setIsPeeled(true);
		if (peeledId != null)
			b.setPeeled(toRefData(peeledId));
		return b.build();
	}

	private static RefData.Id toRefData(AnyObjectId id) {
		RefData.Id.Builder r = RefData.Id.newBuilder();
		r.setObjectName(id.name());
		if (id instanceof IdWithChunk)
			r.setChunkKey(((IdWithChunk) id).getChunkKey().asString());
		return r.build();
	}

	static class IdWithChunk extends ObjectId {
		static ObjectId create(RefData.Id src) {
			if (src.hasChunkKey()) {
				return new IdWithChunk(
						ObjectId.fromString(src.getObjectName()),
						ChunkKey.fromString(src.getChunkKey()));
			}
			return ObjectId.fromString(src.getObjectName());
		}

		private final ChunkKey chunkKey;

		IdWithChunk(AnyObjectId id, ChunkKey key) {
			super(id);
			this.chunkKey = key;
		}

		ChunkKey getChunkKey() {
			return chunkKey;
		}

		@Override
		public String toString() {
			return name() + "->" + chunkKey;
		}
	}

	private RefDataUtil() {
		// Utility class, do not create instances.
	}
}
