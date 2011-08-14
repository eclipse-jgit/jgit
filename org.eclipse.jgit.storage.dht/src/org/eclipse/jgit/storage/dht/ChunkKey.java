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

import static org.eclipse.jgit.storage.dht.KeyUtils.format32;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse32;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import java.text.MessageFormat;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/** Unique identifier of a {@link PackChunk} in the DHT. */
public final class ChunkKey implements RowKey {
	static final int KEYLEN = 49;

	/**
	 * @param repo
	 * @param chunk
	 * @return the key
	 */
	public static ChunkKey create(RepositoryKey repo, ObjectId chunk) {
		return new ChunkKey(repo.asInt(), chunk);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static ChunkKey fromBytes(byte[] key) {
		return fromBytes(key, 0, key.length);
	}

	/**
	 * @param key
	 * @param ptr
	 * @param len
	 * @return the key
	 */
	public static ChunkKey fromBytes(byte[] key, int ptr, int len) {
		if (len != KEYLEN)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidChunkKey, decode(key, ptr, ptr + len)));

		int repo = parse32(key, ptr);
		ObjectId chunk = ObjectId.fromString(key, ptr + 9);
		return new ChunkKey(repo, chunk);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static ChunkKey fromString(String key) {
		return fromBytes(Constants.encodeASCII(key));
	}

	private final int repo;

	private final ObjectId chunk;

	ChunkKey(int repo, ObjectId chunk) {
		this.repo = repo;
		this.chunk = chunk;
	}

	/** @return the repository that contains the chunk. */
	public RepositoryKey getRepositoryKey() {
		return RepositoryKey.fromInt(repo);
	}

	int getRepositoryId() {
		return repo;
	}

	/** @return unique SHA-1 describing the chunk. */
	public ObjectId getChunkHash() {
		return chunk;
	}

	public byte[] asBytes() {
		byte[] r = new byte[KEYLEN];
		format32(r, 0, repo);
		r[8] = '.';
		chunk.copyTo(r, 9);
		return r;
	}

	public String asString() {
		return decode(asBytes());
	}

	@Override
	public int hashCode() {
		return chunk.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof ChunkKey) {
			ChunkKey thisChunk = this;
			ChunkKey otherChunk = (ChunkKey) other;
			return thisChunk.repo == otherChunk.repo
					&& thisChunk.chunk.equals(otherChunk.chunk);
		}
		return false;
	}

	@Override
	public String toString() {
		return "chunk:" + asString();
	}
}
