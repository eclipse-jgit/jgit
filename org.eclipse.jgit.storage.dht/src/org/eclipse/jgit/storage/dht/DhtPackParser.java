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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.file.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.LongList;

class DhtPackParser extends PackParser {
	private final RepositoryKey repo;

	private final Database db;

	private final WriteBuffer buffer;

	private final DhtInserterOptions options;

	private final MessageDigest digest;

	/** Chunk writers for the 4 major object types, keyed by object type code. */
	private ChunkWriter[] writers;

	/** Prior chunks that were written, keyed by object type code. */
	private List<ChunkKey>[] keys;

	// Correlated lists, sorted by object stream position.
	private LongList objStreamPositions;

	private LongList objChunkPositions;

	/** Writer handling the current object's data stream. */
	private ChunkWriter currWriter;

	/** Position of the current object in the input stream. */
	private long currStreamPos;

	/** Position of the current object in the chunks we create. */
	private long currChunkPos;

	/** Starting byte of the object data (aka end of the object header). */
	private int currDataPos;

	/** If true, the header can be reused when the chunk splits. */
	private boolean currReuseHeader;

	/** If using OFS_DELTA, location of the base object in chunk space. */
	private long currBaseKey;

	/** If using OFS_DELTA, inflated size of the current delta. */
	private long currDeltaSize;

	/** Total number of bytes in the object representation. */
	private long currDataSize;

	/** If the current object is fragmented, the list of chunks holding it. */
	private List<ChunkKey> currFragments;

	/** Previously written chunk that is being re-read during delta resolution. */
	private PackChunk dbChunk;

	/** Current read position in {@link #dbChunk}. */
	private int dbPtr;

	/** Recent chunks that were written, or recently read. */
	private LinkedHashMap<ChunkKey, PackChunk> recentChunks;

