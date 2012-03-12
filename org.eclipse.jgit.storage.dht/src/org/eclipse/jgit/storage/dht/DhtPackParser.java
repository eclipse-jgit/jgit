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
import static org.eclipse.jgit.lib.Constants.OBJ_OFS_DELTA;
import static org.eclipse.jgit.lib.Constants.OBJ_REF_DELTA;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
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
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.LongList;

import com.google.protobuf.ByteString;

/** Parses the pack stream into chunks, and indexes the chunks for lookup. */
public class DhtPackParser extends PackParser {
	private final DhtObjDatabase objdb;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtInserterOptions options;

	private final MessageDigest chunkKeyDigest;

	/** Number of objects to write to the global index at once. */
	private final int linkBatchSize;

	private Boolean saveAsCachedPack;

	private WriteBuffer dbWriteBuffer;

	/** Chunk writers for the 4 major object types, keyed by object type code. */
	private ChunkFormatter[] openChunks;

	/** Edges for current chunks. */
	private Edges[] openEdges;

	/** Prior chunks that were written, keyed by object type code. */
	private List<ChunkKey>[] chunkByOrder;

	/** Information on chunks already written out. */
	private Map<ChunkKey, ChunkInfo> infoByKey;

	/** Information on chunks already written out. */
	private Map<ChunkKey, ChunkMeta> chunkMeta;

	/** ChunkMeta that needs to be written out again, as it was modified. */
	private Map<ChunkKey, ChunkMeta> dirtyMeta;

	private Map<ChunkKey, Edges> chunkEdges;

	// Correlated lists, sorted by object stream position.
	private LongList objStreamPos;

	private LongList objChunkPtrs;

	/** Formatter handling the current object's data stream. */
	private ChunkFormatter currChunk;

	/** Current type of the object, if known. */
	private int currType;

	/** Position of the current object in the chunks we create. */
	private long currChunkPtr;

	/** If using OFS_DELTA, location of the base object in chunk space. */
	private long currBasePtr;

	/** Starting byte of the object data (aka end of the object header). */
	private int currDataPos;

	/** Total number of bytes in the object representation. */
	private long currPackedSize;

	/** Total number of bytes in the entire inflated object. */
	private long currInflatedSize;

	/** If the current object is fragmented, the list of chunks holding it. */
	private List<ChunkKey> currFragments;

	/** Previously written chunk that is being re-read during delta resolution. */
	private PackChunk dbChunk;

	/** Current read position in {@link #dbChunk}. */
	private int dbPtr;

	/** Recent chunks that were written, or recently read. */
	private LinkedHashMap<ChunkKey, PackChunk> chunkReadBackCache;

	/** Objects parsed from the stream, sorted by SHA-1. */
	private List<DhtInfo> objectListByName;

	/** Objects parsed from the stream, sorted by chunk (aka offset). */
	private List<DhtInfo> objectListByChunk;

	/** Iterators to write {@link #objectListByName} into the global index. */
	private ListIterator<DhtInfo>[] linkIterators;

	/** If the pack stream was self-contained, the cached pack info record key. */
	private CachedPackKey cachedPackKey;

	private CanonicalTreeParser treeParser;

	private final MutableObjectId idBuffer;

	private ObjectIdSubclassMap<DhtInfo> objectMap;

	DhtPackParser(DhtObjDatabase objdb, InputStream in) {
		super(objdb, in);

		// Disable collision checking. DhtReader performs some magic to look
		// only at old objects, so a colliding replacement will be ignored until
		// its removed during garbage collection.
		//
		setCheckObjectCollisions(false);

		this.objdb = objdb;
		this.repo = objdb.getRepository().getRepositoryKey();
		this.db = objdb.getDatabase();
		this.options = objdb.getInserterOptions();
		this.chunkKeyDigest = Constants.newMessageDigest();

		dbWriteBuffer = db.newWriteBuffer();
		openChunks = new ChunkFormatter[5];
		openEdges = new Edges[5];
		chunkByOrder = newListArray(5);
		infoByKey = new HashMap<ChunkKey, ChunkInfo>();
		dirtyMeta = new HashMap<ChunkKey, ChunkMeta>();
		chunkMeta = new HashMap<ChunkKey, ChunkMeta>();
		chunkEdges = new HashMap<ChunkKey, Edges>();
		treeParser = new CanonicalTreeParser();
		idBuffer = new MutableObjectId();
		objectMap = new ObjectIdSubclassMap<DhtInfo>();

		final int max = options.getParserCacheSize();
		chunkReadBackCache = new LinkedHashMap<ChunkKey, PackChunk>(max, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<ChunkKey, PackChunk> e) {
				return max < size();
			}
		};

