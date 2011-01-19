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
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/** Index into a {@link PackChunk}. */
public class ChunkIndex {
	private static final int V1 = 0x0001;

	static ChunkIndex fromBytes(ChunkKey key, byte[] index) throws DhtException {
		int v = NB.decodeUInt16(index, 0);
		switch (v) {
		case V1:
			return new ChunkIndex(index);
		default:
			throw new DhtException(MessageFormat.format(
					DhtText.get().unsupportedChunkIndex, Integer.valueOf(v),
					key));
		}
	}

	/**
	 * Format the chunk index and return its binary representation.
	 *
	 * @param list
	 *            the list of objects that appear in the chunk. This list will
	 *            be sorted in-place if it has more than 1 element.
	 * @return binary representation of the chunk's objects and their starting
	 *         offsets. The format is private to this class.
	 */
	static byte[] create(List<? extends PackedObjectInfo> list) {
		int cnt = list.size();

		if (1 < cnt)
			sortObjectList(list);

		byte[] index = new byte[2 + cnt * Constants.OBJECT_ID_LENGTH + cnt * 4];
		NB.encodeInt16(index, 0, V1);

		int ptr = 2;
		for (PackedObjectInfo oe : list) {
			oe.copyRawTo(index, ptr);
			ptr += Constants.OBJECT_ID_LENGTH;
		}

		for (PackedObjectInfo oe : list) {
			NB.encodeInt32(index, ptr, (int) oe.getOffset());
			ptr += 4;
		}

		return index;
	}

	@SuppressWarnings("unchecked")
	private static void sortObjectList(List<? extends PackedObjectInfo> list) {
		Collections.sort(list);
	}

	private final byte[] index;

	private final int count;

	private final int offsetTable;

	ChunkIndex(byte[] index) {
		this.index = index;
		this.count = (index.length - 2) / (Constants.OBJECT_ID_LENGTH + 4);
		this.offsetTable = 2 + count * Constants.OBJECT_ID_LENGTH;
	}

	/**
	 * Get the total number of objects described by this index.
	 *
	 * @return number of objects in this index and its associated chunk.
	 */
	public int getObjectCount() {
		return count;
	}

	/**
	 * Get an ObjectId from this index.
	 *
	 * @param nth
	 *            the object to return. Must be in range [0, getObjectCount).
	 * @return the object id.
	 */
	public ObjectId getObjectId(int nth) {
		return ObjectId.fromRaw(index, idOffset(nth));
	}

	/** @return the size of this index, in bytes. */
	int getIndexSize() {
		return index.length;
	}

	/**
	 * Search for an object in the index.
	 *
	 * @param objId
	 *            the object to locate.
	 * @return offset of the object in the corresponding chunk; -1 if not found.
	 */
	int findOffset(AnyObjectId objId) {
		int high = count;
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final int cmp = objId.compareTo(index, idOffset(mid));
			if (cmp < 0)
				high = mid;
			else if (cmp == 0)
				return NB.decodeInt32(index, offsetTable + (mid * 4));
			else
				low = mid + 1;
		} while (low < high);
		return -1;
	}

	private static int idOffset(int nth) {
		return 2 + (nth * Constants.OBJECT_ID_LENGTH);
	}
}