	DhtPackParser(DhtObjDatabase objdb, InputStream in, RepositoryKey repo,
			Database db, WriteBuffer buffer, DhtInserterOptions options) {
		super(objdb, in);
		this.repo = repo;
		this.db = db;
		this.buffer = buffer;
		this.options = options;
		this.digest = Constants.newMessageDigest();

		writers = new ChunkWriter[5];
		keys = new List[5];

		final int max = 4;
		recentChunks = new LinkedHashMap<ChunkKey, PackChunk>(max, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<ChunkKey, PackChunk> e) {
				return max < size();
			}
		};
	}

	@Override
	public PackLock parse(ProgressMonitor progress) throws IOException {
		PackLock lock = super.parse(progress);

		putIndex();
		buffer.flush();

		objStreamPositions = null;
		objChunkPositions = null;
		writers = null;
		keys = null;
		recentChunks = null;

		return lock;
	}

	private void putIndex() throws DhtException {
		final int objCnt = getObjectCount();
		if (objCnt == 0)
			return;

		// Below we assume offset is chunkPos, but its not yet. Copy it over.
		//
		for (int i = 0; i < objCnt; i++) {
			PackedObjectInfo oe = getObject(i);
			oe.setOffset(((DhtInfo) oe).chunkPos);
		}

		// Sorting all of the objects by offset puts objects in the same chunk
		// next to each other in the list. This groups them for chunk indexing.
		//
		List<PackedObjectInfo> all = getSortedObjectList(new Comparator<PackedObjectInfo>() {
			public int compare(PackedObjectInfo a, PackedObjectInfo b) {
				return Long.signum(a.getOffset() - b.getOffset());
			}
		});

		int beginIdx = 0;
		ChunkKey key = chunkOf(all.get(0).getOffset());
		linkToChunk((DhtInfo) all.get(0), key);

		for (int i = 1; i < all.size(); i++) {
			DhtInfo oe = (DhtInfo) all.get(i);
			ChunkKey oeKey = chunkOf(oe.getOffset());
			if (!key.equals(oeKey)) {
				putIndex(all, key, beginIdx, i);
				beginIdx = i;
				key = oeKey;
			}
			linkToChunk(oe, key);
		}
		putIndex(all, key, beginIdx, all.size());
	}

	private void linkToChunk(DhtInfo o, ChunkKey k) throws DhtException {
		db.objectIndex().add(ObjectIndexKey.create(repo, o), o.link(k), buffer);
	}

	private void putIndex(List<PackedObjectInfo> all, ChunkKey key, int b, int e)
			throws DhtException {
		db.chunks().putIndex(key, ChunkIndex.create(all.subList(b, e)), buffer);
	}

	@Override
	protected PackedObjectInfo newInfo(AnyObjectId id, UnresolvedDelta delta,
			ObjectId baseId) {
		DhtInfo obj = new DhtInfo(id);
		if (delta != null) {
			DhtDelta d = (DhtDelta) delta;
			obj.chunkPos = d.chunkPos;
			obj.base = baseId;
			obj.setSize(d.getSize());
		}
		return obj;
	}

	@Override
	protected void onPackHeader(long objCnt) throws IOException {
		if (Integer.MAX_VALUE < objCnt) {
			throw new DhtException(MessageFormat.format(
					DhtText.get().tooManyObjectsInPack, objCnt));
		}

		objStreamPositions = new LongList((int) objCnt);
		objChunkPositions = new LongList((int) objCnt);
	}

	@Override
	protected void onBeginWholeObject(long streamPosition, int type,
			long inflatedSize) throws IOException {
		currStreamPos = streamPosition;
		currDataSize = 0;

		ChunkWriter w = begin(type);
		if (!w.whole(type, inflatedSize)) {
			flush(type);
			w = begin(type);
			if (!w.whole(type, inflatedSize))
				throw panicCannotInsert();
		}

		currDataPos = w.position();
		currReuseHeader = true;
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		if (currFragments != null)
			finishFragmentedObject();

		objStreamPositions.add(currStreamPos);
		objChunkPositions.add(currChunkPos);

		DhtInfo oe = (DhtInfo) info;
		oe.chunkPos = currChunkPos;
		oe.setSize((int) Math.min(currDataSize, Integer.MAX_VALUE));
	}

	@Override
	protected void onBeginOfsDelta(long deltaPos, long basePos, long sz)
			throws IOException {
		currStreamPos = deltaPos;
		currDataSize = 0;

		int baseIdx = findStreamPosition(basePos);
		long baseKey = objChunkPositions.get(baseIdx);
		int type = typeOf(baseKey);
		ChunkWriter w = begin(type);

		// If the base is in the same chunk, use standard OFS_DELTA format.
		if (isCurrentChunk(baseKey)) {
			if (w.ofsDelta(sz, w.position() - offsetOf(baseKey))) {
				currDataPos = w.position();
				currBaseKey = baseKey;
				currDeltaSize = sz;
				currReuseHeader = false;
				return;
			}

			flush(type);
			w = begin(type);
		}

		ChunkKey baseChunk = chunkOf(baseKey);
		if (!w.chunkDelta(sz, baseChunk, offsetOf(baseKey))) {
			flush(type);
			w = begin(type);
			if (!w.chunkDelta(sz, baseChunk, offsetOf(baseKey)))
				throw panicCannotInsert();
		}

		currDataPos = w.position();
		currReuseHeader = true;
	}

	@Override
	protected void onBeginRefDelta(long deltaPos, AnyObjectId baseId, long sz)
			throws IOException {
		currStreamPos = deltaPos;
		currDataSize = 0;

		int type = OBJ_BLOB;
		ChunkWriter w = begin(type);
		if (!w.refDelta(sz, baseId)) {
			flush(type);
			w = begin(type);
			if (!w.refDelta(sz, baseId))
				throw panicCannotInsert();
		}

		currDataPos = w.position();
		currReuseHeader = true;
	}

	@Override
	protected DhtDelta onEndDelta() throws IOException {
		if (currFragments != null)
			finishFragmentedObject();

		objStreamPositions.add(currStreamPos);
		objChunkPositions.add(currChunkPos);

		DhtDelta delta = new DhtDelta();
		delta.chunkPos = currChunkPos;
		delta.setSize((int) Math.min(currDataSize, Integer.MAX_VALUE));
		return delta;
	}

	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		if (src != Source.INPUT)
			return;

		currDataSize += len;

		if (currWriter.append(raw, pos, len))
			return;

		if (currWriter.getObjectCount() == 1)
			currFragments = new LinkedList<ChunkKey>();
		if (currFragments != null) {
			appendToFragment(raw, pos, len);
			return;
		}

		// Everything between dataPos and dataEnd must be saved.
		//
		final ChunkWriter oldWriter = currWriter;
		final long oldPos = currChunkPos;
		final int type = typeOf(oldPos);
		int dataPos = currDataPos;
		int dataEnd = currWriter.position();

		flush(type);
		currWriter = begin(type);

		if (currReuseHeader) {
			dataPos = offsetOf(oldPos);
		} else {
			if (currWriter == oldWriter && dataPos < 64) {
				// The old data is in danger of being overwritten by the new
				// header. Simplest solution is a new writer.
				writers[type] = null;
				currWriter = begin(type);
			}

			ChunkKey c = chunkOf(currBaseKey);
			if (!currWriter.chunkDelta(currDeltaSize, c, offsetOf(currBaseKey)))
				throw panicCannotInsert();
		}

		if (!currWriter.append(oldWriter, dataPos, dataEnd - dataPos))
			throw panicCannotInsert(); // must work, its already buffered.

		if (!currWriter.append(raw, pos, len)) {
			currFragments = new LinkedList<ChunkKey>();
			appendToFragment(raw, pos, len);
		}
	}

	private void appendToFragment(byte[] raw, int pos, int len)
			throws DhtException {
		while (0 < len) {
			if (currWriter.free() == 0)
				currFragments.add(flush(typeOf(currChunkPos)));
			int n = Math.min(len, currWriter.free());
			currWriter.append(raw, pos, n);
			pos += n;
			len -= n;
		}
	}

	private void finishFragmentedObject() throws DhtException {
		ChunkKey key = flush(typeOf(currChunkPos));
		if (key != null)
			currFragments.add(key);

		byte[] bin = ChunkFragments.toByteArray(currFragments);
		for (ChunkKey k : currFragments)
			db.chunks().putFragments(k, bin, buffer);
		currFragments = null;
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo obj,
			ObjectTypeAndSize info) throws IOException {
		return seekDatabase(((DhtInfo) obj).chunkPos, info);
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		return seekDatabase(((DhtDelta) delta).chunkPos, info);
	}

	private ObjectTypeAndSize seekDatabase(long chunkPos, ObjectTypeAndSize info)
			throws DhtException {
		seekChunk(chunkOf(chunkPos), true);
		dbPtr = dbChunk.readObjectTypeAndSize(offsetOf(chunkPos), info);
		if (info.type == PackChunk.OBJ_CHUNK_DELTA)
			info.type = Constants.OBJ_REF_DELTA;
		return info;
	}

	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt) throws IOException {
		int n = dbChunk.read(dbPtr, dst, pos, cnt);
		if (0 < n) {
			dbPtr += n;
			return n;
		}

		if (dbPtr != dbChunk.size())
			return 0; // This should be impossible.

		ChunkKey next = dbChunk.getNextFragment();
		if (next == null)
			return 0;

		seekChunk(next, false);
		n = dbChunk.read(0, dst, pos, cnt);
		dbPtr = n;
		return n;
	}

	private void seekChunk(ChunkKey key, boolean cache) throws DhtException,
			DhtTimeoutException {
		if (dbChunk == null || !dbChunk.getChunkKey().equals(key)) {
			dbChunk = recentChunks.get(key);
			if (dbChunk == null) {
				buffer.flush();

				Collection<PackChunk> found;
				Sync<Collection<PackChunk>> sync = Sync.create();
				db.chunks().get(Collections.singleton(key), sync);
				try {
					found = sync.get(DhtReaderOptions.DEFAULT.getTimeout());
				} catch (InterruptedException e) {
					throw new DhtTimeoutException(e);
				} catch (TimeoutException e) {
					throw new DhtTimeoutException(e);
				}

				if (found.isEmpty()) {
					throw new DhtException(MessageFormat.format(
							DhtText.get().missingChunk, key));
				}

				dbChunk = found.iterator().next();
				if (cache)
					recentChunks.put(key, dbChunk);
			}
		}
	}

	@Override
	protected boolean onAppendBase(int typeCode, byte[] data,
			PackedObjectInfo info) throws IOException {
		return false; // This implementation does not copy base objects.
	}

	@Override
	protected void onEndThinPack() throws IOException {
		// Do nothing, this event is not relevant.
	}

	@Override
	protected void onPackFooter(byte[] hash) throws IOException {
		// TODO Combine together fractional chunks to reduce overhead.
		// Fractional chunks are common for single-commit pushes since
		// they are broken out by object type.

		// If there are deltas to be resolved the pending chunks
		// will need to be reloaded later. Ensure they are stored.
		//
		flush(OBJ_COMMIT);
		flush(OBJ_TREE);
		flush(OBJ_BLOB);
		flush(OBJ_TAG);
	}

	@Override
	protected void onObjectHeader(Source src, byte[] raw, int pos, int len)
			throws IOException {
		// Do nothing, the original stream headers are not used.
	}

	@Override
	protected void onStoreStream(byte[] raw, int pos, int len)
			throws IOException {
		// Do nothing, the stream is being sliced and cannot be stored as-is.
	}

	@Override
	protected boolean checkCRC(int oldCRC) {
		return true; // Don't bother to check CRCs, assume the chunk is OK.
	}

	private ChunkWriter begin(int typeCode) {
		final ChunkWriter w = writer(typeCode);
		currWriter = w;
		currChunkPos = position(w, typeCode);
		return w;
	}

	private ChunkKey flush(int typeCode) throws DhtException {
		ChunkWriter w = writers[typeCode];
		if (w == null)
			return null;

		// Locally cache only non-fragmented chunks during flush.
		Map<ChunkKey, PackChunk> cache = null;
		if (currFragments == null)
			cache = recentChunks;
		ChunkKey key = w.putData(digest, cache);
		if (key == null)
			return null;

		keys(typeCode).add(key);
		return key;
	}

	private List<ChunkKey> keys(int typeCode) {
		List<ChunkKey> list = keys[typeCode];
		if (list == null)
			keys[typeCode] = list = new ArrayList<ChunkKey>(4);
		return list;
	}

	private ChunkWriter writer(int typeCode) {
		ChunkWriter w = writers[typeCode];
		if (w == null)
			writers[typeCode] = w = newWriter();
		return w;
	}

	private int findStreamPosition(long streamPosition) throws DhtException {
		int high = objStreamPositions.size();
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final long pos = objStreamPositions.get(mid);
			if (streamPosition < pos)
				high = mid;
			else if (streamPosition == pos)
				return mid;
			else
				low = mid + 1;
		} while (low < high);
		throw new DhtException(MessageFormat.format(
				DhtText.get().noSavedTypeForBase, streamPosition));
	}

	private long position(ChunkWriter w, int typeCode) {
		List<ChunkKey> list = keys(typeCode);
		int idx = list.size();
		int ptr = w.position();
		return (((long) typeCode) << 61) | (((long) idx) << 32) | ptr;
	}

	private static int typeOf(long position) {
		return (int) (position >>> 61);
	}

	private boolean isCurrentChunk(long position) {
		List<ChunkKey> list = keys(typeOf(position));
		int idx = ((int) ((position << 3) >>> (32 + 3)));
		return idx == list.size();
	}

	private ChunkKey chunkOf(long position) {
		List<ChunkKey> list = keys(typeOf(position));
		int idx = ((int) ((position << 3) >>> (32 + 3)));
		return list.get(idx);
	}

	private static int offsetOf(long position) {
		return (int) position;
	}

	private ChunkWriter newWriter() {
		return new ChunkWriter(repo, db, buffer, options.getChunkSize());
	}

	private static DhtException panicCannotInsert() {
		// This exception should never happen.
		return new DhtException(DhtText.get().cannotInsertObject);
	}

	static class DhtInfo extends PackedObjectInfo {
		long chunkPos;

		ObjectId base;

		DhtInfo(AnyObjectId id) {
			super(id);
		}

		// Since the CRC isn't used in this implementation, reuse its storage
		// for the size field that is required.

		int getSize() {
			return getCRC();
		}

		void setSize(int size) {
			setCRC(size);
		}

		ChunkLink link(ChunkKey chunkKey) {
			int pos = offsetOf(chunkPos);
			return new ChunkLink(chunkKey, -1, pos, getSize(), base);
		}
	}

	static class DhtDelta extends UnresolvedDelta {
		long chunkPos;

		// Since the CRC isn't used in this implementation, reuse its storage
		// for the size field that is required.

		int getSize() {
			return getCRC();
		}

		void setSize(int size) {
			setCRC(size);
		}
	}
}