		// The typical WriteBuffer flushes at 512 KiB increments, and
		// the typical ObjectInfo record is around 180 bytes. Use these
		// figures to come up with a rough estimate for how many links
		// to construct in one region of the DHT before moving onto a
		// different region in order to increase parallelism on large
		// object imports.
		//
		linkBatchSize = 512 * 1024 / 180;
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T>[] newListArray(int size) {
		return new List[size];
	}

	/** @return if true, the pack stream is marked as a cached pack. */
	public boolean isSaveAsCachedPack() {
		return saveAsCachedPack != null && saveAsCachedPack.booleanValue();
	}

	/**
	 * Enable saving the pack stream as a cached pack.
	 *
	 * @param save
	 *            if true, the stream is saved.
	 */
	public void setSaveAsCachedPack(boolean save) {
		saveAsCachedPack = Boolean.valueOf(save);
	}

	@Override
	public PackLock parse(ProgressMonitor receiving, ProgressMonitor resolving)
			throws IOException {
		boolean success = false;
		try {
			PackLock lock = super.parse(receiving, resolving);

			chunkReadBackCache = null;
			openChunks = null;
			openEdges = null;
			treeParser = null;

			final int objCnt = getObjectCount();
			if (objCnt == 0) {
				// If no objects were received, no chunks were created. Leaving
				// success to false and doing a rollback is a good way to make
				// sure this is true.
				//
				return lock;
			}

			createObjectLists();

			if (isSaveAsCachedPack())
				putCachedPack();
			computeChunkEdges();
			putChunkIndexes();
			putDirtyMeta();

			chunkMeta = null;
			chunkEdges = null;
			dirtyMeta = null;
			objectMap = null;
			objectListByChunk = null;
			dbWriteBuffer.flush();

			putGlobalIndex(resolving);
			dbWriteBuffer.flush();

			success = true;
			return lock;
		} finally {
			openChunks = null;
			openEdges = null;
			objStreamPos = null;
			objChunkPtrs = null;
			currChunk = null;
			currFragments = null;
			dbChunk = null;
			chunkReadBackCache = null;
			infoByKey = null;
			chunkMeta = null;
			chunkEdges = null;
			treeParser = null;

			if (!success)
				rollback();

			chunkByOrder = null;
			objectListByName = null;
			objectListByChunk = null;
			linkIterators = null;
			dbWriteBuffer = null;
		}
	}

	@SuppressWarnings("unchecked")
	private void createObjectLists() {
		List objs = getSortedObjectList(null /* by name */);
		objectListByName = objs;

		int cnt = objectListByName.size();
		DhtInfo[] copy = objectListByName.toArray(new DhtInfo[cnt]);
		Arrays.sort(copy, new Comparator<PackedObjectInfo>() {
			public int compare(PackedObjectInfo o1, PackedObjectInfo o2) {
				DhtInfo a = (DhtInfo) o1;
				DhtInfo b = (DhtInfo) o2;
				return Long.signum(a.chunkPtr - b.chunkPtr);
			}
		});
		objectListByChunk = Arrays.asList(copy);
	}

	private void putCachedPack() throws DhtException {
		CachedPackInfo.Builder info = CachedPackInfo.newBuilder();

		for (DhtInfo obj : objectMap) {
			if (!obj.isInPack())
				return;

			if (!obj.isReferenced())
				info.getTipListBuilder().addObjectName(obj.name());
		}

		MessageDigest version = Constants.newMessageDigest();
		addChunkList(info, version, chunkByOrder[OBJ_TAG]);
		addChunkList(info, version, chunkByOrder[OBJ_COMMIT]);
		addChunkList(info, version, chunkByOrder[OBJ_TREE]);
		addChunkList(info, version, chunkByOrder[OBJ_BLOB]);

		info.setName(computePackName().name());
		info.setVersion(ObjectId.fromRaw(version.digest()).name());

		cachedPackKey = CachedPackKey.fromInfo(info.build());
		for (List<ChunkKey> list : chunkByOrder) {
			if (list == null)
				continue;
			for (ChunkKey key : list) {
				ChunkInfo oldInfo = infoByKey.get(key);
				GitStore.ChunkInfo.Builder b =
					GitStore.ChunkInfo.newBuilder(oldInfo.getData());
				b.setCachedPackKey(cachedPackKey.asString());
				ChunkInfo newInfo = new ChunkInfo(key, b.build());
				infoByKey.put(key, newInfo);

				// A fragment was already put, and has to be re-put.
				// Non-fragments will put later and do not put now.
				if (newInfo.getData().getIsFragment())
					db.repository().put(repo, newInfo, dbWriteBuffer);
			}
		}

		db.repository().put(repo, info.build(), dbWriteBuffer);
	}

