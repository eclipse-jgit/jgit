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

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.AsyncObjectSizeQueue;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.PackWriter;

/**
 * ObjectReader implementation for DHT based repositories.
 * <p>
 * This class is public only to expose its unique statistics for runtime
 * performance reporting. Applications should always prefer to use the more
 * generic base class, {@link ObjectReader}.
 */
public class DhtReader extends ObjectReader implements ObjectReuseAsIs {
	private final DhtRepository repository;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtReaderOptions readerOptions;

	private final DhtInserterOptions inserterOptions;

	private final Statistics stats;

	private final RecentInfoCache recentInfo;

	private final RecentChunks recentChunks;

	private final DeltaBaseCache deltaBaseCache;

	private Collection<CachedPack> cachedPacks;

	private Inflater inflater;

	private Prefetcher prefetcher;

	DhtReader(DhtObjDatabase objdb) {
		this.repository = objdb.getRepository();
		this.repo = objdb.getRepository().getRepositoryKey();
		this.db = objdb.getDatabase();
		this.readerOptions = objdb.getReaderOptions();
		this.inserterOptions = objdb.getInserterOptions();

		this.stats = new Statistics();
		this.recentInfo = new RecentInfoCache(getOptions());
		this.recentChunks = new RecentChunks(this);
		this.deltaBaseCache = new DeltaBaseCache(this);
	}

	/** @return describes how this DhtReader has performed. */
	public Statistics getStatistics() {
		return stats;
	}

	Database getDatabase() {
		return db;
	}

	RepositoryKey getRepositoryKey() {
		return repo;
	}

	DhtReaderOptions getOptions() {
		return readerOptions;
	}

	DhtInserterOptions getInserterOptions() {
		return inserterOptions;
	}

	RecentInfoCache getRecentInfoCache() {
		return recentInfo;
	}

	RecentChunks getRecentChunks() {
		return recentChunks;
	}

	DeltaBaseCache getDeltaBaseCache() {
		return deltaBaseCache;
	}

	Inflater inflater() {
		if (inflater == null)
			inflater = InflaterCache.get();
		else
			inflater.reset();
		return inflater;
	}

	@Override
	public void release() {
		recentChunks.clear();
		endPrefetch();

		InflaterCache.release(inflater);
		inflater = null;

		super.release();
	}

	@Override
	public ObjectReader newReader() {
		return new DhtReader(repository.getObjectDatabase());
	}

	@Override
	public boolean has(AnyObjectId objId, int typeHint) throws IOException {
		if (objId instanceof RefDataUtil.IdWithChunk)
			return true;

		if (recentChunks.has(repo, objId))
			return true;

		if (repository.getRefDatabase().findChunk(objId) != null)
			return true;

		return !find(objId).isEmpty();
	}

	@Override
	public ObjectLoader open(AnyObjectId objId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		ObjectLoader ldr = recentChunks.open(repo, objId, typeHint);
		if (ldr != null)
			return ldr;

		ChunkAndOffset p = getChunk(objId, typeHint, false);
		ldr = PackChunk.read(p.chunk, p.offset, this, typeHint);
		recentChunk(p.chunk);
		return ldr;
	}

