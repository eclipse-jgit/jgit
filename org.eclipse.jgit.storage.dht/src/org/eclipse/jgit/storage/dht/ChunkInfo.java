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

import org.eclipse.jgit.lib.Constants;

/**
 * Summary information about a chunk owned by a repository.
 */
public class ChunkInfo {
	/** Source the chunk (what code path created it). */
	public static enum Source implements TinyProtobuf.Enum {
		/** Came in over the network from an external source */
		RECEIVE(1),
		/** Created in this repository (e.g. a merge). */
		INSERT(2),
		/** Generated during a repack of this repository. */
		REPACK(3);

		private final int value;

		Source(int val) {
			this.value = val;
		}

		public int value() {
			return value;
		}
	}

	/** Mixed objects are stored in the chunk (instead of single type). */
	public static final int OBJ_MIXED = 0;

	/**
	 * Parse info from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the link points to.
	 * @param raw
	 *            the raw encoding of the info.
	 * @return the info object.
	 */
	public static ChunkInfo fromBytes(ChunkKey chunkKey, byte[] raw) {
		ChunkInfo info = new ChunkInfo();
		info.chunkKey = chunkKey;

		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				info.source = d.intEnum(Source.values());
				continue;
			case 2:
				info.objectType = d.int32();
				continue;
			case 3:
				info.fragment = d.bool();
				continue;
			case 4:
				info.cachedPack = CachedPackKey.fromBytes(d);
				continue;

			case 5: {
				TinyProtobuf.Decoder m = d.message();
				for (;;) {
					switch (m.next()) {
					case 0:
						continue PARSE;
					case 1:
						info.objectsTotal = m.int32();
						continue;
					case 2:
						info.objectsWhole = m.int32();
						continue;
					case 3:
						info.objectsOfsDelta = m.int32();
						continue;
					case 4:
						info.objectsRefDelta = m.int32();
						continue;
					default:
						m.skip();
						continue;
					}
				}
			}
			case 6:
				info.chunkSize = d.int32();
				continue;
			case 7:
				info.indexSize = d.int32();
				continue;
			case 8:
				info.metaSize = d.int32();
				continue;
			default:
				d.skip();
				continue;
			}
		}
		return info;
	}

	private static byte[] asBytes(ChunkInfo info) {
		TinyProtobuf.Encoder objects = TinyProtobuf.encode(48);
		objects.int32IfNotZero(1, info.objectsTotal);
		objects.int32IfNotZero(2, info.objectsWhole);
		objects.int32IfNotZero(3, info.objectsOfsDelta);
		objects.int32IfNotZero(4, info.objectsRefDelta);

		TinyProtobuf.Encoder e = TinyProtobuf.encode(128);
		e.intEnum(1, info.source);
		e.int32IfNotNegative(2, info.objectType);
		e.boolIfTrue(3, info.fragment);
		e.string(4, info.cachedPack);
		e.message(5, objects);
		e.int32IfNotZero(6, info.chunkSize);
		e.int32IfNotZero(7, info.indexSize);
		e.int32IfNotZero(8, info.metaSize);
		return e.asByteArray();
	}

	ChunkKey chunkKey;

	Source source;

	int objectType = -1;

	boolean fragment;

	CachedPackKey cachedPack;

	int objectsTotal;

	int objectsWhole;

	int objectsOfsDelta;

	int objectsRefDelta;

	int chunkSize;

	int indexSize;

	int metaSize;

	/** @return the repository that contains the chunk. */
	public RepositoryKey getRepositoryKey() {
		return chunkKey.getRepositoryKey();
	}

	/** @return the chunk this information describes. */
	public ChunkKey getChunkKey() {
		return chunkKey;
	}

	/** @return source of this chunk. */
	public Source getSource() {
		return source;
	}

	/** @return type of object in the chunk, or {@link #OBJ_MIXED}. */
	public int getObjectType() {
		return objectType;
	}

	/** @return true if this chunk is part of a large fragmented object. */
	public boolean isFragment() {
		return fragment;
	}

	/** @return cached pack this is a member of, or null. */
	public CachedPackKey getCachedPack() {
		return cachedPack;
	}

	/** @return size of the chunk's compressed data, in bytes. */
	public int getChunkSizeInBytes() {
		return chunkSize;
	}

	/** @return size of the chunk's index data, in bytes. */
	public int getIndexSizeInBytes() {
		return indexSize;
	}

	/** @return size of the chunk's meta data, in bytes. */
	public int getMetaSizeInBytes() {
		return metaSize;
	}

	/** @return number of objects stored in the chunk. */
	public int getObjectsTotal() {
		return objectsTotal;
	}

	/** @return number of whole objects stored in the chunk. */
	public int getObjectsWhole() {
		return objectsWhole;
	}

	/** @return number of OFS_DELTA objects stored in the chunk. */
	public int getObjectsOffsetDelta() {
		return objectsOfsDelta;
	}

	/** @return number of REF_DELTA objects stored in the chunk. */
	public int getObjectsReferenceDelta() {
		return objectsRefDelta;
	}

	/**
	 * Convert this link into a byte array for storage.
	 *
	 * @return the link data, encoded as a byte array. This does not include the
	 *         ChunkKey, callers must store that separately.
	 */
	public byte[] asBytes() {
		return asBytes(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("ChunkInfo:");
		b.append(chunkKey);
		b.append(" [");
		if (getSource() != null)
			b.append(" ").append(getSource());
		if (isFragment())
			b.append(" fragment");
		if (getObjectType() != 0)
			b.append(" ").append(Constants.typeString(getObjectType()));
		if (0 < getObjectsTotal())
			b.append(" objects=").append(getObjectsTotal());
		if (0 < getChunkSizeInBytes())
			b.append(" chunk=").append(getChunkSizeInBytes()).append("B");
		if (0 < getIndexSizeInBytes())
			b.append(" index=").append(getIndexSizeInBytes()).append("B");
		b.append(" ]");
		return b.toString();
	}
}
