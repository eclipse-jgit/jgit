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

import java.io.IOException;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.storage.pack.BinaryDelta;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.transport.PackParser;

/** Chunk of object data, stored under a {@link ChunkKey}. */
public class PackChunk {
	static final int OBJ_CHUNK_DELTA = 0x10;

	static final int FLAG_FRAGMENTED = 0x40;

	/** Constructs a {@link PackChunk} while reading from the DHT. */
	public static class Builder {
		private ChunkKey chunkKey;

		private byte[] chunkData;

		private byte[] chunkIndex;

		private byte[] fragments;

		/**
		 * @param key
		 * @return {@code this}
		 */
		public Builder setChunkKey(ChunkKey key) {
			this.chunkKey = key;
			return this;
		}

		/**
		 * @param chunkData
		 * @return {@code this}
		 */
		public Builder setChunkData(byte[] chunkData) {
			this.chunkData = chunkData;
			return this;
		}

		/**
		 * @param chunkIndex
		 * @return {@code this}
		 */
		public Builder setChunkIndex(byte[] chunkIndex) {
			this.chunkIndex = chunkIndex;
			return this;
		}

		/**
		 * @param fragments
		 * @return {@code this}
		 */
		public Builder setFragments(byte[] fragments) {
			this.fragments = fragments;
			return this;
		}

		/**
		 * @return the PackChunk instance.
		 * @throws DhtException
		 *             if early validation indicates the chunk data is corrupt
		 *             or not recognized by this version of the library.
		 */
		public PackChunk build() throws DhtException {
			ChunkIndex i;
			if (chunkIndex != null)
				i = ChunkIndex.fromRaw(chunkKey, chunkIndex);
			else
				i = null;

			ChunkFragments f;
			if (fragments != null)
				f = ChunkFragments.fromRaw(fragments);
			else
				f = null;

			return new PackChunk(chunkKey, chunkData, i, f);
		}
	}

	private final ChunkKey key;

	private final byte[] chunk;

	private final ChunkIndex index;

	private final ChunkFragments fragments;

	private volatile Boolean valid;

	private volatile ChunkKey nextFragment;

	PackChunk(ChunkKey key, byte[] chunk, ChunkIndex index,
			ChunkFragments fragments) {
		this.key = key;
		this.chunk = chunk;
		this.index = index;
		this.fragments = fragments;
	}

	ChunkKey getChunkKey() {
		return key;
	}

	boolean hasIndex() {
		return index != null;
	}

	@SuppressWarnings("boxing")
	boolean isValid() {
		Boolean v = valid;
		if (v == null) {
			MessageDigest m = Constants.newMessageDigest();
			m.update(chunk);
			v = key.getChunkHash().compareTo(m.digest(), 0) == 0;
			valid = v;
		}
		return v.booleanValue();
	}

	int findOffset(RepositoryKey repo, AnyObjectId objId) {
		if (key.isFor(repo))
			return index.findOffset(objId);
		return -1;
	}

	boolean contains(RepositoryKey repo, AnyObjectId objId) {
		return 0 <= findOffset(repo, objId);
	}

