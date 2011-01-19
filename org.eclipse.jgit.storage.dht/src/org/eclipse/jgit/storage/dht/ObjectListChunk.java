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

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/** A chunk of an object list. */
public class ObjectListChunk {
	/**
	 * @param key
	 * @param raw
	 *            the raw data from the database.
	 * @return the parsed object list.
	 */
	public static ObjectListChunk fromBytes(ObjectListChunkKey key, byte[] raw) {
		return new ObjectListChunk(key, raw);
	}

	static ObjectListChunk create(ObjectListChunkKey key, ObjectId[] list,
			int[] hashes, int cnt) {
		byte[] raw;
		switch (key.getObjectType()) {
		case OBJ_COMMIT:
			raw = new byte[cnt * OBJECT_ID_LENGTH];
			for (int nth = 0; nth < cnt; nth++)
				list[nth].copyRawTo(raw, nth * OBJECT_ID_LENGTH);
			break;
		default:
			raw = new byte[cnt * (OBJECT_ID_LENGTH + 4)];
			for (int nth = 0; nth < cnt; nth++) {
				int ptr = nth * (OBJECT_ID_LENGTH + 4);
				list[nth].copyRawTo(raw, ptr);
				NB.encodeInt32(raw, ptr + OBJECT_ID_LENGTH, hashes[nth]);
			}
			break;
		}
		return new ObjectListChunk(key, raw);
	}

	static int getRecordLength(int objectType) {
		switch (objectType) {
		case OBJ_COMMIT:
			return OBJECT_ID_LENGTH;
		default:
			return OBJECT_ID_LENGTH + 4;
		}
	}

	private final ObjectListChunkKey key;

	private final byte[] raw;

	private final int recLen;

	private final int size;

	ObjectListChunk(ObjectListChunkKey key, byte[] raw) {
		this.key = key;
		this.raw = raw;
		this.recLen = getRecordLength(key.getObjectType());
		this.size = raw.length / recLen;
	}

	int getByteSize() {
		return raw.length;
	}

	/** @return key for this list chunk. */
	public ObjectListChunkKey getRowKey() {
		return key;
	}

	/** @return number of objects in this chunk. */
	public int size() {
		return size;
	}

	/**
	 * Get the nth object id from the list.
	 *
	 * @param nth
	 *            position to get.
	 * @return the object id.
	 */
	public ObjectId getObjectId(int nth) {
		return ObjectId.fromRaw(raw, nth * recLen);
	}

	/**
	 * Get the nth object id from the list.
	 *
	 * @param out
	 *            buffer to populate with the object id.
	 * @param nth
	 *            position to get.
	 */
	public void getObjectId(MutableObjectId out, int nth) {
		out.fromRaw(raw, nth * recLen);
	}

	/**
	 * Get the nth object's path hash code value.
	 *
	 * @param nth
	 *            position o get.
	 * @return the path hash code.
	 */
	public int getPathHashCode(int nth) {
		return NB.decodeInt32(raw, nth * recLen + OBJECT_ID_LENGTH);
	}

	/** @return this object list, as a byte array. */
	public byte[] toBytes() {
		return raw;
	}

	@Override
	public String toString() {
		return key.toString();
	}
}
