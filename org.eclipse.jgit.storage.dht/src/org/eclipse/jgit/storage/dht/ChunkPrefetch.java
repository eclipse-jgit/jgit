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
import java.util.Collections;
import java.util.List;

/** Prefetch hints on which chunks to read next. */
public class ChunkPrefetch {
	/** Describes the hint for a particular object type. */
	public static class Hint {
		private final List<ChunkKey> edge;

		private final List<ChunkKey> sequential;

		Hint(List<ChunkKey> edge, List<ChunkKey> sequential) {
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

		TinyProtobuf.Encoder toBytes() {
			int max = (2 * ChunkKey.SHORT_KEYLEN) * edge.size();
			max += (2 * ChunkKey.SHORT_KEYLEN) * sequential.size();
			TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
			for (ChunkKey key : edge)
				e.bytes(1, key.toShortBytes());
			for (ChunkKey key : sequential)
				e.bytes(2, key.toShortBytes());
			return e;
		}

		static Hint fromBytes(ChunkKey key, TinyProtobuf.Decoder d) {
			RepositoryKey repo = key.getRepositoryKey();
			ArrayList<ChunkKey> edge = null;
			ArrayList<ChunkKey> sequential = null;

			PARSE: for (;;) {
				switch (d.next()) {
				case 0:
					break PARSE;
				case 1:
					if (edge == null)
						edge = new ArrayList<ChunkKey>(4);
					edge.add(ChunkKey.fromShortBytes(repo, d.bytes()));
					continue;
				case 2:
					if (sequential == null)
						sequential = new ArrayList<ChunkKey>(4);
					sequential.add(ChunkKey.fromShortBytes(repo, d.bytes()));
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

			return new Hint(edge, sequential);
		}
	}

	/**
	 * Convert from byte array.
	 *
	 * @param key
	 *            the chunk key this prefetch hint sits in.
	 * @param raw
	 *            the raw byte array.
	 * @return the prefetch hint data.
	 */
	public static ChunkPrefetch fromBytes(ChunkKey key, byte[] raw) {
		Hint commit = null;
		Hint tree = null;
		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				commit = Hint.fromBytes(key, d.message());
				continue;
			case 2:
				tree = Hint.fromBytes(key, d.message());
				continue;
			default:
				d.skip();
				continue;
			}
		}

		return new ChunkPrefetch(commit, tree);
	}

	private final Hint commits;

	private final Hint trees;

	ChunkPrefetch(Hint commit, Hint tree) {
		this.commits = commit;
		this.trees = tree;
	}

	/** @return chunks to visit. */
	public Hint getCommits() {
		return commits;
	}

	/** @return chunks to visit. */
	public Hint getTrees() {
		return trees;
	}

	/** @return true if there are no prefetch rules in this object. */
	boolean isEmpty() {
		boolean commitsEmpty = commits == null || commits.isEmpty();
		boolean treesEmpty = trees == null || trees.isEmpty();
		return commitsEmpty && treesEmpty;
	}

	/** @return format as byte array for storage. */
	public byte[] toBytes() {
		TinyProtobuf.Encoder e = TinyProtobuf.encode(256);
		if (commits != null)
			e.message(1, commits.toBytes());
		if (trees != null)
			e.message(2, trees.toBytes());
		return e.asByteArray();
	}
}
