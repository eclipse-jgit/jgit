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

import static org.eclipse.jgit.storage.dht.KeyUtils.format16;
import static org.eclipse.jgit.storage.dht.KeyUtils.format32;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse16;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse32;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import java.text.MessageFormat;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;

/** Unique identifier of a {@link PackChunk} in the DHT. */
public class ChunkKey implements RowKey {
	/** Earliest possible period. */
	private static final long PERIOD_EPOCH = 1262304000000L; // 2010-01-01 GMT

	/**
	 * Approximate number of milliseconds per week.
	 * <p>
	 * This value doesn't correctly account for leap seconds. Nor does it
	 * account for leap years. They way period is used, this is an acceptable
	 * loss in accuracy.
	 */
	private static final long MS_PER_WEEK = 7L * 24 * 3600 * 1000;

	static final int KEYLEN = 59;

	/**
	 * @param repo
	 * @param chunk
	 * @return the key
	 */
	public static ChunkKey create(RepositoryKey repo, ObjectId chunk) {
		short bucket = bucket(chunk);
		short period = current();
		return new ChunkKey(bucket, period, repo.asInt(), chunk);
	}

	private static short bucket(ObjectId chunk) {
		return (short) ((chunk.getByte(0) << 8) | (chunk.getByte(1)));
	}

	private static short current() {
		long t = SystemReader.getInstance().getCurrentTime() - PERIOD_EPOCH;
		if (t < 0)
			return 0;
		return (short) (t / MS_PER_WEEK);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static ChunkKey fromBytes(byte[] key) {
		if (key.length != KEYLEN)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidChunkKey, decode(key)));

		short bucket = parse16(key, 0);
		short period = parse16(key, 5);
		int repo = parse32(key, 10);
		ObjectId chunk = ObjectId.fromString(key, 19);
		return new ChunkKey(bucket, period, repo, chunk);
	}

	static ChunkKey fromChunk(ChunkKey container, byte[] raw, int ptr) {
		int repo = container.repo;
		short period = (short) NB.decodeUInt16(raw, ptr);
		ObjectId chunk = ObjectId.fromRaw(raw, ptr + 2);
		short bucket = bucket(chunk);
		return new ChunkKey(bucket, period, repo, chunk);
	}

	private final short bucket;

	private final short period;

	private final int repo;

	private final ObjectId chunk;

	ChunkKey(short bucket, short period, int repo, ObjectId chunk) {
		this.bucket = bucket;
		this.period = period;
		this.repo = repo;
		this.chunk = chunk;
	}

	boolean isFor(RepositoryKey other) {
		return this.repo == other.asInt();
	}

	ObjectId getChunkHash() {
		return chunk;
	}

	void toChunk(byte[] dst, int ptr) {
		NB.encodeInt16(dst, ptr, period);
		chunk.copyRawTo(dst, ptr + 2);
	}

	public byte[] asByteArray() {
		byte[] r = new byte[KEYLEN];
		format16(r, 0, bucket);
		r[4] = '.';
		format16(r, 5, period);
		r[9] = '.';
		format32(r, 10, repo);
		r[18] = '.';
		chunk.copyTo(r, 19);
		return r;
	}

	long getApproximateCreationTime() {
		return PERIOD_EPOCH + (period * MS_PER_WEEK);
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
			return thisChunk.bucket == otherChunk.bucket
					&& thisChunk.period == otherChunk.period
					&& thisChunk.repo == otherChunk.repo
					&& thisChunk.chunk.equals(otherChunk.chunk);
		}
		return false;
	}

	@Override
	public String toString() {
		return "chunk:" + decode(asByteArray());
	}
}
