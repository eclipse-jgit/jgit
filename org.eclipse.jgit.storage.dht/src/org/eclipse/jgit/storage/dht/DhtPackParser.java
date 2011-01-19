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
import static org.eclipse.jgit.storage.dht.ChunkInfo.OBJ_MIXED;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.file.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.LongList;

/** Parses the pack stream into chunks, and indexes the chunks for lookup. */
final class DhtPackParser extends PackParser {
	private final RepositoryKey repo;

	private final Database db;

	private final DhtInserterOptions options;

	private final MessageDigest digest;

	/** Tiny buffer for sliding an object header from one chunk to another. */
	private final byte[] hdrBuf = new byte[ChunkFormatter.TRAILER_SIZE];

	private WriteBuffer dbWriteBuffer;

	/** Chunk writers for the 4 major object types, keyed by object type code. */
	private ChunkFormatter[] openChunks;

	/** Prior chunks that were written, keyed by object type code. */
	private List<ChunkKey>[] keys;

	/** Edges for current chunks. */
	private Edges[] openEdges;

	/** Information on chunks already written out. */
	private Map<ChunkKey, ChunkInfo> chunkInfo;

	private Map<ChunkKey, Edges> chunkEdges;

	// Correlated lists, sorted by object stream position.
	private LongList objStreamPos;

	private LongList objChunkPtrs;

	/** Writer handling the current object's data stream. */
	private ChunkFormatter currChunkFmt;

	/** Position of the current object in the input stream. */
	private long currStreamPos;

	/** Position of the current object in the chunks we create. */
	private long currChunkPtr;

	/** If true, the header can be reused when the chunk splits. */
	private boolean currCanReuseHeader;

	/** If using OFS_DELTA, location of the base object in chunk space. */
	private long currBasePtr;

	/** If using OFS_DELTA, inflated size of the current delta. */
	private long currInflatedSize;

	/** Starting byte of the object data (aka end of the object header). */
	private int currDataPos;

	/** Total number of bytes in the object representation. */
	private long currDataLen;

	/** If the current object is fragmented, the list of chunks holding it. */
	private List<ChunkKey> currFragments;

	/** Previously written chunk that is being re-read during delta resolution. */
	private PackChunk dbChunk;

	/** Current read position in {@link #dbChunk}. */
	private int dbPtr;

	/** Recent chunks that were written, or recently read. */
	private LinkedHashMap<ChunkKey, PackChunk> chunkReadBackCache;

	/** During {@ilnk #putIndex()}, objects in this pack stream. */
	private List<PackedObjectInfo> allObjects;

	/** Index of last put to the object index table. */
	private int linkedIdx;

	private final MutableObjectId idBuffer;

	private ObjectIdSubclassMap<DhtInfo> objectsByName;