	ObjectLoader read(int pos, DhtReader ctx, int typeHint) throws IOException {
		try {
			PackChunk pc = this;
			Delta delta = null;
			byte[] data = null;
			int type = Constants.OBJ_BAD;
			boolean cached = false;

			SEARCH: for (;;) {
				if (pc.fragments != null)
					throw new DhtException.TODO("read fragmented objects");

				final byte[] chunk = pc.chunk;

				int c = chunk[pos] & 0xff;
				int typeCode = (c >> 4) & 7;
				long sz = c & 15;
				int shift = 4;
				int p = 1;
				while ((c & 0x80) != 0) {
					c = chunk[pos + p++] & 0xff;
					sz += (c & 0x7f) << shift;
					shift += 7;
				}
				if (typeCode == Constants.OBJ_EXT)
					typeCode = chunk[pos + p++] & 0xff;

				switch (typeCode) {
				case Constants.OBJ_COMMIT:
				case Constants.OBJ_TREE:
				case Constants.OBJ_BLOB:
				case Constants.OBJ_TAG: {
					if (sz < Integer.MAX_VALUE)
						data = pc.decompress(pos + p, (int) sz, ctx);

					if (delta != null) {
						type = typeCode;
						break SEARCH;
					}

					if (data != null)
						return new ObjectLoader.SmallObject(typeCode, data);
					else
						return new LargeObject(chunk, typeCode, sz, pos + p);
				}

				case Constants.OBJ_OFS_DELTA: {
					c = chunk[pos + p++] & 0xff;
					int base = c & 127;
					while ((c & 128) != 0) {
						base += 1;
						c = chunk[pos + p++] & 0xff;
						base <<= 7;
						base += (c & 127);
					}
					base = pos - base;
					delta = new Delta(delta, //
							pc.key, pos, (int) sz, p, //
							pc.key, base);
					if (sz != delta.deltaSize)
						break SEARCH;

					DeltaBaseCache.Entry e = delta.getBase(ctx);
					if (e != null) {
						type = e.type;
						data = e.data;
						cached = true;
						break SEARCH;
					}
					pos = base;
					continue SEARCH;
				}

				case Constants.OBJ_REF_DELTA: {
					ObjectId id = ObjectId.fromRaw(chunk, pos + p);
					PackChunk nc = pc;
					int base = pc.index.findOffset(id);
					if (base < 0) {
						DhtReader.ChunkAndOffset n = ctx.getChunk(id, typeHint);
						nc = n.chunk;
						base = n.offset;
					}
					delta = new Delta(delta, //
							pc.key, pos, (int) sz, p + 20, //
							nc.getChunkKey(), base);
					if (sz != delta.deltaSize)
						break SEARCH;

					DeltaBaseCache.Entry e = delta.getBase(ctx);
					if (e != null) {
						type = e.type;
						data = e.data;
						cached = true;
						break SEARCH;
					}
					pc = nc;
					pos = base;
					continue SEARCH;
				}

				case OBJ_CHUNK_DELTA: {
					ChunkKey baseKey = ChunkKey.fromChunk(key, chunk, pos + p);
					p += 22;

					c = chunk[pos + p++] & 0xff;
					int base = c & 127;
					while ((c & 128) != 0) {
						base += 1;
						c = chunk[pos + p++] & 0xff;
						base <<= 7;
						base += (c & 127);
					}

					delta = new Delta(delta, //
							pc.key, pos, (int) sz, p, //
							baseKey, base);
					if (sz != delta.deltaSize)
						break SEARCH;

					DeltaBaseCache.Entry e = delta.getBase(ctx);
					if (e != null) {
						type = e.type;
						data = e.data;
						cached = true;
						break SEARCH;
					}
					pc = ctx.getChunk(baseKey);
					pos = base;
					continue SEARCH;
				}

				default:
					throw new DhtException(MessageFormat.format(
							DhtText.get().unsupportedObjectTypeInChunk,
							typeCode, key, pos));
				}
			}

			// At this point there is at least one delta to apply to data.
			// (Whole objects with no deltas to apply return early above.)

			if (data == null)
				return delta.large();

			do {
				pos = delta.deltaPos;

				// Cache only the base immediately before desired object.
				if (cached)
					cached = false;
				else if (delta.next == null)
					delta.putBase(ctx, type, data);

				final byte[] cmds = delta.decompress(this, ctx);
				if (cmds == null) {
					data = null; // Discard base in case of OutOfMemoryError
					return delta.large();
				}

				final long sz = BinaryDelta.getResultSize(cmds);
				if (Integer.MAX_VALUE <= sz)
					return delta.large();

				final byte[] result;
				try {
					result = new byte[(int) sz];
				} catch (OutOfMemoryError tooBig) {
					data = null; // Discard base in case of OutOfMemoryError
					return delta.large();
				}

				BinaryDelta.apply(data, cmds, result);
				data = result;
				delta = delta.next;
			} while (delta != null);

			return new ObjectLoader.SmallObject(type, data);

		} catch (DataFormatException dfe) {
			CorruptObjectException coe = new CorruptObjectException(
					MessageFormat.format(DhtText.get().corruptCompressedObject,
							key, pos));
			coe.initCause(dfe);
			throw coe;
		}
	}

	final byte[] decompress(int pos, int sz, DhtReader reader)
			throws DataFormatException {
		byte[] dstbuf;
		try {
			dstbuf = new byte[sz];
		} catch (OutOfMemoryError noMemory) {
			// The size may be larger than our heap allows, return null to
			// let the caller know allocation isn't possible and it should
			// use the large object streaming approach instead.
			//
			// For example, this can occur when sz is 640 MB, and JRE
			// maximum heap size is only 256 MB. Even if the JRE has
			// 200 MB free, it cannot allocate a 640 MB byte array.
			return null;
		}

		int dstoff = 0;
		Inflater inf = reader.inflater();
		inf.setInput(chunk, pos, chunk.length - pos);
		for (;;) {
			int n = inf.inflate(dstbuf, dstoff, dstbuf.length - dstoff);
			if (n == 0) {
				if (inf.finished())
					break;
				throw new DataFormatException();
			}
			dstoff += n;
		}

		if (dstoff != sz)
			throw new DataFormatException(MessageFormat.format(
					DhtText.get().shortCompressedObject, key, pos));
		return dstbuf;
	}

	int readObjectTypeAndSize(int ptr, PackParser.ObjectTypeAndSize info) {
		int c = chunk[ptr++] & 0xff;
		int typeCode = (c >> 4) & 7;
		long sz = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = chunk[ptr++] & 0xff;
			sz += (c & 0x7f) << shift;
			shift += 7;
		}

		if (typeCode == Constants.OBJ_EXT)
			typeCode = chunk[ptr++] & 0xff;