	private void addChunkList(CachedPackInfo.Builder info,
			MessageDigest version, List<ChunkKey> list) {
		if (list == null)
			return;

		long bytesTotal = info.getBytesTotal();
		long objectsTotal = info.getObjectsTotal();
		long objectsDelta = info.getObjectsDelta();

		byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
		for (ChunkKey key : list) {
			ChunkInfo chunkInfo = infoByKey.get(key);
			GitStore.ChunkInfo c = chunkInfo.getData();
			int len = c.getChunkSize() - ChunkFormatter.TRAILER_SIZE;
			bytesTotal += len;
			objectsTotal += c.getObjectCounts().getTotal();
			objectsDelta += c.getObjectCounts().getOfsDelta();
			objectsDelta += c.getObjectCounts().getRefDelta();
			info.getChunkListBuilder().addChunkKey(
					chunkInfo.getChunkKey().asString());
			chunkInfo.getChunkKey().getChunkHash().copyRawTo(buf, 0);
			version.update(buf);
		}

		info.setBytesTotal(bytesTotal);
		info.setObjectsTotal(objectsTotal);
		info.setObjectsDelta(objectsDelta);
	}

	private ObjectId computePackName() {
		byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
		MessageDigest md = Constants.newMessageDigest();
		for (DhtInfo otp : objectListByName) {
			otp.copyRawTo(buf, 0);
			md.update(buf);
		}
		return ObjectId.fromRaw(md.digest());
	}

	private void rollback() throws DhtException {
		try {
			dbWriteBuffer.abort();
			dbWriteBuffer = db.newWriteBuffer();

			if (cachedPackKey != null)
				db.repository().remove(repo, cachedPackKey, dbWriteBuffer);

			if (linkIterators != null) {
				boolean removed = true;
				while (removed) {
					removed = false;
					for (ListIterator<DhtInfo> itr : linkIterators) {
						int cnt = 0;
						while (itr.hasPrevious() && cnt < linkBatchSize) {
							DhtInfo oe = itr.previous();
							db.objectIndex().remove( //
									ObjectIndexKey.create(repo, oe), //
									chunkOf(oe.chunkPtr), //
									dbWriteBuffer);
							cnt++;
						}
						if (0 < cnt)
							removed = true;
					}
				}
			}

			deleteChunks(chunkByOrder[OBJ_COMMIT]);
			deleteChunks(chunkByOrder[OBJ_TREE]);
			deleteChunks(chunkByOrder[OBJ_BLOB]);
			deleteChunks(chunkByOrder[OBJ_TAG]);

			dbWriteBuffer.flush();
		} catch (Throwable err) {
			throw new DhtException(DhtText.get().packParserRollbackFailed, err);
		}
	}

	private void deleteChunks(List<ChunkKey> list) throws DhtException {
		if (list != null) {
			for (ChunkKey key : list) {
				db.chunk().remove(key, dbWriteBuffer);
				db.repository().remove(repo, key, dbWriteBuffer);
			}
		}
	}

	private void putGlobalIndex(ProgressMonitor pm) throws DhtException {
		int objcnt = objectListByName.size();
		pm.beginTask(DhtText.get().recordingObjects, objcnt);

		int segments = Math.max(1, Math.min(objcnt / linkBatchSize, 32));
		linkIterators = newListIteratorArray(segments);

		int objsPerSegment = objcnt / segments;
		int beginIdx = 0;
		for (int i = 0; i < segments - 1; i++) {
			int endIdx = Math.min(beginIdx + objsPerSegment, objcnt);
			linkIterators[i] = objectListByName.subList(beginIdx, endIdx)
					.listIterator();
			beginIdx = endIdx;
		}
		linkIterators[segments - 1] = objectListByName
				.subList(beginIdx, objcnt).listIterator();

		boolean inserted = true;
		while (inserted) {
			inserted = false;
			for (ListIterator<DhtInfo> itr : linkIterators) {
				int cnt = 0;
				while (itr.hasNext() && cnt < linkBatchSize) {
					DhtInfo oe = itr.next();
					db.objectIndex().add( //
							ObjectIndexKey.create(repo, oe), //
							oe.info(chunkOf(oe.chunkPtr)), //
							dbWriteBuffer);
					cnt++;
				}
				if (0 < cnt) {
					pm.update(cnt);
					inserted = true;
				}
			}
		}

		pm.endTask();
	}

