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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.zip.Inflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
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
import org.eclipse.jgit.storage.dht.RefData.IdWithChunk;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.PackWriter;

class DhtReader extends ObjectReader implements ObjectReuseAsIs {
	private final DhtRepository repository;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtReaderOptions options;

	private final RecentChunks recentChunks;

	private final DeltaBaseCache deltaBaseCache;

	private Collection<CachedPack> cachedPacks;

	private Inflater inflater;

	private Prefetcher prefetcher;

	DhtReader(DhtRepository repository, RepositoryKey repo, Database db) {
		this.repository = repository;
		this.repo = repo;
		this.db = db;
		this.options = DhtReaderOptions.DEFAULT;

		this.recentChunks = new RecentChunks(this);
		this.deltaBaseCache = new DeltaBaseCache(options);
	}

	DhtReaderOptions getOptions() {
		return options;
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
		return new DhtReader(repository, repo, db);
	}

	@Override
	public boolean has(AnyObjectId objId, int typeHint) throws IOException {
		if (objId instanceof RefData.IdWithChunk)
			return true;

		if (recentChunks.has(repo, objId))
			return true;

		if (repository.getRefDatabase().findChunk(objId) != null)
			return true;

		// TODO(spearce) This is expensive. Is it worthwhile?
		if (ChunkCache.get().find(repo, objId) != null)
			return true;

		return !find(ObjectIndexKey.create(repo, objId)).isEmpty();
	}

	@Override
	public ObjectLoader open(AnyObjectId objId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		ObjectLoader ldr = recentChunks.open(repo, objId, typeHint);
		if (ldr != null)
			return ldr;

		ChunkAndOffset p = getChunk(objId, typeHint, true, false);
		ldr = PackChunk.read(p.chunk, p.offset, this, typeHint);
		recentChunk(p.chunk);
		return ldr;
	}

