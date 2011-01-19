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

import org.eclipse.jgit.lib.ObjectId;

/** Key for part of an ObjectList. */
public final class ObjectListChunkKey implements RowKey {
	private static final int KEYLEN = 58;

	/**
	 * @param key
	 * @return the key
	 */
	public static ObjectListChunkKey fromBytes(byte[] key) {
		if (key.length != KEYLEN)
			throw new IllegalArgumentException(MessageFormat.format(DhtText
					.get().invalidChunkKey, decode(key)));

		int repo = parse32(key, 0);
		ObjectId id = ObjectId.fromString(key, 9);
		int chunk = parse32(key, 50);
		return new ObjectListChunkKey(repo, id, chunk);
	}

	private final int repo;

	private final ObjectId id;

	private final int chunk;

	ObjectListChunkKey(int repo, ObjectId id, int chunk) {
		this.repo = repo;
		this.id = id;
		this.chunk = chunk;
	}

	ObjectListChunkKey(RepositoryKey repo, ObjectId id, int chunk) {
		this.repo = repo.asInt();
		this.id = id;
		this.chunk = chunk;
	}

	int getObjectType() {
		return chunk >>> 29;
	}

	public byte[] toBytes() {
		byte[] r = new byte[KEYLEN];
		format32(r, 0, repo);
		r[8] = '.';
		id.copyTo(r, 9);
		r[49] = '.';
		format32(r, 50, chunk);
		return r;
	}

	@Override
	public int hashCode() {
		return chunk;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof ObjectListChunkKey) {
			ObjectListChunkKey k = (ObjectListChunkKey) other;
			return repo == k.repo && id.equals(k.id) && chunk == k.chunk;
		}
		return false;
	}

	@Override
	public String toString() {
		return "object-list:" + decode(toBytes());
	}
}