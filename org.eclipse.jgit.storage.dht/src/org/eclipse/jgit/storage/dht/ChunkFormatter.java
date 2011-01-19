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
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Formats one {@link PackChunk} for storage in the DHT.
 * <p>
 * Each formatter instance can be used only once.
 */
class ChunkFormatter {
	static final int TRAILER_SIZE = 8;

	private final RepositoryKey repo;

	private final DhtInserterOptions options;

	private final byte[] varIntBuf;

	private final ChunkInfo info;

	private final int maxObjects;

	private List<StoredObject> objectList;

	private byte[] chunkData;

	private int ptr;

	private int mark;

	private int currentObjectType;

	private PackChunk.Members builder;

	ChunkFormatter(RepositoryKey repo, DhtInserterOptions options) {
		this.repo = repo;
		this.options = options;
		this.varIntBuf = new byte[32];
		this.info = new ChunkInfo();
		this.chunkData = new byte[options.getChunkSize()];
		this.maxObjects = options.getMaxObjectCount();
	}

	void setSource(ChunkInfo.Source src) {
		info.source = src;
	}

	void setObjectType(int type) {
		info.objectType = type;
	}

	void setFragment() {
		info.fragment = true;
	}

	ChunkKey getChunkKey() {
		return getChunkInfo().getChunkKey();
	}

	ChunkInfo getChunkInfo() {
		return info;
	}

	PackChunk getPackChunk() throws DhtException {
		return builder.build();
	}

	ChunkKey end(MessageDigest md) {
		if (md == null)
			md = Constants.newMessageDigest();

		// Embed a small amount of randomness into the chunk content,
		// and thus impact its name. This prevents malicious clients from
		// being able to predict what a chunk is called, which keeps them
		// from replacing an existing chunk.
		//
		NB.encodeInt32(chunkData, ptr, options.nextChunkSalt());
		ptr += 4;

		CRC32 crc = new CRC32();
		crc.update(chunkData, 0, ptr);
		NB.encodeInt32(chunkData, ptr, (int) crc.getValue());
		ptr += 4;

		md.update(chunkData, 0, ptr);
		chunkData = resize(chunkData, ptr);

		info.chunkKey = ChunkKey.create(repo, ObjectId.fromRaw(md.digest()));
		info.chunkSize = chunkData.length;

		builder = new PackChunk.Members();
		builder.setChunkKey(info.chunkKey);
		builder.setChunkData(chunkData);

		if (objectList != null && !objectList.isEmpty()) {
			byte[] index = ChunkIndex.create(objectList);
			builder.setChunkIndex(index);
			info.indexSize = index.length;
		}

		return getChunkKey();
	}

	/**
	 * Safely put the chunk to the database.
	 * <p>
	 * This method is slow. It first puts the chunk info, waits for success,
	 * then puts the chunk itself, waits for success, and finally queues up the
	 * object index with its chunk links in the supplied buffer.
	 *
	 * @param db
	 * @param dbWriteBuffer
	 * @throws DhtException
	 */
	void safePut(Database db, WriteBuffer dbWriteBuffer) throws DhtException {
		WriteBuffer chunkBuf = db.newWriteBuffer();

		db.repository().put(repo, info, chunkBuf);
		chunkBuf.flush();

		db.chunk().put(builder, chunkBuf);
		chunkBuf.flush();

		linkObjects(db, dbWriteBuffer);
	}

	void unsafePut(Database db, WriteBuffer dbWriteBuffer) throws DhtException {
		db.repository().put(repo, info, dbWriteBuffer);
		db.chunk().put(builder, dbWriteBuffer);
		linkObjects(db, dbWriteBuffer);
	}

	private void linkObjects(Database db, WriteBuffer dbWriteBuffer)
			throws DhtException {
		if (objectList != null && !objectList.isEmpty()) {
			for (StoredObject obj : objectList) {
				db.objectIndex().add(ObjectIndexKey.create(repo, obj),
						obj.link(getChunkKey()), dbWriteBuffer);
			}
		}
	}

	boolean whole(Deflater def, int type, byte[] data, int off, int len,
			ObjectId objId) {
		if (free() < 10 || maxObjects <= info.objectsTotal)
			return false;

		header(type, len);
		info.objectsWhole++;
		currentObjectType = type;

		int endOfHeader = ptr;
		def.setInput(data, off, len);
		def.finish();
		do {
			int left = free();
			if (left == 0) {
				rollback();
				return false;
			}

			int n = def.deflate(chunkData, ptr, left);
			if (n == 0) {
				rollback();
				return false;
			}

			ptr += n;
		} while (!def.finished());

		if (objectList == null)
			objectList = new ArrayList<StoredObject>();
		objectList.add(new StoredObject(objId, mark, ptr - endOfHeader));

		if (info.objectType < 0)
			info.objectType = type;
		else if (info.objectType != type)
			info.objectType = ChunkInfo.OBJ_MIXED;

		return true;
	}