	@Override
	public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
			Iterable<T> objectIds, boolean reportMissing) {
		return new OpenQueue<T>(repo, db, this, objectIds, reportMissing);
	}

	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		for (ObjectInfo info : find(ObjectIndexKey.create(repo, objectId)))
			return info.getSize();
		throw missing(objectId, typeHint);
	}

	@Override
	public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
			Iterable<T> objectIds, boolean reportMissing) {
		return new SizeQueue<T>(repo, db, options, objectIds, reportMissing);
	}

	@Override
	public void walkAdviceBeginCommits(RevWalk walk, Collection<RevCommit> roots)
			throws IOException {
		endPrefetch();

		prefetcher = new Prefetcher(db, options, OBJ_COMMIT);
		prefetcher.push(this, roots);
	}

	@Override
	public void walkAdviceBeginTrees(ObjectWalk ow, RevCommit min, RevCommit max)
			throws IOException {
		endPrefetch();

		if (min != null && min.getTree() != null) {
			prefetcher = new Prefetcher(db, options, OBJ_TREE);
			prefetcher.push(this, min.getTree());
		}
	}

	@Override
	public void walkAdviceEnd() {
		endPrefetch();
	}

	void recentChunk(PackChunk chunk) {
		recentChunks.put(chunk);
	}

	ChunkAndOffset getChunk(AnyObjectId objId, int typeHint)
			throws DhtException, MissingObjectException {
		return getChunk(objId, typeHint, true /* load */, true /* recent */);
	}

	ChunkAndOffset getChunkGently(AnyObjectId objId, int typeHint)
			throws DhtException, MissingObjectException {
		return getChunk(objId, typeHint, false /* no load */, true /* recent */);
	}

	private ChunkAndOffset getChunk(AnyObjectId objId, int typeHint,
			boolean loadIfRequired, boolean checkRecent) throws DhtException,
			MissingObjectException {
		if (checkRecent) {
			ChunkAndOffset r = recentChunks.find(repo, objId);
			if (r != null)
				return r;
		}

		ChunkKey key;
		if (objId instanceof RefData.IdWithChunk)
			key = ((RefData.IdWithChunk) objId).getChunkKey();
		else
			key = repository.getRefDatabase().findChunk(objId);
		if (key != null) {
			PackChunk chunk = ChunkCache.get().get(key);
			if (chunk != null) {
				int pos = chunk.findOffset(repo, objId);
				if (0 <= pos)
					return new ChunkAndOffset(chunk, pos);
			}

			if (loadIfRequired) {
				chunk = load(key);
				if (chunk != null && chunk.hasIndex()) {
					int pos = chunk.findOffset(repo, objId);
					if (0 <= pos) {
						chunk = ChunkCache.get().put(chunk);
						return new ChunkAndOffset(chunk, pos);
					}
				}
			}

			// The hint above is stale. Fall through and do a
			// more exhaustive lookup to find the object.
		}

		ChunkAndOffset r = ChunkCache.get().find(repo, objId);
		if (r != null)
			return r;

		if (!loadIfRequired)
			return null;

		if (prefetcher != null && prefetcher.isType(typeHint)) {
			r = prefetcher.find(repo, objId);
			if (r != null)
				return r;
		}

		for (ObjectInfo link : find(ObjectIndexKey.create(repo, objId))) {
			PackChunk chunk;

			if (prefetcher != null && prefetcher.isType(typeHint)) {
				chunk = prefetcher.get(link.getChunkKey());
				if (chunk == null) {
					chunk = load(link.getChunkKey());
					if (chunk == null)
						continue;
					prefetcher.push(chunk.getMeta());
				}
			} else {
				chunk = load(link.getChunkKey());
				if (chunk == null)
					continue;
			}

			if (chunk.hasIndex())
				chunk = ChunkCache.get().put(chunk);
			return new ChunkAndOffset(chunk, link.getOffset());
		}

		throw missing(objId, typeHint);
	}

	ChunkKey findChunk(AnyObjectId objId) throws DhtException {
		if (objId instanceof IdWithChunk)
			return ((IdWithChunk) objId).getChunkKey();

		ChunkKey key = repository.getRefDatabase().findChunk(objId);
		if (key != null)
			return key;

		ChunkAndOffset r = recentChunks.find(repo, objId);
		if (r != null)
			return r.chunk.getChunkKey();

		r = ChunkCache.get().find(repo, objId);
		if (r != null)
			return r.chunk.getChunkKey();

		for (ObjectInfo link : find(ObjectIndexKey.create(repo, objId)))
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

		chunk = ChunkCache.get().get(key);
		if (chunk != null)
			return chunk;

		chunk = load(key);
		if (chunk != null) {
			if (chunk.hasIndex())
				return ChunkCache.get().put(chunk);
			return chunk;
		}

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
		// TODO(spearce) If the object is in lastChunk we may be able
		// to prefill the selection to use lastChunk and avoid looking it up.
		return new DhtObjectToPack(obj);
	}

	@SuppressWarnings("unchecked")
	public void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException {
		Iterable itr = objects;
		new RepresentationSelector(packer, repo, db, this, monitor).select(itr);
	}

	private void endPrefetch() {
		if (prefetcher != null) {
			prefetcher.cancel();
			prefetcher = null;
		}
	}

	@SuppressWarnings("unchecked")
	public void writeObjects(PackOutputStream out, List<ObjectToPack> objects)
			throws IOException {
		Prefetcher p = new Prefetcher(db, options, 0);
		try {
			List itr = objects;
			new ObjectWriter(p).writeObjects(out, itr);
		} finally {
			p.cancel();
		}
	}

	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp)
			throws IOException, StoredObjectRepresentationNotAvailableException {
		try {
			DhtObjectToPack obj = (DhtObjectToPack) otp;
			PackChunk.copyAsIs(getChunk(obj.chunk), out, obj, this);
		} catch (DhtMissingChunkException missingChunk) {
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

	public void copyPackAsIs(PackOutputStream out, CachedPack pack)
			throws IOException {
		Prefetcher p = new Prefetcher(db, options, 0);
		try {
			p.setCacheLoadedChunks(false);
			((DhtCachedPack) pack).copyAsIs(out, p, this);
		} finally {
			p.cancel();
		}
	}

	private List<ObjectInfo> find(ObjectIndexKey idxKey) throws DhtException {
		Context opt = Context.READ_REPAIR;
		Sync<Map<ObjectIndexKey, Collection<ObjectInfo>>> sync = Sync.create();
		db.objectIndex().get(opt, Collections.singleton(idxKey), sync);
		try {
			Collection<ObjectInfo> m;

			m = sync.get(options.getTimeout()).get(idxKey);
			if (m == null || m.isEmpty())
				return Collections.emptyList();

			List<ObjectInfo> t = new ArrayList<ObjectInfo>(m);
			ObjectInfo.sort(t);
			return t;
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private PackChunk load(ChunkKey chunkKey) throws DhtException {
		Context opt = Context.READ_REPAIR;
		Sync<Collection<PackChunk.Members>> sync = Sync.create();
		db.chunk().get(opt, Collections.singleton(chunkKey), sync);
		try {
			Collection<PackChunk.Members> c = sync.get(options.getTimeout());
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
}