	@Override
	public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
			Iterable<T> objectIds, boolean reportMissing) {
		return new OpenQueue<T>(this, objectIds, reportMissing);
	}

	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		for (ObjectInfo info : find(objectId))
			return info.getSize();
		throw missing(objectId, typeHint);
	}

	@Override
	public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
			Iterable<T> objectIds, boolean reportMissing) {
		return new SizeQueue<T>(this, objectIds, reportMissing);
	}

	@Override
	public void walkAdviceBeginCommits(RevWalk rw, Collection<RevCommit> roots)
			throws IOException {
		endPrefetch();

		// Don't assign the prefetcher right away. Delay until its
		// configured as push might invoke our own methods that may
		// try to call back into the active prefetcher.
		//
		Prefetcher p = prefetch(OBJ_COMMIT, readerOptions.getWalkCommitsPrefetchRatio());
		p.push(this, roots);
		prefetcher = p;
	}

	@Override
	public void walkAdviceBeginTrees(ObjectWalk ow, RevCommit min, RevCommit max)
			throws IOException {
		endPrefetch();

		// Don't assign the prefetcher right away. Delay until its
		// configured as push might invoke our own methods that may
		// try to call back into the active prefetcher.
		//
		Prefetcher p = prefetch(OBJ_TREE, readerOptions.getWalkTreesPrefetchRatio());
		p.push(this, min.getTree(), max.getTree());
		prefetcher = p;
	}

	@Override
	public void walkAdviceEnd() {
		endPrefetch();
	}

	void recentChunk(PackChunk chunk) {
		recentChunks.put(chunk);
	}

	ChunkAndOffset getChunkGently(AnyObjectId objId) {
		return recentChunks.find(repo, objId);
	}

	ChunkAndOffset getChunk(AnyObjectId objId, int typeHint, boolean checkRecent)
			throws DhtException, MissingObjectException {
		if (checkRecent) {
			ChunkAndOffset r = recentChunks.find(repo, objId);
			if (r != null)
				return r;
		}

		ChunkKey key;
		if (objId instanceof RefDataUtil.IdWithChunk)
			key = ((RefDataUtil.IdWithChunk) objId).getChunkKey();
		else
			key = repository.getRefDatabase().findChunk(objId);

		if (key != null) {
			PackChunk chunk = load(key);
			if (chunk != null && chunk.hasIndex()) {
				int pos = chunk.findOffset(repo, objId);
				if (0 <= pos)
					return new ChunkAndOffset(chunk, pos);
			}

			// The hint above is stale. Fall through and do a
			// more exhaustive lookup to find the object.
		}

		if (prefetcher != null) {
			ChunkAndOffset r = prefetcher.find(repo, objId);
			if (r != null)
				return r;
		}

		for (ObjectInfo link : find(objId)) {
			PackChunk chunk;

			if (prefetcher != null) {
				chunk = prefetcher.get(link.getChunkKey());
				if (chunk == null) {
					chunk = load(link.getChunkKey());
					if (chunk == null)
						continue;
					if (prefetcher.isType(typeHint))
						prefetcher.push(chunk.getMeta());
				}
			} else {
				chunk = load(link.getChunkKey());
				if (chunk == null)
					continue;
			}

			return new ChunkAndOffset(chunk, link.getOffset());
		}

		throw missing(objId, typeHint);
	}

	ChunkKey findChunk(AnyObjectId objId) throws DhtException {
		if (objId instanceof RefDataUtil.IdWithChunk)
			return ((RefDataUtil.IdWithChunk) objId).getChunkKey();

		ChunkKey key = repository.getRefDatabase().findChunk(objId);
		if (key != null)
			return key;

		ChunkAndOffset r = recentChunks.find(repo, objId);
		if (r != null)
			return r.chunk.getChunkKey();

		for (ObjectInfo link : find(objId))
			return link.getChunkKey();

		return null;
	}

	static MissingObjectException missing(AnyObjectId objId, int typeHint) {
		ObjectId id = objId.copy();
		if (typeHint != OBJ_ANY)
			return new MissingObjectException(id, typeHint);
		return new MissingObjectException(id, DhtText.get().objectTypeUnknown);
	}

	PackChunk getChunk(ChunkKey key) throws DhtException {
		PackChunk chunk = recentChunks.get(key);
		if (chunk != null)
			return chunk;

		chunk = load(key);
		if (chunk != null)
			return chunk;

		throw new DhtMissingChunkException(key);
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		// Because ObjectIndexKey requires at least 4 leading digits
		// don't resolve anything that is shorter than 4 digits.
		//
		if (id.length() < 4)
			return Collections.emptySet();

		throw new DhtException.TODO("resolve abbreviations");
	}

	public DhtObjectToPack newObjectToPack(RevObject obj) {
		return new DhtObjectToPack(obj);
	}

	@SuppressWarnings("unchecked")
	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		Iterable itr = objects;
		new RepresentationSelector(packer, this, monitor).select(itr);
	}

	private Prefetcher prefetch(final int type, final int ratio) {
		int limit = readerOptions.getChunkLimit();
		int prefetchLimit = (int) (limit * (ratio / 100.0));
		recentChunks.setMaxBytes(limit - prefetchLimit);
		return new Prefetcher(this, type, prefetchLimit);
	}

	private void endPrefetch() {
		recentChunks.setMaxBytes(getOptions().getChunkLimit());
		prefetcher = null;
	}

	@SuppressWarnings("unchecked")
	public void writeObjects(PackOutputStream out, List<ObjectToPack> objects)
			throws IOException {
		prefetcher = prefetch(0, readerOptions.getWriteObjectsPrefetchRatio());
		try {
			List itr = objects;
			new ObjectWriter(this, prefetcher).plan(itr);
			for (ObjectToPack otp : objects)
				out.writeObject(otp);
		} finally {
			endPrefetch();
		}
	}

	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		DhtObjectToPack obj = (DhtObjectToPack) otp;
		try {
			PackChunk chunk = recentChunks.get(obj.chunk);
			if (chunk == null) {
				chunk = prefetcher.get(obj.chunk);
				if (chunk == null) {
					// This should never happen during packing, it implies
					// the fetch plan was incorrect. Unfortunately that can
					// occur if objects need to be recompressed on the fly.
					//
					stats.access(obj.chunk).cntCopyObjectAsIs_PrefetchMiss++;
					chunk = getChunk(obj.chunk);
				}
				if (!chunk.isFragment())
					recentChunk(chunk);
			}
			chunk.copyObjectAsIs(out, obj, validate, this);
		} catch (DhtMissingChunkException missingChunk) {
			stats.access(missingChunk.getChunkKey()).cntCopyObjectAsIs_InvalidChunk++;
			throw new StoredObjectRepresentationNotAvailableException(otp);
		}
	}

	public Collection<CachedPack> getCachedPacks() throws IOException {
		if (cachedPacks == null) {
			Collection<CachedPackInfo> info;
			Collection<CachedPack> packs;

			try {
				info = db.repository().getCachedPacks(repo);
			} catch (TimeoutException e) {
				throw new DhtTimeoutException(e);
			}

			packs = new ArrayList<CachedPack>(info.size());
			for (CachedPackInfo i : info)
				packs.add(new DhtCachedPack(i));
			cachedPacks = packs;
		}
		return cachedPacks;
	}

	public void copyPackAsIs(PackOutputStream out, CachedPack pack,
			boolean validate) throws IOException {
		((DhtCachedPack) pack).copyAsIs(out, validate, this);
	}

	private List<ObjectInfo> find(AnyObjectId obj) throws DhtException {
		List<ObjectInfo> info = recentInfo.get(obj);
		if (info != null)
			return info;

		stats.cntObjectIndex_Load++;
		ObjectIndexKey idxKey = ObjectIndexKey.create(repo, obj);
		Context opt = Context.READ_REPAIR;
		Sync<Map<ObjectIndexKey, Collection<ObjectInfo>>> sync = Sync.create();
		db.objectIndex().get(opt, Collections.singleton(idxKey), sync);
		try {
			Collection<ObjectInfo> m;

			m = sync.get(getOptions().getTimeout()).get(idxKey);
			if (m == null || m.isEmpty())
				return Collections.emptyList();

			info = new ArrayList<ObjectInfo>(m);
			ObjectInfo.sort(info);
			recentInfo.put(obj, info);
			return info;
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private PackChunk load(ChunkKey chunkKey) throws DhtException {
		if (0 == stats.access(chunkKey).cntReader_Load++
				&& readerOptions.isTrackFirstChunkLoad())
			stats.access(chunkKey).locReader_Load = new Throwable("first");
		Context opt = Context.READ_REPAIR;
		Sync<Collection<PackChunk.Members>> sync = Sync.create();
		db.chunk().get(opt, Collections.singleton(chunkKey), sync);
		try {
			Collection<PackChunk.Members> c = sync.get(getOptions()
					.getTimeout());
			if (c.isEmpty())
				return null;
			if (c instanceof List)
				return ((List<PackChunk.Members>) c).get(0).build();
			return c.iterator().next().build();
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	static class ChunkAndOffset {
		final PackChunk chunk;

		final int offset;

		ChunkAndOffset(PackChunk chunk, int offset) {
			this.chunk = chunk;
			this.offset = offset;
		}
	}

	/** How this DhtReader has performed since creation. */
	public static class Statistics {
		private final Map<ChunkKey, ChunkAccess> chunkAccess = new LinkedHashMap<ChunkKey, ChunkAccess>();

		ChunkAccess access(ChunkKey chunkKey) {
			ChunkAccess ca = chunkAccess.get(chunkKey);
			if (ca == null) {
				ca = new ChunkAccess(chunkKey);
				chunkAccess.put(chunkKey, ca);
			}
			return ca;
		}

		/**
		 * Number of sequential {@link ObjectIndexTable} lookups made by the
		 * reader. These were made without the support of batch lookups.
		 */
		public int cntObjectIndex_Load;

		/** Cycles detected in delta chains during OBJ_REF_DELTA reads. */
		public int deltaChainCycles;

		int recentChunks_Hits;

		int recentChunks_Miss;

		int deltaBaseCache_Hits;

		int deltaBaseCache_Miss;

		/** @return ratio of recent chunk hits, [0.00,1.00]. */
		public double getRecentChunksHitRatio() {
			int total = recentChunks_Hits + recentChunks_Miss;
			return ((double) recentChunks_Hits) / total;
		}

		/** @return ratio of delta base cache hits, [0.00,1.00]. */
		public double getDeltaBaseCacheHitRatio() {
			int total = deltaBaseCache_Hits + deltaBaseCache_Miss;
			return ((double) deltaBaseCache_Hits) / total;
		}

		/**
		 * @return collection of chunk accesses made by the application code
		 *         against this reader. The collection's iterator has no
		 *         relevant order.
		 */
		public Collection<ChunkAccess> getChunkAccess() {
			return chunkAccess.values();
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder();
			b.append("DhtReader.Statistics:\n");
			b.append(" ");
			if (recentChunks_Hits != 0 || recentChunks_Miss != 0)
				ratio(b, "recentChunks", getRecentChunksHitRatio());
			if (deltaBaseCache_Hits != 0 || deltaBaseCache_Miss != 0)
				ratio(b, "deltaBaseCache", getDeltaBaseCacheHitRatio());
			appendFields(this, b);
			b.append("\n");
			for (ChunkAccess ca : getChunkAccess()) {
				b.append("  ");
				b.append(ca.toString());
				b.append("\n");
			}
			return b.toString();
		}

		@SuppressWarnings("boxing")
		static void ratio(StringBuilder b, String name, double value) {
			b.append(String.format(" %s=%.2f%%", name, value * 100.0));
		}

		static void appendFields(Object obj, StringBuilder b) {
			try {
				for (Field field : obj.getClass().getDeclaredFields()) {
					String n = field.getName();

					if (field.getType() == Integer.TYPE
							&& (field.getModifiers() & Modifier.PUBLIC) != 0) {
						int v = field.getInt(obj);
						if (0 < v)
							b.append(' ').append(n).append('=').append(v);
					}
				}
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		/** Summary describing how a chunk was accessed. */
		public static final class ChunkAccess {
			/** Chunk this access block describes. */
			public final ChunkKey chunkKey;

			/**
			 * Number of times chunk was loaded sequentially. Incremented when
			 * the reader had to load the chunk on demand with no cache or
			 * prefetcher support.
			 */
			public int cntReader_Load;

			Throwable locReader_Load;

			/**
			 * Number of times the prefetcher loaded from the database.
			 * Incremented each time the prefetcher asked for the chunk from the
			 * underlying database (which might have its own distributed cache,
			 * or not).
			 */
			public int cntPrefetcher_Load;

			/**
			 * Number of times the prefetcher ordering was wrong. Incremented if
			 * a reader wants a chunk but the prefetcher didn't have it ready at
			 * the time of request. This indicates a bad prefetching plan as the
			 * chunk should have been listed earlier in the prefetcher's list.
			 */
			public int cntPrefetcher_OutOfOrder;

			/**
			 * Number of times the reader had to stall to wait for a chunk that
			 * is currently being prefetched to finish loading and become ready.
			 * This indicates the prefetcher may have fetched other chunks first
			 * (had the wrong order), or does not have a deep enough window to
			 * hide these loads from the application.
			 */
			public int cntPrefetcher_WaitedForLoad;

			/**
			 * Number of times the reader asked the prefetcher for the same
			 * chunk after it was already consumed from the prefetcher. This
			 * indicates the reader has walked back on itself and revisited a
			 * chunk again.
			 */
			public int cntPrefetcher_Revisited;

			/**
			 * Number of times the reader needed this chunk to copy an object
			 * as-is into a pack stream, but the prefetcher didn't have it
			 * ready. This correlates with {@link #cntPrefetcher_OutOfOrder} or
			 * {@link #cntPrefetcher_Revisited}.
			 */
			public int cntCopyObjectAsIs_PrefetchMiss;

			/**
			 * Number of times the reader tried to copy an object from this
			 * chunk, but discovered the chunk was corrupt or did not contain
			 * the object as expected.
			 */
			public int cntCopyObjectAsIs_InvalidChunk;

			ChunkAccess(ChunkKey key) {
				chunkKey = key;
			}

			@Override
			public String toString() {
				StringBuilder b = new StringBuilder();
				b.append(chunkKey).append('[');
				appendFields(this, b);
				b.append(" ]");
				if (locReader_Load != null) {
					StringWriter sw = new StringWriter();
					locReader_Load.printStackTrace(new PrintWriter(sw));
					b.append(sw);
				}
				return b.toString();
			}
		}
	}
}