		switch (typeCode) {
		case Constants.OBJ_OFS_DELTA:
			c = chunk[ptr++] & 0xff;
			while ((c & 128) != 0)
				c = chunk[ptr++] & 0xff;
			break;

		case Constants.OBJ_REF_DELTA:
			ptr += 20;
			break;

		case PackChunk.OBJ_CHUNK_DELTA:
			ptr += 22;
			c = chunk[ptr++] & 0xff;
			while ((c & 128) != 0)
				c = chunk[ptr++] & 0xff;
			break;
		}

		info.type = typeCode;
		info.size = sz;
		return ptr;
	}

	int read(int ptr, byte[] dst, int dstPos, int cnt) {
		int n = Math.min(cnt, chunk.length - ptr);
		System.arraycopy(chunk, ptr, dst, dstPos, n);
		return n;
	}

	void copyAsIs(PackOutputStream out, DhtObjectToPack obj, DhtReader ctx)
			throws IOException, StoredObjectRepresentationNotAvailableException {
		if (ctx.getOptions().isValidateOnCopyAsIs() && !isValid()) {
			StoredObjectRepresentationNotAvailableException gone;

			gone = new StoredObjectRepresentationNotAvailableException(obj);
			gone.initCause(new DhtException(MessageFormat.format(
					DhtText.get().corruptChunk, getChunkKey())));
			throw gone;
		}

		int ptr = obj.offset;
		int c = chunk[ptr++] & 0xff;
		int typeCode = (c >> 4) & 7;
		long inflatedSize = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = chunk[ptr++] & 0xff;
			inflatedSize += (c & 0x7f) << shift;
			shift += 7;
		}

		if (typeCode == Constants.OBJ_EXT)
			typeCode = chunk[ptr++] & 0xff;

		switch (typeCode) {
		case Constants.OBJ_OFS_DELTA:
			c = chunk[ptr++] & 0xff;
			while ((c & 128) != 0)
				c = chunk[ptr++] & 0xff;
			break;

		case Constants.OBJ_REF_DELTA:
			ptr += 20;
			break;

		case PackChunk.OBJ_CHUNK_DELTA:
			ptr += 22;
			c = chunk[ptr++] & 0xff;
			while ((c & 128) != 0)
				c = chunk[ptr++] & 0xff;
			break;
		}

		int size = obj.size;
		if (size == 0)
			throw new DhtException(MessageFormat.format(
					DhtText.get().expectedObjectSizeDuringCopyAsIs, obj));

		if (size() - ptr < size) // Large fragmented object, include rest
			size = size() - ptr;
		out.writeHeader(obj, inflatedSize);
		out.write(chunk, ptr, size);

		// If the object was fragmented, send all of the other fragments.
		if (fragments != null) {
			for (int fragId = 1; fragId < fragments.size(); fragId++)
				ctx.getChunk(fragments.getChunkKey(fragId)).copyAsIs(out);
		}
	}

	private void copyAsIs(PackOutputStream out) throws IOException {
		out.write(chunk, 0, chunk.length);
	}

	int size() {
		return chunk.length;
	}

	ChunkKey getNextFragment() {
		if (fragments == null)
			return null;

		ChunkKey next = nextFragment;
		if (next == null)
			nextFragment = next = fragments.getNextFor(getChunkKey());
		return next;
	}

	private static class Delta {
		/** Child that applies onto this object. */
		final Delta next;

		/** The chunk the delta is stored in. */
		final ChunkKey deltaChunk;

		/** Offset of the delta object. */
		final int deltaPos;

		/** Size of the inflated delta stream. */
		final int deltaSize;

		/** Total size of the delta's pack entry header (including base). */
		final int hdrLen;

		/** The chunk the base is stored in. */
		final ChunkKey baseChunk;

		/** Offset of the base object. */
		final int basePos;

		Delta(Delta next, ChunkKey dc, int ofs, int sz, int hdrLen,
				ChunkKey bc, int bp) {
			this.next = next;
			this.deltaChunk = dc;
			this.deltaPos = ofs;
			this.deltaSize = sz;
			this.hdrLen = hdrLen;
			this.baseChunk = bc;
			this.basePos = bp;
		}

		byte[] decompress(PackChunk chunk, DhtReader reader)
				throws DataFormatException, DhtException {
			if (!deltaChunk.equals(chunk.getChunkKey()))
				chunk = reader.getChunk(deltaChunk);
			return chunk.decompress(deltaPos + hdrLen, deltaSize, reader);
		}

		DeltaBaseCache.Entry getBase(DhtReader ctx) {
			return ctx.getDeltaBaseCache().get(baseChunk, basePos);
		}

		void putBase(DhtReader ctx, int type, byte[] data) {
			ctx.getDeltaBaseCache().put(baseChunk, basePos, type, data);
		}

		ObjectLoader large() throws DhtException {
			Delta d = this;
			while (d.next != null)
				d = d.next;
			return d.newLargeLoader();
		}

		private ObjectLoader newLargeLoader() throws DhtException {
			throw new DhtException.TODO("large delta support");
		}
	}
}
