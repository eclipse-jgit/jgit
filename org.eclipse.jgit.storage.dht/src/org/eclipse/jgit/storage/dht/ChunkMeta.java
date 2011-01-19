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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metadata stored inline with each PackChunk.. */
public class ChunkMeta {
	/**
	 * Convert from byte array.
	 *
	 * @param key
	 *            the chunk key this meta object sits in.
	 * @param raw
	 *            the raw byte array.
	 * @return the chunk meta.
	 */
	public static ChunkMeta fromBytes(ChunkKey key, byte[] raw) {
		List<BaseChunk> baseChunk = null;
		List<ChunkKey> fragment = null;
		PrefetchHint commit = null;
		PrefetchHint tree = null;

		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				if (baseChunk == null)
					baseChunk = new ArrayList<BaseChunk>(4);
				baseChunk.add(BaseChunk.fromBytes(d.message()));
				continue;
			case 2:
				if (fragment == null)
					fragment = new ArrayList<ChunkKey>(4);
				fragment.add(ChunkKey.fromBytes(d.bytes()));
				continue;
			case 51:
				commit = PrefetchHint.fromBytes(d.message());
				continue;
			case 52:
				tree = PrefetchHint.fromBytes(d.message());
				continue;
			default:
				d.skip();
				continue;
			}
		}

		return new ChunkMeta(key, baseChunk, fragment, commit, tree);
	}

	private final ChunkKey chunkKey;

	List<BaseChunk> baseChunks;

	List<ChunkKey> fragments;

	PrefetchHint commitPrefetch;

	PrefetchHint treePrefetch;

	ChunkMeta(ChunkKey key) {
		this(key, null, null, null, null);
	}

	ChunkMeta(ChunkKey chunkKey, List<BaseChunk> baseChunk,
			List<ChunkKey> fragment, PrefetchHint commit, PrefetchHint tree) {
		this.chunkKey = chunkKey;
		this.baseChunks = baseChunk;
		this.fragments = fragment;
		this.commitPrefetch = commit;
		this.treePrefetch = tree;
	}

	/** @return key of the chunk this meta information is for. */
	public ChunkKey getChunkKey() {
		return chunkKey;
	}

	BaseChunk getBaseChunk(long position) throws DhtException {
		// Chunks are sorted by ascending relative_start order.
		// Thus for a pack sequence of: A B C, we have:
		//
		// -- C relative_start = 10,000
		// -- B relative_start = 20,000
		// -- A relative_start = 30,000
		//
		// Indicating that chunk C starts 10,000 bytes before us,
		// chunk B starts 20,000 bytes before us (and 10,000 before C),
		// chunk A starts 30,000 bytes before us (and 10,000 before B),
		//
		// If position falls within:
		//
		// -- C (10k), then position is between 0..10,000
		// -- B (20k), then position is between 10,000 .. 20,000
		// -- A (30k), then position is between 20,000 .. 30,000

		int high = baseChunks.size();
		int low = 0;
		while (low < high) {
			final int mid = (low + high) >>> 1;
			final BaseChunk base = baseChunks.get(mid);

			if (position > base.relativeStart) {
				low = mid + 1;

			} else if (mid == 0 || position == base.relativeStart) {
				return base;

			} else if (baseChunks.get(mid - 1).relativeStart < position) {
				return base;

			} else {
				high = mid;
			}
		}

		throw new DhtException(MessageFormat.format(
				DhtText.get().missingLongOffsetBase, chunkKey,
				Long.valueOf(position)));
	}

	/** @return number of fragment chunks that make up the object. */
	public int getFragmentCount() {
		return fragments != null ? fragments.size() : 0;
	}

	/**
	 * Get the nth fragment key.
	 *
	 * @param nth
	 * @return the key.
	 */
	public ChunkKey getFragmentKey(int nth) {
		return fragments.get(nth);
	}

	/**
	 * Find the key of the fragment that occurs after this chunk.
	 *
	 * @param currentKey
	 *            the current chunk key.
	 * @return next chunk after this; null if there isn't one.
	 */
	public ChunkKey getNextFragment(ChunkKey currentKey) {
		for (int i = 0; i < fragments.size() - 1; i++) {
			if (fragments.get(i).equals(currentKey))
				return fragments.get(i + 1);
		}
		return null;
	}

	/** @return chunks to visit. */
	public PrefetchHint getCommitPrefetch() {
		return commitPrefetch;
	}

	/** @return chunks to visit. */
	public PrefetchHint getTreePrefetch() {
		return treePrefetch;
	}

	/** @return true if there is no data in this object worth storing. */
	boolean isEmpty() {
		if (baseChunks != null && !baseChunks.isEmpty())
			return false;
		if (fragments != null && !fragments.isEmpty())
			return false;
		if (commitPrefetch != null && !commitPrefetch.isEmpty())
			return false;
		if (treePrefetch != null && !treePrefetch.isEmpty())
			return false;
		return true;
	}

	/** @return format as byte array for storage. */
	public byte[] asBytes() {
		TinyProtobuf.Encoder e = TinyProtobuf.encode(256);

		if (baseChunks != null) {
			for (BaseChunk base : baseChunks)
				e.message(1, base.asBytes());
		}

		if (fragments != null) {
			for (ChunkKey key : fragments)
				e.bytes(2, key.asBytes());
		}

		if (commitPrefetch != null)
			e.message(51, commitPrefetch.asBytes());
		if (treePrefetch != null)
			e.message(52, treePrefetch.asBytes());

		return e.asByteArray();
	}

	/** Describes other chunks that contain the bases for this chunk's deltas. */
	public static class BaseChunk {
		final long relativeStart;

		private final ChunkKey chunk;

		BaseChunk(long relativeStart, ChunkKey chunk) {
			this.relativeStart = relativeStart;
			this.chunk = chunk;
		}

		/** @return bytes backward from current chunk to start of base chunk. */
		public long getRelativeStart() {
			return relativeStart;
		}

		/** @return unique key of this chunk. */
		public ChunkKey getChunkKey() {
			return chunk;
		}

		TinyProtobuf.Encoder asBytes() {
			int max = 11 + 2 + ChunkKey.KEYLEN;
			TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
			e.int64(1, relativeStart);
			e.bytes(2, chunk.asBytes());
			return e;
		}

		static BaseChunk fromBytes(TinyProtobuf.Decoder d) {
			long relativeStart = -1;
			ChunkKey chunk = null;

			PARSE: for (;;) {
				switch (d.next()) {
				case 0:
					break PARSE;
				case 1:
					relativeStart = d.int64();
					continue;
				case 2:
					chunk = ChunkKey.fromBytes(d.bytes());
					continue;
				default:
					d.skip();
					continue;
				}
			}

			return new BaseChunk(relativeStart, chunk);
		}
	}

	/** Describes the prefetching for a particular object type. */
	public static class PrefetchHint {
		private final List<ChunkKey> edge;

		private final List<ChunkKey> sequential;

		PrefetchHint(List<ChunkKey> edge, List<ChunkKey> sequential) {
			if (edge == null)
				edge = Collections.emptyList();
			else
				edge = Collections.unmodifiableList(edge);

			if (sequential == null)
				sequential = Collections.emptyList();
			else
				sequential = Collections.unmodifiableList(sequential);

			this.edge = edge;
			this.sequential = sequential;
		}

		/** @return chunks on the edge of this chunk. */
		public List<ChunkKey> getEdge() {
			return edge;
		}

		/** @return chunks according to sequential ordering. */
		public List<ChunkKey> getSequential() {
			return sequential;
		}

		boolean isEmpty() {
			return edge.isEmpty() && sequential.isEmpty();
		}

		TinyProtobuf.Encoder asBytes() {
			int max = 0;

			max += (2 + ChunkKey.KEYLEN) * edge.size();
			max += (2 + ChunkKey.KEYLEN) * sequential.size();

			TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
			for (ChunkKey key : edge)
				e.bytes(1, key.asBytes());
			for (ChunkKey key : sequential)
				e.bytes(2, key.asBytes());
			return e;
		}

		static PrefetchHint fromBytes(TinyProtobuf.Decoder d) {
			ArrayList<ChunkKey> edge = null;
			ArrayList<ChunkKey> sequential = null;

			PARSE: for (;;) {
				switch (d.next()) {
				case 0:
					break PARSE;
				case 1:
					if (edge == null)
						edge = new ArrayList<ChunkKey>(16);
					edge.add(ChunkKey.fromBytes(d.bytes()));
					continue;
				case 2:
					if (sequential == null)
						sequential = new ArrayList<ChunkKey>(16);
					sequential.add(ChunkKey.fromBytes(d.bytes()));
					continue;
				default:
					d.skip();
					continue;
				}
			}

			if (edge != null)
				edge.trimToSize();

			if (sequential != null)
				sequential.trimToSize();

			return new PrefetchHint(edge, sequential);
		}
	}
}
