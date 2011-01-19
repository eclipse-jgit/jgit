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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/** Connects an object to the chunk it is stored in. */
public class ObjectInfo {
	/** Orders ObjectInfo by their time member, oldest first. */
	public static final Comparator<ObjectInfo> BY_TIME = new Comparator<ObjectInfo>() {
		public int compare(ObjectInfo a, ObjectInfo b) {
			return Long.signum(b.getTime() - a.getTime());
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

	/**
	 * Parse a link from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the link points to.
	 * @param data
	 *            the data of the ObjectInfo.
	 * @param time
	 *            timestamp of the ObjectInfo. If the implementation does not
	 *            store timestamp data, supply a negative value and the time
	 *            will be approximated from the chunk key.
	 * @return the link object.
	 */
	public static ObjectInfo fromBytes(ChunkKey chunkKey, byte[] data, long time) {
		TinyProtobuf.Decoder d = TinyProtobuf.decode(data);
		int offset = -1;
		int packedSize = -1;
		ObjectId deltaBase = null;
		boolean fragment = false;

		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				offset = d.int32();
				continue;
			case 2:
				packedSize = d.int32();
				continue;
			case 3:
				deltaBase = d.bytesObjectId();
				continue;
			case 4:
				fragment = d.bool();
				continue;
			default:
				d.skip();
				continue;
			}
		}

		if (offset < 0 || packedSize < 0)
			throw new IllegalArgumentException(MessageFormat.format(DhtText
					.get().invalidChunkLink, chunkKey));

		return new ObjectInfo(chunkKey, time, offset, packedSize, deltaBase,
				fragment);
	}

	private final ChunkKey chunk;

	private final long time;

	private final int offset;

	private final int packedSize;

	private final ObjectId deltaBase;

	private final boolean fragment;

	ObjectInfo(ChunkKey chunk, long time, int offset, int size, ObjectId base,
			boolean fragment) {
		this.chunk = chunk;
		this.time = time < 0 ? chunk.getApproximateCreationTime() : time;
		this.offset = offset;
		this.packedSize = size;
		this.deltaBase = base;
		this.fragment = fragment;
	}

	/** @return the chunk this link points to. */
	public ChunkKey getChunkKey() {
		return chunk;
	}

	/** @return approximate time the link was created, in milliseconds. */
	public long getTime() {
		return time;
	}

	int getOffset() {
		return offset;
	}

	int getSize() {
		return packedSize;
	}

	ObjectId getDeltaBase() {
		return deltaBase;
	}

	boolean isFragment() {
		return fragment;
	}

	/**
	 * Convert this ObjectInfo into a byte array for storage.
	 *
	 * @return the ObjectInfo data, encoded as a byte array. This does not
	 *         include the ChunkKey, callers must store that separately.
	 */
	public byte[] toBytes() {
		int max = 2 * 6 + Constants.OBJECT_ID_LENGTH;
		TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
		e.int32(1, offset);
		e.int32(2, packedSize);
		e.bytes(3, deltaBase);
		e.boolIfTrue(4, fragment);
		return e.asByteArray();
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("ObjectInfo:");
		b.append(chunk);
		b.append(" [");
		b.append("time=").append(new Date(time));
		b.append(" offset=").append(offset);
		b.append(" packedSize=").append(packedSize);
		if (deltaBase != null)
			b.append(" deltaBase=").append(deltaBase.name());
		if (fragment)
			b.append(" fragment");
		b.append(']');
		return b.toString();
	}
}
