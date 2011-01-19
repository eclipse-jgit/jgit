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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.transport.PackedObjectInfo;

class ChunkWriter {
	private final RepositoryKey repo;

	private final Database db;

	private final WriteBuffer buffer;

	private final byte[] hdrBuf;

	private final byte[] chunk;

	private int ptr;

	private List<StoredObject> objects;

	private int mark;

	private int objectCount;

	ChunkWriter(RepositoryKey repo, Database db, WriteBuffer buffer, int size) {
		this.repo = repo;
		this.db = db;
		this.buffer = buffer;

		hdrBuf = new byte[32];
		chunk = new byte[size];
		objects = new ArrayList<StoredObject>(16);
	}

	void flush(MessageDigest md) throws DhtException {
		if (!isEmpty())
			return;

		ChunkKey key = newKey(md);
		db.chunks().putData(key, copy(chunk, ptr), buffer);
		db.chunks().putIndex(key, ChunkIndex.create(objects), buffer);

		ObjectIndexTable idx = db.objectIndex();
		for (StoredObject o : objects)
			idx.add(ObjectIndexKey.create(repo, o), o.link(key), buffer);
		clear();
	}

	ChunkKey putData(MessageDigest md, Map<ChunkKey, PackChunk> cache)
			throws DhtException {
		if (isEmpty())
			return null;

		ChunkKey key = newKey(md);
		byte[] data = copy(chunk, ptr);
		if (cache != null) {
			PackChunk.Builder b = new PackChunk.Builder();
			b.setChunkKey(key);
			b.setChunkData(data);
			cache.put(key, b.build());
		}
		db.chunks().putData(key, data, buffer);
		clear();
		return key;
	}

	void clear() {
		ptr = 0;
		objectCount = 0;
		objects.clear();
	}

	boolean whole(Deflater def, int type, byte[] data, int off, int len,
			ObjectId objId) {
		if (free() < 10)
			return false;

		header(type, len);
		int endOfHeader = ptr;
		def.setInput(data, off, len);
		def.finish();
		do {
			int left = free();
			if (left == 0) {
				rollback();
				return false;
			}

			int n = def.deflate(chunk, ptr, left);
			if (n == 0) {
				rollback();
				return false;
			}

			ptr += n;
		} while (!def.finished());

		objects.add(new StoredObject(objId, mark, ptr - endOfHeader));
		objectCount++;
		return true;
	}

	void deflate(Deflater def) {
		while (!def.finished()) {
			int left = free();
			if (left == 0)
				return;
			int n = def.deflate(chunk, ptr, left);
			if (n == 0)
				return;
			ptr += n;
		}
	}

	boolean whole(int type, long len) {
		if (free() < 10)
			return false;

		header(type, len);
		return true;
	}

	boolean ofsDelta(long len, int offsetDiff) {
		int n = encodeVarInt(offsetDiff);
		if (free() < 10 + (hdrBuf.length - n))
			return false;

		header(Constants.OBJ_OFS_DELTA, len);
		if (append(hdrBuf, n, hdrBuf.length - n))
			return true;

		rollback();
		return false;
	}

	private int encodeVarInt(int value) {
		int n = hdrBuf.length - 1;
		hdrBuf[n] = (byte) (value & 0x7F);
		while ((value >>= 7) > 0)
			hdrBuf[--n] = (byte) (0x80 | (--value & 0x7F));
		return n;
	}

	boolean refDelta(long len, AnyObjectId baseId) {
		if (free() < 30)
			return false;

		header(Constants.OBJ_REF_DELTA, len);
		baseId.copyRawTo(chunk, ptr);
		ptr += 20;
		return true;
	}

	boolean chunkDelta(long len, ChunkKey key, int offset) {
		int n = encodeVarInt(offset);
		if (free() < 10 + 1 + 22 + n)
			return false;

		header(Constants.OBJ_EXT, len);
		chunk[ptr++] = PackChunk.OBJ_CHUNK_DELTA;

		key.toChunk(chunk, ptr);
		ptr += 22;
		if (append(hdrBuf, n, hdrBuf.length - n))
			return true;

		rollback();
		return false;
	}

	boolean append(byte[] data, int off, int len) {
		if (free() < len)
			return false;

		System.arraycopy(data, off, chunk, ptr, len);
		ptr += len;
		return true;
	}

	boolean append(ChunkWriter w, int off, int len) {
		if (free() < len)
			return false;

		System.arraycopy(w.chunk, off, chunk, ptr, len);
		ptr += len;
		return true;
	}

	boolean isEmpty() {
		return ptr == 0;
	}

	int getObjectCount() {
		return objectCount;
	}

	int position() {
		return ptr;
	}

	void rollback() {
		ptr = mark;
		objectCount--;
	}

	int size() {
		return ptr;
	}

	int free() {
		return chunk.length - ptr;
	}

	private void header(int type, long rawLength) {
		mark = ptr;
		objectCount++;

		long nextLength = rawLength >>> 4;
		chunk[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (rawLength & 0x0F));
		rawLength = nextLength;
		while (rawLength > 0) {
			nextLength >>>= 7;
			chunk[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (rawLength & 0x7F));
			rawLength = nextLength;
		}
	}

	private static byte[] copy(byte[] src, int len) {
		byte[] dst = new byte[len];
		System.arraycopy(src, 0, dst, 0, len);
		return dst;
	}

	private ChunkKey newKey(MessageDigest md) {
		md.update(chunk, 0, ptr);
		return ChunkKey.create(repo, ObjectId.fromRaw(md.digest()));
	}

	private static class StoredObject extends PackedObjectInfo {
		private final int size;

		StoredObject(AnyObjectId id, int offset, int size) {
			super(id);
			setOffset(offset);
			this.size = size;
		}

		ChunkLink link(ChunkKey key) {
			return new ChunkLink(key, -1, (int) getOffset(), size, null);
		}
	}
}
