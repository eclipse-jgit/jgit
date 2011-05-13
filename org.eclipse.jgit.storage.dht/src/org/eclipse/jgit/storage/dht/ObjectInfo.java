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

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore;
import org.eclipse.jgit.lib.ObjectId;

/** Connects an object to the chunk it is stored in. */
public class ObjectInfo {
	/** Orders ObjectInfo by their time member, oldest first. */
	public static final Comparator<ObjectInfo> BY_TIME = new Comparator<ObjectInfo>() {
		public int compare(ObjectInfo a, ObjectInfo b) {
			return Long.signum(a.getTime() - b.getTime());
		}
	};

	/**
	 * Sort the info list according to time, oldest member first.
	 *
	 * @param toSort
	 *            list to sort.
	 */
	public static void sort(List<ObjectInfo> toSort) {
		Collections.sort(toSort, BY_TIME);
	}

	private final ChunkKey chunk;

	private final long time;

	private final GitStore.ObjectInfo data;

	/**
	 * Wrap an ObjectInfo from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the object points to.
	 * @param data
	 *            the data of the ObjectInfo.
	 */
	public ObjectInfo(ChunkKey chunkKey, GitStore.ObjectInfo data) {
		this.chunk = chunkKey;
		this.time = 0;
		this.data = data;
	}

	/**
	 * Wrap an ObjectInfo from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the object points to.
	 * @param time
	 *            timestamp of the ObjectInfo.
	 * @param data
	 *            the data of the ObjectInfo.
	 */
	public ObjectInfo(ChunkKey chunkKey, long time, GitStore.ObjectInfo data) {
		this.chunk = chunkKey;
		this.time = time < 0 ? 0 : time;
		this.data = data;
	}

	/** @return the chunk this link points to. */
	public ChunkKey getChunkKey() {
		return chunk;
	}

	/** @return approximate time the object was created, in milliseconds. */
	public long getTime() {
		return time;
	}

	/** @return GitStore.ObjectInfo to embed in the database. */
	public GitStore.ObjectInfo getData() {
		return data;
	}

	/** @return type of the object, in OBJ_* constants. */
	public int getType() {
		return data.getObjectType().getNumber();
	}

	/** @return size of the object when fully inflated. */
	public long getSize() {
		return data.getInflatedSize();
	}

	/** @return true if the object storage uses delta compression. */
	public boolean isDelta() {
		return data.hasDeltaBase();
	}

	/** @return true if the object has been fragmented across chunks. */
	public boolean isFragmented() {
		return data.getIsFragmented();
	}

	int getOffset() {
		return data.getOffset();
	}

	long getPackedSize() {
		return data.getPackedSize();
	}

	ObjectId getDeltaBase() {
		if (data.hasDeltaBase())
			return ObjectId.fromRaw(data.getDeltaBase().toByteArray(), 0);
		return null;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("ObjectInfo:");
		b.append(chunk);
		if (0 < time)
			b.append(" @ ").append(new Date(time));
		b.append("\n");
		b.append(data.toString());
		return b.toString();
	}
}
