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
	 * Parse an ObjectInfo from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the object points to.
	 * @param data
	 *            the data of the ObjectInfo.
	 * @param time
	 *            timestamp of the ObjectInfo. If the implementation does not
	 *            store timestamp data, supply a negative value.
	 * @return the object's information.
	 */
	public static ObjectInfo fromBytes(ChunkKey chunkKey, byte[] data, long time) {
		TinyProtobuf.Decoder d = TinyProtobuf.decode(data);
		int typeCode = -1;
		int offset = -1;
		long packedSize = -1;
		long inflatedSize = -1;
		ObjectId deltaBase = null;

		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				typeCode = d.int32();
				continue;
			case 2:
				offset = d.int32();
				continue;
			case 3:
				packedSize = d.int64();
				continue;
			case 4:
				inflatedSize = d.int64();
				continue;
			case 5:
				deltaBase = d.bytesObjectId();
				continue;
			default:
				d.skip();
				continue;
			}
		}

		if (typeCode < 0 || offset < 0 || packedSize < 0 || inflatedSize < 0)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidObjectInfo, chunkKey));

		return new ObjectInfo(chunkKey, time, typeCode, offset, //
				packedSize, inflatedSize, deltaBase);
	}

	private final ChunkKey chunk;

	private final long time;

	private final int typeCode;

	private final int offset;

	private final long packedSize;

	private final long inflatedSize;

	private final ObjectId deltaBase;

	ObjectInfo(ChunkKey chunk, long time, int typeCode, int offset,
			long packedSize, long inflatedSize, ObjectId base) {
		this.chunk = chunk;
		this.time = time < 0 ? 0 : time;
		this.typeCode = typeCode;
		this.offset = offset;
		this.packedSize = packedSize;
		this.inflatedSize = inflatedSize;
		this.deltaBase = base;
	}

	/** @return the chunk this link points to. */
	public ChunkKey getChunkKey() {
		return chunk;
	}

	/** @return approximate time the object was created, in milliseconds. */
	public long getTime() {
		return time;
	}

	/** @return type of the object, in OBJ_* constants. */
	public int getType() {
		return typeCode;
	}

	/** @return size of the object when fully inflated. */
	public long getSize() {
		return inflatedSize;
	}

	int getOffset() {
		return offset;
	}

	long getPackedSize() {
		return packedSize;
	}

	ObjectId getDeltaBase() {
		return deltaBase;
	}

	/**
	 * Convert this ObjectInfo into a byte array for storage.
	 *
	 * @return the ObjectInfo data, encoded as a byte array. This does not
	 *         include the ChunkKey, callers must store that separately.
	 */
	public byte[] asBytes() {
		int max = 2 + 2 * 6 + Constants.OBJECT_ID_LENGTH;
		TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
		e.int32(1, typeCode);
		e.int32(2, offset);
		e.int64(3, packedSize);
		e.int64(4, inflatedSize);
		e.bytes(5, deltaBase);
		return e.asByteArray();
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("ObjectInfo:");
		b.append(chunk);
		b.append(" [");
		if (0 < time)
			b.append(" time=").append(new Date(time));
		b.append(" type=").append(Constants.typeString(typeCode));
		b.append(" offset=").append(offset);
		b.append(" packedSize=").append(packedSize);
		b.append(" inflatedSize=").append(inflatedSize);
		if (deltaBase != null)
			b.append(" deltaBase=").append(deltaBase.name());
		b.append(" ]");
		return b.toString();
	}
}
