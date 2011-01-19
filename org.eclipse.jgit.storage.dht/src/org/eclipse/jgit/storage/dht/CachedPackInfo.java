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
	 * @param repo
	 *            the repository that owns the cached pack.
	 * @param raw
	 *            the raw encoding of the info.
	 * @return the info object.
	 */
	public static CachedPackInfo fromBytes(RepositoryKey repo, byte[] raw) {
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
				info.tips.add(d.stringObjectId());
				continue;
			case 4:
				info.totalObjects = d.int64();
				continue;
			case 5:
				info.totalBytes = d.int64();
				continue;
			case 6:
				info.chunks.add(ChunkKey.fromShortBytes(repo, d.bytes()));
				continue;
			default:
				d.skip();
				continue;
			}
		}
		return info;
	}

	private static byte[] toBytes(CachedPackInfo info) {
		TinyProtobuf.Encoder e = TinyProtobuf.encode(1024);
		e.string(1, info.name);
		e.string(2, info.version);
		for (ObjectId tip : info.tips)
			e.string(3, tip);
		e.int64IfNotZero(4, info.totalObjects);
		e.int64IfNotZero(5, info.totalBytes);
		for (ChunkKey key : info.chunks)
			e.bytes(6, key.toShortBytes());
		return e.asByteArray();
	}

	ObjectId name;

	ObjectId version;

	SortedSet<ObjectId> tips = new TreeSet<ObjectId>();

	long totalObjects;

	long totalBytes;

	List<ChunkKey> chunks = new ArrayList<ChunkKey>();

	/** @return name of the information object. */
	public CachedPackKey getRowKey() {
		return new CachedPackKey(name, version);
	}

	/** @return number of objects stored in the cached pack. */
	public long getTotalObjects() {
		return totalObjects;
	}

	/** @return number of bytes in the cached pack. */
	public long getTotalBytes() {
		return totalBytes;
	}

	/**
	 * Convert this information into a byte array for storage.
	 *
	 * @return the data, encoded as a byte array. This does not include the key,
	 *         callers must store that separately.
	 */
	public byte[] toBytes() {
		return toBytes(this);
	}

	@Override
	public String toString() {
		return getRowKey().toString();
	}
}