	boolean whole(int type, long len) {
		if (free() < 10 || maxObjects <= info.objectsTotal)
			return false;

		header(type, len);
		info.objectsWhole++;
		currentObjectType = type;
		return true;
	}

	boolean ofsDelta(long len, int offsetDiff) {
		int n = encodeVarInt(offsetDiff);
		if (free() < 10 + (varIntBuf.length - n)
				|| maxObjects <= info.objectsTotal)
			return false;

		header(Constants.OBJ_OFS_DELTA, len);
		info.objectsOfsDelta++;
		currentObjectType = Constants.OBJ_OFS_DELTA;

		if (append(varIntBuf, n, varIntBuf.length - n))
			return true;

		rollback();
		return false;
	}

	boolean refDelta(long len, AnyObjectId baseId) {
		if (free() < 30 || maxObjects <= info.objectsTotal)
			return false;

		header(Constants.OBJ_REF_DELTA, len);
		info.objectsRefDelta++;
		currentObjectType = Constants.OBJ_REF_DELTA;

		baseId.copyRawTo(chunkData, ptr);
		ptr += 20;
		return true;
	}

	boolean chunkDelta(long len, ChunkKey key, int offset) {
		int n = encodeVarInt(offset);
		if (free() < 10 + 1 + 22 + n || maxObjects <= info.objectsTotal)
			return false;

		header(Constants.OBJ_EXT, len);
		chunkData[ptr++] = PackChunk.OBJ_CHUNK_DELTA;

		info.objectsChunkDelta++;
		currentObjectType = PackChunk.OBJ_CHUNK_DELTA;

		key.toChunk(chunkData, ptr);
		ptr += 22;
		if (append(varIntBuf, n, varIntBuf.length - n))
			return true;

		rollback();
		return false;
	}

	void appendDeflateOutput(Deflater def) {
		while (!def.finished()) {
			int left = free();
			if (left == 0)
				return;
			int n = def.deflate(chunkData, ptr, left);
			if (n == 0)
				return;
			ptr += n;
		}
	}

	boolean append(byte[] data, int off, int len) {
		if (free() < len)
			return false;

		System.arraycopy(data, off, chunkData, ptr, len);
		ptr += len;
		return true;
	}

	boolean isEmpty() {
		return ptr == 0;
	}

	int getObjectCount() {
		return info.objectsTotal;
	}

	int position() {
		return ptr;
	}

	int size() {
		return ptr;
	}

	int free() {
		return (chunkData.length - TRAILER_SIZE) - ptr;
	}

	byte[] getRawChunkDataArray() {
		return chunkData;
	}

	int getCurrentObjectType() {
		return currentObjectType;
	}

	void rollback() {
		ptr = mark;
		adjustObjectCount(-1, currentObjectType);
	}

	void adjustObjectCount(int delta, int type) {
		info.objectsTotal += delta;

		switch (type) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			info.objectsWhole += delta;
			break;

		case Constants.OBJ_OFS_DELTA:
			info.objectsOfsDelta += delta;
			break;

		case Constants.OBJ_REF_DELTA:
			info.objectsRefDelta += delta;
			break;

		case PackChunk.OBJ_CHUNK_DELTA:
			info.objectsChunkDelta += delta;
			break;
		}
	}

	private void header(int type, long rawLength) {
		mark = ptr;
		info.objectsTotal++;

		long nextLength = rawLength >>> 4;
		chunkData[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (rawLength & 0x0F));
		rawLength = nextLength;
		while (rawLength > 0) {
			nextLength >>>= 7;
			chunkData[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (rawLength & 0x7F));
			rawLength = nextLength;
		}
	}

	private int encodeVarInt(int value) {
		int n = varIntBuf.length - 1;
		varIntBuf[n] = (byte) (value & 0x7F);
		while ((value >>= 7) > 0)
			varIntBuf[--n] = (byte) (0x80 | (--value & 0x7F));
		return n;
	}

	private static byte[] resize(byte[] src, int len) {
		if (src.length == len)
			return src;
		byte[] dst = new byte[len];
		System.arraycopy(src, 0, dst, 0, len);
		return dst;
	}

	private static class StoredObject extends PackedObjectInfo {
		private final int size;

		StoredObject(AnyObjectId id, int offset, int size) {
			super(id);
			setOffset(offset);
			this.size = size;
		}

		ObjectInfo link(ChunkKey key) {
			return new ObjectInfo(key, -1, (int) getOffset(), size, null, false);
		}
	}
}
