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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta.BaseChunk;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ObjectInfo.ObjectType;
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
	static final int TRAILER_SIZE = 4;

	private final RepositoryKey repo;

	private final DhtInserterOptions options;

	private final byte[] varIntBuf;

	private final int maxObjects;

	private Map<ChunkKey, BaseChunkInfo> baseChunks;

	private List<StoredObject> objectList;

	private byte[] chunkData;

	private int ptr;

	private int mark;

	private int currentObjectType;

	private BaseChunkInfo currentObjectBase;

	private PackChunk.Members builder;

	private GitStore.ChunkInfo.Source source;

	private boolean fragment;

	private int objectType;

	private int objectsTotal, objectsWhole, objectsRefDelta, objectsOfsDelta;

	private ChunkInfo chunkInfo;

	ChunkFormatter(RepositoryKey repo, DhtInserterOptions options) {
		this.repo = repo;
		this.options = options;
		this.varIntBuf = new byte[32];
		this.chunkData = new byte[options.getChunkSize()];
		this.maxObjects = options.getMaxObjectCount();
		this.objectType = -1;
	}

	void setSource(GitStore.ChunkInfo.Source src) {
		source = src;
	}

	void setObjectType(int type) {
		objectType = type;
	}

	void setFragment() {
		fragment = true;
	}

	ChunkKey getChunkKey() {
		return getChunkInfo().getChunkKey();
	}

	ChunkInfo getChunkInfo() {
		return chunkInfo;
	}

	ChunkMeta getChunkMeta() {
		return builder.getMeta();
	}

	PackChunk getPackChunk() throws DhtException {
		return builder.build();
	}

	void setChunkIndex(List<PackedObjectInfo> objs) {
		builder.setChunkIndex(ChunkIndex.create(objs));
	}

	ChunkKey end(MessageDigest md) {
		if (md == null)
			md = Constants.newMessageDigest();

		// Embed a small amount of randomness into the chunk content,
		// and thus impact its name. This prevents malicious clients from
		// being able to predict what a chunk is called, which keeps them
		// from replacing an existing chunk.
		//
		chunkData = cloneArray(chunkData, ptr + TRAILER_SIZE);
		NB.encodeInt32(chunkData, ptr, options.nextChunkSalt());
		ptr += 4;

		md.update(chunkData, 0, ptr);
		ChunkKey key = ChunkKey.create(repo, ObjectId.fromRaw(md.digest()));

		GitStore.ChunkInfo.Builder info = GitStore.ChunkInfo.newBuilder();
		info.setSource(source);
		info.setObjectType(GitStore.ChunkInfo.ObjectType.valueOf(objectType));
		if (fragment)
			info.setIsFragment(true);
		info.setChunkSize(chunkData.length);

		GitStore.ChunkInfo.ObjectCounts.Builder cnts = info.getObjectCountsBuilder();
		cnts.setTotal(objectsTotal);
		if (objectsWhole > 0)
			cnts.setWhole(objectsWhole);
		if (objectsRefDelta > 0)
			cnts.setRefDelta(objectsRefDelta);
		if (objectsOfsDelta > 0)
			cnts.setOfsDelta(objectsOfsDelta);

		builder = new PackChunk.Members();
		builder.setChunkKey(key);
		builder.setChunkData(chunkData);

		if (baseChunks != null) {
			List<BaseChunk> list = new ArrayList<BaseChunk>(baseChunks.size());
			for (BaseChunkInfo b : baseChunks.values()) {
				if (0 < b.useCount) {
					BaseChunk.Builder c = BaseChunk.newBuilder();
					c.setRelativeStart(b.relativeStart);
					c.setChunkKey(b.key.asString());
					list.add(c.build());
				}
			}
			Collections.sort(list, new Comparator<BaseChunk>() {
				public int compare(BaseChunk a, BaseChunk b) {
					return Long.signum(a.getRelativeStart()
							- b.getRelativeStart());
				}
			});
			ChunkMeta.Builder b = ChunkMeta.newBuilder();
			b.addAllBaseChunk(list);
			ChunkMeta meta = b.build();
			builder.setMeta(meta);
			info.setMetaSize(meta.getSerializedSize());
		}

		if (objectList != null && !objectList.isEmpty()) {
			byte[] index = ChunkIndex.create(objectList);
			builder.setChunkIndex(index);
			info.setIndexSize(index.length);
		}

		chunkInfo = new ChunkInfo(key, info.build());
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

		db.repository().put(repo, getChunkInfo(), chunkBuf);
		chunkBuf.flush();

		db.chunk().put(builder, chunkBuf);
		chunkBuf.flush();

		linkObjects(db, dbWriteBuffer);
	}

	void unsafePut(Database db, WriteBuffer dbWriteBuffer) throws DhtException {
		db.repository().put(repo, getChunkInfo(), dbWriteBuffer);
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

	boolean whole(Deflater def, int type, byte[] data, int off, final int size,
			ObjectId objId) {
		if (free() < 10 || maxObjects <= objectsTotal)
			return false;

		header(type, size);
		objectsWhole++;
		currentObjectType = type;

		int endOfHeader = ptr;
		def.setInput(data, off, size);
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

		final int packedSize = ptr - endOfHeader;
		objectList.add(new StoredObject(objId, type, mark, packedSize, size));

		if (objectType < 0)
			objectType = type;
		else if (objectType != type)
			objectType = ChunkInfo.OBJ_MIXED;

		return true;
	}

	boolean whole(int type, long inflatedSize) {
		if (free() < 10 || maxObjects <= objectsTotal)
			return false;

		header(type, inflatedSize);
		objectsWhole++;
		currentObjectType = type;
		return true;
	}

	boolean ofsDelta(long inflatedSize, long negativeOffset) {
		final int ofsPtr = encodeVarInt(negativeOffset);
		final int ofsLen = varIntBuf.length - ofsPtr;
		if (free() < 10 + ofsLen || maxObjects <= objectsTotal)
			return false;

		header(Constants.OBJ_OFS_DELTA, inflatedSize);
		objectsOfsDelta++;
		currentObjectType = Constants.OBJ_OFS_DELTA;
		currentObjectBase = null;

		if (append(varIntBuf, ofsPtr, ofsLen))
			return true;

		rollback();
		return false;
	}

	boolean refDelta(long inflatedSize, AnyObjectId baseId) {
		if (free() < 30 || maxObjects <= objectsTotal)
			return false;

		header(Constants.OBJ_REF_DELTA, inflatedSize);
		objectsRefDelta++;
		currentObjectType = Constants.OBJ_REF_DELTA;

		baseId.copyRawTo(chunkData, ptr);
		ptr += 20;
		return true;
	}

	void useBaseChunk(long relativeStart, ChunkKey baseChunkKey) {
		if (baseChunks == null)
			baseChunks = new HashMap<ChunkKey, BaseChunkInfo>();

		BaseChunkInfo base = baseChunks.get(baseChunkKey);
		if (base == null) {
			base = new BaseChunkInfo(relativeStart, baseChunkKey);
			baseChunks.put(baseChunkKey, base);
		}
		base.useCount++;
		currentObjectBase = base;
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
		return objectsTotal;
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
		objectsTotal += delta;

		switch (type) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			objectsWhole += delta;
			break;

		case Constants.OBJ_OFS_DELTA:
			objectsOfsDelta += delta;
			if (currentObjectBase != null && --currentObjectBase.useCount == 0)
				baseChunks.remove(currentObjectBase.key);
			currentObjectBase = null;
			break;

		case Constants.OBJ_REF_DELTA:
			objectsRefDelta += delta;
			break;
		}
	}

	private void header(int type, long inflatedSize) {
		mark = ptr;
		objectsTotal++;

		long nextLength = inflatedSize >>> 4;
		chunkData[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (inflatedSize & 0x0F));
		inflatedSize = nextLength;
		while (inflatedSize > 0) {
			nextLength >>>= 7;
			chunkData[ptr++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (inflatedSize & 0x7F));
			inflatedSize = nextLength;
		}
	}

	private int encodeVarInt(long value) {
		int n = varIntBuf.length - 1;
		varIntBuf[n] = (byte) (value & 0x7F);
		while ((value >>= 7) > 0)
			varIntBuf[--n] = (byte) (0x80 | (--value & 0x7F));
		return n;
	}

	private static byte[] cloneArray(byte[] src, int len) {
		byte[] dst = new byte[len];
		System.arraycopy(src, 0, dst, 0, len);
		return dst;
	}

	private static class BaseChunkInfo {
		final long relativeStart;

		final ChunkKey key;

		int useCount;

		BaseChunkInfo(long relativeStart, ChunkKey key) {
			this.relativeStart = relativeStart;
			this.key = key;
		}
	}

	private static class StoredObject extends PackedObjectInfo {
		private final int type;

		private final int packed;

		private final int inflated;

		StoredObject(AnyObjectId id, int type, int offset, int packed, int size) {
			super(id);
			setOffset(offset);
			this.type = type;
			this.packed = packed;
			this.inflated = size;
		}

		ObjectInfo link(ChunkKey key) {
			GitStore.ObjectInfo.Builder b = GitStore.ObjectInfo.newBuilder();
			b.setObjectType(ObjectType.valueOf(type));
			b.setOffset((int) getOffset());
			b.setPackedSize(packed);
			b.setInflatedSize(inflated);
			return new ObjectInfo(key, b.build());
		}
	}
}