	DhtPackParser(DhtObjDatabase objdb, InputStream in, RepositoryKey repo,
			Database db, DhtInserterOptions options) {
		super(objdb, in);

		// Disable collision checking. DhtReader performs some magic to look
		// only at old objects, so a colliding replacement will be ignored until
		// its removed during garbage collection.
		//
		setCheckObjectCollisions(false);

		this.repo = repo;
		this.db = db;
		this.options = options;
		this.digest = Constants.newMessageDigest();

		dbWriteBuffer = db.newWriteBuffer();
		openChunks = new ChunkFormatter[5];
		keys = newListArray(5);
		openEdges = new Edges[5];
		chunkEdges = new HashMap<ChunkKey, Edges>();
		chunkInfo = new HashMap<ChunkKey, ChunkInfo>();
		idBuffer = new MutableObjectId();
		objectsByName = new ObjectIdSubclassMap<DhtInfo>();

		final int max = options.getParserCacheSize();
		chunkReadBackCache = new LinkedHashMap<ChunkKey, PackChunk>(max, 0.75f,
				true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<ChunkKey, PackChunk> e) {
				return max < size();
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T>[] newListArray(int size) {
		return new List[size];
	}

	@Override
	public PackLock parse(ProgressMonitor progress) throws IOException {
		boolean success = false;
		try {
			PackLock lock = super.parse(progress);

			chunkReadBackCache = null;
			openChunks = null;
			openEdges = null;

			final int objCnt = getObjectCount();
			if (objCnt == 0) {
				// If no objects were received, no chunks were created. Leaving
				// success to false and doing a rollback is a good way to make
				// sure this is true.
				//
				return lock;
			}

			// Later offset should be chunkPos, but its not yet.
			//
			for (int i = 0; i < objCnt; i++) {
				DhtInfo oe = (DhtInfo) getObject(i);
				oe.setOffset(oe.chunkPtr);
				objectsByName.add(oe);
			}

			allObjects = getSortedObjectList(new Comparator<PackedObjectInfo>() {
				public int compare(PackedObjectInfo a, PackedObjectInfo b) {
					return Long.signum(a.getOffset() - b.getOffset());
				}
			});

			computeChunkEdges();
			putChunkIndexes();
			dbWriteBuffer.flush();

			chunkEdges = null;
			objectsByName = null;

			putGlobalIndex();
			dbWriteBuffer.flush();

			success = true;
			return lock;
		} finally {
			dbWriteBuffer = null;
			openChunks = null;
			openEdges = null;
			objStreamPos = null;
			objChunkPtrs = null;
			currChunkFmt = null;
			currFragments = null;
			dbChunk = null;
			chunkReadBackCache = null;
			chunkInfo = null;
			chunkEdges = null;

			if (!success)
				rollback();

			keys = null;
			allObjects = null;
		}
	}

	private void rollback() {
		try {
			// TODO(spearce) Just discarding the prior buffer isn't enough.
			// Some puts may have already started in the background, and
			// its possible the deletes below arrive first, which means the
			// existing puts will succeed and the data will live.

			// Because this is an abort, don't allow flushing prior writes.
			//
			dbWriteBuffer = db.newWriteBuffer();

			for (--linkedIdx; 0 <= linkedIdx; linkedIdx--) {
				DhtInfo oe = (DhtInfo) allObjects.get(linkedIdx);
				ObjectIndexKey objKey = ObjectIndexKey.create(repo, oe);
				ChunkKey oeKey = chunkOf(oe.getOffset());
				db.objectIndex().remove(objKey, oeKey, dbWriteBuffer);
			}

			deleteChunks(keys[OBJ_COMMIT]);
			deleteChunks(keys[OBJ_TREE]);
			deleteChunks(keys[OBJ_BLOB]);
			deleteChunks(keys[OBJ_TAG]);

			dbWriteBuffer.flush();
		} catch (Throwable cleanupError) {
			// TODO(spearce) This should at least be logged.
		}
	}

	private void deleteChunks(List<ChunkKey> list) throws DhtException {
		if (list != null) {
			for (ChunkKey key : list) {
				db.chunk().remove(key, dbWriteBuffer);
				db.repository().removeInfo(repo, key, dbWriteBuffer);
			}
		}
	}

	private void putGlobalIndex() throws DhtException {
		for (linkedIdx = 0; linkedIdx < allObjects.size(); linkedIdx++) {
			DhtInfo oe = (DhtInfo) allObjects.get(linkedIdx);
			ChunkKey key = chunkOf(oe.chunkPtr);
			ChunkInfo info = chunkInfo.get(key);
			db.objectIndex().add(ObjectIndexKey.create(repo, oe),
					oe.link(key, info), dbWriteBuffer);
		}
	}

	private void computeChunkEdges() {
		int beginIdx = 0;
		ChunkKey key = chunkOf(allObjects.get(0).getOffset());
		int type = typeOf(allObjects.get(0).getOffset());

		int objIdx = 1;
		for (; objIdx < allObjects.size(); objIdx++) {
			DhtInfo oe = (DhtInfo) allObjects.get(objIdx);
			ChunkKey oeKey = chunkOf(oe.getOffset());
			if (!key.equals(oeKey)) {
				computeEdges(allObjects.subList(beginIdx, objIdx), key, type);
				beginIdx = objIdx;

				key = oeKey;
				type = typeOf(oe.getOffset());
			}
			if (type != OBJ_MIXED && type != typeOf(oe.getOffset()))
				type = OBJ_MIXED;
		}
		computeEdges(allObjects.subList(beginIdx, allObjects.size()), key, type);
	}

	private void computeEdges(List<PackedObjectInfo> objs, ChunkKey key,
			int type) {
		Edges edges = chunkEdges.get(key);
		if (edges == null)
			return;

		for (PackedObjectInfo obj : objs)
			edges.remove(obj);

		switch (type) {
		case OBJ_COMMIT:
			edges.commitEdges = toChunkList(edges.commitIds);
			break;
		case OBJ_TREE:
			// TODO prefetch tree edges
			break;
		}

		edges.commitIds = null;
	}

	private List<ChunkKey> toChunkList(Set<ObjectId> objects) {
		if (objects == null || objects.isEmpty())
			return null;

		Map<ChunkKey, ChunkOrderingEntry> map = new HashMap<ChunkKey, ChunkOrderingEntry>();
		for (ObjectId obj : objects) {
			if (!(obj instanceof DhtInfo)) {
				obj = objectsByName.get(obj);
				if (obj == null)
					continue;
			}

			long chunkPtr = ((DhtInfo) obj).chunkPtr;
			ChunkKey key = chunkOf(chunkPtr);
			ChunkOrderingEntry e = map.get(key);
			if (e == null) {
				e = new ChunkOrderingEntry();
				e.key = key;
				e.order = chunkIdx(chunkPtr);
				map.put(key, e);
			} else {
				e.order = Math.min(e.order, chunkIdx(chunkPtr));
			}
		}

		ChunkOrderingEntry[] tmp = map.values().toArray(
				new ChunkOrderingEntry[map.size()]);
		Arrays.sort(tmp);

		ChunkKey[] out = new ChunkKey[tmp.length];
		for (int i = 0; i < tmp.length; i++)
			out[i] = tmp[i].key;
		return Arrays.asList(out);
	}

	private static final class ChunkOrderingEntry implements
			Comparable<ChunkOrderingEntry> {
		ChunkKey key;

		int order;

		public int compareTo(ChunkOrderingEntry o) {
			return order - o.order;
		}
	}

	private void putChunkIndexes() throws DhtException {
		int sIdx = 0;
		ChunkKey key = chunkOf(allObjects.get(0).getOffset());
		int type = typeOf(allObjects.get(0).getOffset());

		int objIdx = 1;
		for (; objIdx < allObjects.size(); objIdx++) {
			DhtInfo oe = (DhtInfo) allObjects.get(objIdx);
			ChunkKey oeKey = chunkOf(oe.getOffset());
			if (!key.equals(oeKey)) {
				putChunkIndex(allObjects.subList(sIdx, objIdx), key, type);
				sIdx = objIdx;

				key = oeKey;
				type = typeOf(oe.getOffset());
			}
			if (type != OBJ_MIXED && type != typeOf(oe.getOffset()))
				type = OBJ_MIXED;
		}
		putChunkIndex(allObjects.subList(sIdx, allObjects.size()), key, type);
	}

	private void putChunkIndex(List<PackedObjectInfo> objs, ChunkKey key,
			int type) throws DhtException {
		ChunkInfo info = chunkInfo.get(key);
		info.objectsTotal = objs.size();
		info.objectType = type;

		PackChunk.Members builder = new PackChunk.Members();
		builder.setChunkKey(key);

		byte[] index = ChunkIndex.create(objs);
		info.indexSize = index.length;
		builder.setChunkIndex(index);

		Edges edges = chunkEdges.get(key);
		if (edges != null) {
			ChunkPrefetch.Hint commits = null;
			ChunkPrefetch.Hint trees = null;

			switch (type) {
			case OBJ_COMMIT:
				commits = toHint(edges.commitEdges, null);
				break;
			case OBJ_TREE:
				trees = toHint(null, sequentialHint(key, OBJ_TREE));
				break;
			}

			ChunkPrefetch prefetch = new ChunkPrefetch(commits, trees);
			if (!prefetch.isEmpty()) {
				info.prefetchSize = prefetch.toBytes().length;
				builder.setPrefetch(prefetch);
			}
		}

		db.repository().put(repo, info, dbWriteBuffer);
		db.chunk().put(builder, dbWriteBuffer);
	}

	private List<ChunkKey> sequentialHint(ChunkKey key, int type) {
		List<ChunkKey> all = keys(type);
		int idx = all.indexOf(key);
		if (0 <= idx) {
			int max = options.getPrefetchDepth();
			int end = Math.min(idx + 1 + max, all.size());
			return all.subList(idx + 1, end);
		}
		return null;
	}

	private ChunkPrefetch.Hint toHint(List<ChunkKey> edge,
			List<ChunkKey> sequential) {
		return new ChunkPrefetch.Hint(edge, sequential);
	}

	@Override
	protected PackedObjectInfo newInfo(AnyObjectId id, UnresolvedDelta delta,
			ObjectId baseId) {
		DhtInfo obj = new DhtInfo(id);
		if (delta != null) {
			DhtDelta d = (DhtDelta) delta;
			obj.chunkPtr = d.chunkPtr;
			obj.base = baseId;
			obj.setSize(d.getSize());
		}
		return obj;
	}

	@Override
	protected void onPackHeader(long objCnt) throws IOException {
		if (Integer.MAX_VALUE < objCnt) {
			throw new DhtException(MessageFormat.format(
					DhtText.get().tooManyObjectsInPack, Long.valueOf(objCnt)));
		}

		objStreamPos = new LongList((int) objCnt);
		objChunkPtrs = new LongList((int) objCnt);
	}

	@Override
	protected void onBeginWholeObject(long streamPosition, int type,
			long inflatedSize) throws IOException {
		ChunkFormatter w = begin(type);
		if (!w.whole(type, inflatedSize)) {
			endChunk(type);
			w = begin(type);
			if (!w.whole(type, inflatedSize))
				throw panicCannotInsert();
		}

		currStreamPos = streamPosition;
		currDataPos = w.position();
		currDataLen = 0;
		currCanReuseHeader = true;
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		if (currFragments != null)
			finishFragmentedObject();

		objStreamPos.add(currStreamPos);
		objChunkPtrs.add(currChunkPtr);

		DhtInfo oe = (DhtInfo) info;
		oe.chunkPtr = currChunkPtr;
		oe.setSize((int) Math.min(currDataLen, Integer.MAX_VALUE));
	}

	@Override
	protected void onBeginOfsDelta(long deltaPos, long basePos, long sz)
			throws IOException {
		long basePtr = objChunkPtrs.get(findStreamIndex(basePos));
		int type = typeOf(basePtr);
		ChunkFormatter w = begin(type);

		// If the base is in the same chunk, use standard OFS_DELTA format.
		if (isInCurrentChunk(basePtr)) {
			if (w.ofsDelta(sz, w.position() - offsetOf(basePtr))) {
				currStreamPos = deltaPos;
				currDataPos = w.position();
				currDataLen = 0;
				currBasePtr = basePtr;
				currInflatedSize = sz;
				currCanReuseHeader = false;
				return;
			}

			endChunk(type);
			w = begin(type);
		}

		ChunkKey baseChunkKey = chunkOf(basePtr);
		if (!w.chunkDelta(sz, baseChunkKey, offsetOf(basePtr))) {
			endChunk(type);
			w = begin(type);
			if (!w.chunkDelta(sz, baseChunkKey, offsetOf(basePtr)))
				throw panicCannotInsert();
		}

		currStreamPos = deltaPos;
		currDataPos = w.position();
		currDataLen = 0;
		currBasePtr = basePtr;
		currInflatedSize = sz;
		currCanReuseHeader = true;
	}

	@Override
	protected void onBeginRefDelta(long deltaPos, AnyObjectId baseId, long sz)
			throws IOException {
		int type = OBJ_BLOB;
		ChunkFormatter w = begin(type);
		if (!w.refDelta(sz, baseId)) {
			endChunk(type);
			w = begin(type);
			if (!w.refDelta(sz, baseId))
				throw panicCannotInsert();
		}

		currStreamPos = deltaPos;
		currDataPos = w.position();
		currDataLen = 0;
		currCanReuseHeader = true;
	}

	@Override
	protected DhtDelta onEndDelta() throws IOException {
		if (currFragments != null)
			finishFragmentedObject();

		objStreamPos.add(currStreamPos);
		objChunkPtrs.add(currChunkPtr);

		DhtDelta delta = new DhtDelta();
		delta.chunkPtr = currChunkPtr;
		delta.setSize((int) Math.min(currDataLen, Integer.MAX_VALUE));
		return delta;
	}

	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		if (src != Source.INPUT)
			return;

		if (currChunkFmt.append(raw, pos, len)) {
			currDataLen += len;
			return;
		}

		if (currChunkFmt.getObjectCount() == 1)
			currFragments = new LinkedList<ChunkKey>();
		if (currFragments != null) {
			appendToFragment(raw, pos, len);
			currDataLen += len;
			return;
		}

		// Everything between dataPos and dataEnd must be saved.
		//
		int dataPos = currDataPos;
		final int dataEnd = currChunkFmt.position();
		final int hdrPos = offsetOf(currChunkPtr);
		final int hdrLen = dataPos - hdrPos;
		final int type = typeOf(currChunkPtr);
		byte[] dataOld = currChunkFmt.getRawChunkDataArray();

		// Save the trailer, as this will be replaced when the chunk ends.
		//
		final int tsz = ChunkFormatter.TRAILER_SIZE;
		System.arraycopy(dataOld, hdrPos, hdrBuf, 0, tsz);

		final ChunkFormatter w;
		if (currCanReuseHeader) {
			final int typeOld = currChunkFmt.getCurrentObjectType();

			currChunkFmt.rollback();
			endChunk(type);
			w = begin(type);
			w.adjustObjectCount(1, typeOld);

			if (!w.append(hdrBuf, 0, Math.min(hdrLen, tsz)))
				throw panicCannotInsert();
			if (tsz < hdrLen && !w.append(dataOld, hdrPos + tsz, hdrLen - tsz))
				throw panicCannotInsert();

		} else {
			currChunkFmt.rollback();
			endChunk(type);
			w = begin(type);
			ChunkKey baseKey = chunkOf(currBasePtr);
			int basePos = offsetOf(currBasePtr);
			if (!w.chunkDelta(currInflatedSize, baseKey, basePos))
				throw panicCannotInsert();
		}

		if (hdrLen < tsz) {
			int left = Math.min(dataEnd - dataPos, tsz - hdrLen);
			if (0 < left && !w.append(hdrBuf, hdrLen, left))
				throw panicCannotInsert();
			dataPos += left;
		}

		if (dataPos < dataEnd && !w.append(dataOld, dataPos, dataEnd - dataPos))
			throw panicCannotInsert();
		dataOld = null;

		if (!w.append(raw, pos, len)) {
			currFragments = new LinkedList<ChunkKey>();
			appendToFragment(raw, pos, len);
		}
		currDataLen += len;
	}

	private void appendToFragment(byte[] raw, int pos, int len)
			throws DhtException {
		while (0 < len) {
			if (currChunkFmt.free() == 0) {
				int typeCode = typeOf(currChunkPtr);
				currChunkFmt.setFragment();
				currFragments.add(endChunk(typeCode));

				currChunkFmt = newChunk(typeCode);
				openChunks[typeCode] = currChunkFmt;
			}
			int n = Math.min(len, currChunkFmt.free());
			currChunkFmt.append(raw, pos, n);
			pos += n;
			len -= n;
		}
	}

	private void finishFragmentedObject() throws DhtException {
		currChunkFmt.setFragment();
		ChunkKey key = endChunk(typeOf(currChunkPtr));
		if (key != null)
			currFragments.add(key);

		for (ChunkKey k : currFragments) {
			db.chunk().put(new PackChunk.Members().setChunkKey(k) //
					.setFragments(new ChunkFragments(currFragments)), //
					dbWriteBuffer);
		}
		currFragments = null;
	}

	@Override
	protected void onInflatedObjectData(PackedObjectInfo obj, int typeCode,
			byte[] data) throws IOException {
		switch (typeCode) {
		case OBJ_COMMIT:
			updateCommitEdges((DhtInfo) obj, data);
			break;

		case OBJ_TREE:
			updateTreeEdges((DhtInfo) obj, data);
			break;
		}
	}

	private void updateCommitEdges(DhtInfo obj, byte[] raw) {
		objectsByName.add(obj);

		Edges edges = edges(obj.chunkPtr);
		edges.remove(obj);

		// TODO compute hints for trees.
		// idBuffer.fromString(raw, 5);
		// edges.tree(lookupByName(idBuffer));

		int ptr = 46;
		while (raw[ptr] == 'p') {
			idBuffer.fromString(raw, ptr + 7);
			edges.commit(lookupByName(idBuffer));
			ptr += 48;
		}
	}

	private void updateTreeEdges(DhtInfo obj, byte[] data) {
	}

	private ObjectId lookupByName(AnyObjectId obj) {
		DhtInfo info = objectsByName.get(obj);
		return info != null ? info : obj.copy();
	}

	private Edges edges(long chunkPtr) {
		if (isInCurrentChunk(chunkPtr)) {
			int type = typeOf(chunkPtr);
			Edges s = openEdges[type];
			if (s == null) {
				s = new Edges();
				openEdges[type] = s;
			}
			return s;
		} else {
			ChunkKey key = chunkOf(chunkPtr);
			Edges s = chunkEdges.get(key);
			if (s == null) {
				s = new Edges();
				chunkEdges.put(key, s);
			}
			return s;
		}
	}

	private static class Edges {
		Set<ObjectId> commitIds;

		List<ChunkKey> commitEdges;

		void commit(ObjectId id) {
			if (commitIds == null)
				commitIds = new HashSet<ObjectId>();
			commitIds.add(id);
		}

		void remove(AnyObjectId id) {
			if (commitIds != null)
				commitIds.remove(id);
		}
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo obj,
			ObjectTypeAndSize info) throws IOException {
		return seekDatabase(((DhtInfo) obj).chunkPtr, info);
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		return seekDatabase(((DhtDelta) delta).chunkPtr, info);
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
			dbChunk = chunkReadBackCache.get(key);
			if (dbChunk == null) {
				dbWriteBuffer.flush();

				Collection<PackChunk.Members> found;
				Context opt = Context.READ_REPAIR;
				Sync<Collection<PackChunk.Members>> sync = Sync.create();
				db.chunk().get(opt, Collections.singleton(key), sync);
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

				dbChunk = found.iterator().next().build();
				if (cache)
					chunkReadBackCache.put(key, dbChunk);
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

		// TODO Try to combine the chunk data and its index into a single
		// put call for the last chunk of each type. This would break the
		// read back we do in seekDatabase during delta resolution.

		// If there are deltas to be resolved the pending chunks
		// will need to be reloaded later. Ensure they are stored.
		//
		endChunk(OBJ_COMMIT);
		endChunk(OBJ_TREE);
		endChunk(OBJ_BLOB);
		endChunk(OBJ_TAG);

		// These are only necessary during initial parsing. Drop them now.
		//
		objStreamPos = null;
		objChunkPtrs = null;
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

	private ChunkFormatter begin(int typeCode) {
		final ChunkFormatter w = chunk(typeCode);
		currChunkFmt = w;
		currChunkPtr = position(w, typeCode);
		return w;
	}

	private ChunkKey endChunk(int typeCode) throws DhtException {
		ChunkFormatter w = openChunks[typeCode];
		if (w == null)
			return null;

		openChunks[typeCode] = null;
		currChunkFmt = null;

		if (w.isEmpty())
			return null;

		ChunkKey key = w.end(digest);
		keys(typeCode).add(key);
		chunkInfo.put(key, w.getChunkInfo());

		Edges e = openEdges[typeCode];
		if (e != null) {
			chunkEdges.put(key, e);
			openEdges[typeCode] = null;
		}

		if (currFragments == null)
			chunkReadBackCache.put(key, w.getPackChunk());

		w.unsafePut(db, dbWriteBuffer);
		return key;
	}

	private List<ChunkKey> keys(int typeCode) {
		List<ChunkKey> list = keys[typeCode];
		if (list == null)
			keys[typeCode] = list = new ArrayList<ChunkKey>(4);
		return list;
	}

	private ChunkFormatter chunk(int typeCode) {
		ChunkFormatter w = openChunks[typeCode];
		if (w == null) {
			w = newChunk(typeCode);
			openChunks[typeCode] = w;
		}
		return w;
	}

	private int findStreamIndex(long streamPosition) throws DhtException {
		int high = objStreamPos.size();
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final long pos = objStreamPos.get(mid);
			if (streamPosition < pos)
				high = mid;
			else if (streamPosition == pos)
				return mid;
			else
				low = mid + 1;
		} while (low < high);
		throw new DhtException(MessageFormat.format(
				DhtText.get().noSavedTypeForBase, Long.valueOf(streamPosition)));
	}

	private long position(ChunkFormatter w, int typeCode) {
		List<ChunkKey> list = keys(typeCode);
		int idx = list.size();
		int ptr = w.position();
		return (((long) typeCode) << 61) | (((long) idx) << 32) | ptr;
	}

	private static int typeOf(long position) {
		return (int) (position >>> 61);
	}

	private boolean isInCurrentChunk(long position) {
		List<ChunkKey> list = keys(typeOf(position));
		return chunkIdx(position) == list.size();
	}

	private ChunkKey chunkOf(long position) {
		List<ChunkKey> list = keys(typeOf(position));
		return list.get(chunkIdx(position));
	}

	private static int chunkIdx(long position) {
		return ((int) ((position << 3) >>> (32 + 3)));
	}

	private static int offsetOf(long position) {
		return (int) position;
	}

	private ChunkFormatter newChunk(int typeCode) {
		ChunkFormatter fmt;

		fmt = new ChunkFormatter(repo, options);
		fmt.setSource(ChunkInfo.Source.RECEIVE);
		fmt.setObjectType(typeCode);
		return fmt;
	}

	private static DhtException panicCannotInsert() {
		// This exception should never happen.
		return new DhtException(DhtText.get().cannotInsertObject);
	}

	static class DhtInfo extends PackedObjectInfo {
		long chunkPtr;

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

		ObjectInfo link(ChunkKey chunkKey, ChunkInfo info) {
			return new ObjectInfo(chunkKey, -1, (int) getOffset(), getSize(),
					base, info.fragment);
		}
	}

	static class DhtDelta extends UnresolvedDelta {
		long chunkPtr;

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