	@SuppressWarnings("unchecked")
	private static ListIterator<DhtInfo>[] newListIteratorArray(int size) {
		return new ListIterator[size];
	}

	private void computeChunkEdges() throws DhtException {
		List<DhtInfo> objs = objectListByChunk;
		int beginIdx = 0;
		ChunkKey key = chunkOf(objs.get(0).chunkPtr);
		int type = typeOf(objs.get(0).chunkPtr);

		int objIdx = 1;
		for (; objIdx < objs.size(); objIdx++) {
			DhtInfo oe = objs.get(objIdx);
			ChunkKey oeKey = chunkOf(oe.chunkPtr);
			if (!key.equals(oeKey)) {
				computeEdges(objs.subList(beginIdx, objIdx), key, type);
				beginIdx = objIdx;

				key = oeKey;
				type = typeOf(oe.chunkPtr);
			}
			if (type != OBJ_MIXED && type != typeOf(oe.chunkPtr))
				type = OBJ_MIXED;
		}
		computeEdges(objs.subList(beginIdx, objs.size()), key, type);
	}

	private void computeEdges(List<DhtInfo> objs, ChunkKey key, int type)
			throws DhtException {
		Edges edges = chunkEdges.get(key);
		if (edges == null)
			return;

		for (DhtInfo obj : objs)
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

	private List<ChunkKey> toChunkList(Set<DhtInfo> objects)
			throws DhtException {
		if (objects == null || objects.isEmpty())
			return null;

		Map<ChunkKey, ChunkOrderingEntry> map = new HashMap<ChunkKey, ChunkOrderingEntry>();
		for (DhtInfo obj : objects) {
			if (!obj.isInPack())
				continue;

			long chunkPtr = obj.chunkPtr;
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
		List<DhtInfo> objs = objectListByChunk;
		int sIdx = 0;
		DhtInfo oe = objs.get(0);
		oe.setOffset(offsetOf(oe.chunkPtr));

		ChunkKey key = chunkOf(oe.chunkPtr);
		int type = typeOf(oe.chunkPtr);

		int objIdx = 1;
		for (; objIdx < objs.size(); objIdx++) {
			oe = objs.get(objIdx);
			oe.setOffset(offsetOf(oe.chunkPtr));

			ChunkKey oeKey = chunkOf(oe.chunkPtr);
			if (!key.equals(oeKey)) {
				putChunkIndex(objs.subList(sIdx, objIdx), key, type);
				sIdx = objIdx;

				key = oeKey;
				type = typeOf(oe.chunkPtr);
			}
			if (type != OBJ_MIXED && type != typeOf(oe.chunkPtr))
				type = OBJ_MIXED;
		}
		putChunkIndex(objs.subList(sIdx, objs.size()), key, type);
	}

	private void putChunkIndex(List<DhtInfo> objectList, ChunkKey key, int type)
			throws DhtException {
		ChunkInfo oldInfo = infoByKey.get(key);
		GitStore.ChunkInfo.Builder info
			= GitStore.ChunkInfo.newBuilder(oldInfo.getData());

		PackChunk.Members builder = new PackChunk.Members();
		builder.setChunkKey(key);

		byte[] index = ChunkIndex.create(objectList);
		info.setIndexSize(index.length);
		builder.setChunkIndex(index);

		ChunkMeta meta = dirtyMeta.remove(key);
		if (meta == null)
			meta = chunkMeta.get(key);

		switch (type) {
		case OBJ_COMMIT: {
			Edges edges = chunkEdges.get(key);
			List<ChunkKey> e = edges != null ? edges.commitEdges : null;
			List<ChunkKey> s = sequentialHint(key, OBJ_COMMIT);
			if (e == null)
				e = Collections.emptyList();
			if (s == null)
				s = Collections.emptyList();
			if (!e.isEmpty() || !s.isEmpty()) {
				ChunkMeta.Builder m = edit(meta);
				ChunkMeta.PrefetchHint.Builder h = m.getCommitPrefetchBuilder();
				for (ChunkKey k : e)
					h.addEdge(k.asString());
				for (ChunkKey k : s)
					h.addSequential(k.asString());
				meta = m.build();
			}
			break;
		}
		case OBJ_TREE: {
			List<ChunkKey> s = sequentialHint(key, OBJ_TREE);
			if (s == null)
				s = Collections.emptyList();
			if (!s.isEmpty()) {
				ChunkMeta.Builder m = edit(meta);
				ChunkMeta.PrefetchHint.Builder h = m.getTreePrefetchBuilder();
				for (ChunkKey k : s)
					h.addSequential(k.asString());
				meta = m.build();
			}
			break;
		}
		}

		if (meta != null) {
			info.setMetaSize(meta.getSerializedSize());
			builder.setMeta(meta);
		}

		ChunkInfo newInfo = new ChunkInfo(key, info.build());
		infoByKey.put(key, newInfo);
		db.repository().put(repo, newInfo, dbWriteBuffer);
		db.chunk().put(builder, dbWriteBuffer);
	}

	private static ChunkMeta.Builder edit(ChunkMeta meta) {
		if (meta != null)
			return ChunkMeta.newBuilder(meta);
		return ChunkMeta.newBuilder();
	}

	private List<ChunkKey> sequentialHint(ChunkKey key, int typeCode) {
		List<ChunkKey> all = chunkByOrder[typeCode];
		if (all == null)
			return null;
		int idx = all.indexOf(key);
		if (0 <= idx) {
			int max = options.getPrefetchDepth();
			int end = Math.min(idx + 1 + max, all.size());
			return all.subList(idx + 1, end);
		}
		return null;
	}

	private void putDirtyMeta() throws DhtException {
		for (Map.Entry<ChunkKey, ChunkMeta> meta : dirtyMeta.entrySet()) {
			PackChunk.Members builder = new PackChunk.Members();
			builder.setChunkKey(meta.getKey());
			builder.setMeta(meta.getValue());
			db.chunk().put(builder, dbWriteBuffer);
		}
	}

	@Override
	protected PackedObjectInfo newInfo(AnyObjectId id, UnresolvedDelta delta,
			ObjectId baseId) {
		DhtInfo obj = objectMap.addIfAbsent(new DhtInfo(id));
		if (delta != null) {
			DhtDelta d = (DhtDelta) delta;
			obj.chunkPtr = d.chunkPtr;
			obj.packedSize = d.packedSize;
			obj.inflatedSize = d.inflatedSize;
			obj.base = baseId;
			obj.setType(d.getType());
			if (d.isFragmented())
				obj.setFragmented();
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

		if (saveAsCachedPack == null)
			setSaveAsCachedPack(1000 < objCnt);
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

		currType = type;
		currDataPos = w.position();
		currPackedSize = 0;
		currInflatedSize = inflatedSize;
		objStreamPos.add(streamPosition);
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		boolean fragmented = currFragments != null;
		endOneObject();

		DhtInfo oe = (DhtInfo) info;
		oe.chunkPtr = currChunkPtr;
		oe.packedSize = currPackedSize;
		oe.inflatedSize = currInflatedSize;
		oe.setType(currType);
		if (fragmented)
			oe.setFragmented();
	}

	private void endOneObject() throws DhtException {
		if (currFragments != null)
			endFragmentedObject();
		objChunkPtrs.add(currChunkPtr);
	}

	@Override
	protected void onBeginOfsDelta(long deltaPos, long basePos,
			long inflatedSize) throws IOException {
		long basePtr = objChunkPtrs.get(findStreamIndex(basePos));
		int type = typeOf(basePtr);

		currType = type;
		currPackedSize = 0;
		currInflatedSize = inflatedSize;
		currBasePtr = basePtr;
		objStreamPos.add(deltaPos);

		ChunkFormatter w = begin(type);
		if (isInCurrentChunk(basePtr)) {
			if (w.ofsDelta(inflatedSize, w.position() - offsetOf(basePtr))) {
				currDataPos = w.position();
				return;
			}

			endChunk(type);
			w = begin(type);
		}

		if (!longOfsDelta(w, inflatedSize, basePtr)) {
			endChunk(type);
			w = begin(type);
			if (!longOfsDelta(w, inflatedSize, basePtr))
				throw panicCannotInsert();
		}

		currDataPos = w.position();
	}

	@Override
	protected void onBeginRefDelta(long deltaPos, AnyObjectId baseId,
			long inflatedSize) throws IOException {
		// Try to get the base type, but only if it was seen before in this
		// pack stream. If not assume worst-case of BLOB type.
		//
		int typeCode;
		DhtInfo baseInfo = objectMap.get(baseId);
		if (baseInfo != null && baseInfo.isInPack()) {
			typeCode = baseInfo.getType();
			currType = typeCode;
		} else {
			typeCode = OBJ_BLOB;
			currType = -1;
		}

		ChunkFormatter w = begin(typeCode);
		if (!w.refDelta(inflatedSize, baseId)) {
			endChunk(typeCode);
			w = begin(typeCode);
			if (!w.refDelta(inflatedSize, baseId))
				throw panicCannotInsert();
		}

		currDataPos = w.position();
		currPackedSize = 0;
		currInflatedSize = inflatedSize;
		objStreamPos.add(deltaPos);
	}

	@Override
	protected DhtDelta onEndDelta() throws IOException {
		boolean fragmented = currFragments != null;
		endOneObject();

		DhtDelta delta = new DhtDelta();
		delta.chunkPtr = currChunkPtr;
		delta.packedSize = currPackedSize;
		delta.inflatedSize = currInflatedSize;
		if (0 < currType)
			delta.setType(currType);
		if (fragmented)
			delta.setFragmented();
		return delta;
	}

	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		if (src != Source.INPUT)
			return;

		if (currChunk.append(raw, pos, len)) {
			currPackedSize += len;
			return;
		}

		if (currFragments == null && currChunk.getObjectCount() == 1)
			currFragments = new LinkedList<ChunkKey>();
		if (currFragments != null) {
			appendToFragment(raw, pos, len);
			return;
		}

		// Everything between dataPos and dataEnd must be saved.
		//
		final int dataPos = currDataPos;
		final int dataEnd = currChunk.position();
		final int hdrPos = offsetOf(currChunkPtr);
		final int hdrLen = dataPos - hdrPos;
		final int type = typeOf(currChunkPtr);
		byte[] dataOld = currChunk.getRawChunkDataArray();
		final int typeOld = currChunk.getCurrentObjectType();

		currChunk.rollback();
		endChunk(type);

		final ChunkFormatter w = begin(type);
		switch (typeOld) {
		case OBJ_COMMIT:
		case OBJ_BLOB:
		case OBJ_TREE:
		case OBJ_TAG:
		case OBJ_REF_DELTA:
			w.adjustObjectCount(1, typeOld);
			if (!w.append(dataOld, hdrPos, hdrLen))
				throw panicCannotInsert();
			break;

		case OBJ_OFS_DELTA:
			if (!longOfsDelta(w, currInflatedSize, currBasePtr))
				throw panicCannotInsert();
			break;

		default:
			throw new DhtException("Internal programming error: " + typeOld);
		}

		currDataPos = w.position();
		if (dataPos < dataEnd && !w.append(dataOld, dataPos, dataEnd - dataPos))
			throw panicCannotInsert();
		dataOld = null;

		if (w.append(raw, pos, len)) {
			currPackedSize += len;
		} else {
			currFragments = new LinkedList<ChunkKey>();
			appendToFragment(raw, pos, len);
		}
	}

	private boolean longOfsDelta(ChunkFormatter w, long infSize, long basePtr) {
		final int type = typeOf(basePtr);
		final List<ChunkKey> infoList = chunkByOrder[type];
		final int baseIdx = chunkIdx(basePtr);
		final ChunkInfo baseInfo = infoByKey.get(infoList.get(baseIdx));

		// Go backwards to the start of the base's chunk.
		long relativeChunkStart = 0;
		for (int i = infoList.size() - 1; baseIdx <= i; i--) {
			GitStore.ChunkInfo info = infoByKey.get(infoList.get(i)).getData();
			int packSize = info.getChunkSize() - ChunkFormatter.TRAILER_SIZE;
			relativeChunkStart += packSize;
		}

		// Offset to the base goes back to start of our chunk, then start of
		// the base chunk, but slide forward the distance of the base within
		// its own chunk.
		//
		long ofs = w.position() + relativeChunkStart - offsetOf(basePtr);
		if (w.ofsDelta(infSize, ofs)) {
			w.useBaseChunk(relativeChunkStart, baseInfo.getChunkKey());
			return true;
		}
		return false;
	}

	private void appendToFragment(byte[] raw, int pos, int len)
			throws DhtException {
		while (0 < len) {
			if (currChunk.free() == 0) {
				int typeCode = typeOf(currChunkPtr);
				currChunk.setFragment();
				currFragments.add(endChunk(typeCode));
				currChunk = openChunk(typeCode);
			}

			int n = Math.min(len, currChunk.free());
			currChunk.append(raw, pos, n);
			currPackedSize += n;
			pos += n;
			len -= n;
		}
	}

	private void endFragmentedObject() throws DhtException {
		currChunk.setFragment();
		ChunkKey lastKey = endChunk(typeOf(currChunkPtr));
		if (lastKey != null)
			currFragments.add(lastKey);

		ChunkMeta.Builder protoBuilder = ChunkMeta.newBuilder();
		for (ChunkKey key : currFragments)
			protoBuilder.addFragment(key.asString());
		ChunkMeta protoMeta = protoBuilder.build();

		for (ChunkKey key : currFragments) {
			ChunkMeta oldMeta = chunkMeta.get(key);
			if (oldMeta != null) {
				ChunkMeta.Builder newMeta = ChunkMeta.newBuilder(oldMeta);
				newMeta.clearFragment();
				newMeta.mergeFrom(protoMeta);
				ChunkMeta meta = newMeta.build();
				dirtyMeta.put(key, meta);
				chunkMeta.put(key, meta);
			} else {
				dirtyMeta.put(key, protoMeta);
				chunkMeta.put(key, protoMeta);
			}
		}
		currFragments = null;
	}

	@Override
	protected void onInflatedObjectData(PackedObjectInfo obj, int typeCode,
			byte[] data) throws IOException {
		DhtInfo info = (DhtInfo) obj;
		info.inflatedSize = data.length;
		info.setType(typeCode);

		switch (typeCode) {
		case OBJ_COMMIT:
			onCommit(info, data);
			break;

		case OBJ_TREE:
			onTree(data);
			break;

		case OBJ_TAG:
			onTag(data);
			break;
		}
	}

	private void onCommit(DhtInfo obj, byte[] raw) throws DhtException {
		Edges edges = edges(obj.chunkPtr);
		edges.remove(obj);

		// TODO compute hints for trees.
		if (isSaveAsCachedPack()) {
			idBuffer.fromString(raw, 5);
			lookupByName(idBuffer).setReferenced();
		}

		int ptr = 46;
		while (raw[ptr] == 'p') {
			idBuffer.fromString(raw, ptr + 7);
			DhtInfo p = lookupByName(idBuffer);
			p.setReferenced();
			edges.commit(p);
			ptr += 48;
		}
	}

	private void onTree(byte[] data) {
		if (isSaveAsCachedPack()) {
			treeParser.reset(data);
			while (!treeParser.eof()) {
				idBuffer.fromRaw(treeParser.idBuffer(), treeParser.idOffset());
				lookupByName(idBuffer).setReferenced();
				treeParser.next();
			}
		}
	}

	private void onTag(byte[] data) {
		if (isSaveAsCachedPack()) {
			idBuffer.fromString(data, 7); // "object $sha1"
			lookupByName(idBuffer).setReferenced();
		}
	}

	private DhtInfo lookupByName(AnyObjectId obj) {
		DhtInfo info = objectMap.get(obj);
		if (info == null) {
			info = new DhtInfo(obj);
			objectMap.add(info);
		}
		return info;
	}

	private Edges edges(long chunkPtr) throws DhtException {
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
		Set<DhtInfo> commitIds;

		List<ChunkKey> commitEdges;

		void commit(DhtInfo id) {
			if (commitIds == null)
				commitIds = new HashSet<DhtInfo>();
			commitIds.add(id);
		}

		void remove(DhtInfo id) {
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

	private ObjectTypeAndSize seekDatabase(long chunkPtr, ObjectTypeAndSize info)
			throws DhtException {
		seekChunk(chunkOf(chunkPtr), true);
		dbPtr = dbChunk.readObjectTypeAndSize(offsetOf(chunkPtr), info);
		return info;
	}

	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt) throws IOException {
		int n = dbChunk.read(dbPtr, dst, pos, cnt);
		if (0 < n) {
			dbPtr += n;
			return n;
		}

		// ChunkMeta for fragments is delayed writing, so it isn't available
		// on the chunk if the chunk was read-back from the database. Use
		// our copy of ChunkMeta instead of the PackChunk's copy.

		ChunkMeta meta = chunkMeta.get(dbChunk.getChunkKey());
		if (meta == null)
			return 0;

		ChunkKey next = ChunkMetaUtil.getNextFragment(meta, dbChunk.getChunkKey());
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
					found = sync.get(objdb.getReaderOptions().getTimeout());
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

	private ChunkFormatter begin(int typeCode) throws DhtException {
		ChunkFormatter w = openChunk(typeCode);
		currChunk = w;
		currChunkPtr = makeObjectPointer(w, typeCode);
		return w;
	}

	private ChunkFormatter openChunk(int typeCode) throws DhtException {
		if (typeCode == 0)
			throw new DhtException("Invalid internal typeCode 0");

		ChunkFormatter w = openChunks[typeCode];
		if (w == null) {
			w = new ChunkFormatter(repo, options);
			w.setSource(GitStore.ChunkInfo.Source.RECEIVE);
			w.setObjectType(typeCode);
			openChunks[typeCode] = w;
		}
		return w;
	}

	private ChunkKey endChunk(int typeCode) throws DhtException {
		ChunkFormatter w = openChunks[typeCode];
		if (w == null)
			return null;

		openChunks[typeCode] = null;
		currChunk = null;

		if (w.isEmpty())
			return null;

		ChunkKey key = w.end(chunkKeyDigest);
		ChunkInfo info = w.getChunkInfo();

		if (chunkByOrder[typeCode] == null)
			chunkByOrder[typeCode] = new ArrayList<ChunkKey>();
		chunkByOrder[typeCode].add(key);
		infoByKey.put(key, info);

		if (w.getChunkMeta() != null)
			chunkMeta.put(key, w.getChunkMeta());

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

	private long makeObjectPointer(ChunkFormatter w, int typeCode) {
		List<ChunkKey> list = chunkByOrder[typeCode];
		int idx = list == null ? 0 : list.size();
		int ptr = w.position();
		return (((long) typeCode) << 61) | (((long) idx) << 32) | ptr;
	}

	private static int typeOf(long objectPtr) {
		return (int) (objectPtr >>> 61);
	}

	private static int chunkIdx(long objectPtr) {
		return ((int) ((objectPtr << 3) >>> (32 + 3)));
	}

	private static int offsetOf(long objectPtr) {
		return (int) objectPtr;
	}

	private boolean isInCurrentChunk(long objectPtr) {
		List<ChunkKey> list = chunkByOrder[typeOf(objectPtr)];
		if (list == null)
			return chunkIdx(objectPtr) == 0;
		return chunkIdx(objectPtr) == list.size();
	}

	private ChunkKey chunkOf(long objectPtr) throws DhtException {
		List<ChunkKey> list = chunkByOrder[typeOf(objectPtr)];
		int idx = chunkIdx(objectPtr);
		if (list == null || list.size() <= idx) {
			throw new DhtException(MessageFormat.format(
					DhtText.get().packParserInvalidPointer, //
					Constants.typeString(typeOf(objectPtr)), //
					Integer.valueOf(idx), //
					Integer.valueOf(offsetOf(objectPtr))));
		}
		return list.get(idx);
	}

	private static DhtException panicCannotInsert() {
		// This exception should never happen.
		return new DhtException(DhtText.get().cannotInsertObject);
	}

	static class DhtInfo extends PackedObjectInfo {
		private static final int REFERENCED = 1 << 3;

		static final int FRAGMENTED = 1 << 4;

		long chunkPtr;

		long packedSize;

		long inflatedSize;

		ObjectId base;

		DhtInfo(AnyObjectId id) {
			super(id);
		}

		boolean isInPack() {
			return chunkPtr != 0;
		}

		boolean isReferenced() {
			return (getCRC() & REFERENCED) != 0;
		}

		void setReferenced() {
			setCRC(getCRC() | REFERENCED);
		}

		boolean isFragmented() {
			return (getCRC() & FRAGMENTED) != 0;
		}

		void setFragmented() {
			setCRC(getCRC() | FRAGMENTED);
		}

		int getType() {
			return getCRC() & 7;
		}

		void setType(int type) {
			setCRC((getCRC() & ~7) | type);
		}

		ObjectInfo info(ChunkKey chunkKey) {
			GitStore.ObjectInfo.Builder b = GitStore.ObjectInfo.newBuilder();
			b.setObjectType(GitStore.ObjectInfo.ObjectType.valueOf(getType()));
			b.setOffset(offsetOf(chunkPtr));
			b.setPackedSize(packedSize);
			b.setInflatedSize(inflatedSize);
			if (base != null) {
				byte[] t = new byte[Constants.OBJECT_ID_LENGTH];
				base.copyRawTo(t, 0);
				b.setDeltaBase(ByteString.copyFrom(t));
			}
			if (isFragmented())
				b.setIsFragmented(true);
			return new ObjectInfo(chunkKey, b.build());
		}
	}

	static class DhtDelta extends UnresolvedDelta {
		long chunkPtr;

		long packedSize;

		long inflatedSize;

		int getType() {
			return getCRC() & 7;
		}

		void setType(int type) {
			setCRC((getCRC() & ~7) | type);
		}

		boolean isFragmented() {
			return (getCRC() & DhtInfo.FRAGMENTED) != 0;
		}

		void setFragmented() {
			setCRC(getCRC() | DhtInfo.FRAGMENTED);
		}
	}
}
