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

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.storage.dht.TinyProtobuf.encode;

import java.util.Arrays;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.dht.TinyProtobuf.Encoder;

/**
 * Describes the current state of a Git reference.
 * <p>
 * The reference state contains not just the SHA-1 object name that a reference
 * points to, but the state also caches its peeled value if its a tag, and the
 * {@link ChunkKey} the object was observed in when the reference was last
 * updated. This cached data reduces latency when initially starting to work
 * with a repository.
 */
public class RefData {
	/** Magic constant meaning does not exist. */
	public static final RefData NONE = new RefData(new byte[0]);

	static final int TAG_SYMREF = 1;

	static final int TAG_TARGET = 2;

	static final int TAG_IS_PEELED = 3;

	static final int TAG_PEELED = 4;

	/**
	 * @param data
	 * @return the content
	 */
	public static RefData fromBytes(byte[] data) {
		return new RefData(data);
	}

	static RefData symbolic(String target) {
		Encoder e = encode(2 + target.length());
		e.string(TAG_SYMREF, target);
		return new RefData(e.asByteArray());
	}

	static RefData id(AnyObjectId id) {
		Encoder e = encode(4 + OBJECT_ID_STRING_LENGTH + ChunkKey.KEYLEN);
		e.message(TAG_TARGET, IdWithChunk.encode(id));
		return new RefData(e.asByteArray());
	}

	static RefData fromRef(Ref ref) {
		if (ref.isSymbolic())
			return symbolic(ref.getTarget().getName());

		if (ref.getObjectId() == null)
			return RefData.NONE;

		int max = 8 + 2 * OBJECT_ID_STRING_LENGTH + 2 * ChunkKey.KEYLEN;
		Encoder e = encode(max);
		e.message(TAG_TARGET, IdWithChunk.encode(ref.getObjectId()));
		if (ref.isPeeled()) {
			e.bool(TAG_IS_PEELED, true);
			if (ref.getPeeledObjectId() != null)
				e.message(TAG_PEELED,
						IdWithChunk.encode(ref.getPeeledObjectId()));
		}
		return new RefData(e.asByteArray());
	}

	static RefData peeled(ObjectId targetId, ObjectId peeledId) {
		int max = 8 + 2 * OBJECT_ID_STRING_LENGTH + 2 * ChunkKey.KEYLEN;
		Encoder e = encode(max);
		e.message(TAG_TARGET, IdWithChunk.encode(targetId));
		e.bool(TAG_IS_PEELED, true);
		if (peeledId != null)
			e.message(TAG_PEELED, IdWithChunk.encode(peeledId));
		return new RefData(e.asByteArray());
	}

	private final byte[] data;

	RefData(byte[] data) {
		this.data = data;
	}

	TinyProtobuf.Decoder decode() {
		return TinyProtobuf.decode(data);
	}

	/** @return the contents, encoded as a byte array for storage. */
	public byte[] asBytes() {
		return data;
	}

	@Override
	public int hashCode() {
		int hash = 5381;
		for (int ptr = 0; ptr < data.length; ptr++)
			hash = ((hash << 5) + hash) + (data[ptr] & 0xff);
		return hash;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof RefData)
			return Arrays.equals(data, ((RefData) other).data);
		return false;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		TinyProtobuf.Decoder d = decode();
		for (;;) {
			switch (d.next()) {
			case 0:
				return b.toString().substring(1);
			case TAG_SYMREF:
				b.append("\nsymref: ").append(d.string());
				continue;
			case TAG_TARGET:
				b.append("\ntarget: ").append(IdWithChunk.decode(d.message()));
				continue;
			case TAG_IS_PEELED:
				b.append("\nis_peeled: ").append(d.bool());
				continue;
			case TAG_PEELED:
				b.append("\npeeled: ").append(IdWithChunk.decode(d.message()));
				continue;
			default:
				d.skip();
				continue;
			}
		}
	}

	static class IdWithChunk extends ObjectId {
		static ObjectId decode(TinyProtobuf.Decoder d) {
			ObjectId id = null;
			ChunkKey key = null;
			DECODE: for (;;) {
				switch (d.next()) {
				case 0:
					break DECODE;
				case 1:
					id = d.stringObjectId();
					continue;
				case 2:
					key = ChunkKey.fromBytes(d.bytes());
					continue;
				default:
					d.skip();
				}
			}
			return key != null ? new IdWithChunk(id, key) : id;
		}

		static TinyProtobuf.Encoder encode(AnyObjectId id) {
			if (id instanceof IdWithChunk) {
				int max = 4 + OBJECT_ID_STRING_LENGTH + ChunkKey.KEYLEN;
				TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
				e.string(1, id);
				e.string(2, ((IdWithChunk) id).chunkKey);
				return e;
			} else {
				int max = 2 + OBJECT_ID_STRING_LENGTH;
				TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
				e.string(1, id);
				return e;
			}
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
}
