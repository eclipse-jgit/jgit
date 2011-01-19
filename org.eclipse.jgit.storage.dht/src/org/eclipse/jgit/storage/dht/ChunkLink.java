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
public class ChunkLink {
	/** Orders ChunkLinks by their time member, oldest first. */
	public static final Comparator<ChunkLink> BY_TIME = new Comparator<ChunkLink>() {
		public int compare(ChunkLink a, ChunkLink b) {
			return Long.signum(b.getTime() - a.getTime());
		}
	};

	/**
	 * Sort the chunk link list according to time, oldest member first.
	 *
	 * @param toSort
	 *            list to sort.
	 */
	public static void sort(List<ChunkLink> toSort) {
		Collections.sort(toSort, BY_TIME);
	}

	/**
	 * Parse a link from the storage system.
	 *
	 * @param chunkKey
	 *            the chunk the link points to.
	 * @param data
	 *            the data of the link.
	 * @param time
	 *            timestamp of the chunk link. If the implementation does not
	 *            store timestamp data, supply a negative value and the time
	 *            will be approximated from the chunk key.
	 * @return the link object.
	 */
	public static ChunkLink fromBytes(ChunkKey chunkKey, byte[] data, long time) {
		TinyProtobuf.Decoder d = TinyProtobuf.decode(data);
		int offset = -1;
		int size = -1;
		ObjectId baseId = null;

		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				offset = d.int32();
				continue;
			case 2:
				size = d.int32();
				continue;
			case 3:
				baseId = d.stringObjectId();
				continue;
			default:
				d.skip();
				continue;
			}
		}

		if (offset < 0 || size < 0)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidChunkLink, chunkKey));

		return new ChunkLink(chunkKey, time, offset, size, baseId);
	}

	private final ChunkKey chunk;

	private final long time;

	private final int pos;

	private final int size;

	private final ObjectId base;

	ChunkLink(ChunkKey chunk, long time, int pos, int size, ObjectId base) {
		this.chunk = chunk;
		this.time = time < 0 ? chunk.getApproximateCreationTime() : time;
		this.pos = pos;
		this.size = size;
		this.base = base;
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
		return pos;
	}

	int getSize() {
		return size;
	}

	ObjectId getDeltaBase() {
		return base;
	}

	/**
	 * Convert this link into a byte array for storage.
	 *
	 * @return the link data, encoded as a byte array. This does not include the
	 *         ChunkKey, callers must store that separately.
	 */
	public byte[] asByteArray() {
		int max = 3 * 6 + Constants.OBJECT_ID_STRING_LENGTH;
		TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
		e.int32(1, pos);
		e.int32(2, size);
		if (base != null)
			e.string(3, base);
		return e.asByteArray();
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("ChunkLink:");
		b.append(chunk);
		b.append(" [");
		b.append("time=").append(new Date(time));
		b.append(" offset=").append(pos);
		b.append(" size=").append(size);
		if (base != null)
			b.append(" base=").append(base);
		b.append(']');
		return b.toString();
	}
}
