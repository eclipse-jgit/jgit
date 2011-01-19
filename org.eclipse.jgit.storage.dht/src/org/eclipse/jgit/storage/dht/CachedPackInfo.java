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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Summary information about a cached pack owned by a repository.
 */
public class CachedPackInfo {
	/**
	 * Parse info from the storage system.
	 *
	 * @param raw
	 *            the raw encoding of the info.
	 * @return the info object.
	 */
	public static CachedPackInfo fromBytes(byte[] raw) {
		CachedPackInfo info = new CachedPackInfo();

		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				info.name = d.stringObjectId();
				continue;
			case 2:
				info.version = d.stringObjectId();
				continue;
			case 3:
				info.objectsTotal = d.int64();
				continue;
			case 4:
				info.objectsDelta = d.int64();
				continue;
			case 5:
				info.bytesTotal = d.int64();
				continue;
			case 6: {
				TinyProtobuf.Decoder m = d.message();
				for (;;) {
					switch (m.next()) {
					case 0:
						continue PARSE;
					case 1:
						info.tips.add(m.stringObjectId());
						continue;
					default:
						m.skip();
						continue;
					}
				}
			}
			case 7: {
				TinyProtobuf.Decoder m = d.message();
				for (;;) {
					switch (m.next()) {
					case 0:
						continue PARSE;
					case 1:
						info.chunks.add(ChunkKey.fromBytes(m.bytes()));
						continue;
					default:
						m.skip();
						continue;
					}
				}
			}
			default:
				d.skip();
				continue;
			}
		}
		return info;
	}

	private static byte[] asBytes(CachedPackInfo info) {
		int tipSize = (2 + OBJECT_ID_STRING_LENGTH) * info.tips.size();
		TinyProtobuf.Encoder tipList = TinyProtobuf.encode(tipSize);
		for (ObjectId tip : info.tips)
			tipList.string(1, tip);

		int chunkSize = (2 + ChunkKey.KEYLEN) * info.chunks.size();
		TinyProtobuf.Encoder chunkList = TinyProtobuf.encode(chunkSize);
		for (ChunkKey key : info.chunks)
			chunkList.bytes(1, key.asBytes());

		TinyProtobuf.Encoder e = TinyProtobuf.encode(1024);
		e.string(1, info.name);
		e.string(2, info.version);
		e.int64(3, info.objectsTotal);
		e.int64IfNotZero(4, info.objectsDelta);
		e.int64IfNotZero(5, info.bytesTotal);
		e.message(6, tipList);
		e.message(7, chunkList);
		return e.asByteArray();
	}

	ObjectId name;

	ObjectId version;

	SortedSet<ObjectId> tips = new TreeSet<ObjectId>();

	long objectsTotal;

	long objectsDelta;

	long bytesTotal;

	List<ChunkKey> chunks = new ArrayList<ChunkKey>();

	/** @return name of the information object. */
	public CachedPackKey getRowKey() {
		return new CachedPackKey(name, version);
	}

	/** @return number of objects stored in the cached pack. */
	public long getObjectsTotal() {
		return objectsTotal;
	}

	/** @return number of objects stored in delta format. */
	public long getObjectsDelta() {
		return objectsDelta;
	}

	/** @return number of bytes in the cached pack. */
	public long getTotalBytes() {
		return bytesTotal;
	}

	/**
	 * Convert this information into a byte array for storage.
	 *
	 * @return the data, encoded as a byte array. This does not include the key,
	 *         callers must store that separately.
	 */
	public byte[] asBytes() {
		return asBytes(this);
	}

	@Override
	public String toString() {
		return getRowKey().toString();
	}
}
